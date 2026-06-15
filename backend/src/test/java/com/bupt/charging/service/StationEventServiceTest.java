package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.PileStatus;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.domain.StationEvent;
import com.bupt.charging.dto.ConfigDtos;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
import com.bupt.charging.repository.StationEventRepository;
import com.bupt.charging.repository.VehicleRepository;
import com.bupt.charging.support.BusinessException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:station-event-service-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class StationEventServiceTest {
    private static final LocalDateTime COURSE_START = LocalDateTime.of(2026, 6, 1, 6, 0);

    @Autowired
    private ConfigService configService;

    @Autowired
    private StationClockService stationClockService;

    @Autowired
    private StationRuntimeService stationRuntimeService;

    @Autowired
    private StationEventService stationEventService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ChargingService chargingService;

    @Autowired
    private StationEventRepository eventRepository;

    @Autowired
    private ChargingRequestRepository requestRepository;

    @Autowired
    private ChargingPileRepository pileRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @BeforeEach
    void setUp() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(2, 3, 10, 3, 30.0, 10.0));
        stationClockService.resetClock(new RuntimeDtos.SetClockRequest(
                COURSE_START,
                1.0,
                false,
                null,
                null
        ));
    }

    @Test
    void importCourseSamplePersistsThirtySixUnappliedEvents() {
        RuntimeDtos.ImportEventsResponse response = stationEventService.importCourseSample(false);

        List<StationEvent> events = eventRepository.findAllByOrderByEventTimeAscSequenceAsc();
        assertEquals(36, response.eventCount());
        assertEquals(36, events.size());
        assertTrue(events.stream().noneMatch(StationEvent::isApplied));
        assertEquals(LocalDateTime.of(2026, 6, 1, 6, 0), events.get(0).getEventTime());
        assertEquals(LocalDateTime.of(2026, 6, 1, 9, 30), events.get(35).getEventTime());
    }

    @Test
    void advancingStationTimeAppliesCourseEventsIntoLiveRuntime() {
        stationEventService.importCourseSample(false);

        stationRuntimeService.advanceTo(COURSE_START);
        assertEquals(RequestStatus.CHARGING, requestRepository.findFirstByCarIdOrderByRequestTimeDesc("V1")
                .orElseThrow()
                .getStatus());
        assertEquals("V1", pileRepository.findByPileId("T-1").orElseThrow().getCurrentCarId());

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 6, 10));
        assertEquals(RequestStatus.CHARGING, requestRepository.findFirstByCarIdOrderByRequestTimeDesc("V3")
                .orElseThrow()
                .getStatus());

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 6, 25));
        assertTrue(requestRepository.findFirstByCarIdOrderByRequestTimeDesc("V5").isPresent());
        assertEquals(0, eventRepository.findByAppliedFalseAndEventTimeLessThanEqualOrderByEventTimeAscSequenceAsc(
                LocalDateTime.of(2026, 6, 1, 6, 25)).size());
    }

    @Test
    void importedFaultTargetsAreNormalizedToLivePileIds() {
        stationEventService.importCourseSample(false);

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 8, 25));

        ChargingPile slowPile = pileRepository.findByPileId("T-1").orElseThrow();
        assertEquals(PileStatus.FAULT, slowPile.getStatus());
        assertTrue(pileRepository.findByPileId("T1").isEmpty());
    }

    @Test
    void completeCourseSampleAppliesLateNoOpEventsWithoutAbortingRuntime() {
        stationEventService.importCourseSample(false);

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 9, 30));

        assertTrue(eventRepository.findAll().stream().allMatch(StationEvent::isApplied));
        assertTrue(requestRepository.findFirstByCarIdOrderByRequestTimeDesc("V28").isPresent());
    }

    @Test
    void manualFutureEventAppliesOnlyWhenDue() {
        stationEventService.addManualChargeRequest(new RuntimeDtos.ManualChargeRequestEvent(
                LocalDateTime.of(2026, 6, 1, 6, 30),
                "MANUAL-1",
                null,
                0,
                null,
                25.0,
                null
        ));

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 6, 20));

        assertFalse(vehicleRepository.existsByCarId("MANUAL-1"));
        assertEquals(1, eventRepository.findByAppliedFalseAndEventTimeLessThanEqualOrderByEventTimeAscSequenceAsc(
                LocalDateTime.of(2026, 6, 1, 6, 30)).size());

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 6, 30));

        assertTrue(vehicleRepository.existsByCarId("MANUAL-1"));
        assertEquals(RequestStatus.CHARGING, requestRepository.findFirstByCarIdOrderByRequestTimeDesc("MANUAL-1")
                .orElseThrow()
                .getStatus());
        assertEquals(ChargeMode.FAST, requestRepository.findFirstByCarIdOrderByRequestTimeDesc("MANUAL-1")
                .orElseThrow()
                .getMode());
        assertTrue(eventRepository.findAll().stream().allMatch(StationEvent::isApplied));
    }

    @Test
    void manualEventBeforeRuntimeCursorIsRejected() {
        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 6, 20));

        assertThrows(BusinessException.class, () -> stationEventService.addManualChargeRequest(
                manualEvent("MANUAL-BACKDATED", LocalDateTime.of(2026, 6, 1, 6, 10))));
        assertEquals(0, eventRepository.count());
    }

    @Test
    void courseImportWithoutResetAfterRuntimeCursorIsRejected() {
        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 6, 20));

        assertThrows(BusinessException.class, () -> stationEventService.importCourseSample(false));
        assertEquals(0, eventRepository.count());
    }

    @Test
    void duplicateManualFutureSubmitEventForSameCarIsRejected() {
        stationEventService.addManualChargeRequest(manualEvent("MANUAL-DUP", LocalDateTime.of(2026, 6, 1, 6, 30)));

        assertThrows(BusinessException.class, () -> stationEventService.addManualChargeRequest(
                manualEvent("MANUAL-DUP", LocalDateTime.of(2026, 6, 1, 6, 40))));
        assertEquals(1, eventRepository.count());
    }

    @Test
    void duplicateCourseImportWithoutResetIsRejected() {
        stationEventService.importCourseSample(false);

        assertThrows(BusinessException.class, () -> stationEventService.importCourseSample(false));
        assertEquals(36, eventRepository.count());
    }

    @Test
    void courseImportRejectsPendingManualSubmitForSameTarget() {
        stationEventService.addManualChargeRequest(manualEvent("V1", LocalDateTime.of(2026, 6, 1, 6, 30)));

        assertThrows(BusinessException.class, () -> stationEventService.importCourseSample(false));
        assertEquals(1, eventRepository.count());

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 6, 30));

        assertTrue(eventRepository.findAll().stream().allMatch(StationEvent::isApplied));
        assertEquals(RequestStatus.CHARGING, requestRepository.findFirstByCarIdOrderByRequestTimeDesc("V1")
                .orElseThrow()
                .getStatus());
    }

    @Test
    void pendingCourseSubmitEventReservesCarIdAgainstDirectSubmit() {
        stationEventService.importCourseSample(false);
        accountService.createNewAccount("V5", "V5", 80.0);

        assertThrows(BusinessException.class, () -> chargingService.submitRequest("V5", 20.0, ChargeMode.SLOW));

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 6, 25));

        assertEquals(0, eventRepository.findByAppliedFalseAndEventTimeLessThanEqualOrderByEventTimeAscSequenceAsc(
                LocalDateTime.of(2026, 6, 1, 6, 25)).size());
        assertTrue(requestRepository.findFirstByCarIdOrderByRequestTimeDesc("V5").isPresent());
    }

    private RuntimeDtos.ManualChargeRequestEvent manualEvent(String carId, LocalDateTime eventTime) {
        return new RuntimeDtos.ManualChargeRequestEvent(
                eventTime,
                carId,
                null,
                0,
                ChargeMode.FAST,
                25.0,
                null
        );
    }
}
