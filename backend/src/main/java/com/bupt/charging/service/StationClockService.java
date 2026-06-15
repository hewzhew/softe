package com.bupt.charging.service;

import com.bupt.charging.domain.StationClock;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.repository.StationClockRepository;
import com.bupt.charging.support.TimeProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StationClockService {
    private final StationClockRepository clockRepository;
    private final TimeProvider timeProvider;

    public StationClockService(StationClockRepository clockRepository, TimeProvider timeProvider) {
        this.clockRepository = clockRepository;
        this.timeProvider = timeProvider;
    }

    @Transactional
    public RuntimeDtos.ClockResponse setClock(RuntimeDtos.SetClockRequest request) {
        LocalDateTime wallNow = timeProvider.now();
        StationClock clock = loadClock();
        RuntimeDtos.ClockResponse current = toResponse(clock, wallNow);
        LocalDateTime stationTime = request.currentTime() == null ? current.currentTime() : request.currentTime();
        double rate = request.rate() > 0 ? request.rate() : current.rate();
        boolean running = request.running() == null ? current.running() : request.running();
        LocalDateTime windowStart = request.windowStart() == null ? current.windowStart() : request.windowStart();
        LocalDateTime windowEnd = request.windowEnd() == null ? current.windowEnd() : request.windowEnd();
        clock.configure(wallNow, stationTime, rate, running, windowStart, windowEnd);
        if (request.currentTime() != null) {
            clock.resetRuntimeCursor(stationTime);
        }
        return toResponse(clockRepository.save(clock), wallNow);
    }

    @Transactional
    public RuntimeDtos.ClockResponse play() {
        return setClock(new RuntimeDtos.SetClockRequest(null, 0, true, null, null));
    }

    @Transactional
    public RuntimeDtos.ClockResponse pause() {
        return setClock(new RuntimeDtos.SetClockRequest(null, 0, false, null, null));
    }

    @Transactional
    public RuntimeDtos.ClockResponse currentClock() {
        return toResponse(loadClock(), timeProvider.now());
    }

    @Transactional
    public LocalDateTime currentStationTime() {
        return currentClock().currentTime();
    }

    @Transactional
    public LocalDateTime runtimeCursorTime() {
        return loadClock().getRuntimeCursorTime();
    }

    @Transactional
    public void markRuntimeAdvancedTo(LocalDateTime stationTime) {
        StationClock clock = loadClock();
        clock.resetRuntimeCursor(stationTime);
        clockRepository.save(clock);
    }

    private synchronized StationClock loadClock() {
        return clockRepository.findById(StationClock.SINGLETON_ID)
                .orElseGet(() -> clockRepository.saveAndFlush(new StationClock(timeProvider.now(), timeProvider.now())));
    }

    private RuntimeDtos.ClockResponse toResponse(StationClock clock, LocalDateTime wallNow) {
        LocalDateTime stationTime = clock.getBaseStationTime();
        if (clock.isRunning()) {
            long elapsedMillis = Duration.between(clock.getBaseWallTime(), wallNow).toMillis();
            stationTime = stationTime.plusNanos(Math.round(elapsedMillis * clock.getRate()) * 1_000_000L);
        }
        return new RuntimeDtos.ClockResponse(
                stationTime,
                clock.getRate(),
                clock.isRunning(),
                clock.getWindowStart(),
                clock.getWindowEnd()
        );
    }
}
