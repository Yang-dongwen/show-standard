package com.ddmo.app.controller;

import com.ddmo.app.dto.ApiResponse;
import com.ddmo.app.dto.ConsumeRequest;
import com.ddmo.app.dto.RechargeRequest;
import com.ddmo.app.dto.ReverseRequest;
import com.ddmo.app.model.ConsumeRecord;
import com.ddmo.app.model.RechargeRecord;
import com.ddmo.app.service.BarbershopService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final BarbershopService barbershopService;

    public TransactionController(BarbershopService barbershopService) {
        this.barbershopService = barbershopService;
    }

    @PostMapping("/recharge")
    public ApiResponse<RechargeRecord> recharge(@Valid @RequestBody RechargeRequest request) {
        return ApiResponse.ok("充值成功", barbershopService.createRecharge(request));
    }

    @PostMapping("/consume")
    public ApiResponse<ConsumeRecord> consume(@Valid @RequestBody ConsumeRequest request) {
        return ApiResponse.ok("消费登记成功", barbershopService.createConsume(request));
    }

    @PostMapping("/recharge/{id}/reverse")
    public ApiResponse<RechargeRecord> reverseRecharge(
        @PathVariable String id,
        @RequestBody(required = false) ReverseRequest request
    ) {
        String reason = request == null ? null : request.getReason();
        return ApiResponse.ok("充值已冲正", barbershopService.reverseRecharge(id, reason));
    }

    @PostMapping("/consume/{id}/reverse")
    public ApiResponse<ConsumeRecord> reverseConsume(
        @PathVariable String id,
        @RequestBody(required = false) ReverseRequest request
    ) {
        String reason = request == null ? null : request.getReason();
        return ApiResponse.ok("消费已冲正", barbershopService.reverseConsume(id, reason));
    }

    @GetMapping
    public ApiResponse<?> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.ok(barbershopService.listTransactionRowsPaged(keyword, page, size));
    }
}
