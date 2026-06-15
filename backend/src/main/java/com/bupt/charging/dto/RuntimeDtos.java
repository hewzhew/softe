package com.bupt.charging.dto;

import com.bupt.charging.domain.ChargeMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;

public final class RuntimeDtos {
    private RuntimeDtos() {
    }

    public record SetClockRequest(
            LocalDateTime currentTime,
            double rate,
            Boolean running,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    ) {
    }

    public record ClockResponse(
            LocalDateTime currentTime,
            double rate,
            boolean running,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    ) {
    }

    public record AdvanceRequest(@NotNull LocalDateTime toTime) {
    }

    public record ManualChargeRequestEvent(
            LocalDateTime eventTime,
            @NotBlank String carId,
            String ownerName,
            double carCapacity,
            ChargeMode mode,
            @Positive double requestAmount,
            String sourceName
    ) {
    }

    public record ImportEventsRequest(String sourceType, String sourceName, boolean resetBeforeImport) {
    }

    public record RuntimeEventRow(
            Long id,
            LocalDateTime eventTime,
            String sourceType,
            String sourceName,
            String eventType,
            String targetId,
            String mode,
            double amount,
            boolean applied,
            String rawText
    ) {
    }

    public record ImportEventsResponse(String sourceName, int eventCount, List<RuntimeEventRow> events) {
    }
}
