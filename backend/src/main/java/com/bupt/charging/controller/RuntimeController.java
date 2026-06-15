package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.ConfigDtos;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.service.ConfigService;
import com.bupt.charging.service.StationClockService;
import com.bupt.charging.service.StationEventService;
import com.bupt.charging.service.StationRuntimeService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
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
    private final StationEventService stationEventService;
    private final ConfigService configService;

    public RuntimeController(
            StationClockService stationClockService,
            StationRuntimeService stationRuntimeService,
            StationEventService stationEventService,
            ConfigService configService
    ) {
        this.stationClockService = stationClockService;
        this.stationRuntimeService = stationRuntimeService;
        this.stationEventService = stationEventService;
        this.configService = configService;
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

    @GetMapping("/events")
    public ApiResult<List<RuntimeDtos.RuntimeEventRow>> events() {
        return ApiResult.ok(stationEventService.listEvents());
    }

    @PostMapping("/events")
    public ApiResult<RuntimeDtos.RuntimeEventRow> addEvent(
            @Valid @RequestBody RuntimeDtos.ManualChargeRequestEvent request
    ) {
        return ApiResult.ok(stationEventService.addManualChargeRequest(request));
    }

    @PostMapping("/events/import")
    public ApiResult<RuntimeDtos.ImportEventsResponse> importEvents(
            @RequestBody RuntimeDtos.ImportEventsRequest request
    ) {
        if (request != null && request.resetBeforeImport()) {
            configService.resetDemoData();
            configService.initialize(new ConfigDtos.UpdateConfigRequest(2, 3, 10, 3, 30.0, 10.0));
            stationClockService.resetClock(new RuntimeDtos.SetClockRequest(
                    LocalDateTime.of(2026, 6, 1, 6, 0),
                    10.0,
                    false,
                    LocalDateTime.of(2026, 6, 1, 6, 0),
                    LocalDateTime.of(2026, 6, 1, 9, 30)
            ));
        }
        return ApiResult.ok(stationEventService.importCourseSample(false));
    }
}
