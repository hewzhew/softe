package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.domain.SessionStatus;
import com.bupt.charging.dto.ConfigDtos;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.repository.BillRepository;
import com.bupt.charging.repository.ChargingSessionRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
}
