package com.bupt.charging.dto;

import java.util.List;

public final class AcceptanceDtos {
    private AcceptanceDtos() {
    }

    public record AcceptanceScenarioConfig(
            double fastPower,
            double slowPower,
            double peakPrice,
            double normalPrice,
            double valleyPrice,
            double servicePrice,
            int fastPileCount,
            int slowPileCount,
            int queueLength,
            int waitingAreaSize,
            String startTime,
            String endTime
    ) {
    }

    public record AcceptanceEventRow(
            String time,
            String event,
            List<String> fast1,
            List<String> fast2,
            List<String> slow1,
            List<String> slow2,
            List<String> slow3,
            String waitingAreaText,
            String notes
    ) {
    }

    public record AcceptanceSampleCheck(
            String time,
            String column,
            String expected,
            String actual,
            boolean matched
    ) {
    }

    public record AcceptanceScenarioResponse(
            AcceptanceScenarioConfig config,
            List<AcceptanceEventRow> rows,
            List<AcceptanceSampleCheck> sampleChecks
    ) {
    }
}
