package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class StationConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int fastPileCount;

    @Column(nullable = false)
    private int slowPileCount;

    @Column(nullable = false)
    private int waitingAreaSize;

    @Column(nullable = false)
    private int queueLength;

    @Column(nullable = false)
    private double fastPower;

    @Column(nullable = false)
    private double slowPower;

    protected StationConfig() {
    }

    public StationConfig(int fastPileCount, int slowPileCount, int waitingAreaSize,
                         int queueLength, double fastPower, double slowPower) {
        this.fastPileCount = fastPileCount;
        this.slowPileCount = slowPileCount;
        this.waitingAreaSize = waitingAreaSize;
        this.queueLength = queueLength;
        this.fastPower = fastPower;
        this.slowPower = slowPower;
    }

    public static StationConfig defaults() {
        return new StationConfig(2, 3, 10, 2, 30.0, 10.0);
    }

    public double powerFor(ChargeMode mode) {
        return mode == ChargeMode.FAST ? fastPower : slowPower;
    }

    public Long getId() {
        return id;
    }

    public int getFastPileCount() {
        return fastPileCount;
    }

    public int getSlowPileCount() {
        return slowPileCount;
    }

    public int getWaitingAreaSize() {
        return waitingAreaSize;
    }

    public int getQueueLength() {
        return queueLength;
    }

    public double getFastPower() {
        return fastPower;
    }

    public double getSlowPower() {
        return slowPower;
    }
}
