package com.bupt.charging.dto;

import com.bupt.charging.domain.ChargeMode;

public final class SchedulerDtos {
    private SchedulerDtos() {
    }

    public record DispatchOneRequest(ChargeMode mode, String carId) {
    }

    public record DispatchAssignmentResponse(
            String carId,
            String pileId,
            int queuePosition,
            double expectedFinishHours
    ) {
    }
}
