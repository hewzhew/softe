package com.bupt.charging.dto;

import java.util.List;

public final class ConfigDtos {
    private ConfigDtos() {
    }

    public record UpdateConfigRequest(
            int fastPileCount,
            int slowPileCount,
            int waitingAreaSize,
            int queueLength,
            double fastPower,
            double slowPower
    ) {
    }

    public record ConfigResponse(
            int fastPileCount,
            int slowPileCount,
            int waitingAreaSize,
            int queueLength,
            double fastPower,
            double slowPower
    ) {
    }

    public record SystemSnapshot(
            ConfigResponse config,
            List<PileDtos.PileStateResponse> piles,
            QueueDtos.QueueStateResponse queues,
            List<BillingDtos.BillResponse> recentBills
    ) {
    }
}
