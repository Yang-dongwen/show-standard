package com.ddmo.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class EmployeeRequest {

    public static final int NAME_MAX = 32;

    @NotBlank(message = "员工姓名不能为空")
    @Size(max = NAME_MAX, message = "员工姓名最多" + NAME_MAX + "个字")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
