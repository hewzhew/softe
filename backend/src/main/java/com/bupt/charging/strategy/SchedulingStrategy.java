package com.bupt.charging.strategy;

import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface SchedulingStrategy {
    Optional<Assignment> select(
            ChargingRequest request,
            List<ChargingPile> candidatePiles,
            Map<String, PileQueueLoad> currentLoads
    );
}
