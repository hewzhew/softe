package com.bupt.charging.service;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.domain.ChargingSession;
import com.bupt.charging.domain.FaultRecord;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.domain.SessionStatus;
import com.bupt.charging.domain.StationConfig;
import com.bupt.charging.dto.FaultDtos;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
import com.bupt.charging.repository.ChargingSessionRepository;
import com.bupt.charging.repository.FaultRecordRepository;
import com.bupt.charging.repository.StationConfigRepository;
import com.bupt.charging.strategy.PriorityFaultStrategy;
import com.bupt.charging.strategy.TimeOrderFaultStrategy;
import com.bupt.charging.support.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FaultService {
    private final ChargingPileRepository pileRepository;
    private final ChargingRequestRepository requestRepository;
    private final ChargingSessionRepository sessionRepository;
    private final FaultRecordRepository faultRecordRepository;
    private final StationConfigRepository configRepository;
    private final BillingService billingService;
    private final PriorityFaultStrategy priorityFaultStrategy;
    private final TimeOrderFaultStrategy timeOrderFaultStrategy;
    private final StationClockService stationClockService;

    public FaultService(
            ChargingPileRepository pileRepository,
            ChargingRequestRepository requestRepository,
            ChargingSessionRepository sessionRepository,
            FaultRecordRepository faultRecordRepository,
            StationConfigRepository configRepository,
            BillingService billingService,
            PriorityFaultStrategy priorityFaultStrategy,
            TimeOrderFaultStrategy timeOrderFaultStrategy,
            StationClockService stationClockService
    ) {
        this.pileRepository = pileRepository;
        this.requestRepository = requestRepository;
        this.sessionRepository = sessionRepository;
        this.faultRecordRepository = faultRecordRepository;
        this.configRepository = configRepository;
        this.billingService = billingService;
        this.priorityFaultStrategy = priorityFaultStrategy;
        this.timeOrderFaultStrategy = timeOrderFaultStrategy;
        this.stationClockService = stationClockService;
    }

    @Transactional
    public FaultDtos.FaultResult handleFault(String pileId, String strategy) {
        ChargingPile faultPile = pileRepository.findByPileId(pileId)
                .orElseThrow(() -> new BusinessException("pile not found"));
        ChargeMode mode = faultPile.getMode();
        LocalDateTime now = stationClockService.currentStationTime();
        FaultRecord faultRecord = faultRecordRepository.save(new FaultRecord(pileId, strategy, now));

        int generatedDetailCount = interruptCurrentSessionIfNeeded(faultPile, now);
        List<ChargingRequest> candidates = collectCandidates(faultPile, strategy);
        List<ChargingRequest> ordered = orderCandidates(candidates, strategy);
        List<String> reorderedCars = ordered.stream().map(ChargingRequest::getCarId).toList();

        for (ChargingRequest request : ordered) {
            request.requeueAfterFault();
        }
        requestRepository.saveAll(ordered);

        faultPile.markFault();
        pileRepository.save(faultPile);
        List<String> movedCars = reassign(mode, ordered);
        faultRecord.updateResult(String.join(",", movedCars));
        faultRecordRepository.save(faultRecord);

        return new FaultDtos.FaultResult(pileId, strategy, movedCars, reorderedCars, generatedDetailCount);
    }

    @Transactional
    public FaultDtos.FaultResult recoverPile(String pileId) {
        ChargingPile pile = pileRepository.findByPileId(pileId)
                .orElseThrow(() -> new BusinessException("pile not found"));
        pile.recover();
        pileRepository.save(pile);
        faultRecordRepository.findFirstByPileIdAndStatusOrderByFaultTimeDesc(pileId, "OPEN")
                .ifPresent(record -> {
                    record.close(stationClockService.currentStationTime(), "recovered");
                    faultRecordRepository.save(record);
                });
        return new FaultDtos.FaultResult(pileId, "RECOVER", List.of(), List.of(), 0);
    }

    private int interruptCurrentSessionIfNeeded(ChargingPile faultPile, LocalDateTime now) {
        return sessionRepository.findFirstByPileIdAndStatusOrderByStartTimeDesc(
                        faultPile.getPileId(), SessionStatus.CHARGING)
                .map(session -> {
                    ChargingRequest request = requestRepository.findById(session.getRequestId())
                            .orElseThrow(() -> new BusinessException("request not found"));
                    double amount = interruptedAmount(session, request, faultPile, now);
                    session.interrupt(now.isAfter(session.getStartTime()) ? now : session.getStartTime().plusMinutes(1), amount);
                    request.requeueAfterFault();
                    billingService.createBillForSession(session, faultPile, amount, session.getEndTime());
                    sessionRepository.save(session);
                    requestRepository.save(request);
                    return 1;
                })
                .orElse(0);
    }

    private double interruptedAmount(ChargingSession session, ChargingRequest request, ChargingPile pile, LocalDateTime now) {
        double elapsedHours = Math.max(0.0, Duration.between(session.getStartTime(), now).toSeconds() / 3600.0);
        return Math.min(request.getRequestAmount(), Math.max(1.0, elapsedHours * pile.getPower()));
    }

    private List<ChargingRequest> collectCandidates(ChargingPile faultPile, String strategy) {
        List<ChargingRequest> candidates = new ArrayList<>(requestRepository
                .findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc(
                        faultPile.getPileId(), RequestStatus.PILE_QUEUE));
        if ("TIME_ORDER".equalsIgnoreCase(strategy)) {
            for (ChargingPile pile : pileRepository.findByModeOrderByPileIdAsc(faultPile.getMode())) {
                if (!pile.getPileId().equals(faultPile.getPileId())) {
                    candidates.addAll(requestRepository.findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc(
                            pile.getPileId(), RequestStatus.PILE_QUEUE));
                }
            }
        }
        return candidates;
    }

    private List<ChargingRequest> orderCandidates(List<ChargingRequest> candidates, String strategy) {
        if ("TIME_ORDER".equalsIgnoreCase(strategy)) {
            return timeOrderFaultStrategy.order(candidates);
        }
        return priorityFaultStrategy.order(candidates);
    }

    private List<String> reassign(ChargeMode mode, List<ChargingRequest> ordered) {
        StationConfig config = configRepository.findFirstByOrderByIdDesc().orElseGet(StationConfig::defaults);
        List<ChargingPile> availablePiles = pileRepository.findByModeOrderByPileIdAsc(mode).stream()
                .filter(ChargingPile::isAvailableForQueue)
                .toList();
        Map<String, List<ChargingRequest>> queues = currentQueues(availablePiles);
        List<String> movedCars = new ArrayList<>();
        for (ChargingRequest request : ordered) {
            ChargingPile bestPile = null;
            double bestFinishHours = Double.MAX_VALUE;
            for (ChargingPile pile : availablePiles) {
                List<ChargingRequest> queue = queues.getOrDefault(pile.getPileId(), List.of());
                if (queue.size() >= config.getQueueLength()) {
                    continue;
                }
                double finishHours = queue.stream()
                        .mapToDouble(item -> item.getRequestAmount() / pile.getPower())
                        .sum() + request.getRequestAmount() / pile.getPower();
                if (finishHours < bestFinishHours) {
                    bestFinishHours = finishHours;
                    bestPile = pile;
                }
            }
            if (bestPile != null) {
                List<ChargingRequest> queue = queues.computeIfAbsent(bestPile.getPileId(), key -> new ArrayList<>());
                request.assignToPile(bestPile.getPileId(), queue.size() + 1);
                queue.add(request);
                requestRepository.save(request);
                movedCars.add(request.getCarId());
            }
        }
        return movedCars;
    }

    private Map<String, List<ChargingRequest>> currentQueues(List<ChargingPile> availablePiles) {
        Map<String, List<ChargingRequest>> queues = new LinkedHashMap<>();
        for (ChargingPile pile : availablePiles) {
            queues.put(pile.getPileId(), new ArrayList<>(requestRepository
                    .findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc(
                            pile.getPileId(), RequestStatus.PILE_QUEUE)));
        }
        return queues;
    }
}
