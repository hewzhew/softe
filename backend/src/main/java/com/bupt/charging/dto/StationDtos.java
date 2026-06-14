package com.bupt.charging.dto;

import java.util.List;
import java.util.Map;

public final class StationDtos {
    private StationDtos() {
    }

    public record SourceSummary(
            String primarySourceType,
            String primarySourceName,
            List<String> sourceTypes,
            int eventCount,
            int snapshotCount
    ) {
    }

    public record StationSnapshot(
            String time,
            StationState station,
            Map<String, VehicleState> vehicles,
            Metrics metrics,
            String sessionMode,
            SourceSummary sourceSummary
    ) {
        public StationSnapshot(
                String time,
                StationState station,
                Map<String, VehicleState> vehicles,
                Metrics metrics
        ) {
            this(
                    time,
                    station,
                    vehicles,
                    metrics,
                    "LIVE",
                    new SourceSummary("LIVE_MANUAL", "当前站点", List.of("LIVE_MANUAL"), 0, 1)
            );
        }
    }

    public record StationState(
            List<String> waitingArea,
            List<PileState> fastPiles,
            List<PileState> slowPiles
    ) {
    }

    public record PileState(
            String id,
            String mode,
            String status,
            String currentVehicle,
            List<String> queue,
            String power
    ) {
    }

    public record VehicleState(
            String id,
            String mode,
            String state,
            String requestKwh,
            String chargedKwh,
            String queueNo,
            String position
    ) {
    }

    public record Metrics(
            int waitingCount,
            int pileQueueCount,
            int faultPileCount,
            int activePileCount
    ) {
    }
}
