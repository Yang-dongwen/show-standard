package com.ddmo.saas.dto;

public class UpdateTenantMetaRequest {
    private String tags;
    private String remark;
    /** 天数；null 不改；0 清空到期 */
    private Integer expireDays;

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getExpireDays() {
        return expireDays;
    }

    public void setExpireDays(Integer expireDays) {
        this.expireDays = expireDays;
    }
}
