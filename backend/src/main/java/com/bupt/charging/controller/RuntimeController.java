package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.service.StationClockService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/station")
public class RuntimeController {
    private final StationClockService stationClockService;

    public RuntimeController(StationClockService stationClockService) {
        this.stationClockService = stationClockService;
    }

    @GetMapping("/clock")
    public ApiResult<RuntimeDtos.ClockResponse> clock() {
        return ApiResult.ok(stationClockService.currentClock());
    }

    @PatchMapping("/clock")
    public ApiResult<RuntimeDtos.ClockResponse> setClock(@RequestBody RuntimeDtos.SetClockRequest request) {
        return ApiResult.ok(stationClockService.setClock(request));
    }

    @PostMapping("/clock/play")
    public ApiResult<RuntimeDtos.ClockResponse> playClock() {
        return ApiResult.ok(stationClockService.play());
    }

    @PostMapping("/clock/pause")
    public ApiResult<RuntimeDtos.ClockResponse> pauseClock() {
        return ApiResult.ok(stationClockService.pause());
    }
}
