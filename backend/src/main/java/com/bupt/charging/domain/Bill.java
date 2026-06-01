package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
public class Bill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String carId;

    @Column(nullable = false)
    private LocalDate billDate;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    @Column(nullable = false)
    private BigDecimal totalChargeFee;

    @Column(nullable = false)
    private BigDecimal totalServiceFee;

    @Column(nullable = false)
    private BigDecimal totalFee;

    protected Bill() {
    }

    public Bill(String carId, LocalDate billDate, LocalDateTime generatedAt,
                BigDecimal totalChargeFee, BigDecimal totalServiceFee, BigDecimal totalFee) {
        this.carId = carId;
        this.billDate = billDate;
        this.generatedAt = generatedAt;
        this.totalChargeFee = totalChargeFee;
        this.totalServiceFee = totalServiceFee;
        this.totalFee = totalFee;
    }

    public Long getId() {
        return id;
    }

    public String getCarId() {
        return carId;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public BigDecimal getTotalChargeFee() {
        return totalChargeFee;
    }

    public BigDecimal getTotalServiceFee() {
        return totalServiceFee;
    }

    public BigDecimal getTotalFee() {
        return totalFee;
    }
}
