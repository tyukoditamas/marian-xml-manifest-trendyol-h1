package org.app.service;

import org.app.logging.AppLogger;
import org.app.logging.NoOpLogger;
import org.app.model.GoodsItem;
import org.app.scraper.WebScrapingService;

import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class GoodsDescriptionResolver {
    private static final String DEFAULT_DESCRIPTION = "marfuri industriale";

    private final WebScrapingService scraper = new WebScrapingService();
    private final AppLogger logger;
    private final ProgressListener progressListener;
    private final Map<String, String> descriptionCache = new HashMap<>();
    private final Map<String, String> correctedCodeCache = new HashMap<>();

    public GoodsDescriptionResolver(AppLogger logger, ProgressListener progressListener) {
        this.logger = logger == null ? new NoOpLogger() : logger;
        this.progressListener = progressListener == null ? new NoOpProgressListener() : progressListener;
    }

    public void applyDescriptions(List<GoodsItem> items) {
        Set<String> uniqueCodes = new TreeSet<>();
        List<GoodsItem> invalidItems = new ArrayList<>();

        for (GoodsItem item : items) {
            String harmonized = normalizeHsCode(item.getHsCode());
            if (harmonized == null) {
                invalidItems.add(item);
            } else {
                uniqueCodes.add(harmonized);
            }
        }

        if (!uniqueCodes.isEmpty()) {
            logger.info("Preluare descrieri TARIC pentru " + uniqueCodes.size() + " coduri unice.");
        }

        int fallbackCount = 0;
        boolean taricUnavailable = false;
        int total = uniqueCodes.size();
        int processed = 0;
        progressListener.onStart("taric", total);
        for (String harmonized : uniqueCodes) {
            String description;
            if (taricUnavailable) {
                description = DEFAULT_DESCRIPTION;
                fallbackCount++;
            } else {
                try {
                    WebScrapingService.TaricResult info = fetchTaricInfo(harmonized);
                    description = descriptionOrDefault(info);
                    correctedCodeCache.put(harmonized, correctedCodeOrOriginal(info, harmonized));
                    if (DEFAULT_DESCRIPTION.equals(description)) {
                        fallbackCount++;
                    }
                } catch (Exception ex) {
                    taricUnavailable = true;
                    fallbackCount++;
                    description = DEFAULT_DESCRIPTION;
                    correctedCodeCache.put(harmonized, harmonized);
                    logger.error("Eroare la interogarea TARIC. Se foloseste descriere implicita.", ex);
                }
            }
            descriptionCache.put(harmonized, description);
            processed++;
            progressListener.onProgress(processed, total);
        }

        for (GoodsItem item : items) {
            String harmonized = normalizeHsCode(item.getHsCode());
            String description = harmonized == null
                    ? DEFAULT_DESCRIPTION
                    : descriptionCache.getOrDefault(harmonized, DEFAULT_DESCRIPTION);
            item.setDescriptionOfGoods(description);
        }
        regroupByCorrectedCode(items);

        if (!uniqueCodes.isEmpty()) {
            logger.info("Descrieri TARIC finalizate. Coduri: " + uniqueCodes.size()
                    + ", fallback: " + fallbackCount + ".");
        }
        if (!invalidItems.isEmpty()) {
            logger.warn("Au fost gasite " + invalidItems.size() + " linii fara HS valid.");
        }
        progressListener.onDone("taric");
    }

    private String normalizeHsCode(String hsCode) {
        if (hsCode == null) {
            return null;
        }
        String digits = hsCode.replaceAll("\\D+", "");
        if (digits.length() < 6) {
            return null;
        }
        return digits.substring(0, Math.min(10, digits.length()));
    }

    private WebScrapingService.TaricResult fetchTaricInfo(String harmonized) throws Exception {
        return scraper.getTaricInfo(harmonized);
    }

    private String descriptionOrDefault(WebScrapingService.TaricResult info) {
        String desc = info.getDescription();
        if (desc != null && !desc.trim().isEmpty()) {
            return desc.trim();
        }
        return DEFAULT_DESCRIPTION;
    }

    private String correctedCodeOrOriginal(WebScrapingService.TaricResult info, String originalCode) {
        String correctedCode = normalizeHsCode(info.getCorrectedCode());
        return correctedCode == null ? originalCode : correctedCode;
    }

    private void regroupByCorrectedCode(List<GoodsItem> items) {
        Map<String, ItemTotals> grouped = new java.util.TreeMap<>();
        for (GoodsItem item : items) {
            String originalCode = normalizeHsCode(item.getHsCode());
            String correctedCode = originalCode == null
                    ? item.getHsCode()
                    : correctedCodeCache.getOrDefault(originalCode, originalCode);
            grouped.computeIfAbsent(correctedCode, key -> new ItemTotals())
                    .add(item, correctedCode);
        }

        items.clear();
        for (Map.Entry<String, ItemTotals> entry : grouped.entrySet()) {
            ItemTotals totals = entry.getValue();
            GoodsItem merged = new GoodsItem(entry.getKey(), totals.firstHawb, totals.firstOriginCountry,
                    formatNumeric(totals.kg), formatNumeric(totals.value), totals.value,
                    formatNumeric(totals.parcels), new ArrayList<>(totals.hawbs));
            merged.setDescriptionOfGoods(totals.description);
            items.add(merged);
        }
    }

    private BigDecimal parseNumeric(String value) {
        if (value == null || value.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.trim());
    }

    private String formatNumeric(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private class ItemTotals {
        private BigDecimal kg = BigDecimal.ZERO;
        private BigDecimal value = BigDecimal.ZERO;
        private BigDecimal parcels = BigDecimal.ZERO;
        private final Set<String> hawbs = new LinkedHashSet<>();
        private String firstHawb = "";
        private String firstOriginCountry = "";
        private String description = "";

        private void add(GoodsItem item, String correctedCode) {
            kg = kg.add(parseNumeric(item.getKgText()));
            value = value.add(item.getValueAmount() == null ? BigDecimal.ZERO : item.getValueAmount());
            parcels = parcels.add(parseNumeric(item.getParcelsText()));
            hawbs.addAll(item.getHawbs());
            if (firstHawb.isEmpty()) {
                firstHawb = item.getHawb();
            }
            if (firstOriginCountry.isEmpty()) {
                firstOriginCountry = item.getOriginCountry();
            }
            if (description.isEmpty()) {
                description = item.getDescriptionOfGoods();
            }
            if (description.isEmpty()) {
                String correctedDescription = descriptionCache.get(correctedCode);
                description = correctedDescription == null ? DEFAULT_DESCRIPTION : correctedDescription;
            }
        }
    }
}
