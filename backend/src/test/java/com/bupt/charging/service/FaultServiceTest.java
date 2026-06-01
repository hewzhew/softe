package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.dto.ConfigDtos;
import com.bupt.charging.dto.FaultDtos;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
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
    private ChargingRequestRepository requestRepository;

    @Autowired
    private ChargingPileRepository pileRepository;

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

    private void register(String carId) {
        accountService.createNewAccount(carId, carId, 80.0);
        accountService.setPassword(carId, "123456");
    }

    private ChargingRequest request(String carId, LocalDateTime requestTime) {
        ChargingPile pile = pileRepository.findByPileId("F-1").orElseThrow();
        return new ChargingRequest(carId, 80.0, 30.0, pile.getMode(), requestTime, "F-" + carId, 1);
    }
}
