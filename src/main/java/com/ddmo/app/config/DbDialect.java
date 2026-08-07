package com.ddmo.app.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * C 端双库方言：本地 SQLite / 云端 MySQL（可后续扩展其它）。
 */
@Component
public class DbDialect {

    public enum Kind {
        SQLITE,
        MYSQL,
        OTHER
    }

    private final Kind kind;
    private final String jdbcUrl;

    public DbDialect(Environment environment) {
        this.jdbcUrl = environment.getProperty("spring.datasource.url", "");
        this.kind = detect(jdbcUrl);
    }

    private static Kind detect(String url) {
        if (url == null) {
            return Kind.OTHER;
        }
        String u = url.toLowerCase();
        if (u.contains("jdbc:sqlite:")) {
            return Kind.SQLITE;
        }
        if (u.contains("jdbc:mysql:") || u.contains("jdbc:mariadb:")) {
            return Kind.MYSQL;
        }
        return Kind.OTHER;
    }

    public Kind kind() {
        return kind;
    }

    public String jdbcUrl() {
        return jdbcUrl;
    }

    public boolean isSqlite() {
        return kind == Kind.SQLITE;
    }

    public boolean isMysql() {
        return kind == Kind.MYSQL;
    }

    /** 文件型库（SQLite），支持整库文件备份 */
    public boolean isFileDatabase() {
        return isSqlite();
    }

    public String label() {
        return switch (kind) {
            case SQLITE -> "sqlite";
            case MYSQL -> "mysql";
            default -> "other";
        };
    }

    /**
     * 今天日期表达式（可嵌入 SQL，无参数）。
     * SQLite: DATE('now','localtime') · MySQL: CURDATE()
     */
    public String todayExpr() {
        return isSqlite() ? "DATE('now', 'localtime')" : "CURDATE()";
    }

    /**
     * 相对今天的日期表达式。
     * @param days 负数为过去，正数为未来
     */
    public String dateOffsetExpr(int days) {
        if (isSqlite()) {
            if (days == 0) {
                return "DATE('now', 'localtime')";
            }
            String sign = days > 0 ? "+" : "";
            return "DATE('now', 'localtime', '" + sign + days + " days')";
        }
        // MySQL / 其它
        if (days == 0) {
            return "CURDATE()";
        }
        if (days > 0) {
            return "DATE_ADD(CURDATE(), INTERVAL " + days + " DAY)";
        }
        return "DATE_SUB(CURDATE(), INTERVAL " + (-days) + " DAY)";
    }

    /**
     * 当前时间戳表达式（比较 expire_at 等）。
     */
    public String nowExpr() {
        return isSqlite() ? "datetime('now', 'localtime')" : "NOW()";
    }

    /**
     * 相对现在的时间戳。
     */
    public String nowOffsetExpr(int days) {
        if (isSqlite()) {
            if (days == 0) {
                return "datetime('now', 'localtime')";
            }
            String sign = days > 0 ? "+" : "";
            return "datetime('now', 'localtime', '" + sign + days + " days')";
        }
        if (days == 0) {
            return "NOW()";
        }
        if (days > 0) {
            return "DATE_ADD(NOW(), INTERVAL " + days + " DAY)";
        }
        return "DATE_SUB(NOW(), INTERVAL " + (-days) + " DAY)";
    }

    /**
     * 转字符串：SQLite TEXT / MySQL CHAR。
     */
    public String castAsString(String expr) {
        if (isSqlite()) {
            return "CAST(" + expr + " AS TEXT)";
        }
        return "CAST(" + expr + " AS CHAR)";
    }

    /**
     * 字符串拼接：SQLite {@code ||} / MySQL {@code CONCAT}。
     */
    public String concat(String... parts) {
        if (parts == null || parts.length == 0) {
            return isSqlite() ? "''" : "''";
        }
        if (isSqlite()) {
            return String.join(" || ", parts);
        }
        return "CONCAT(" + String.join(", ", parts) + ")";
    }
}
