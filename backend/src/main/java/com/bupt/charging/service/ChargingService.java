package com.bupt.charging.service;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.domain.ChargingSession;
import com.bupt.charging.domain.EventCommitState;
import com.bupt.charging.domain.PileStatus;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.domain.SessionStatus;
import com.bupt.charging.domain.StationEventType;
import com.bupt.charging.domain.Vehicle;
import com.bupt.charging.dto.BillingDtos;
import com.bupt.charging.dto.ChargingDtos;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
import com.bupt.charging.repository.ChargingSessionRepository;
import com.bupt.charging.repository.StationEventRepository;
import com.bupt.charging.repository.VehicleRepository;
import com.bupt.charging.support.BusinessException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChargingService {
    private static final List<RequestStatus> ACTIVE_REQUEST_STATUSES = List.of(
            RequestStatus.WAITING_AREA,
            RequestStatus.PILE_QUEUE,
            RequestStatus.CHARGING
    );

    private final VehicleRepository vehicleRepository;
    private final ChargingRequestRepository requestRepository;
    private final ChargingPileRepository pileRepository;
    private final ChargingSessionRepository sessionRepository;
    private final StationEventRepository eventRepository;
    private final BillingService billingService;
    private final SchedulerService schedulerService;
    private final StationClockService stationClockService;

    public ChargingService(
            VehicleRepository vehicleRepository,
            ChargingRequestRepository requestRepository,
            ChargingPileRepository pileRepository,
            ChargingSessionRepository sessionRepository,
            StationEventRepository eventRepository,
            BillingService billingService,
            SchedulerService schedulerService,
            StationClockService stationClockService
    ) {
        this.vehicleRepository = vehicleRepository;
        this.requestRepository = requestRepository;
        this.pileRepository = pileRepository;
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.billingService = billingService;
        this.schedulerService = schedulerService;
        this.stationClockService = stationClockService;
    }

    @Transactional
    public ChargingDtos.RequestResponse submitRequest(String carId, double requestAmount, ChargeMode mode) {
        rejectPendingSubmitEvent(carId);
        return submitRequestAt(carId, requestAmount, mode, stationClockService.currentStationTime());
    }

    @Transactional
    public ChargingDtos.RequestResponse submitRequestAt(
            String carId,
            double requestAmount,
            ChargeMode mode,
            LocalDateTime requestTime
    ) {
        Vehicle vehicle = vehicleRepository.findByCarId(carId)
                .orElseThrow(() -> new BusinessException("vehicle not found"));
        if (mode == null) {
            throw new BusinessException("charge mode is required");
        }
        if (requestAmount <= 0 || requestAmount > vehicle.getCarCapacity()) {
            throw new BusinessException("invalid request amount");
        }
        requestRepository.findFirstByCarIdAndStatusInOrderByRequestTimeDesc(carId, ACTIVE_REQUEST_STATUSES)
                .ifPresent(existing -> {
                    throw new BusinessException("car already has active request");
                });

        long sequence = requestRepository.countByMode(mode) + 1;
        String queueNum = queuePrefix(mode) + sequence;
        ChargingRequest request = requestRepository.save(new ChargingRequest(
                carId,
                vehicle.getCarCapacity(),
                requestAmount,
                mode,
                requestTime,
                queueNum,
                sequence
        ));
        return toRequestResponse(request);
    }

    @Transactional
    public ChargingDtos.RequestResponse modifyAmount(String carId, double amount) {
        return modifyAmountAt(carId, amount, stationClockService.currentStationTime(), false);
    }

    @Transactional
    public ChargingDtos.RequestResponse modifyAmountAt(
            String carId,
            double amount,
            LocalDateTime eventTime,
            boolean ignoreMissing
    ) {
        Optional<ChargingRequest> maybeRequest = findActiveRequest(carId);
        if (maybeRequest.isEmpty()) {
            if (ignoreMissing) {
                return null;
            }
            throw new BusinessException("active request not found");
        }
        ChargingRequest request = maybeRequest.get();
        if (request.getStatus() == RequestStatus.CHARGING) {
            if (ignoreMissing) {
                return toRequestResponse(request);
            }
            throw new BusinessException("charging request cannot be modified after charging starts");
        }
        if (amount <= 0 || amount > request.getCarCapacity()) {
            if (ignoreMissing) {
                return toRequestResponse(request);
            }
            throw new BusinessException("invalid request amount");
        }
        request.changeAmount(amount);
        return toRequestResponse(requestRepository.save(request));
    }

    @Transactional
    public ChargingDtos.RequestResponse modifyMode(String carId, ChargeMode mode) {
        ChargingRequest request = activeRequest(carId);
        if (request.getStatus() == RequestStatus.CHARGING) {
            throw new BusinessException("charging mode cannot be modified after charging starts");
        }
        long sequence = requestRepository.countByMode(mode) + 1;
        request.changeMode(mode, queuePrefix(mode) + sequence, sequence, stationClockService.currentStationTime());
        return toRequestResponse(requestRepository.save(request));
    }

    public ChargingDtos.CarStateResponse queryCarState(String carId) {
        ChargingRequest request = requestRepository.findFirstByCarIdOrderByRequestTimeDesc(carId)
                .orElseThrow(() -> new BusinessException("request not found"));
        int before = 0;
        if (request.getStatus() == RequestStatus.PILE_QUEUE && request.getAssignedPileId() != null) {
            before = Math.max(0, request.getPileQueuePosition() - 1);
        } else if (request.getStatus() == RequestStatus.WAITING_AREA) {
            before = (int) requestRepository.findByModeAndStatusOrderByRequestTimeAsc(
                            request.getMode(), RequestStatus.WAITING_AREA).stream()
                    .takeWhile(item -> !item.getCarId().equals(carId))
                    .count();
        }
        return new ChargingDtos.CarStateResponse(
                request.getCarId(),
                before,
                request.getStatus(),
                request.getQueueNum(),
                request.getRequestTime(),
                request.getAssignedPileId()
        );
    }

    @Transactional
    public void startCharging(String carId, String pileId) {
        startChargingAt(carId, pileId, stationClockService.currentStationTime());
    }

    @Transactional
    public void startChargingAt(String carId, String pileId, LocalDateTime startTime) {
        ChargingRequest request = activeRequest(carId);
        ChargingPile pile = pileRepository.findByPileId(pileId)
                .orElseThrow(() -> new BusinessException("pile not found"));
        if (request.getStatus() == RequestStatus.CHARGING) {
            ChargingSession session = sessionRepository.findFirstByCarIdAndStatusOrderByStartTimeDesc(
                            carId, SessionStatus.CHARGING)
                    .orElseThrow(() -> new BusinessException("charging session not found"));
            if (!pileId.equals(session.getPileId())) {
                throw new BusinessException("session is not on this pile");
            }
            if (pile.getStatus() == PileStatus.WORKING && carId.equals(pile.getCurrentCarId())) {
                return;
            }
            throw new BusinessException("pile is not available");
        }
        if (request.getStatus() != RequestStatus.PILE_QUEUE || !pileId.equals(request.getAssignedPileId())) {
            throw new BusinessException("car is not assigned to this pile");
        }
        List<ChargingRequest> queue = requestRepository.findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc(
                pileId, RequestStatus.PILE_QUEUE);
        if (queue.isEmpty() || !queue.get(0).getCarId().equals(carId)) {
            throw new BusinessException("car is not first in pile queue");
        }
        if (pile.getStatus() != PileStatus.IDLE || pile.getCurrentCarId() != null) {
            throw new BusinessException("pile is not available");
        }
        LocalDateTime effectiveStart = startTime.isBefore(request.getRequestTime())
                ? request.getRequestTime()
                : startTime;
        request.startCharging();
        pile.markWorking(carId);
        sessionRepository.save(new ChargingSession(request.getId(), carId, pileId, effectiveStart));
        requestRepository.save(request);
        pileRepository.save(pile);
    }

    @Transactional
    public void cancelRequestAt(String carId, LocalDateTime eventTime) {
        Optional<ChargingRequest> maybeRequest = findActiveRequest(carId);
        if (maybeRequest.isEmpty()) {
            return;
        }
        ChargingRequest request = maybeRequest.get();
        if (request.getStatus() == RequestStatus.CHARGING) {
            interruptChargingForCancellation(request, eventTime);
        } else {
            request.cancel();
            requestRepository.save(request);
        }
        schedulerService.dispatchAll();
    }

    public ChargingDtos.ChargingStateResponse queryChargingState(String carId) {
        ChargingRequest request = activeRequest(carId);
        ChargingSession session = sessionRepository.findFirstByCarIdAndStatusOrderByStartTimeDesc(
                        carId, SessionStatus.CHARGING)
                .orElseThrow(() -> new BusinessException("charging session not found"));
        ChargingPile pile = pileRepository.findByPileId(session.getPileId())
                .orElseThrow(() -> new BusinessException("pile not found"));
        LocalDateTime now = stationClockService.currentStationTime();
        double elapsedHours = Math.max(0.0, Duration.between(session.getStartTime(), now).toSeconds() / 3600.0);
        double chargedAmount = Math.min(request.getRequestAmount(), elapsedHours * pile.getPower());
        return new ChargingDtos.ChargingStateResponse(
                carId,
                session.getPileId(),
                chargedAmount,
                request.getRequestAmount(),
                BigDecimal.ZERO
        );
    }

    @Transactional
    public BillingDtos.BillResponse endCharging(String carId, String pileId, double actualAmount) {
        ChargingRequest request = activeRequest(carId);
        ChargingSession session = sessionRepository.findFirstByCarIdAndStatusOrderByStartTimeDesc(
                        carId, SessionStatus.CHARGING)
                .orElseThrow(() -> new BusinessException("charging session not found"));
        ChargingPile pile = pileRepository.findByPileId(pileId)
                .orElseThrow(() -> new BusinessException("pile not found"));
        if (!pileId.equals(session.getPileId())) {
            throw new BusinessException("session is not on this pile");
        }

        LocalDateTime endTime = session.getStartTime()
                .plusMinutes(Math.max(1, Math.round((actualAmount / pile.getPower()) * 60.0)));
        session.finish(endTime, actualAmount);
        request.finish();
        pile.addChargingStats(actualAmount / pile.getPower(), actualAmount);
        pile.release();
        BillingDtos.BillResponse bill = billingService.createBillForSession(session, pile, actualAmount, endTime);
        sessionRepository.save(session);
        requestRepository.save(request);
        pileRepository.save(pile);
        schedulerService.dispatchAll();
        return bill;
    }

    private ChargingRequest activeRequest(String carId) {
        return findActiveRequest(carId)
                .orElseThrow(() -> new BusinessException("active request not found"));
    }

    private Optional<ChargingRequest> findActiveRequest(String carId) {
        return requestRepository.findFirstByCarIdAndStatusInOrderByRequestTimeDesc(carId, ACTIVE_REQUEST_STATUSES);
    }

    private void rejectPendingSubmitEvent(String carId) {
        boolean hasPendingSubmitEvent = eventRepository
                .existsByAppliedFalseAndCommitStateAndEventTypeAndTargetIdAndEventTimeGreaterThanEqual(
                        EventCommitState.COMMITTED,
                        StationEventType.ChargeRequestSubmitted,
                        carId,
                        stationClockService.runtimeCursorTime());
        if (hasPendingSubmitEvent) {
            throw new BusinessException("car has pending station submit event");
        }
    }

    private void interruptChargingForCancellation(ChargingRequest request, LocalDateTime eventTime) {
        ChargingSession session = sessionRepository.findFirstByCarIdAndStatusOrderByStartTimeDesc(
                        request.getCarId(), SessionStatus.CHARGING)
                .orElse(null);
        if (session == null) {
            request.cancel();
            requestRepository.save(request);
            return;
        }
        ChargingPile pile = pileRepository.findByPileId(session.getPileId())
                .orElseThrow(() -> new BusinessException("pile not found"));
        LocalDateTime endTime = eventTime.isAfter(session.getStartTime())
                ? eventTime
                : session.getStartTime().plusMinutes(1);
        double elapsedHours = Math.max(0.0, Duration.between(session.getStartTime(), endTime).toSeconds() / 3600.0);
        double amount = Math.min(request.getRequestAmount(), elapsedHours * pile.getPower());
        session.interrupt(endTime, amount);
        request.cancel();
        pile.addChargingStats(elapsedHours, amount);
        pile.release();
        billingService.createBillForSession(session, pile, amount, endTime);
        sessionRepository.save(session);
        requestRepository.save(request);
        pileRepository.save(pile);
    }

    private ChargingDtos.RequestResponse toRequestResponse(ChargingRequest request) {
        String position = request.getStatus() == RequestStatus.WAITING_AREA ? "WAITING_AREA" : request.getAssignedPileId();
        return new ChargingDtos.RequestResponse(
                request.getCarId(),
                position,
                request.getStatus(),
                request.getQueueNum(),
                request.getRequestTime(),
                request.getAssignedPileId()
        );
    }

    private String queuePrefix(ChargeMode mode) {
        return mode == ChargeMode.FAST ? "F" : "T";
    }
}
