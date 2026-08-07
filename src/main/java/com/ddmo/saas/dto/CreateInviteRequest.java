package com.ddmo.saas.dto;

public class CreateInviteRequest {
    private Integer maxUses = 1;
    private String note = "";
    private Integer expireDays;

    public Integer getMaxUses() {
        return maxUses;
    }

    public void setMaxUses(Integer maxUses) {
        this.maxUses = maxUses;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getExpireDays() {
        return expireDays;
    }

    public void setExpireDays(Integer expireDays) {
        this.expireDays = expireDays;
    }
}
