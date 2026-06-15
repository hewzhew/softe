package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.service.StationClockService;
import com.bupt.charging.service.StationRuntimeService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
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
    private final StationRuntimeService stationRuntimeService;

    public RuntimeController(
            StationClockService stationClockService,
            StationRuntimeService stationRuntimeService
    ) {
        this.stationClockService = stationClockService;
        this.stationRuntimeService = stationRuntimeService;
    }

    @GetMapping("/clock")
    public ApiResult<RuntimeDtos.ClockResponse> clock() {
        return ApiResult.ok(stationClockService.currentClock());
    }

    @PatchMapping("/clock")
    public ApiResult<RuntimeDtos.ClockResponse> setClock(@RequestBody RuntimeDtos.SetClockRequest request) {
        return ApiResult.ok(stationClockService.setClock(request));
    }

    @PostMapping("/advance")
    public ApiResult<RuntimeDtos.ClockResponse> advance(@Valid @RequestBody RuntimeDtos.AdvanceRequest request) {
        LocalDateTime cursorTime = stationClockService.runtimeCursorTime();
        LocalDateTime effectiveTime = request.toTime().isBefore(cursorTime) ? cursorTime : request.toTime();
        stationRuntimeService.advanceTo(effectiveTime);
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(effectiveTime, 1.0, false, null, null));
        return ApiResult.ok(stationClockService.currentClock());
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
