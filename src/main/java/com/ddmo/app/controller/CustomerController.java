package com.ddmo.app.controller;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.dto.CustomerImportResult;
import com.ddmo.app.dto.CustomerRequest;
import com.ddmo.app.model.Customer;
import com.ddmo.app.service.BarbershopService;
import com.ddmo.app.service.CustomerImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final BarbershopService barbershopService;
    private final CustomerImportService customerImportService;

    public CustomerController(BarbershopService barbershopService, CustomerImportService customerImportService) {
        this.barbershopService = barbershopService;
        this.customerImportService = customerImportService;
    }

    @GetMapping
    public ApiResponse<?> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(barbershopService.listCustomersPaged(keyword, page, size));
    }

    @GetMapping("/import-template")
    public ResponseEntity<String> importTemplate() {
        String body = customerImportService.buildTemplateCsv();
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + CustomerImportService.TEMPLATE_FILE_NAME + "\"")
            .body("\uFEFF" + body);
    }

    @PostMapping("/import")
    public ApiResponse<CustomerImportResult> importCustomers(@RequestParam("file") MultipartFile file) {
        CustomerImportResult result = customerImportService.importCsv(file);
        String msg = "导入完成：成功 " + result.getSuccess() + "，失败 " + result.getFailed();
        return ApiResponse.ok(msg, result);
    }

    @PostMapping
    public ApiResponse<Customer> create(@Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok("创建成功", barbershopService.createCustomer(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<Customer> update(@PathVariable String id, @Valid @RequestBody CustomerRequest request) {
        return ApiResponse.ok("更新成功", barbershopService.updateCustomer(id, request));
    }

    @PatchMapping("/{id}/toggle-status")
    public ApiResponse<Customer> toggleStatus(@PathVariable String id) {
        return ApiResponse.ok("状态已更新", barbershopService.toggleCustomerStatus(id));
    }
}
