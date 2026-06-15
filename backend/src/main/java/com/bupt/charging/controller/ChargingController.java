package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.BillingDtos;
import com.bupt.charging.dto.ChargingDtos;
import com.bupt.charging.service.ChargingService;
import com.bupt.charging.service.StationClockService;
import com.bupt.charging.service.StationRuntimeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/charging")
public class ChargingController {
    private final ChargingService chargingService;
    private final StationClockService stationClockService;
    private final StationRuntimeService stationRuntimeService;

    public ChargingController(
            ChargingService chargingService,
            StationClockService stationClockService,
            StationRuntimeService stationRuntimeService
    ) {
        this.chargingService = chargingService;
        this.stationClockService = stationClockService;
        this.stationRuntimeService = stationRuntimeService;
    }

    @PostMapping("/requests")
    public ApiResult<ChargingDtos.RequestResponse> submit(@Valid @RequestBody ChargingDtos.SubmitRequest request) {
        advanceRuntime();
        return ApiResult.ok(chargingService.submitRequest(request.carId(), request.requestAmount(), request.mode()));
    }

    @PatchMapping("/requests/{carId}/amount")
    public ApiResult<ChargingDtos.RequestResponse> modifyAmount(
            @PathVariable String carId,
            @Valid @RequestBody ChargingDtos.ModifyAmountRequest request
    ) {
        advanceRuntime();
        return ApiResult.ok(chargingService.modifyAmount(carId, request.amount()));
    }

    @PatchMapping("/requests/{carId}/mode")
    public ApiResult<ChargingDtos.RequestResponse> modifyMode(
            @PathVariable String carId,
            @Valid @RequestBody ChargingDtos.ModifyModeRequest request
    ) {
        advanceRuntime();
        return ApiResult.ok(chargingService.modifyMode(carId, request.mode()));
    }

    @GetMapping("/cars/{carId}/state")
    public ApiResult<ChargingDtos.CarStateResponse> carState(@PathVariable String carId) {
        advanceRuntime();
        return ApiResult.ok(chargingService.queryCarState(carId));
    }

    @PostMapping("/{carId}/start")
    public ApiResult<Boolean> start(
            @PathVariable String carId,
            @Valid @RequestBody ChargingDtos.StartChargingRequest request
    ) {
        chargingService.startCharging(carId, request.pileId());
        return ApiResult.ok(true);
    }

    @GetMapping("/{carId}/state")
    public ApiResult<ChargingDtos.ChargingStateResponse> chargingState(@PathVariable String carId) {
        advanceRuntime();
        return ApiResult.ok(chargingService.queryChargingState(carId));
    }

    @PostMapping("/{carId}/end")
    public ApiResult<BillingDtos.BillResponse> end(
            @PathVariable String carId,
            @Valid @RequestBody ChargingDtos.EndChargingRequest request
    ) {
        advanceRuntime();
        return ApiResult.ok(chargingService.endCharging(carId, request.pileId(), request.actualAmount()));
    }

    private void advanceRuntime() {
        stationRuntimeService.advanceTo(stationClockService.currentStationTime());
    }
}
