package org.app.service;

import org.app.logging.AppLogger;
import org.app.logging.NoOpLogger;
import org.app.model.GoodsItem;
import org.app.scraper.WebScrapingService;

import java.util.ArrayList;
import java.util.HashMap;
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
                    description = fetchDescription(harmonized);
                    if (DEFAULT_DESCRIPTION.equals(description)) {
                        fallbackCount++;
                    } else {
                        description = trimToFirstComma(description);
                    }
                } catch (Exception ex) {
                    taricUnavailable = true;
                    fallbackCount++;
                    description = DEFAULT_DESCRIPTION;
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
        String digits = hsCode.replaceAll("^\\D+", "");
        if (digits.length() < 6) {
            return null;
        }
        return digits.substring(0, 6);
    }

    private String fetchDescription(String harmonized) throws Exception {
        try {
            WebScrapingService.TaricResult info = scraper.getTaricInfo(harmonized);
            String desc = info.getDescription();
            if (desc != null && !desc.trim().isEmpty()) {
                return desc.trim();
            }
        } catch (Exception ex) {
            throw ex;
        }
        return DEFAULT_DESCRIPTION;
    }

    private String trimToFirstComma(String description) {
        if (description == null) {
            return null;
        }
        int commaIndex = description.indexOf(',');
        if (commaIndex == -1) {
            return description;
        }
        return description.substring(0, commaIndex).trim();
    }
}
