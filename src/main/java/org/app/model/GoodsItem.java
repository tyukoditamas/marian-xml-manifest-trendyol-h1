package org.app.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GoodsItem {
    private final String hsCode;
    private final String hawb;
    private final List<String> hawbs;
    private final String originCountry;
    private final String kgText;
    private final String valueText;
    private final BigDecimal valueAmount;
    private final String parcelsText;
    private String descriptionOfGoods;

    public GoodsItem(String hsCode, String hawb, String originCountry, String kgText, String valueText,
                     BigDecimal valueAmount, String parcelsText) {
        this(hsCode, hawb, originCountry, kgText, valueText, valueAmount, parcelsText,
                Collections.singletonList(hawb));
    }

    public GoodsItem(String hsCode, String hawb, String originCountry, String kgText, String valueText,
                     BigDecimal valueAmount, String parcelsText, List<String> hawbs) {
        this.hsCode = hsCode;
        this.hawbs = normalizeHawbs(hawbs);
        this.hawb = this.hawbs.isEmpty() ? hawb : this.hawbs.get(0);
        this.originCountry = originCountry;
        this.kgText = kgText;
        this.valueText = valueText;
        this.valueAmount = valueAmount;
        this.parcelsText = parcelsText;
        this.descriptionOfGoods = "";
    }

    public String getHsCode() {
        return hsCode;
    }

    public String getHawb() {
        return hawb;
    }

    public List<String> getHawbs() {
        return hawbs;
    }

    public String getOriginCountry() {
        return originCountry;
    }

    public String getKgText() {
        return kgText;
    }

    public String getValueText() {
        return valueText;
    }

    public BigDecimal getValueAmount() {
        return valueAmount;
    }

    public String getParcelsText() {
        return parcelsText;
    }

    public String getDescriptionOfGoods() {
        return descriptionOfGoods;
    }

    public void setDescriptionOfGoods(String descriptionOfGoods) {
        this.descriptionOfGoods = descriptionOfGoods == null ? "" : descriptionOfGoods;
    }

    private static List<String> normalizeHawbs(List<String> hawbs) {
        if (hawbs == null || hawbs.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> normalized = new ArrayList<>();
        for (String hawb : hawbs) {
            if (hawb == null || hawb.trim().isEmpty()) {
                continue;
            }
            normalized.add(hawb.trim());
        }
        return Collections.unmodifiableList(normalized);
    }
}
