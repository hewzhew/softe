package com.bupt.charging.strategy;

import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ShortestFinishTimeStrategy implements SchedulingStrategy {
    @Override
    public Optional<Assignment> select(
            ChargingRequest request,
            java.util.List<ChargingPile> candidatePiles,
            Map<String, PileQueueLoad> currentLoads
    ) {
        return candidatePiles.stream()
                .filter(pile -> pile.getMode() == request.getMode())
                .filter(ChargingPile::isAvailableForQueue)
                .map(pile -> assignmentFor(
                        request,
                        pile,
                        currentLoads.getOrDefault(pile.getPileId(), PileQueueLoad.empty())
                ))
                .min(Comparator
                        .comparingDouble(Assignment::expectedFinishHours)
                        .thenComparing(Assignment::pileId));
    }

    private Assignment assignmentFor(ChargingRequest request, ChargingPile pile, PileQueueLoad load) {
        double waitingHours = load.expectedWaitHours();
        double finishHours = waitingHours + request.getRequestAmount() / pile.getPower();
        return new Assignment(pile.getPileId(), load.occupiedPositions() + 1, finishHours);
    }
}
