package com.bupt.charging.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class BillingDtos {
    private BillingDtos() {
    }

    public record BillResponse(
            Long billId,
            String carId,
            LocalDate date,
            String pileId,
            double chargeAmount,
            double chargeDuration,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal totalChargeFee,
            BigDecimal totalServiceFee,
            BigDecimal totalFee
    ) {
    }

    public record DetailedListResponse(
            Long detailId,
            Long billId,
            String carId,
            String pileId,
            double chargeAmount,
            double chargeDuration,
            LocalDateTime startTime,
            LocalDateTime endTime,
            BigDecimal chargeFee,
            BigDecimal serviceFee,
            BigDecimal subtotalFee
    ) {
    }
}
