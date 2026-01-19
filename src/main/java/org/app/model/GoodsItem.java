package org.app.model;

import java.math.BigDecimal;

public class GoodsItem {
    private final String hsCode;
    private final String kgText;
    private final String valueText;
    private final BigDecimal valueAmount;
    private final String parcelsText;
    private String descriptionOfGoods;

    public GoodsItem(String hsCode, String kgText, String valueText, BigDecimal valueAmount, String parcelsText) {
        this.hsCode = hsCode;
        this.kgText = kgText;
        this.valueText = valueText;
        this.valueAmount = valueAmount;
        this.parcelsText = parcelsText;
        this.descriptionOfGoods = "";
    }

    public String getHsCode() {
        return hsCode;
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
}
