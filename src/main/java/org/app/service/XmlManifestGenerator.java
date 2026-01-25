package org.app.service;

import org.app.logging.AppLogger;
import org.app.logging.NoOpLogger;
import org.app.model.GoodsItem;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class XmlManifestGenerator {
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
    private final AppLogger logger;

    public XmlManifestGenerator(AppLogger logger) {
        this.logger = logger == null ? new NoOpLogger() : logger;
    }

    public File generate(List<GoodsItem> items, String lrn, File outputFile) throws Exception {
        Document document = loadTemplate();
        Element root = document.getRootElement();
        Namespace ns2 = root.getNamespace();
        Namespace commonNs = root.getNamespace("");
        Namespace ns3 = root.getNamespace("ns3");

        updatePreparationTime(root, ns2, commonNs);
        updateLrn(root, ns2, ns3, lrn);
        updateGoodsShipment(root, ns2, ns3, items);
        updateCurrencyExchange(root, ns2, ns3);

        writeXml(document, outputFile);
        logger.info("XML scris pe disc.");
        return outputFile;
    }

    private Document loadTemplate() throws Exception {
        SAXBuilder builder = new SAXBuilder();
        InputStream stream = XmlManifestGenerator.class.getResourceAsStream("/test/example.xml");
        if (stream != null) {
            return builder.build(stream);
        }
        try (InputStream fileStream = new FileInputStream("src/main/resources/test/example.xml")) {
            return builder.build(fileStream);
        }
    }

    private void updatePreparationTime(Element root, Namespace ns2, Namespace commonNs) {
        Element message = root.getChild("MESSAGE", ns2);
        if (message == null) {
            return;
        }
        Element prep = message.getChild("PreparationDateAndTime", commonNs);
        if (prep != null) {
            prep.setText(LocalDateTime.now().format(DATE_TIME_FORMATTER));
        }
    }

    private void updateLrn(Element root, Namespace ns2, Namespace ns3, String lrn) {
        Element importOperation = root.getChild("ImportOperation", ns2);
        if (importOperation == null) {
            return;
        }
        Element lrnElement = importOperation.getChild("LRN", ns3);
        if (lrnElement != null) {
            lrnElement.setText(lrn);
        }
    }

    private void updateCurrencyExchange(Element root, Namespace ns2, Namespace ns3) {
        Element currencyExchange = root.getChild("CurrencyExchange", ns2);
        if (currencyExchange == null) {
            return;
        }
        setText(currencyExchange, "internalCurrencyUnit", ns3, "EUR");
    }

    private void updateGoodsShipment(Element root, Namespace ns2, Namespace ns3, List<GoodsItem> items) {
        Element goodsShipment = root.getChild("GoodsShipment", ns2);
        if (goodsShipment == null) {
            return;
        }

        Element totalAmount = goodsShipment.getChild("totalAmountInvoiced", ns3);
        if (totalAmount != null) {
            totalAmount.setText(totalAmount(items));
        }

        List<Element> existingItems = goodsShipment.getChildren("GoodsShipmentItem", ns3);
        if (existingItems.isEmpty()) {
            return;
        }
        Element itemTemplate = existingItems.get(0).clone();
        goodsShipment.removeChildren("GoodsShipmentItem", ns3);

        Element existingEvaluationNote = goodsShipment.getChild("EvaluationNote", ns3);
        Element evaluationNote = new Element("EvaluationNote", ns3);
        Element enTemplate = null;
        if (existingEvaluationNote != null) {
            List<Element> enItems = existingEvaluationNote.getChildren("ENForGoods", ns3);
            if (!enItems.isEmpty()) {
                enTemplate = enItems.get(0).clone();
            }
            goodsShipment.removeContent(existingEvaluationNote);
        }
        if (enTemplate == null) {
            enTemplate = new Element("ENForGoods", ns3);
            enTemplate.addContent(new Element("GoodsItemNumber", ns3));
            enTemplate.addContent(new Element("ValueTyp", ns3).setText("1"));
            enTemplate.addContent(new Element("CurValue", ns3));
            enTemplate.addContent(new Element("CurCod", ns3).setText("EUR"));
        }

        int itemNumber = 1;
        for (GoodsItem item : items) {
            Element newItem = itemTemplate.clone();
            updateGoodsShipmentItem(newItem, ns3, itemNumber, item);
            goodsShipment.addContent(newItem);

            Element enForGoods = enTemplate.clone();
            setText(enForGoods, "GoodsItemNumber", ns3, String.valueOf(itemNumber));
            setText(enForGoods, "CurValue", ns3, item.getValueText());
            setText(enForGoods, "CurCod", ns3, "EUR");
            evaluationNote.addContent(enForGoods);

            itemNumber++;
        }

        goodsShipment.addContent(evaluationNote);
    }

    private void updateGoodsShipmentItem(Element goodsItem, Namespace ns3, int itemNumber, GoodsItem item) {
        setText(goodsItem, "declarationGoodsItemNumber", ns3, String.valueOf(itemNumber));

        insertPreviousDocument(goodsItem, ns3, item.getHawb());

        Element commodity = goodsItem.getChild("Commodity", ns3);
        if (commodity != null) {
            setText(commodity, "descriptionOfGoods", ns3, item.getDescriptionOfGoods());

            Element commodityCode = commodity.getChild("CommodityCode", ns3);
            if (commodityCode != null) {
                String hsCode = item.getHsCode();
                setText(commodityCode, "harmonizedSystemSubheadingCode", ns3, safeSubstring(hsCode, 0, 6));
                setText(commodityCode, "combinedNomenclatureCode", ns3, safeSubstring(hsCode, 6, 8));
                setText(commodityCode, "taricCode", ns3, safeSubstring(hsCode, 8, 10));
            }

            Element goodsMeasure = commodity.getChild("GoodsMeasure", ns3);
            if (goodsMeasure != null) {
                setText(goodsMeasure, "grossMass", ns3, item.getKgText());
                setText(goodsMeasure, "netMass", ns3, netMassText(item.getKgText()));
                setText(goodsMeasure, "suppUnitAmount", ns3, item.getParcelsText());
            }

            Element invoiceLine = commodity.getChild("InvoiceLine", ns3);
            if (invoiceLine != null) {
                setText(invoiceLine, "itemAmountInvoiced", ns3, item.getValueText());
            }
        }

        Element packaging = goodsItem.getChild("Packaging", ns3);
        if (packaging != null) {
            setText(packaging, "numberOfPackages", ns3, item.getParcelsText());
        }

        Element origin = goodsItem.getChild("Origin", ns3);
        if (origin != null) {
            setText(origin, "countryOfOrigin", ns3, "TR");
        }
    }

    private void insertPreviousDocument(Element goodsItem, Namespace ns3, String hawb) {
        if (hawb == null || hawb.trim().isEmpty()) {
            return;
        }

        goodsItem.removeChildren("PreviousDocument", ns3);

        Element previousDocument = new Element("PreviousDocument", ns3);
        previousDocument.addContent(new Element("sequenceNumber", ns3).setText("1"));
        previousDocument.addContent(new Element("referenceNumber", ns3).setText(hawb.trim()));
        previousDocument.addContent(new Element("type", ns3).setText("N740"));

        List<Element> additionalRefs = goodsItem.getChildren("AdditionalReference", ns3);
        if (!additionalRefs.isEmpty()) {
            int index = goodsItem.indexOf(additionalRefs.get(0));
            if (index < 0) {
                goodsItem.addContent(previousDocument);
            } else {
                goodsItem.addContent(index, previousDocument);
            }
            return;
        }

        Element procedure = goodsItem.getChild("Procedure", ns3);
        if (procedure != null) {
            int index = goodsItem.indexOf(procedure);
            if (index < 0) {
                goodsItem.addContent(previousDocument);
            } else {
                goodsItem.addContent(index + 1, previousDocument);
            }
            return;
        }

        goodsItem.addContent(previousDocument);
    }

    private String totalAmount(List<GoodsItem> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (GoodsItem item : items) {
            total = total.add(item.getValueAmount());
        }
        return total.stripTrailingZeros().toPlainString();
    }

    private void setText(Element parent, String childName, Namespace namespace, String value) {
        Element child = parent.getChild(childName, namespace);
        if (child != null) {
            child.setText(value == null ? "" : value);
        }
    }

    private String safeSubstring(String value, int start, int end) {
        if (value == null || value.length() <= start) {
            return "";
        }
        return value.substring(start, Math.min(end, value.length()));
    }

    private String netMassText(String grossMassText) {
        if (grossMassText == null || grossMassText.trim().isEmpty()) {
            return "";
        }
        try {
            BigDecimal gross = new BigDecimal(grossMassText.trim());
            BigDecimal net = gross.multiply(new BigDecimal("0.95"));
            return net.stripTrailingZeros().toPlainString();
        } catch (NumberFormatException ex) {
            return grossMassText;
        }
    }

    private void writeXml(Document document, File outputFile) throws IOException {
        Format format = Format.getPrettyFormat();
        format.setIndent("   ");
        format.setLineSeparator("\n");
        XMLOutputter outputter = new XMLOutputter(format);
        try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
            outputter.output(document, outputStream);
        }
    }
}
