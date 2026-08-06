package com.ddmo.app.controller;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.dto.EmployeeRequest;
import com.ddmo.app.model.Employee;
import com.ddmo.app.service.BarbershopService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final BarbershopService barbershopService;

    public EmployeeController(BarbershopService barbershopService) {
        this.barbershopService = barbershopService;
    }

    @GetMapping
    public ApiResponse<?> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(barbershopService.listEmployeesPaged(keyword, page, size));
    }

    /** 消费/收银下拉：全部在岗员工 */
    @GetMapping("/options")
    public ApiResponse<List<Employee>> options() {
        return ApiResponse.ok(barbershopService.listActiveEmployeeOptions());
    }

    @PostMapping
    public ApiResponse<Employee> create(@RequestBody EmployeeRequest request) {
        return ApiResponse.ok("创建成功", barbershopService.createEmployee(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Employee> update(@PathVariable String id, @RequestBody EmployeeRequest request) {
        return ApiResponse.ok("更新成功", barbershopService.updateEmployee(id, request));
    }

    @PatchMapping("/{id}/toggle-status")
    public ApiResponse<Employee> toggleStatus(@PathVariable String id) {
        return ApiResponse.ok("状态已更新", barbershopService.toggleEmployeeStatus(id));
    }
}
