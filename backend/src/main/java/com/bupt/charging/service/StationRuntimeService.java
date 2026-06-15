package com.bupt.charging.service;

import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.domain.ChargingSession;
import com.bupt.charging.domain.PileStatus;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.domain.SessionStatus;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
import com.bupt.charging.repository.ChargingSessionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StationRuntimeService {
    private final ChargingSessionRepository sessionRepository;
    private final ChargingRequestRepository requestRepository;
    private final ChargingPileRepository pileRepository;
    private final BillingService billingService;
    private final SchedulerService schedulerService;
    private final ChargingService chargingService;
    private final StationClockService stationClockService;
    private final StationEventService stationEventService;

    public StationRuntimeService(
            ChargingSessionRepository sessionRepository,
            ChargingRequestRepository requestRepository,
            ChargingPileRepository pileRepository,
            BillingService billingService,
            SchedulerService schedulerService,
            ChargingService chargingService,
            StationClockService stationClockService,
            StationEventService stationEventService
    ) {
        this.sessionRepository = sessionRepository;
        this.requestRepository = requestRepository;
        this.pileRepository = pileRepository;
        this.billingService = billingService;
        this.schedulerService = schedulerService;
        this.chargingService = chargingService;
        this.stationClockService = stationClockService;
        this.stationEventService = stationEventService;
    }

    @Transactional
    public void advanceTo(LocalDateTime targetTime) {
        LocalDateTime cursorTime = stationClockService.lockRuntimeCursorTime();
        if (targetTime.isBefore(cursorTime)) {
            targetTime = cursorTime;
        }

        LocalDateTime boundaryTime = cursorTime;
        while (true) {
            LocalDateTime nextEventTime = stationEventService.nextDueEventTime(targetTime).orElse(null);
            LocalDateTime horizonTime = nextEventTime == null ? targetTime : nextEventTime;

            schedulerService.dispatchAll();
            startIdlePileHeads(boundaryTime, horizonTime);

            DueSession nextSession = nextDueSession(horizonTime);
            if (nextSession != null) {
                finishSession(nextSession.session(), nextSession.request(), nextSession.pile(), nextSession.finishTime());
                boundaryTime = nextSession.finishTime();
                continue;
            }

            if (nextEventTime != null) {
                stationEventService.applyNextDueEvent(nextEventTime);
                boundaryTime = nextEventTime;
                continue;
            }

            break;
        }

        schedulerService.dispatchAll();
        startIdlePileHeads(targetTime, targetTime);
        stationClockService.markRuntimeAdvancedTo(targetTime);
    }

    private DueSession nextDueSession(LocalDateTime targetTime) {
        ChargingSession nextSession = null;
        ChargingRequest nextRequest = null;
        ChargingPile nextPile = null;
        LocalDateTime nextFinishTime = null;
        List<ChargingSession> activeSessions = sessionRepository.findByStatusOrderByStartTimeAsc(
                SessionStatus.CHARGING);
        for (ChargingSession session : activeSessions) {
            ChargingRequest request = requestRepository.findById(session.getRequestId()).orElse(null);
            ChargingPile pile = pileRepository.findByPileId(session.getPileId()).orElse(null);
            if (request == null || pile == null) {
                continue;
            }
            LocalDateTime finishTime = finishTime(session, request, pile);
            if (!finishTime.isAfter(targetTime)) {
                if (isEarlierDueSession(finishTime, pile, request, nextFinishTime, nextPile, nextRequest)) {
                    nextSession = session;
                    nextRequest = request;
                    nextPile = pile;
                    nextFinishTime = finishTime;
                }
            }
        }
        if (nextSession == null) {
            return null;
        }
        return new DueSession(nextSession, nextRequest, nextPile, nextFinishTime);
    }

    private LocalDateTime finishTime(ChargingSession session, ChargingRequest request, ChargingPile pile) {
        long seconds = Math.max(1L, Math.round((request.getRequestAmount() / pile.getPower()) * 3600.0));
        return session.getStartTime().plusSeconds(seconds);
    }

    private boolean isEarlierDueSession(
            LocalDateTime finishTime,
            ChargingPile pile,
            ChargingRequest request,
            LocalDateTime currentFinishTime,
            ChargingPile currentPile,
            ChargingRequest currentRequest
    ) {
        if (currentFinishTime == null) {
            return true;
        }
        int finishComparison = finishTime.compareTo(currentFinishTime);
        if (finishComparison != 0) {
            return finishComparison < 0;
        }
        int pileComparison = pile.getPileId().compareTo(currentPile.getPileId());
        if (pileComparison != 0) {
            return pileComparison < 0;
        }
        return request.getCarId().compareTo(currentRequest.getCarId()) < 0;
    }

    private void finishSession(
            ChargingSession session,
            ChargingRequest request,
            ChargingPile pile,
            LocalDateTime finishTime
    ) {
        double amount = request.getRequestAmount();
        session.finish(finishTime, amount);
        request.finish();
        pile.addChargingStats(Duration.between(session.getStartTime(), finishTime).toSeconds() / 3600.0, amount);
        pile.release();
        billingService.createBillForSession(session, pile, amount, finishTime);
        sessionRepository.save(session);
        requestRepository.save(request);
        pileRepository.save(pile);
    }

    private void startIdlePileHeads(LocalDateTime boundaryTime, LocalDateTime targetTime) {
        for (ChargingPile pile : pileRepository.findAll()) {
            if (pile.getCurrentCarId() != null || pile.getStatus() != PileStatus.IDLE) {
                continue;
            }
            List<ChargingRequest> queue = requestRepository.findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc(
                    pile.getPileId(),
                    RequestStatus.PILE_QUEUE
            );
            if (!queue.isEmpty()) {
                ChargingRequest request = queue.get(0);
                LocalDateTime effectiveStart = max(boundaryTime, request.getRequestTime());
                if (!effectiveStart.isAfter(targetTime)) {
                    chargingService.startChargingAt(request.getCarId(), pile.getPileId(), effectiveStart);
                }
            }
        }
    }

    private LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        return left.isAfter(right) ? left : right;
    }

    private record DueSession(
            ChargingSession session,
            ChargingRequest request,
            ChargingPile pile,
            LocalDateTime finishTime
    ) {
    }
}
