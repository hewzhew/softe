package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class StationClock {
    public static final Long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private LocalDateTime baseWallTime;

    @Column(nullable = false)
    private LocalDateTime baseStationTime;

    @Column(nullable = false)
    private double rate;

    @Column(nullable = false)
    private boolean running;

    private LocalDateTime runtimeCursorTime;

    private LocalDateTime windowStart;

    private LocalDateTime windowEnd;

    protected StationClock() {
    }

    public StationClock(LocalDateTime baseWallTime, LocalDateTime baseStationTime) {
        this.id = SINGLETON_ID;
        this.baseWallTime = baseWallTime;
        this.baseStationTime = baseStationTime;
        this.rate = 1.0;
        this.running = false;
        this.runtimeCursorTime = baseStationTime;
    }

    public void configure(LocalDateTime wallTime, LocalDateTime stationTime, double rate, boolean running,
                          LocalDateTime windowStart, LocalDateTime windowEnd) {
        this.baseWallTime = wallTime;
        this.baseStationTime = stationTime;
        this.rate = rate;
        this.running = running;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public void resetRuntimeCursor(LocalDateTime runtimeCursorTime) {
        this.runtimeCursorTime = runtimeCursorTime;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getBaseWallTime() {
        return baseWallTime;
    }

    public LocalDateTime getBaseStationTime() {
        return baseStationTime;
    }

    public double getRate() {
        return rate;
    }

    public boolean isRunning() {
        return running;
    }

    public LocalDateTime getRuntimeCursorTime() {
        return runtimeCursorTime == null ? baseStationTime : runtimeCursorTime;
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }
}
