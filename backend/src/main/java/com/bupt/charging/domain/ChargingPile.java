package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class ChargingPile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String pileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeMode mode;

    @Column(nullable = false)
    private double power;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PileStatus status;

    private String currentCarId;

    private int totalChargeNum;

    private double totalChargeTime;

    private double totalCapacity;

    protected ChargingPile() {
    }

    public ChargingPile(String pileId, ChargeMode mode, double power) {
        this.pileId = pileId;
        this.mode = mode;
        this.power = power;
        this.status = PileStatus.IDLE;
    }

    public boolean isAvailableForQueue() {
        return status == PileStatus.IDLE || status == PileStatus.WORKING;
    }

    public void powerOn() {
        if (status == PileStatus.OFFLINE) {
            status = PileStatus.IDLE;
        }
    }

    public void startPile() {
        if (status != PileStatus.FAULT) {
            status = PileStatus.IDLE;
        }
    }

    public void powerOff() {
        status = PileStatus.OFFLINE;
        currentCarId = null;
    }

    public void markWorking(String carId) {
        status = PileStatus.WORKING;
        currentCarId = carId;
    }

    public void release() {
        status = PileStatus.IDLE;
        currentCarId = null;
    }

    public void markFault() {
        status = PileStatus.FAULT;
        currentCarId = null;
    }

    public void recover() {
        status = PileStatus.IDLE;
        currentCarId = null;
    }

    public void addChargingStats(double durationHours, double amount) {
        totalChargeNum += 1;
        totalChargeTime += durationHours;
        totalCapacity += amount;
    }

    public Long getId() {
        return id;
    }

    public String getPileId() {
        return pileId;
    }

    public ChargeMode getMode() {
        return mode;
    }

    public double getPower() {
        return power;
    }

    public PileStatus getStatus() {
        return status;
    }

    public String getCurrentCarId() {
        return currentCarId;
    }

    public int getTotalChargeNum() {
        return totalChargeNum;
    }

    public double getTotalChargeTime() {
        return totalChargeTime;
    }

    public double getTotalCapacity() {
        return totalCapacity;
    }
}
