package com.bupt.charging.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class FaultDtos {
    private FaultDtos() {
    }

    public record CreateFaultRequest(@NotBlank String pileId, @NotBlank String strategy) {
    }

    public record FaultResult(
            String faultPileId,
            String strategy,
            List<String> movedCars,
            List<String> reorderedCars,
            int generatedDetailCount
    ) {
    }
}
