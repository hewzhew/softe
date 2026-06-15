package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.BillingDtos;
import com.bupt.charging.service.BillingService;
import com.bupt.charging.service.StationClockService;
import com.bupt.charging.service.StationRuntimeService;
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
    private final StationClockService stationClockService;
    private final StationRuntimeService stationRuntimeService;

    public BillingController(
            BillingService billingService,
            StationClockService stationClockService,
            StationRuntimeService stationRuntimeService
    ) {
        this.billingService = billingService;
        this.stationClockService = stationClockService;
        this.stationRuntimeService = stationRuntimeService;
    }

    @GetMapping
    public ApiResult<List<BillingDtos.BillResponse>> queryBills(
            @RequestParam String carId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        stationRuntimeService.advanceTo(stationClockService.currentStationTime());
        return ApiResult.ok(billingService.queryBills(carId, date));
    }

    @GetMapping("/{billId}/details")
    public ApiResult<List<BillingDtos.DetailedListResponse>> details(@PathVariable Long billId) {
        return ApiResult.ok(billingService.queryDetails(billId));
    }
}
