package com.bupt.charging.dto;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.RequestStatus;
import java.time.LocalDateTime;
import java.util.List;

public final class QueueDtos {
    private QueueDtos() {
    }

    public record QueueItemResponse(
            String carId,
            double carCapacity,
            double requestAmount,
            ChargeMode mode,
            RequestStatus status,
            String queueNum,
            String pileId,
            int position,
            double waitTime,
            LocalDateTime requestTime
    ) {
    }

    public record QueueStateResponse(
            List<QueueItemResponse> waitingArea,
            List<QueueItemResponse> pileQueues
    ) {
    }
}
