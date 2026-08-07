package com.ddmo.app.install;

/**
 * 安装向导提交：edition=local|saas；saas 时填 MySQL。
 */
public class InstallCompleteRequest {

    /** local | saas */
    private String edition;

    /** 完整 JDBC URL（优先）；否则用 host/port/database 拼接 */
    private String mysqlUrl;
    private String mysqlHost = "127.0.0.1";
    private int mysqlPort = 3306;
    private String mysqlDatabase = "show";
    private String mysqlUsername = "show";
    private String mysqlPassword = "show";
    private boolean enableMiniProgram = true;

    public String getEdition() {
        return edition;
    }

    public void setEdition(String edition) {
        this.edition = edition;
    }

    public String getMysqlUrl() {
        return mysqlUrl;
    }

    public void setMysqlUrl(String mysqlUrl) {
        this.mysqlUrl = mysqlUrl;
    }

    public String getMysqlHost() {
        return mysqlHost;
    }

    public void setMysqlHost(String mysqlHost) {
        this.mysqlHost = mysqlHost;
    }

    public int getMysqlPort() {
        return mysqlPort;
    }

    public void setMysqlPort(int mysqlPort) {
        this.mysqlPort = mysqlPort;
    }

    public String getMysqlDatabase() {
        return mysqlDatabase;
    }

    public void setMysqlDatabase(String mysqlDatabase) {
        this.mysqlDatabase = mysqlDatabase;
    }

    public String getMysqlUsername() {
        return mysqlUsername;
    }

    public void setMysqlUsername(String mysqlUsername) {
        this.mysqlUsername = mysqlUsername;
    }

    public String getMysqlPassword() {
        return mysqlPassword;
    }

    public void setMysqlPassword(String mysqlPassword) {
        this.mysqlPassword = mysqlPassword;
    }

    public boolean isEnableMiniProgram() {
        return enableMiniProgram;
    }

    public void setEnableMiniProgram(boolean enableMiniProgram) {
        this.enableMiniProgram = enableMiniProgram;
    }
}
