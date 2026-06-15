package com.bupt.charging.service;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.EventCommitState;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.domain.StationEvent;
import com.bupt.charging.domain.StationEventSourceType;
import com.bupt.charging.domain.StationEventType;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.repository.ChargingRequestRepository;
import com.bupt.charging.repository.StationEventRepository;
import com.bupt.charging.repository.VehicleRepository;
import com.bupt.charging.support.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StationEventService {
    private static final LocalDate COURSE_DATE = LocalDate.of(2026, 6, 1);
    private static final String COURSE_SOURCE_NAME = "课程事件序列";
    private static final List<RequestStatus> ACTIVE_REQUEST_STATUSES = List.of(
            RequestStatus.WAITING_AREA,
            RequestStatus.PILE_QUEUE,
            RequestStatus.CHARGING
    );

    private final StationEventRepository eventRepository;
    private final AcceptanceScenarioService acceptanceScenarioService;
    private final StationClockService stationClockService;
    private final AccountService accountService;
    private final ChargingRequestRepository requestRepository;
    private final VehicleRepository vehicleRepository;
    private final ChargingService chargingService;
    private final FaultService faultService;

    public StationEventService(
            StationEventRepository eventRepository,
            AcceptanceScenarioService acceptanceScenarioService,
            StationClockService stationClockService,
            AccountService accountService,
            ChargingRequestRepository requestRepository,
            VehicleRepository vehicleRepository,
            ChargingService chargingService,
            FaultService faultService
    ) {
        this.eventRepository = eventRepository;
        this.acceptanceScenarioService = acceptanceScenarioService;
        this.stationClockService = stationClockService;
        this.accountService = accountService;
        this.requestRepository = requestRepository;
        this.vehicleRepository = vehicleRepository;
        this.chargingService = chargingService;
        this.faultService = faultService;
    }

    @Transactional
    public RuntimeDtos.ImportEventsResponse importCourseSample(boolean resetBeforeImport) {
        if (resetBeforeImport) {
            eventRepository.deleteAll();
        } else if (eventRepository.existsBySourceType(StationEventSourceType.COURSE_PRESET)) {
            throw new BusinessException("course preset events already imported; reset before importing again");
        }
        List<StationEvent> events = new ArrayList<>();
        long sequence = nextSequence();
        LocalDateTime receivedAt = stationClockService.currentStationTime();
        for (String raw : acceptanceScenarioService.courseSampleRawEvents()) {
            events.add(parseCourseEvent(raw, sequence++, receivedAt));
        }
        if (!resetBeforeImport) {
            LocalDateTime runtimeCursor = stationClockService.runtimeCursorTime();
            boolean hasBackdatedEvent = events.stream().anyMatch(event -> event.getEventTime().isBefore(runtimeCursor));
            if (hasBackdatedEvent) {
                throw new BusinessException("course event time is before runtime cursor");
            }
            rejectCourseSubmitConflicts(events);
        }
        List<StationEvent> saved = eventRepository.saveAll(events);
        return new RuntimeDtos.ImportEventsResponse(COURSE_SOURCE_NAME, saved.size(), saved.stream()
                .map(this::toRow)
                .toList());
    }

    @Transactional
    public RuntimeDtos.RuntimeEventRow addManualChargeRequest(RuntimeDtos.ManualChargeRequestEvent request) {
        String carId = requireText(request.carId(), "carId is required");
        ChargeMode mode = request.mode() == null ? ChargeMode.FAST : request.mode();
        double capacity = request.carCapacity() > 0.0 ? request.carCapacity() : defaultCapacity(mode);
        double amount = request.requestAmount();
        if (amount <= 0.0) {
            throw new BusinessException("invalid request amount");
        }
        if (amount > capacity) {
            throw new BusinessException("request amount exceeds vehicle capacity");
        }
        LocalDateTime eventTime = request.eventTime() == null
                ? stationClockService.currentStationTime()
                : request.eventTime();
        LocalDateTime runtimeCursor = stationClockService.runtimeCursorTime();
        if (eventTime.isBefore(runtimeCursor)) {
            throw new BusinessException("event time is before runtime cursor");
        }
        rejectDuplicateSubmitEvent(carId, runtimeCursor);
        StationEvent event = eventRepository.save(new StationEvent(
                eventTime,
                stationClockService.currentStationTime(),
                StationEventSourceType.MANUAL_OPERATION,
                textOrDefault(request.sourceName(), "manual"),
                StationEventType.ChargeRequestSubmitted,
                EventCommitState.COMMITTED,
                carId,
                textOrDefault(request.ownerName(), carId),
                capacity,
                mode,
                amount,
                nextSequence(),
                null
        ));
        return toRow(event);
    }

    @Transactional
    public List<RuntimeDtos.RuntimeEventRow> listEvents() {
        return eventRepository.findAllByOrderByEventTimeAscSequenceAsc().stream()
                .map(this::toRow)
                .toList();
    }

    @Transactional
    public Optional<LocalDateTime> nextDueEventTime(LocalDateTime targetTime) {
        return eventRepository
                .findFirstByAppliedFalseAndCommitStateAndEventTimeLessThanEqualOrderByEventTimeAscSequenceAsc(
                        EventCommitState.COMMITTED,
                        targetTime)
                .map(StationEvent::getEventTime);
    }

    @Transactional
    public boolean applyNextDueEvent(LocalDateTime targetTime) {
        Optional<StationEvent> maybeEvent = eventRepository
                .findFirstByAppliedFalseAndCommitStateAndEventTimeLessThanEqualOrderByEventTimeAscSequenceAsc(
                        EventCommitState.COMMITTED,
                        targetTime);
        if (maybeEvent.isEmpty()) {
            return false;
        }
        StationEvent event = maybeEvent.get();
        apply(event);
        event.markApplied(event.getEventTime());
        eventRepository.save(event);
        return true;
    }

    private StationEvent parseCourseEvent(String raw, long sequence, LocalDateTime receivedAt) {
        String[] parts = raw == null ? new String[0] : raw.trim().split("\\s+", 2);
        if (parts.length != 2) {
            throw new BusinessException("malformed course event row: " + raw);
        }
        LocalDateTime eventTime;
        try {
            eventTime = LocalDateTime.of(COURSE_DATE, LocalTime.parse(parts[0].trim()));
        } catch (DateTimeParseException ex) {
            throw new BusinessException("malformed course event time: " + raw);
        }
        String payload = parts[1].trim();
        if (!payload.startsWith("(") || !payload.endsWith(")")) {
            throw new BusinessException("malformed course event payload: " + raw);
        }
        String body = payload.substring(1, payload.length() - 1);
        String[] fields = body.split(",");
        if (fields.length != 4) {
            throw new BusinessException("malformed course event fields: " + raw);
        }
        String source = fields[0].trim();
        String target = normalizeTargetId(fields[1].trim());
        String operation = fields[2].trim();
        double amount;
        try {
            amount = Double.parseDouble(fields[3].trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException("malformed course event amount: " + raw);
        }
        StationEventType eventType;
        ChargeMode mode = null;
        double capacity = 0.0;
        if ("A".equals(source) && ("T".equals(operation) || "F".equals(operation))) {
            eventType = StationEventType.ChargeRequestSubmitted;
            mode = "F".equals(operation) ? ChargeMode.FAST : ChargeMode.SLOW;
            capacity = Math.max(defaultCapacity(mode), amount);
        } else if ("A".equals(source) && "O".equals(operation)) {
            eventType = StationEventType.ChargeRequestCancelled;
        } else if ("B".equals(source) && "O".equals(operation)) {
            eventType = amount == 0.0 ? StationEventType.PileFaulted : StationEventType.PileRecovered;
        } else if ("C".equals(source) && "O".equals(operation)) {
            eventType = StationEventType.RequestedAmountChanged;
        } else {
            throw new BusinessException("unsupported course event: " + raw);
        }
        return new StationEvent(
                eventTime,
                receivedAt,
                StationEventSourceType.COURSE_PRESET,
                COURSE_SOURCE_NAME,
                eventType,
                EventCommitState.COMMITTED,
                target,
                target,
                capacity,
                mode,
                amount,
                sequence,
                raw
        );
    }

    private void apply(StationEvent event) {
        switch (event.getEventType()) {
            case ChargeRequestSubmitted -> {
                ensureVehicle(event);
                chargingService.submitRequestAt(
                        event.getTargetId(),
                        event.getAmount(),
                        event.getMode() == null ? ChargeMode.FAST : event.getMode(),
                        event.getEventTime());
            }
            case ChargeRequestCancelled -> chargingService.cancelRequestAt(event.getTargetId(), event.getEventTime());
            case RequestedAmountChanged -> chargingService.modifyAmountAt(event.getTargetId(), event.getAmount(), event.getEventTime(), true);
            case PileFaulted -> faultService.handleFaultAt(event.getTargetId(), "PRIORITY", event.getEventTime());
            case PileRecovered -> faultService.recoverPileAt(event.getTargetId(), event.getEventTime());
            case ChargingCompleted, BillGenerated -> {
            }
        }
    }

    private void ensureVehicle(StationEvent event) {
        if (vehicleRepository.existsByCarId(event.getTargetId())) {
            return;
        }
        ChargeMode mode = event.getMode() == null ? ChargeMode.FAST : event.getMode();
        double capacity = event.getCarCapacity() > 0.0 ? event.getCarCapacity() : defaultCapacity(mode);
        if (event.getAmount() > capacity) {
            throw new BusinessException("request amount exceeds vehicle capacity");
        }
        accountService.createNewAccount(event.getTargetId(), textOrDefault(event.getOwnerName(), event.getTargetId()), capacity);
    }

    private void rejectDuplicateSubmitEvent(String carId, LocalDateTime runtimeCursor) {
        boolean hasFutureSubmitEvent = eventRepository
                .existsByAppliedFalseAndCommitStateAndEventTypeAndTargetIdAndEventTimeGreaterThanEqual(
                        EventCommitState.COMMITTED,
                        StationEventType.ChargeRequestSubmitted,
                        carId,
                        runtimeCursor);
        if (hasFutureSubmitEvent) {
            throw new BusinessException("car already has pending submit event");
        }
        requestRepository.findFirstByCarIdAndStatusInOrderByRequestTimeDesc(carId, ACTIVE_REQUEST_STATUSES)
                .ifPresent(request -> {
                    throw new BusinessException("car already has active request");
                });
    }

    private void rejectCourseSubmitConflicts(List<StationEvent> events) {
        List<String> submitTargets = events.stream()
                .filter(event -> event.getEventType() == StationEventType.ChargeRequestSubmitted)
                .map(StationEvent::getTargetId)
                .distinct()
                .toList();
        if (submitTargets.isEmpty()) {
            return;
        }
        boolean hasPendingSubmitEvent = eventRepository.existsByAppliedFalseAndCommitStateAndEventTypeAndTargetIdIn(
                EventCommitState.COMMITTED,
                StationEventType.ChargeRequestSubmitted,
                submitTargets);
        if (hasPendingSubmitEvent) {
            throw new BusinessException("course import conflicts with pending submit event");
        }
        for (String targetId : submitTargets) {
            requestRepository.findFirstByCarIdAndStatusInOrderByRequestTimeDesc(targetId, ACTIVE_REQUEST_STATUSES)
                    .ifPresent(request -> {
                        throw new BusinessException("course import conflicts with active request");
                    });
        }
    }

    private RuntimeDtos.RuntimeEventRow toRow(StationEvent event) {
        return new RuntimeDtos.RuntimeEventRow(
                event.getId(),
                event.getEventTime(),
                event.getReceivedTime(),
                event.getSourceType().name(),
                event.getSourceName(),
                event.getEventType().name(),
                event.getCommitState().name(),
                event.getTargetId(),
                event.getMode() == null ? null : event.getMode().name(),
                event.getAmount(),
                event.getSequence(),
                event.isApplied(),
                event.getRawText()
        );
    }

    private long nextSequence() {
        return eventRepository.count() + 1;
    }

    private String normalizeTargetId(String target) {
        if (target.matches("[FT]\\d+")) {
            return target.charAt(0) + "-" + target.substring(1);
        }
        return target;
    }

    private double defaultCapacity(ChargeMode mode) {
        return mode == ChargeMode.FAST ? 120.0 : 80.0;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(message);
        }
        return value.trim();
    }

    private String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
