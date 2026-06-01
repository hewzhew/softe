package com.bupt.charging.dto;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.PileStatus;

public final class PileDtos {
    private PileDtos() {
    }

    public record PileStateResponse(
            String pileId,
            ChargeMode mode,
            double power,
            PileStatus workingState,
            int totalChargeNum,
            double totalChargeTime,
            double totalCapacity,
            String currentCarId
    ) {
    }
}
