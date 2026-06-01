package com.bupt.charging.strategy;

import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ShortestFinishTimeStrategy implements SchedulingStrategy {
    @Override
    public Optional<Assignment> select(
            ChargingRequest request,
            List<ChargingPile> candidatePiles,
            Map<String, List<ChargingRequest>> currentQueues
    ) {
        return candidatePiles.stream()
                .filter(pile -> pile.getMode() == request.getMode())
                .filter(ChargingPile::isAvailableForQueue)
                .map(pile -> assignmentFor(request, pile, currentQueues.getOrDefault(pile.getPileId(), List.of())))
                .min(Comparator
                        .comparingDouble(Assignment::expectedFinishHours)
                        .thenComparing(Assignment::pileId));
    }

    private Assignment assignmentFor(ChargingRequest request, ChargingPile pile, List<ChargingRequest> queue) {
        double waitingHours = queue.stream()
                .mapToDouble(item -> item.getRequestAmount() / pile.getPower())
                .sum();
        double finishHours = waitingHours + request.getRequestAmount() / pile.getPower();
        return new Assignment(pile.getPileId(), queue.size() + 1, finishHours);
    }
}
