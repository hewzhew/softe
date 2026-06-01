package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.dto.BillingDtos;
import com.bupt.charging.dto.ChargingDtos;
import com.bupt.charging.dto.ConfigDtos;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:charging-flow-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ChargingFlowTest {
    @Autowired
    private ConfigService configService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private ChargingService chargingService;

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private BillingService billingService;

    @Test
    void vehicleCanRegisterRequestChargeAndGenerateBill() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(2, 3, 10, 2, 30.0, 10.0));

        accountService.createNewAccount("CAR-1", "Alice", 80.0);
        accountService.setPassword("CAR-1", "123456");
        ChargingDtos.RequestResponse request = chargingService.submitRequest("CAR-1", 30.0, ChargeMode.FAST);

        assertEquals("F1", request.queueNum());

        schedulerService.dispatchAll();
        ChargingDtos.CarStateResponse state = chargingService.queryCarState("CAR-1");
        assertEquals(RequestStatus.PILE_QUEUE, state.carState());

        chargingService.startCharging("CAR-1", state.assignedPileId());
        BillingDtos.BillResponse bill = chargingService.endCharging("CAR-1", state.assignedPileId(), 30.0);

        assertTrue(bill.totalFee().compareTo(BigDecimal.ZERO) > 0);
        assertFalse(billingService.queryBills("CAR-1", LocalDate.now()).isEmpty());
    }
}
