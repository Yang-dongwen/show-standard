package com.ddmo.app.controller;

import com.ddmo.app.model.Customer;
import com.ddmo.app.service.BarbershopService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final BarbershopService barbershopService;

    public ExportController(BarbershopService barbershopService) {
        this.barbershopService = barbershopService;
    }

    @GetMapping("/customers")
    public ResponseEntity<String> exportCustomers(@RequestParam(required = false) String keyword) {
        List<Customer> customers = barbershopService.listCustomers(keyword);
        StringBuilder builder = new StringBuilder();
        builder.append("会员姓名,手机号,状态,余额,备注,创建时间\n");
        for (Customer customer : customers) {
            builder.append(escape(customer.getName())).append(",")
                .append(escape(customer.getPhone())).append(",")
                .append(escape("active".equals(customer.getStatus()) ? "正常" : "停用")).append(",")
                .append(escape(customer.getBalance() == null ? "0" : customer.getBalance().toPlainString())).append(",")
                .append(escape(customer.getRemark())).append(",")
                .append(escape(customer.getCreatedAt().toString()))
                .append("\n");
        }
        return csvResponse("customers.csv", builder.toString());
    }

    @GetMapping("/transactions")
    public ResponseEntity<String> exportTransactions(
        @RequestParam(required = false) String keyword,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<Map<String, Object>> rows = barbershopService.listTransactionRowsBetween(keyword, startDate, endDate);
        StringBuilder builder = new StringBuilder();
        builder.append("时间,类型,状态,会员,金额,详情\n");
        for (Map<String, Object> row : rows) {
            builder.append(escape(String.valueOf(row.get("createdAt")))).append(",")
                .append(escape("recharge".equals(row.get("type")) ? "充值" : "消费")).append(",")
                .append(escape(statusLabel(String.valueOf(row.get("status"))))).append(",")
                .append(escape(String.valueOf(row.get("customerName")))).append(",")
                .append(escape(String.valueOf(row.get("amount")))).append(",")
                .append(escape(String.valueOf(row.get("detail"))))
                .append("\n");
        }
        return csvResponse("transactions.csv", builder.toString());
    }

    @GetMapping("/employee-performance")
    public ResponseEntity<String> exportEmployeePerformance(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<Map<String, Object>> rows = barbershopService.getEmployeePerformance(startDate, endDate);
        StringBuilder builder = new StringBuilder();
        builder.append("员工,订单数,总金额,客单价\n");
        for (Map<String, Object> row : rows) {
            builder.append(escape(String.valueOf(row.get("employeeName")))).append(",")
                .append(escape(String.valueOf(row.get("total_count")))).append(",")
                .append(escape(String.valueOf(row.get("total_amount")))).append(",")
                .append(escape(String.valueOf(row.get("avg_amount"))))
                .append("\n");
        }
        return csvResponse("employee-performance.csv", builder.toString());
    }

    private static String statusLabel(String status) {
        if (status == null || "normal".equals(status) || "null".equals(status)) {
            return "正常";
        }
        if ("reversed".equals(status)) {
            return "已冲正";
        }
        if ("reversal".equals(status)) {
            return "冲正单";
        }
        return status;
    }

    private ResponseEntity<String> csvResponse(String fileName, String body) {
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
            .body("\uFEFF" + body);
    }

    private String escape(String input) {
        if (input == null) {
            return "\"\"";
        }
        return "\"" + input.replace("\"", "\"\"") + "\"";
    }
}
