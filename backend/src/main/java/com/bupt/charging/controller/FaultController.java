package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.FaultDtos;
import com.bupt.charging.service.FaultService;
import com.bupt.charging.service.StationClockService;
import com.bupt.charging.service.StationRuntimeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faults")
public class FaultController {
    private final FaultService faultService;
    private final StationClockService stationClockService;
    private final StationRuntimeService stationRuntimeService;

    public FaultController(
            FaultService faultService,
            StationClockService stationClockService,
            StationRuntimeService stationRuntimeService
    ) {
        this.faultService = faultService;
        this.stationClockService = stationClockService;
        this.stationRuntimeService = stationRuntimeService;
    }

    @PostMapping
    public ApiResult<FaultDtos.FaultResult> fault(@Valid @RequestBody FaultDtos.CreateFaultRequest request) {
        advanceRuntime();
        return ApiResult.ok(faultService.handleFault(request.pileId(), request.strategy()));
    }

    @PostMapping("/{pileId}/recover")
    public ApiResult<FaultDtos.FaultResult> recover(@PathVariable String pileId) {
        advanceRuntime();
        return ApiResult.ok(faultService.recoverPile(pileId));
    }

    private void advanceRuntime() {
        stationRuntimeService.advanceTo(stationClockService.currentStationTime());
    }
}
