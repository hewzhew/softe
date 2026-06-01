package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
public class DetailedList {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long billId;

    @Column(nullable = false)
    private String carId;

    @Column(nullable = false)
    private String pileId;

    @Column(nullable = false)
    private double chargeAmount;

    @Column(nullable = false)
    private double chargeDuration;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private BigDecimal chargeFee;

    @Column(nullable = false)
    private BigDecimal serviceFee;

    @Column(nullable = false)
    private BigDecimal subtotalFee;

    protected DetailedList() {
    }

    public DetailedList(Long billId, String carId, String pileId, double chargeAmount, double chargeDuration,
                        LocalDateTime startTime, LocalDateTime endTime, BigDecimal chargeFee,
                        BigDecimal serviceFee, BigDecimal subtotalFee) {
        this.billId = billId;
        this.carId = carId;
        this.pileId = pileId;
        this.chargeAmount = chargeAmount;
        this.chargeDuration = chargeDuration;
        this.startTime = startTime;
        this.endTime = endTime;
        this.chargeFee = chargeFee;
        this.serviceFee = serviceFee;
        this.subtotalFee = subtotalFee;
    }

    public Long getId() {
        return id;
    }

    public Long getBillId() {
        return billId;
    }

    public String getCarId() {
        return carId;
    }

    public String getPileId() {
        return pileId;
    }

    public double getChargeAmount() {
        return chargeAmount;
    }

    public double getChargeDuration() {
        return chargeDuration;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public BigDecimal getChargeFee() {
        return chargeFee;
    }

    public BigDecimal getServiceFee() {
        return serviceFee;
    }

    public BigDecimal getSubtotalFee() {
        return subtotalFee;
    }
}
