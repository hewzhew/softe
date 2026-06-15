package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.domain.ChargingSession;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.dto.ConfigDtos;
import com.bupt.charging.dto.FaultDtos;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.repository.BillRepository;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
import com.bupt.charging.repository.ChargingSessionRepository;
import com.bupt.charging.repository.FaultRecordRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fault-service-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FaultServiceTest {
    @Autowired
    private ConfigService configService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ChargingService chargingService;

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private FaultService faultService;

    @Autowired
    private StationClockService stationClockService;

    @Autowired
    private ChargingRequestRepository requestRepository;

    @Autowired
    private ChargingSessionRepository sessionRepository;

    @Autowired
    private ChargingPileRepository pileRepository;

    @Autowired
    private FaultRecordRepository faultRecordRepository;

    @Autowired
    private BillRepository billRepository;

    @Test
    void priorityFaultDispatchesFaultQueueBeforeWaitingArea() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(2, 0, 10, 2, 30.0, 10.0));
        register("CAR-FAULT-QUEUE-1");
        chargingService.submitRequest("CAR-FAULT-QUEUE-1", 30.0, ChargeMode.FAST);
        schedulerService.dispatchAll();
        register("CAR-WAITING-NEW");
        chargingService.submitRequest("CAR-WAITING-NEW", 30.0, ChargeMode.FAST);

        FaultDtos.FaultResult result = faultService.handleFault("F-1", "PRIORITY");

        assertEquals("F-1", result.faultPileId());
        assertTrue(result.movedCars().contains("CAR-FAULT-QUEUE-1"));
        assertFalse(result.movedCars().contains("CAR-WAITING-NEW"));
    }

    @Test
    void timeOrderFaultReordersUnstartedCarsByRequestTime() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(2, 0, 10, 3, 30.0, 10.0));
        ChargingRequest early = request("CAR-EARLY", LocalDateTime.of(2026, 6, 1, 8, 0));
        ChargingRequest middle = request("CAR-MIDDLE", LocalDateTime.of(2026, 6, 1, 9, 0));
        ChargingRequest late = request("CAR-LATE", LocalDateTime.of(2026, 6, 1, 10, 0));
        early.assignToPile("F-2", 1);
        middle.assignToPile("F-1", 1);
        late.assignToPile("F-2", 2);
        requestRepository.saveAll(List.of(early, middle, late));

        FaultDtos.FaultResult result = faultService.handleFault("F-1", "TIME_ORDER");

        assertEquals(List.of("CAR-EARLY", "CAR-MIDDLE", "CAR-LATE"), result.reorderedCars());
    }

    @Test
    void chargingFaultCreatesDetailForChargedPart() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(2, 0, 10, 2, 30.0, 10.0));
        register("CAR-CHARGING");
        chargingService.submitRequest("CAR-CHARGING", 30.0, ChargeMode.FAST);
        schedulerService.dispatchAll();
        chargingService.startCharging("CAR-CHARGING", "F-1");

        FaultDtos.FaultResult result = faultService.handleFault("F-1", "PRIORITY");

        assertEquals(1, result.generatedDetailCount());
    }

    @Test
    void priorityFaultDoesNotReassignCarsBackToFaultedPile() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(2, 0, 10, 2, 30.0, 10.0));
        register("CAR-FAULT-REASSIGN");
        chargingService.submitRequest("CAR-FAULT-REASSIGN", 30.0, ChargeMode.FAST);
        schedulerService.dispatchAll();

        faultService.handleFault("F-1", "PRIORITY");

        ChargingRequest request = requestRepository.findFirstByCarIdOrderByRequestTimeDesc("CAR-FAULT-REASSIGN")
                .orElseThrow();
        assertEquals("F-2", request.getAssignedPileId());
    }

    @Test
    void faultAndRecoveryUseStationTimeForRecordsAndInterruptedBill() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(2, 0, 10, 2, 30.0, 10.0));
        stationClockService.resetClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                1.0,
                false,
                null,
                null
        ));
        register("CAR-FAULT-TIME");
        chargingService.submitRequest("CAR-FAULT-TIME", 30.0, ChargeMode.FAST);
        schedulerService.dispatchAll();
        chargingService.startCharging("CAR-FAULT-TIME", "F-1");
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 30),
                1.0,
                false,
                null,
                null
        ));

        faultService.handleFault("F-1", "PRIORITY");
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 45),
                1.0,
                false,
                null,
                null
        ));
        faultService.recoverPile("F-1");

        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 30), faultRecordRepository.findAll().get(0).getFaultTime());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 45), faultRecordRepository.findAll().get(0).getRecoveredAt());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 30), billRepository
                .findByCarIdAndBillDateOrderByGeneratedAtDesc("CAR-FAULT-TIME", LocalDate.of(2026, 6, 15))
                .get(0)
                .getGeneratedAt());
    }

    @Test
    void recoveryRedistributesQueuedCarsToRecoveredPileWithoutMovingChargingHeads() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(0, 3, 10, 3, 30.0, 10.0));
        LocalDateTime baseTime = LocalDateTime.of(2026, 6, 1, 11, 15);

        ChargingPile t1 = pileRepository.findByPileId("T-1").orElseThrow();
        ChargingPile t2 = pileRepository.findByPileId("T-2").orElseThrow();
        ChargingPile t3 = pileRepository.findByPileId("T-3").orElseThrow();
        t1.markFault();
        pileRepository.save(t1);
        saveChargingRequest("V17", 10.0, "T-2", 1, baseTime.minusMinutes(20), t2);
        saveQueuedRequest("V18", 7.5, "T-2", 2, baseTime.minusMinutes(15));
        saveQueuedRequest("V25", 15.0, "T-2", 3, baseTime.minusMinutes(10));
        saveChargingRequest("V26", 20.0, "T-3", 1, baseTime.minusMinutes(10), t3);

        faultService.recoverPileAt("T-1", baseTime);

        List<String> recoveredQueue = requestRepository
                .findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc("T-1", RequestStatus.PILE_QUEUE)
                .stream()
                .map(ChargingRequest::getCarId)
                .toList();
        ChargingRequest v17 = requestRepository.findFirstByCarIdOrderByRequestTimeDesc("V17").orElseThrow();
        ChargingRequest v26 = requestRepository.findFirstByCarIdOrderByRequestTimeDesc("V26").orElseThrow();

        assertFalse(recoveredQueue.isEmpty());
        assertEquals(RequestStatus.CHARGING, v17.getStatus());
        assertEquals("T-2", v17.getAssignedPileId());
        assertEquals(RequestStatus.CHARGING, v26.getStatus());
        assertEquals("T-3", v26.getAssignedPileId());
    }

    private void register(String carId) {
        accountService.createNewAccount(carId, carId, 80.0);
        accountService.setPassword(carId, "123456");
    }

    private ChargingRequest request(String carId, LocalDateTime requestTime) {
        ChargingPile pile = pileRepository.findByPileId("F-1").orElseThrow();
        return new ChargingRequest(carId, 80.0, 30.0, pile.getMode(), requestTime, "F-" + carId, 1);
    }

    private ChargingRequest saveQueuedRequest(
            String carId,
            double amount,
            String pileId,
            int position,
            LocalDateTime requestTime
    ) {
        ChargingRequest request = new ChargingRequest(carId, 120.0, amount, ChargeMode.SLOW, requestTime, "T-" + carId, position);
        request.assignToPile(pileId, position);
        return requestRepository.save(request);
    }

    private void saveChargingRequest(
            String carId,
            double amount,
            String pileId,
            int position,
            LocalDateTime requestTime,
            ChargingPile pile
    ) {
        ChargingRequest request = saveQueuedRequest(carId, amount, pileId, position, requestTime);
        request.startCharging();
        requestRepository.save(request);
        pile.markWorking(carId);
        pileRepository.save(pile);
        sessionRepository.save(new ChargingSession(request.getId(), carId, pileId, requestTime));
    }
}
