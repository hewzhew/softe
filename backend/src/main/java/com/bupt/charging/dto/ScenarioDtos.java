package com.bupt.charging.dto;

import java.util.List;
import java.util.Map;

public final class ScenarioDtos {
    private ScenarioDtos() {
    }

    public record ScenarioDefinition(
            String id,
            String name,
            String version,
            String startTime,
            String stopTime,
            PileConfig pileConfig
    ) {
    }

    public record PileConfig(List<String> fast, List<String> slow) {
    }

    public record TimeSession(
            String id,
            String mode,
            String baseTime,
            String cursorTime,
            String windowStart,
            String windowEnd,
            String materializationPolicy,
            String branchId
    ) {
    }

    public record SourceSummary(
            String primarySourceType,
            String primarySourceName,
            List<String> sourceTypes,
            int eventCount,
            int snapshotCount
    ) {
    }

    public record ScenarioCommand(
            long sequence,
            String time,
            String type,
            String targetId,
            String mode,
            String amount,
            String sourceText,
            String displayText,
            String sourceType,
            String commitState,
            String branchId
    ) {
        public ScenarioCommand(
                long sequence,
                String time,
                String type,
                String targetId,
                String mode,
                String amount,
                String sourceText,
                String displayText
        ) {
            this(
                    sequence,
                    time,
                    type,
                    targetId,
                    mode,
                    amount,
                    sourceText,
                    displayText,
                    "COURSE_SEQUENCE",
                    "PROVISIONAL",
                    null
            );
        }
    }

    public record ReplayBundle(
            ScenarioDefinition scenario,
            TimeSession session,
            SourceSummary sourceSummary,
            List<ScenarioCommand> commands,
            List<StationSnapshot> snapshots,
            List<ScenarioTransition> transitions,
            List<ScenarioCheck> checks,
            List<AcceptanceDtos.AcceptanceEventRow> tableRows
    ) {
    }

    public record StationSnapshot(
            long sequence,
            String time,
            long appliedCommandSequence,
            StationState station,
            Map<String, VehicleState> vehicles,
            List<String> ruleNotes
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

    public record ScenarioTransition(
            long fromSequence,
            long toSequence,
            String time,
            List<TransitionChange> changes
    ) {
    }

    public record TransitionChange(
            String entityType,
            String entityId,
            String changeType,
            String before,
            String after,
            String reason
    ) {
    }

    public record ScenarioCheck(
            String id,
            String name,
            String expected,
            String actual,
            boolean passed,
            String source
    ) {
    }
}
