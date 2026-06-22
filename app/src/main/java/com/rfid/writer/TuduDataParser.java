package com.rfid.writer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses TUDU / výhybka source files (.csv or .sql INSERT dumps).
 *
 * Expected CSV header (semicolon-delimited, UTF-8):
 * TUDU;Výhybka;Rok;TUDU 1;TUDU 2 ASCII;TUDU 2;Část;ID_RFID
 *
 * Minimal CSV:
 * TUDU;Výhybka
 */
public final class TuduDataParser {

    public static final class VyhybkaEntry {
        public final String tudu;
        public final String vyhybka;
        public final String rok;
        public final String tudu1;
        public final String tudu2Ascii;
        public final String tudu2;
        public final String cast;
        public final String idRfid;

        public VyhybkaEntry(String tudu, String vyhybka, String rok,
                            String tudu1, String tudu2Ascii, String tudu2,
                            String cast, String idRfid) {
            this.tudu = tudu != null ? tudu.trim() : "";
            this.vyhybka = vyhybka != null ? vyhybka.trim() : "";
            this.rok = emptyToDefault(rok, "2026");
            this.tudu1 = emptyToDefault(tudu1, "");
            this.tudu2Ascii = emptyToDefault(tudu2Ascii, "");
            this.tudu2 = emptyToDefault(tudu2, "");
            this.cast = emptyToDefault(cast, "1");
            this.idRfid = emptyToDefault(idRfid, "00000001");
        }

        private static String emptyToDefault(String v, String def) {
            return (v == null || v.trim().isEmpty()) ? def : v.trim();
        }
    }

    private static final Pattern INSERT_VALUES = Pattern.compile(
            "INSERT\\s+INTO\\s+[`\"']?\\w+[`\"']?(?:\\s*\\([^)]+\\))?\\s*VALUES\\s*\\((.+)\\)\\s*;?",
            Pattern.CASE_INSENSITIVE);

    private TuduDataParser() {}

    public static Map<String, List<VyhybkaEntry>> parse(File file) throws IOException {
        String lower = file.getName().toLowerCase(Locale.ROOT);
        try (FileInputStream fis = new FileInputStream(file)) {
            return parse(fis, lower.endsWith(".sql") ? "sql" : "csv");
        }
    }

    public static Map<String, List<VyhybkaEntry>> parse(InputStream in, String type) throws IOException {
        if ("sql".equals(type)) return parseSql(in);
        return parseCsv(in);
    }

