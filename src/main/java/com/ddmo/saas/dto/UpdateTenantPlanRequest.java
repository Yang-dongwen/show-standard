package com.ddmo.saas.dto;

public class UpdateTenantPlanRequest {
    private String planCode;
    private Integer maxCustomers;
    private Integer maxEmployees;

    public String getPlanCode() {
        return planCode;
    }

    public void setPlanCode(String planCode) {
        this.planCode = planCode;
    }

    public Integer getMaxCustomers() {
        return maxCustomers;
    }

    public void setMaxCustomers(Integer maxCustomers) {
        this.maxCustomers = maxCustomers;
    }

    public Integer getMaxEmployees() {
        return maxEmployees;
    }

    public void setMaxEmployees(Integer maxEmployees) {
        this.maxEmployees = maxEmployees;
    }
}
