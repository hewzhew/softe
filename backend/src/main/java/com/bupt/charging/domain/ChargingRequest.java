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
public class ChargingRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String carId;

    @Column(nullable = false)
    private double carCapacity;

    @Column(nullable = false)
    private double requestAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChargeMode mode;

    @Column(nullable = false)
    private LocalDateTime requestTime;

    @Column(nullable = false)
    private String queueNum;

    @Column(nullable = false)
    private long queueSequence;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RequestStatus status;

    private String assignedPileId;

    private int pileQueuePosition;

    private boolean faultAffected;

    protected ChargingRequest() {
    }

    public ChargingRequest(String carId, double carCapacity, double requestAmount, ChargeMode mode,
                           LocalDateTime requestTime, String queueNum, long queueSequence) {
        this.carId = carId;
        this.carCapacity = carCapacity;
        this.requestAmount = requestAmount;
        this.mode = mode;
        this.requestTime = requestTime;
        this.queueNum = queueNum;
        this.queueSequence = queueSequence;
        this.status = RequestStatus.WAITING_AREA;
    }

    public double requestedHours(double power) {
        return requestAmount / power;
    }

    public void changeAmount(double requestAmount) {
        this.requestAmount = requestAmount;
    }

    public void changeMode(ChargeMode mode, String queueNum, long queueSequence, LocalDateTime requestTime) {
        this.mode = mode;
        this.queueNum = queueNum;
        this.queueSequence = queueSequence;
        this.requestTime = requestTime;
        clearPileAssignment();
        this.status = RequestStatus.WAITING_AREA;
    }

    public void assignToPile(String pileId, int pileQueuePosition) {
        this.assignedPileId = pileId;
        this.pileQueuePosition = pileQueuePosition;
        this.status = RequestStatus.PILE_QUEUE;
    }

    public void clearPileAssignment() {
        this.assignedPileId = null;
        this.pileQueuePosition = 0;
    }

    public void startCharging() {
        this.status = RequestStatus.CHARGING;
    }

    public void finish() {
        this.status = RequestStatus.FINISHED;
    }

    public void cancel() {
        clearPileAssignment();
        this.status = RequestStatus.CANCELLED;
    }

    public void interrupt() {
        this.status = RequestStatus.INTERRUPTED;
        this.faultAffected = true;
    }

    public void requeueAfterFault() {
        clearPileAssignment();
        this.status = RequestStatus.WAITING_AREA;
        this.faultAffected = true;
    }

    public void returnToWaitingArea() {
        clearPileAssignment();
        this.status = RequestStatus.WAITING_AREA;
    }

    public Long getId() {
        return id;
    }

    public String getCarId() {
        return carId;
    }

    public double getCarCapacity() {
        return carCapacity;
    }

    public double getRequestAmount() {
        return requestAmount;
    }

    public ChargeMode getMode() {
        return mode;
    }

    public LocalDateTime getRequestTime() {
        return requestTime;
    }

    public String getQueueNum() {
        return queueNum;
    }

    public long getQueueSequence() {
        return queueSequence;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public String getAssignedPileId() {
        return assignedPileId;
    }

    public int getPileQueuePosition() {
        return pileQueuePosition;
    }

    public boolean isFaultAffected() {
        return faultAffected;
    }
}
