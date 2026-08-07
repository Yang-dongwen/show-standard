package com.ddmo.app.install;

import java.nio.file.Path;

/**
 * 安装向导配置落盘路径（与 SQLite 同目录，便于备份）。
 */
public final class InstallPaths {

    public static final String FILE_NAME = "install.properties";

    private InstallPaths() {
    }

    public static Path showDir() {
        return Path.of(System.getProperty("user.home"), ".show");
    }

    public static Path installFile() {
        return showDir().resolve(FILE_NAME);
    }

    public static Path sqliteDb() {
        return showDir().resolve("show.db");
    }
}
