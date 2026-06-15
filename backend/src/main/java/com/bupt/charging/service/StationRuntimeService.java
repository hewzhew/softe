package com.bupt.charging.service;

import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.domain.ChargingSession;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.domain.SessionStatus;
import com.bupt.charging.dto.BillingDtos;
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

    public StationRuntimeService(
            ChargingSessionRepository sessionRepository,
            ChargingRequestRepository requestRepository,
            ChargingPileRepository pileRepository,
            BillingService billingService,
            SchedulerService schedulerService,
            ChargingService chargingService
    ) {
        this.sessionRepository = sessionRepository;
        this.requestRepository = requestRepository;
        this.pileRepository = pileRepository;
        this.billingService = billingService;
        this.schedulerService = schedulerService;
        this.chargingService = chargingService;
    }

    @Transactional
    public void advanceTo(LocalDateTime targetTime) {
        schedulerService.dispatchAll();
        startIdlePileHeads(targetTime);

        boolean changed;
        do {
            changed = false;
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
                    finishSession(session, request, pile, finishTime);
                    schedulerService.dispatchAll();
                    startIdlePileHeads(finishTime);
                    changed = true;
                    break;
                }
            }
        } while (changed);

        schedulerService.dispatchAll();
        startIdlePileHeads(targetTime);
    }

    private LocalDateTime finishTime(ChargingSession session, ChargingRequest request, ChargingPile pile) {
        long seconds = Math.max(1L, Math.round((request.getRequestAmount() / pile.getPower()) * 3600.0));
        return session.getStartTime().plusSeconds(seconds);
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
        BillingDtos.BillResponse ignored = billingService.createBillForSession(session, pile, amount, finishTime);
        sessionRepository.save(session);
        requestRepository.save(request);
        pileRepository.save(pile);
    }

    private void startIdlePileHeads(LocalDateTime startTime) {
        for (ChargingPile pile : pileRepository.findAll()) {
            if (pile.getCurrentCarId() != null) {
                continue;
            }
            List<ChargingRequest> queue = requestRepository.findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc(
                    pile.getPileId(),
                    RequestStatus.PILE_QUEUE
            );
            if (!queue.isEmpty()) {
                chargingService.startChargingAt(queue.get(0).getCarId(), pile.getPileId(), startTime);
            }
        }
    }
}
