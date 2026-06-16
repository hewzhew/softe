package com.bupt.charging.service;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.domain.ChargingSession;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.domain.SessionStatus;
import com.bupt.charging.domain.StationConfig;
import com.bupt.charging.dto.SchedulerDtos;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
import com.bupt.charging.repository.ChargingSessionRepository;
import com.bupt.charging.repository.StationConfigRepository;
import com.bupt.charging.strategy.Assignment;
import com.bupt.charging.strategy.PileQueueLoad;
import com.bupt.charging.strategy.ShortestFinishTimeStrategy;
import java.time.Duration;
import java.time.LocalDateTime;
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
    private final ChargingSessionRepository sessionRepository;
    private final StationConfigRepository configRepository;
    private final ShortestFinishTimeStrategy shortestFinishTimeStrategy;
    private final StationClockService stationClockService;

    public SchedulerService(
            ChargingRequestRepository requestRepository,
            ChargingPileRepository pileRepository,
            ChargingSessionRepository sessionRepository,
            StationConfigRepository configRepository,
            ShortestFinishTimeStrategy shortestFinishTimeStrategy,
            StationClockService stationClockService
    ) {
        this.requestRepository = requestRepository;
        this.pileRepository = pileRepository;
        this.sessionRepository = sessionRepository;
        this.configRepository = configRepository;
        this.shortestFinishTimeStrategy = shortestFinishTimeStrategy;
        this.stationClockService = stationClockService;
    }

    @Transactional
    public Optional<Assignment> dispatchOne(ChargeMode mode) {
        return nextWaitingRequest(mode).flatMap(this::assignRequest);
    }

    @Transactional
    public Optional<SchedulerDtos.DispatchAssignmentResponse> dispatchOne(ChargeMode mode, String carId) {
        Optional<ChargingRequest> candidate = selectCandidate(mode, carId);
        return candidate.flatMap(request -> assignRequest(request)
                .map(assignment -> new SchedulerDtos.DispatchAssignmentResponse(
                        request.getCarId(),
                        assignment.pileId(),
                        assignment.queuePosition(),
                        assignment.expectedFinishHours()
                )));
    }

    private Optional<ChargingRequest> selectCandidate(ChargeMode mode, String carId) {
        if (carId != null && !carId.isBlank()) {
            return requestRepository
                    .findFirstByCarIdAndStatusInOrderByRequestTimeDesc(carId, List.of(RequestStatus.WAITING_AREA))
                    .filter(request -> mode == null || request.getMode() == mode);
        }
        if (mode != null) {
            return nextWaitingRequest(mode);
        }
        return requestRepository.findByStatusOrderByRequestTimeAsc(RequestStatus.WAITING_AREA).stream().findFirst();
    }

    private Optional<ChargingRequest> nextWaitingRequest(ChargeMode mode) {
        List<ChargingRequest> waiting = requestRepository.findByModeAndStatusOrderByRequestTimeAsc(
                mode, RequestStatus.WAITING_AREA);
        if (waiting.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(waiting.get(0));
    }

    private Optional<Assignment> assignRequest(ChargingRequest request) {
        ChargeMode mode = request.getMode();
        List<ChargingPile> piles = pileRepository.findByModeOrderByPileIdAsc(mode);
        Map<String, List<ChargingRequest>> queues = pileQueues(piles);
        Map<String, PileQueueLoad> loads = pileLoads(piles, queues, stationClockService.currentStationTime());
        int queueLength = currentConfig().getQueueLength();
        List<ChargingPile> availablePiles = piles.stream()
                .filter(ChargingPile::isAvailableForQueue)
                .filter(pile -> queues.getOrDefault(pile.getPileId(), List.of()).size() < queueLength)
                .toList();

        Optional<Assignment> assignment = shortestFinishTimeStrategy.select(request, availablePiles, loads);
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

    private Map<String, PileQueueLoad> pileLoads(
            List<ChargingPile> piles,
            Map<String, List<ChargingRequest>> queues,
            LocalDateTime planningTime
    ) {
        Map<String, PileQueueLoad> loads = new LinkedHashMap<>();
        for (ChargingPile pile : piles) {
            List<ChargingRequest> queue = queues.getOrDefault(pile.getPileId(), List.of());
            double waitingHours = queue.stream()
                    .mapToDouble(request -> request.requestedHours(pile.getPower()))
                    .sum();
            ActiveLoad activeLoad = activeLoad(pile, planningTime);
            loads.put(pile.getPileId(), new PileQueueLoad(
                    queue.size() + activeLoad.occupiedPositions(),
                    waitingHours + activeLoad.remainingHours()
            ));
        }
        return loads;
    }

    private ActiveLoad activeLoad(ChargingPile pile, LocalDateTime planningTime) {
        if (pile.getCurrentCarId() == null) {
            return ActiveLoad.empty();
        }
        ChargingSession session = sessionRepository.findFirstByPileIdAndStatusOrderByStartTimeDesc(
                pile.getPileId(),
                SessionStatus.CHARGING
        ).orElse(null);
        if (session == null) {
            return ActiveLoad.empty();
        }
        ChargingRequest request = requestRepository.findById(session.getRequestId()).orElse(null);
        if (request == null || request.getStatus() != RequestStatus.CHARGING) {
            return ActiveLoad.empty();
        }
        double elapsedHours = Math.max(0.0, Duration.between(session.getStartTime(), planningTime).toSeconds() / 3600.0);
        double remainingAmount = Math.max(0.0, request.getRequestAmount() - elapsedHours * pile.getPower());
        return new ActiveLoad(1, remainingAmount / pile.getPower());
    }

    private StationConfig currentConfig() {
        return configRepository.findFirstByOrderByIdDesc().orElseGet(StationConfig::defaults);
    }

    private record ActiveLoad(int occupiedPositions, double remainingHours) {
        static ActiveLoad empty() {
            return new ActiveLoad(0, 0.0);
        }
    }
}
