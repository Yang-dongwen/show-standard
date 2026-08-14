package com.ddmo.app.util;

import java.util.ArrayList;
import java.util.List;

/** 轻量 CSV 解析/转义，与导出端 escape 对称。 */
public final class CsvSupport {

    private CsvSupport() {
    }

    public static String escape(String input) {
        if (input == null) {
            return "\"\"";
        }
        return "\"" + input.replace("\"", "\"\"") + "\"";
    }

    /**
     * 解析一行 CSV（支持双引号转义）。分隔符为逗号；空行返回空列表。
     */
    public static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        if (line == null) {
            return fields;
        }
        String s = line;
        if (!s.isEmpty() && s.charAt(0) == '\uFEFF') {
            s = s.substring(1);
        }
        if (s.isEmpty()) {
            return fields;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < s.length() && s.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        fields.add(cur.toString());
        return fields;
    }

    public static String cell(List<String> row, int index) {
        if (index < 0 || index >= row.size()) {
            return "";
        }
        String v = row.get(index);
        return v == null ? "" : v.trim();
    }
}
