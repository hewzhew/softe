package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.BillingDtos;
import com.bupt.charging.service.BillingService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bills")
public class BillingController {
    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping
    public ApiResult<List<BillingDtos.BillResponse>> queryBills(
            @RequestParam String carId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResult.ok(billingService.queryBills(carId, date));
    }

    @GetMapping("/{billId}/details")
    public ApiResult<List<BillingDtos.DetailedListResponse>> details(@PathVariable Long billId) {
        return ApiResult.ok(billingService.queryDetails(billId));
    }
}
