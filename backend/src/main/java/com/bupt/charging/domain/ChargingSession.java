package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class ChargingSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long requestId;

    @Column(nullable = false)
    private String carId;

    @Column(nullable = false)
    private String pileId;

    @Column(nullable = false)
    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private double chargeAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    protected ChargingSession() {
    }

    public ChargingSession(Long requestId, String carId, String pileId, LocalDateTime startTime) {
        this.requestId = requestId;
        this.carId = carId;
        this.pileId = pileId;
        this.startTime = startTime;
        this.status = SessionStatus.CHARGING;
    }

    public void finish(LocalDateTime endTime, double chargeAmount) {
        this.endTime = endTime;
        this.chargeAmount = chargeAmount;
        this.status = SessionStatus.FINISHED;
    }

    public void interrupt(LocalDateTime endTime, double chargeAmount) {
        this.endTime = endTime;
        this.chargeAmount = chargeAmount;
        this.status = SessionStatus.INTERRUPTED;
    }

    public Long getId() {
        return id;
    }

    public Long getRequestId() {
        return requestId;
    }

    public String getCarId() {
        return carId;
    }

    public String getPileId() {
        return pileId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public double getChargeAmount() {
        return chargeAmount;
    }

    public SessionStatus getStatus() {
        return status;
    }
}
