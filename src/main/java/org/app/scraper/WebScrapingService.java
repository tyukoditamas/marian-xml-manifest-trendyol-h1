package org.app.scraper;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class WebScrapingService {

    private static final String BASE =
            "http://taric3.customs.ro:9080/taric/web/browsetariffi2_RO";

    public static class TaricResult {
        private final String description;
        private final String subCode;
        private final String correctedCode;

        public TaricResult(String description, String subCode, String correctedCode) {
            this.description = description;
            this.subCode = subCode;
            this.correctedCode = correctedCode;
        }

        public String getDescription() {
            return description;
        }

        public String getSubCode() {
            return subCode;
        }

        public String getCorrectedCode() {
            return correctedCode;
        }
    }

    public TaricResult getTaricInfo(String hsCode) throws Exception {
        String raw = normalizeCode(hsCode);
        if (raw.length() < 6) {
            return new TaricResult("", "", raw);
        }

        String queryCode = raw.substring(0, 6);
        List<TaricRow> rows = fetchRows(queryCode);
        if (raw.length() >= 8) {
            String queryEight = raw.substring(0, 8);
            if (!queryEight.equals(queryCode)) {
                rows.addAll(fetchRows(queryEight));
            }
        }
        rows = reindex(rows);

        TaricRow selected = selectCorrectedRow(rows, raw);
        if (selected == null) {
            return new TaricResult("", "", raw);
        }

        String correctedCode = canonicalCode(selected.digits);
        if (correctedCode.isEmpty()) {
            correctedCode = raw;
        }

        String header = findNearestHeader(rows, selected.index);
        String description = isUsableDescription(header) ? header : selected.description;
        return new TaricResult(description, subCode(correctedCode), correctedCode);
    }

    private List<TaricRow> fetchRows(String queryCode) throws Exception {
        LocalDate now = LocalDate.now();
        Connection.Response resp = Jsoup.connect(BASE)
                .userAgent("Mozilla/5.0")
                .ignoreContentType(true)
                .method(Connection.Method.GET)
                .data("issection", "n")
                .data("expandelem", queryCode)
                .data("importbutton", "Navigare nomenclatura-import")
                .data("Country", "--------")
                .data("Year", String.valueOf(now.getYear()))
                .data("Month", String.format("%02d", now.getMonthValue()))
                .data("Day", String.format("%02d", now.getDayOfMonth()))
                .data("startpos", "1")
                .data("ctmode", "C")
                .execute();
        return parseRows(resp.parse());
    }

    private List<TaricRow> parseRows(Document doc) {
        List<TaricRow> rows = new ArrayList<>();
        for (Element tr : doc.select("tr")) {
            Elements tds = directCells(tr);
            if (tds.size() >= 2) {
                String first = tds.get(0).text().replace('\u00A0', ' ').trim();
                String digits = normalizeCode(first);
                String description = cleanDescription(tds.get(1).text());
                if (!description.isEmpty()) {
                    rows.add(new TaricRow(rows.size(), digits, description));
                }
            }
        }
        return rows;
    }

    private List<TaricRow> reindex(List<TaricRow> rows) {
        List<TaricRow> reindexed = new ArrayList<>();
        for (TaricRow row : rows) {
            reindexed.add(new TaricRow(reindexed.size(), row.digits, row.description));
        }
        return reindexed;
    }

    private Elements directCells(Element tr) {
        Elements cells = new Elements();
        for (Element child : tr.children()) {
            if ("td".equalsIgnoreCase(child.tagName())) {
                cells.add(child);
            }
        }
        return cells;
    }

    private TaricRow selectCorrectedRow(List<TaricRow> rows, String raw) {
        TaricRow exactTen = findExactTen(rows, raw);
        if (exactTen != null) {
            return exactTen;
        }

        TaricRow tenDigitChild = findTenDigitChild(rows, raw);
        if (tenDigitChild != null) {
            return tenDigitChild;
        }

        TaricRow exactCanonical = findExactCanonical(rows, raw);
        if (exactCanonical != null) {
            return exactCanonical;
        }

        return findClosest(rows, raw);
    }

    private TaricRow findExactTen(List<TaricRow> rows, String raw) {
        for (TaricRow row : rows) {
            if (row.digits.length() == 10 && raw.equals(row.digits) && isUsableDescription(row.description)) {
                return row;
            }
        }
        return null;
    }

    private TaricRow findTenDigitChild(List<TaricRow> rows, String raw) {
        if (raw.length() < 8 || !raw.endsWith("00")) {
            return null;
        }
        String prefixEight = raw.substring(0, 8);
        for (TaricRow row : rows) {
            if (row.digits.length() == 10
                    && row.digits.startsWith(prefixEight)
                    && isUsableDescription(row.description)) {
                return row;
            }
        }
        return null;
    }

    private TaricRow findExactCanonical(List<TaricRow> rows, String raw) {
        for (TaricRow row : rows) {
            if (raw.equals(canonicalCode(row.digits)) && isUsableDescription(row.description)) {
                return row;
            }
        }
        return null;
    }

    private TaricRow findClosest(List<TaricRow> rows, String raw) {
        TaricRow closest = null;
        int bestScore = 0;
        int bestLength = 0;
        for (TaricRow row : rows) {
            if (row.digits.isEmpty() || !isUsableDescription(row.description)) {
                continue;
            }
            String candidate = canonicalCode(row.digits);
            int score = commonPrefixLength(raw, candidate);
            if (score < 4) {
                continue;
            }
            if (score > bestScore || (score == bestScore && candidate.length() > bestLength)) {
                closest = row;
                bestScore = score;
                bestLength = candidate.length();
            }
        }
        return closest;
    }

    private String findNearestHeader(List<TaricRow> rows, int beforeIndex) {
        for (int i = beforeIndex - 1; i >= 0; i--) {
            TaricRow row = rows.get(i);
            if (row.digits.isEmpty() && isUsableDescription(row.description)) {
                return row.description;
            }
        }
        return "";
    }

    private int commonPrefixLength(String left, String right) {
        int max = Math.min(left.length(), right.length());
        int count = 0;
        while (count < max && left.charAt(count) == right.charAt(count)) {
            count++;
        }
        return count;
    }

    private String subCode(String code) {
        return code.length() >= 8 ? code.substring(6, 8) : "";
    }

    private String canonicalCode(String code) {
        if (code == null || code.isEmpty()) {
            return "";
        }
        if (code.length() >= 10) {
            return code.substring(0, 10);
        }
        if (code.length() == 8) {
            return code + "00";
        }
        if (code.length() == 6) {
            return code + "0000";
        }
        return code;
    }

    private String normalizeCode(String value) {
        if (value == null) {
            return "";
        }
        String digits = value.replaceAll("\\D+", "");
        return digits.length() > 10 ? digits.substring(0, 10) : digits;
    }

    private String cleanDescription(String description) {
        if (description == null) {
            return "";
        }
        return description
                .replaceFirst("(?i)\\s*Nota de subsol\\s+TN\\d+\\s*$", "")
                .replaceAll("^[\\s\\-]+", "")
                .trim();
    }

    private boolean isUsableDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return false;
        }
        return !"Altele".equalsIgnoreCase(description.trim());
    }

    private static class TaricRow {
        private final int index;
        private final String digits;
        private final String description;

        private TaricRow(int index, String digits, String description) {
            this.index = index;
            this.digits = digits;
            this.description = description;
        }
    }
}
