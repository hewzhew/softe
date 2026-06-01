package com.bupt.charging.dto;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.RequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class ChargingDtos {
    private ChargingDtos() {
    }

    public record SubmitRequest(@NotBlank String carId, @Positive double requestAmount, ChargeMode mode) {
    }

    public record ModifyAmountRequest(@Positive double amount) {
    }

    public record ModifyModeRequest(ChargeMode mode) {
    }

    public record StartChargingRequest(@NotBlank String pileId) {
    }

    public record EndChargingRequest(@NotBlank String pileId, @Positive double actualAmount) {
    }

    public record RequestResponse(
            String carId,
            String carPosition,
            RequestStatus carState,
            String queueNum,
            LocalDateTime requestTime,
            String assignedPileId
    ) {
    }

    public record CarStateResponse(
            String carId,
            int carNumberBeforePosition,
            RequestStatus carState,
            String queueNum,
            LocalDateTime requestTime,
            String assignedPileId
    ) {
    }

    public record ChargingStateResponse(
            String carId,
            String pileId,
            double chargedAmount,
            double requestedAmount,
            BigDecimal estimatedFee
    ) {
    }
}
