package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.domain.Bill;
import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingSession;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.domain.SessionStatus;
import com.bupt.charging.dto.ConfigDtos;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.repository.BillRepository;
import com.bupt.charging.repository.ChargingSessionRepository;
import com.bupt.charging.support.TimeProvider;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:station-runtime-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class StationRuntimeServiceTest {
    @Autowired
    private ConfigService configService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ChargingService chargingService;

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private StationClockService stationClockService;

    @Autowired
    private StationRuntimeService stationRuntimeService;

    @Autowired
    private ChargingSessionRepository sessionRepository;

    @Autowired
    private BillRepository billRepository;

    @Autowired
    private MutableTimeProvider timeProvider;

    @Test
    void advancingStationTimeCompletesChargingAndStartsNextQueuedCar() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(1, 0, 10, 2, 30.0, 10.0));
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                1.0,
                false,
                null,
                null
        ));

        accountService.createNewAccount("CAR-1", "Alice", 80.0);
        accountService.createNewAccount("CAR-2", "Bob", 80.0);
        chargingService.submitRequest("CAR-1", 30.0, ChargeMode.FAST);
        chargingService.submitRequest("CAR-2", 15.0, ChargeMode.FAST);
        schedulerService.dispatchAll();

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 15, 6, 0));
        assertEquals(RequestStatus.CHARGING, chargingService.queryCarState("CAR-1").carState());
        assertEquals(RequestStatus.PILE_QUEUE, chargingService.queryCarState("CAR-2").carState());

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 15, 7, 1));

        assertEquals(RequestStatus.FINISHED, chargingService.queryCarState("CAR-1").carState());
        assertEquals(RequestStatus.CHARGING, chargingService.queryCarState("CAR-2").carState());
        assertTrue(sessionRepository.findFirstByCarIdAndStatusOrderByStartTimeDesc("CAR-1", SessionStatus.FINISHED).isPresent());
        assertFalse(billRepository.findAll().isEmpty());
    }

    @Test
    void directJumpAdvancementStartsQueuedCarsAtStationClockCursor() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(1, 0, 10, 2, 30.0, 10.0));
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                1.0,
                false,
                null,
                null
        ));

        accountService.createNewAccount("CAR-1", "Alice", 80.0);
        accountService.createNewAccount("CAR-2", "Bob", 80.0);
        chargingService.submitRequest("CAR-1", 30.0, ChargeMode.FAST);
        chargingService.submitRequest("CAR-2", 15.0, ChargeMode.FAST);
        schedulerService.dispatchAll();

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 15, 7, 1));

        assertEquals(RequestStatus.FINISHED, chargingService.queryCarState("CAR-1").carState());
        assertEquals(RequestStatus.CHARGING, chargingService.queryCarState("CAR-2").carState());
        assertTrue(sessionRepository.findFirstByCarIdAndStatusOrderByStartTimeDesc("CAR-1", SessionStatus.FINISHED).isPresent());
        assertFalse(billRepository.findAll().isEmpty());
    }

    @Test
    void directJumpProcessesDueCompletionsByFinishTimeInsteadOfStartTime() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(2, 0, 10, 2, 30.0, 10.0));
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                1.0,
                false,
                null,
                null
        ));

        accountService.createNewAccount("CAR-LONG", "Long", 80.0);
        accountService.createNewAccount("CAR-SHORT", "Short", 80.0);
        accountService.createNewAccount("CAR-WAIT", "Wait", 80.0);
        chargingService.submitRequest("CAR-LONG", 60.0, ChargeMode.FAST);
        chargingService.submitRequest("CAR-SHORT", 5.0, ChargeMode.FAST);
        chargingService.submitRequest("CAR-WAIT", 5.0, ChargeMode.FAST);
        schedulerService.dispatchAll();

        chargingService.startChargingAt("CAR-LONG", "F-1", LocalDateTime.of(2026, 6, 15, 6, 0));
        chargingService.startChargingAt("CAR-SHORT", "F-2", LocalDateTime.of(2026, 6, 15, 6, 10));

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 15, 8, 30));

        ChargingSession waitSession = sessionRepository.findFirstByCarIdAndStatusOrderByStartTimeDesc(
                "CAR-WAIT",
                SessionStatus.FINISHED
        ).orElseThrow();
        List<String> billedCars = billRepository.findAll().stream()
                .sorted(Comparator.comparing(Bill::getId))
                .map(Bill::getCarId)
                .toList();

        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 20), waitSession.getStartTime());
        assertEquals("F-2", waitSession.getPileId());
        assertEquals(List.of("CAR-SHORT", "CAR-WAIT", "CAR-LONG"), billedCars);
    }

    @Test
    void runningClockSnapshotAdvanceStartsQueuedCarAtRuntimeCursor() {
        configService.resetDemoData();
        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 0));
        configService.initialize(new ConfigDtos.UpdateConfigRequest(1, 0, 10, 2, 30.0, 10.0));
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                1.0,
                true,
                null,
                null
        ));

        accountService.createNewAccount("CAR-RUN", "Runner", 80.0);
        chargingService.submitRequest("CAR-RUN", 30.0, ChargeMode.FAST);
        schedulerService.dispatchAll();

        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 9, 1));
        stationRuntimeService.advanceTo(stationClockService.currentStationTime());

        Optional<ChargingSession> maybeSession = sessionRepository.findFirstByCarIdAndStatusOrderByStartTimeDesc(
                "CAR-RUN",
                SessionStatus.FINISHED
        );
        assertTrue(maybeSession.isPresent());
        ChargingSession session = maybeSession.get();
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 0), session.getStartTime());
        assertEquals(RequestStatus.FINISHED, chargingService.queryCarState("CAR-RUN").carState());
    }

    @Test
    void queryChargingStateShowsStationTimeProgressBeforeCompletion() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(1, 0, 10, 2, 30.0, 10.0));
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                1.0,
                false,
                null,
                null
        ));

        accountService.createNewAccount("CAR-PROGRESS", "Progress", 80.0);
        chargingService.submitRequest("CAR-PROGRESS", 60.0, ChargeMode.FAST);
        schedulerService.dispatchAll();

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 15, 6, 30));
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 30),
                1.0,
                false,
                null,
                null
        ));

        double chargedAmount = chargingService.queryChargingState("CAR-PROGRESS").chargedAmount();
        assertTrue(chargedAmount > 0.0);
        assertTrue(chargedAmount < 60.0);
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        MutableTimeProvider mutableTimeProvider() {
            return new MutableTimeProvider();
        }
    }

    static class MutableTimeProvider implements TimeProvider {
        private LocalDateTime now = LocalDateTime.of(2026, 6, 15, 0, 0);

        void setNow(LocalDateTime now) {
            this.now = now;
        }

        @Override
        public LocalDateTime now() {
            return now;
        }
    }
}
