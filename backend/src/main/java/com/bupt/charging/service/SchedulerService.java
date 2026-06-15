package com.bupt.charging.service;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.domain.StationConfig;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
import com.bupt.charging.repository.StationConfigRepository;
import com.bupt.charging.strategy.Assignment;
import com.bupt.charging.strategy.ShortestFinishTimeStrategy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SchedulerService {
    private final ChargingRequestRepository requestRepository;
    private final ChargingPileRepository pileRepository;
    private final StationConfigRepository configRepository;
    private final ShortestFinishTimeStrategy shortestFinishTimeStrategy;

    public SchedulerService(
            ChargingRequestRepository requestRepository,
            ChargingPileRepository pileRepository,
            StationConfigRepository configRepository,
            ShortestFinishTimeStrategy shortestFinishTimeStrategy
    ) {
        this.requestRepository = requestRepository;
        this.pileRepository = pileRepository;
        this.configRepository = configRepository;
        this.shortestFinishTimeStrategy = shortestFinishTimeStrategy;
    }

    @Transactional
    public Optional<Assignment> dispatchOne(ChargeMode mode) {
        List<ChargingRequest> waiting = requestRepository.findByModeAndStatusOrderByRequestTimeAsc(
                mode, RequestStatus.WAITING_AREA);
        if (waiting.isEmpty()) {
            return Optional.empty();
        }

        ChargingRequest request = waiting.get(0);
        List<ChargingPile> piles = pileRepository.findByModeOrderByPileIdAsc(mode);
        Map<String, List<ChargingRequest>> queues = pileQueues(piles);
        int queueLength = currentConfig().getQueueLength();
        List<ChargingPile> availablePiles = piles.stream()
                .filter(ChargingPile::isAvailableForQueue)
                .filter(pile -> queues.getOrDefault(pile.getPileId(), List.of()).size() < queueLength)
                .toList();

        Optional<Assignment> assignment = shortestFinishTimeStrategy.select(request, availablePiles, queues);
        assignment.ifPresent(value -> {
            request.assignToPile(value.pileId(), value.queuePosition());
            requestRepository.save(request);
        });
        return assignment;
    }

    @Transactional
    public List<Assignment> dispatchAll() {
        List<Assignment> assignments = new ArrayList<>();
        boolean moved;
        do {
            moved = false;
            for (ChargeMode mode : ChargeMode.values()) {
                Optional<Assignment> assignment = dispatchOne(mode);
                if (assignment.isPresent()) {
                    assignments.add(assignment.get());
                    moved = true;
                }
            }
        } while (moved);
        return assignments;
    }

    private Map<String, List<ChargingRequest>> pileQueues(List<ChargingPile> piles) {
        Map<String, List<ChargingRequest>> queues = new LinkedHashMap<>();
        for (ChargingPile pile : piles) {
            queues.put(pile.getPileId(), requestRepository
                    .findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc(
                            pile.getPileId(), RequestStatus.PILE_QUEUE));
        }
        return queues;
    }

    private StationConfig currentConfig() {
        return configRepository.findFirstByOrderByIdDesc().orElseGet(StationConfig::defaults);
    }
}
