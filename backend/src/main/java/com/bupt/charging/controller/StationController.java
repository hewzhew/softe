package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.StationDtos;
import com.bupt.charging.service.StationClockService;
import com.bupt.charging.service.StationRuntimeService;
import com.bupt.charging.service.StationSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/station")
public class StationController {
    private final StationSnapshotService stationSnapshotService;
    private final StationRuntimeService stationRuntimeService;
    private final StationClockService stationClockService;

    public StationController(
            StationSnapshotService stationSnapshotService,
            StationRuntimeService stationRuntimeService,
            StationClockService stationClockService
    ) {
        this.stationSnapshotService = stationSnapshotService;
        this.stationRuntimeService = stationRuntimeService;
        this.stationClockService = stationClockService;
    }

    @GetMapping("/snapshot")
    public ApiResult<StationDtos.StationSnapshot> snapshot() {
        stationRuntimeService.advanceTo(stationClockService.currentStationTime());
        return ApiResult.ok(stationSnapshotService.currentSnapshot());
    }
}
