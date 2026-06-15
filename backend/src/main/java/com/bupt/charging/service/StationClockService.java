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
        LocalDateTime stationTime = request.currentTime() == null ? wallNow : request.currentTime();
        double rate = request.rate() > 0 ? request.rate() : 1.0;
        StationClock clock = loadClock();
        clock.configure(wallNow, stationTime, rate, request.running(), request.windowStart(), request.windowEnd());
        return toResponse(clockRepository.save(clock), wallNow);
    }

    @Transactional
    public RuntimeDtos.ClockResponse play() {
        RuntimeDtos.ClockResponse current = currentClock();
        return setClock(new RuntimeDtos.SetClockRequest(
                current.currentTime(),
                current.rate(),
                true,
                current.windowStart(),
                current.windowEnd()
        ));
    }

    @Transactional
    public RuntimeDtos.ClockResponse pause() {
        RuntimeDtos.ClockResponse current = currentClock();
        return setClock(new RuntimeDtos.SetClockRequest(
                current.currentTime(),
                current.rate(),
                false,
                current.windowStart(),
                current.windowEnd()
        ));
    }

    @Transactional
    public RuntimeDtos.ClockResponse currentClock() {
        return toResponse(loadClock(), timeProvider.now());
    }

    @Transactional
    public LocalDateTime currentStationTime() {
        return currentClock().currentTime();
    }

    private StationClock loadClock() {
        return clockRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> clockRepository.save(new StationClock(timeProvider.now(), timeProvider.now())));
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
