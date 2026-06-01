package com.bupt.charging.strategy;

import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import java.util.List;
import java.util.Map;

public record SchedulingContext(
        List<ChargingPile> candidatePiles,
        Map<String, List<ChargingRequest>> currentQueues
) {
}
