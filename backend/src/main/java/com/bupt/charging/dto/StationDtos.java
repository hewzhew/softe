package com.bupt.charging.dto;

import java.util.List;
import java.util.Map;

public final class StationDtos {
    private StationDtos() {
    }

    public record StationSnapshot(
            String time,
            StationState station,
            Map<String, VehicleState> vehicles,
            Metrics metrics
    ) {
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