    public static Map<String, List<VyhybkaEntry>> parseCsv(InputStream in) throws IOException {
        Map<String, List<VyhybkaEntry>> result = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String headerLine = br.readLine();
            if (headerLine == null) return result;
            headerLine = stripBom(headerLine);
            String[] headers = splitRow(headerLine);
            int[] idx = mapColumns(headers);
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] cols = splitRow(line);
                VyhybkaEntry entry = rowToEntry(cols, idx);
                if (entry == null || entry.tudu.isEmpty()) continue;
                result.computeIfAbsent(entry.tudu, k -> new ArrayList<>()).add(entry);
            }
        }
        sortVyhybky(result);
        return result;
    }

    public static Map<String, List<VyhybkaEntry>> parseSql(InputStream in) throws IOException {
        Map<String, List<VyhybkaEntry>> result = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("--") || line.startsWith("/*")) continue;
                sb.append(' ').append(line);
            }
        }
        String sql = sb.toString();
        Matcher m = INSERT_VALUES.matcher(sql);
        boolean found = false;
        while (m.find()) {
            found = true;
            String[] vals = splitSqlValues(m.group(1));
            VyhybkaEntry entry = sqlValuesToEntry(vals);
            if (entry == null || entry.tudu.isEmpty()) continue;
            result.computeIfAbsent(entry.tudu, k -> new ArrayList<>()).add(entry);
        }
        if (!found) {
            // Fallback: treat as semicolon CSV without header
            return parseCsv(new java.io.ByteArrayInputStream(sql.getBytes("UTF-8")));
        }
        sortVyhybky(result);
        return result;
    }

    private static Map<String, List<VyhybkaEntry>> parseSql(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return parseSql(fis);
        }
    }

    private static void sortVyhybky(Map<String, List<VyhybkaEntry>> map) {
        for (List<VyhybkaEntry> list : map.values()) {
            Collections.sort(list, (a, b) -> {
                try {
                    return Integer.compare(Integer.parseInt(a.vyhybka), Integer.parseInt(b.vyhybka));
                } catch (NumberFormatException e) {
                    return a.vyhybka.compareTo(b.vyhybka);
                }
            });
        }
    }

    private static String stripBom(String s) {
        if (s != null && s.length() > 0 && s.charAt(0) == '\uFEFF') return s.substring(1);
        return s;
    }

    private static String[] splitRow(String line) {
        if (line.contains(";")) return line.split(";", -1);
        return line.split(",", -1);
    }

    private static int[] mapColumns(String[] headers) {
        int[] idx = new int[8];
        for (int i = 0; i < idx.length; i++) idx[i] = -1;
        for (int i = 0; i < headers.length; i++) {
            String h = normHeader(headers[i]);
            if (matches(h, "tudu", "kategorie", "tudukategorie", "tudu_kategorie")) idx[0] = i;
            else if (matches(h, "vyhybka", "vhybka")) idx[1] = i;
            else if (matches(h, "rok")) idx[2] = i;
            else if (matches(h, "tudu1", "tudu_1")) idx[3] = i;
            else if (matches(h, "tudu2ascii", "tudu2_ascii", "tudu_2_ascii")) idx[4] = i;
            else if (matches(h, "tudu2", "tudu_2")) idx[5] = i;
            else if (matches(h, "cast", "part")) idx[6] = i;
            else if (matches(h, "idrfid", "id_rfid")) idx[7] = i;
        }
        // Fallback pouze pokud sloupce nejsou pojmenované — nikdy nezaměňovat ID_RFID za TUDU
        if (idx[0] < 0 && idx[7] < 0 && headers.length > 0) {
            String h0 = normHeader(headers[0]);
            if (!matches(h0, "idrfid", "id_rfid")) idx[0] = 0;
        }
        if (idx[1] < 0 && headers.length > 1) idx[1] = 1;
        // Oprava prohozených sloupců: první sloupec je ID_RFID, TUDU je jinde
        if (idx[0] < 0 && idx[7] >= 0) {
            for (int i = 0; i < headers.length; i++) {
                String h = normHeader(headers[i]);
                if (matches(h, "tudu", "kategorie", "tudukategorie", "tudu_kategorie")) {
                    idx[0] = i;
                    break;
                }
            }
        }
        if (idx[7] < 0 && idx[0] >= 0) {
            for (int i = 0; i < headers.length; i++) {
                String h = normHeader(headers[i]);
                if (matches(h, "idrfid", "id_rfid")) {
                    idx[7] = i;
                    break;
                }
            }
        }
        return idx;
    }

    private static String normHeader(String h) {
        if (h == null) return "";
        return h.trim().toLowerCase(Locale.ROOT)
                .replace("á", "a").replace("č", "c").replace("ě", "e")
                .replace("í", "i").replace("ň", "n").replace("ó", "o")
                .replace("ř", "r").replace("š", "s").replace("ť", "t")
                .replace("ú", "u").replace("ů", "u").replace("ý", "y")
                .replace(" ", "").replace("_", "");
    }

    private static boolean matches(String h, String... options) {
        for (String o : options) if (h.equals(o)) return true;
        return false;
    }

    private static VyhybkaEntry rowToEntry(String[] cols, int[] idx) {
        String tudu = col(cols, idx[0]);
        String vyhybka = col(cols, idx[1]);
        if (tudu.isEmpty()) return null;
        String rok = col(cols, idx[2]);
        String tudu1 = col(cols, idx[3]);
        String tudu2Ascii = col(cols, idx[4]);
        String tudu2 = col(cols, idx[5]);
        String cast = col(cols, idx[6]);
        String idRfid = col(cols, idx[7]);
        if (tudu1.isEmpty()) {
            String[] derived = deriveFromTuduKey(tudu);
            if (tudu1.isEmpty()) tudu1 = derived[0];
            if (tudu2Ascii.isEmpty()) tudu2Ascii = derived[1];
            if (tudu2.isEmpty()) tudu2 = derived[2];
        }
        return new VyhybkaEntry(tudu, vyhybka, rok, tudu1, tudu2Ascii, tudu2, cast, idRfid);
    }

    private static VyhybkaEntry sqlValuesToEntry(String[] vals) {
        if (vals.length < 2) return null;
        String tudu = unquote(vals[0]);
        String vyhybka = unquote(vals[1]);
        String rok = vals.length > 2 ? unquote(vals[2]) : "";
        String tudu1 = vals.length > 3 ? unquote(vals[3]) : "";
        String tudu2Ascii = vals.length > 4 ? unquote(vals[4]) : "";
        String tudu2 = vals.length > 5 ? unquote(vals[5]) : "";
        String cast = vals.length > 6 ? unquote(vals[6]) : "";
        String idRfid = vals.length > 7 ? unquote(vals[7]) : "";
        if (tudu1.isEmpty()) {
            String[] derived = deriveFromTuduKey(tudu);
            tudu1 = derived[0];
            if (tudu2Ascii.isEmpty()) tudu2Ascii = derived[1];
            if (tudu2.isEmpty()) tudu2 = derived[2];
        }
        return new VyhybkaEntry(tudu, vyhybka, rok, tudu1, tudu2Ascii, tudu2, cast, idRfid);
    }

    private static String col(String[] cols, int idx) {
        if (idx < 0 || idx >= cols.length) return "";
        return cols[idx] != null ? cols[idx].trim() : "";
    }

    /** Split e.g. 1501J1 -> tudu1=1501, tudu2Ascii=4A, tudu2=01 */
    public static String[] deriveFromTuduKey(String key) {
        String[] out = {"", "", ""};
        if (key == null || key.isEmpty()) return out;
        Matcher m = Pattern.compile("^(\\d{4})([A-Za-z])(\\d+)$").matcher(key.trim());
        if (m.matches()) {
            out[0] = m.group(1);
            out[1] = String.format(Locale.ROOT, "%02X", (int) m.group(2).charAt(0));
            String num = m.group(3);
            try {
                out[2] = String.format(Locale.ROOT, "%02d", Integer.parseInt(num));
            } catch (NumberFormatException e) {
                out[2] = num.length() == 1 ? "0" + num : num;
            }
            return out;
        }
        if (key.matches("\\d+")) {
            out[0] = key;
            while (out[0].length() < 4) out[0] = "0" + out[0];
            if (out[0].length() > 4) out[0] = out[0].substring(out[0].length() - 4);
            return out;
        }
        out[0] = key;
        return out;
    }

    private static String[] splitSqlValues(String valuesPart) {
        List<String> vals = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        char quote = '\'';
        for (int i = 0; i < valuesPart.length(); i++) {
            char c = valuesPart.charAt(i);
            if (!inQuote && (c == '\'' || c == '"')) {
                inQuote = true;
                quote = c;
                continue;
            }
            if (inQuote) {
                if (c == quote) {
                    if (i + 1 < valuesPart.length() && valuesPart.charAt(i + 1) == quote) {
                        cur.append(c);
                        i++;
                    } else {
                        inQuote = false;
                    }
                } else {
                    cur.append(c);
                }
            } else if (c == ',') {
                vals.add(cur.toString().trim());
                cur.setLength(0);
            } else if (!Character.isWhitespace(c)) {
                cur.append(c);
            }
        }
        vals.add(cur.toString().trim());
        return vals.toArray(new String[0]);
    }

    private static String unquote(String s) {
        if (s == null) return "";
        s = s.trim();
        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    public static List<String> sortedTuduKeys(Map<String, List<VyhybkaEntry>> data) {
        List<String> keys = new ArrayList<>(data.keySet());
        Collections.sort(keys);
        return keys;
    }

    public static List<String> filterTuduKeys(Map<String, List<VyhybkaEntry>> data, String query) {
        if (query == null || query.trim().isEmpty()) return sortedTuduKeys(data);
        String q = query.trim().toUpperCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String key : sortedTuduKeys(data)) {
            if (key.toUpperCase(Locale.ROOT).contains(q)) {
                out.add(key);
                continue;
            }
            // Vyhledávání i podle ID_RFID v rámci kategorie
            List<VyhybkaEntry> entries = data.get(key);
            if (entries == null) continue;
            for (VyhybkaEntry e : entries) {
                if (e.idRfid != null && e.idRfid.toUpperCase(Locale.ROOT).contains(q)) {
                    out.add(key);
                    break;
                }
            }
        }
        return out;
    }

    /** Formátuje TUDU pro zobrazení: 1501 + J + 1 -> 1501J1 */
    public static String formatTuduDisplay(String tudu1, String tudu2Ascii, String tudu2) {
        if (tudu1 == null || tudu1.isEmpty()) return "—";
        if (tudu2Ascii == null || tudu2Ascii.isEmpty()) return tudu1;
        try {
            char c = (char) Integer.parseInt(tudu2Ascii, 16);
            String num = tudu2 != null ? tudu2.replaceFirst("^0+", "") : "";
            if (num.isEmpty() && tudu2 != null && !tudu2.isEmpty()) num = "0";
            return tudu1 + c + num;
        } catch (NumberFormatException e) {
            return tudu1;
        }
    }
}
