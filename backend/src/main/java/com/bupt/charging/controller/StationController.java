package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.StationDtos;
import com.bupt.charging.service.StationSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/station")
public class StationController {
    private final StationSnapshotService stationSnapshotService;

    public StationController(StationSnapshotService stationSnapshotService) {
        this.stationSnapshotService = stationSnapshotService;
    }

    @GetMapping("/snapshot")
    public ApiResult<StationDtos.StationSnapshot> snapshot() {
        return ApiResult.ok(stationSnapshotService.currentSnapshot());
    }
}
