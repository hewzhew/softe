package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalTime;

@Entity
public class TariffRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal peakPrice;

    @Column(nullable = false)
    private BigDecimal normalPrice;

    @Column(nullable = false)
    private BigDecimal valleyPrice;

    @Column(nullable = false)
    private BigDecimal servicePrice;

    protected TariffRule() {
    }

    public TariffRule(BigDecimal peakPrice, BigDecimal normalPrice, BigDecimal valleyPrice, BigDecimal servicePrice) {
        this.peakPrice = peakPrice;
        this.normalPrice = normalPrice;
        this.valleyPrice = valleyPrice;
        this.servicePrice = servicePrice;
    }

    public static TariffRule defaults() {
        return new TariffRule(
                new BigDecimal("1.0"),
                new BigDecimal("0.7"),
                new BigDecimal("0.4"),
                new BigDecimal("0.8")
        );
    }

    public BigDecimal priceAt(LocalTime time) {
        if (between(time, "10:00", "15:00") || between(time, "18:00", "21:00")) {
            return peakPrice;
        }
        if (between(time, "07:00", "10:00") || between(time, "15:00", "18:00")
                || between(time, "21:00", "23:00")) {
            return normalPrice;
        }
        return valleyPrice;
    }

    private boolean between(LocalTime time, String start, String end) {
        LocalTime startTime = LocalTime.parse(start);
        LocalTime endTime = LocalTime.parse(end);
        return !time.isBefore(startTime) && time.isBefore(endTime);
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getPeakPrice() {
        return peakPrice;
    }

    public BigDecimal getNormalPrice() {
        return normalPrice;
    }

    public BigDecimal getValleyPrice() {
        return valleyPrice;
    }

    public BigDecimal getServicePrice() {
        return servicePrice;
    }
}
