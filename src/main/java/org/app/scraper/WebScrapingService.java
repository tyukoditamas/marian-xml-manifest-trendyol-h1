package org.app.scraper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.util.regex.Pattern;

public class WebScrapingService {

    private static final String BASE =
            "http://taric3.customs.ro:9080/taric/web/browsetariffi2_RO";

    public static class TaricResult {
        private final String description;
        private final String subCode;

        public TaricResult(String description, String subCode) {
            this.description = description;
            this.subCode = subCode;
        }

        public String getDescription() {
            return description;
        }

        public String getSubCode() {
            return subCode;
        }
    }

    public TaricResult getTaricInfo(String hsCode) throws Exception {
        String raw = hsCode.replaceAll("\\s+", "");
        LocalDate now = LocalDate.now();

        Connection.Response resp = Jsoup.connect(BASE)
                .userAgent("Mozilla/5.0")
                .ignoreContentType(true)
                .method(Connection.Method.GET)
                .data("issection", "n")
                .data("expandelem", raw)
                .data("importbutton", "Navigare nomenclatura-import")
                .data("Country", "--------")
                .data("Year", String.valueOf(now.getYear()))
                .data("Month", String.format("%02d", now.getMonthValue()))
                .data("Day", String.format("%02d", now.getDayOfMonth()))
                .data("startpos", "1")
                .data("ctmode", "C")
                .execute();

        Document doc = resp.parse();

        Element codeAnchor = doc.selectFirst(
                "a[href*=\"expandelem=" + raw + "\"]"
        );
        Element codeTr = null;
        if (codeAnchor != null) {
            codeTr = codeAnchor.closest("tr");
        }

        String subTwo = "";
        if (codeTr != null) {
            String code6 = raw.length() >= 6 ? raw.substring(0, 6) : raw;
            for (Element next = codeTr.nextElementSibling();
                 next != null;
                 next = next.nextElementSibling()) {
                Elements tds = next.select("td");
                if (tds.size() >= 2) {
                    String digitsOnly = tds.get(0).text()
                            .replaceAll("\\D+", "");
                    if (digitsOnly.startsWith(code6) && digitsOnly.length() > code6.length()) {
                        if (digitsOnly.length() >= 8) {
                            subTwo = digitsOnly.substring(6, 8);
                        }
                        break;
                    }
                }
            }
        }

        String code4 = raw.length() >= 4 ? raw.substring(0, 4) : raw;
        String desc4 = findRowDescription(doc, code4);
        if (!desc4.isEmpty()) {
            return new TaricResult(desc4, subTwo);
        }

        if (codeAnchor != null) {

            String header = "";
            for (Element prev = codeTr.previousElementSibling();
                 prev != null;
                 prev = prev.previousElementSibling()) {
                Elements tds = prev.select("td");
                if (tds.size() >= 2) {
                    String left = tds.get(0).text()
                            .replace("\u00A0", "")
                            .trim();
                    if (left.isEmpty()) {
                        header = tds.get(1).text()
                                .replaceFirst("^-+\\s*", "")
                                .trim();
                        break;
                    }
                }
            }

            if (header.isEmpty() && raw.length() >= 6 && raw.endsWith("00")) {
                String grp4 = raw.substring(0, 4);
                Element grpAnchor = doc.selectFirst(
                        "a[name=POS][href*=\"expandelem=" + grp4 + "\"]"
                );
                if (grpAnchor != null) {
                    Element headerRow = grpAnchor.parent().nextElementSibling();
                    if (headerRow != null) {
                        header = headerRow.text()
                                .replaceFirst("^-+\\s*", "")
                                .trim();
                    }
                }
            }

            final String descBase;
            if (!header.isEmpty() && !"Altele".equalsIgnoreCase(header)) {
                descBase = header;
            } else {
                Element inline = codeAnchor.parent().nextElementSibling();
                descBase = (inline != null
                        ? inline.text().replaceFirst("^-+\\s*", "").trim()
                        : "");
            }
            return new TaricResult(descBase, subTwo);
        }

        String code6 = raw.length() > 6 ? raw.substring(0, 6) : raw;
        String regex = "^\\s*" + Pattern.quote(code6) + "\\s*$";
        Element td = doc.selectFirst("td:matchesOwn(" + regex + ")");
        String fallbackDesc = "";
        if (td != null && td.nextElementSibling() != null) {
            fallbackDesc = td.nextElementSibling().text()
                    .replaceFirst("^-+\\s*", "")
                    .trim();
        }

        return new TaricResult(fallbackDesc, "");
    }

    private String findRowDescription(Document doc, String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        for (Element tr : doc.select("tr")) {
            Elements tds = tr.select("td");
            if (tds.size() >= 2) {
                String digits = tds.get(0).text().replaceAll("\\D+", "");
                if (digits.equals(code)) {
                    return tds.get(1).text()
                            .replaceFirst("^-+\\s*", "")
                            .trim();
                }
            }
        }
        return "";
    }
}
