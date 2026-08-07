package com.ddmo.saas.dto;

public class WriteModeRequest {
    /** normal | readonly */
    private String writeMode;

    public String getWriteMode() {
        return writeMode;
    }

    public void setWriteMode(String writeMode) {
        this.writeMode = writeMode;
    }
}
