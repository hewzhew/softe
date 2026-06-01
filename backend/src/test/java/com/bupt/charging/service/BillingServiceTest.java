package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.bupt.charging.domain.TariffRule;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class BillingServiceTest {
    @Test
    void calculatesSinglePriceSlot() {
        BillingService service = new BillingService();
        TariffRule rule = TariffRule.defaults();
        BillingService.FeeResult fee = service.calculateFee(
                LocalDateTime.of(2026, 6, 1, 10, 0),
                LocalDateTime.of(2026, 6, 1, 11, 0),
                30.0,
                30.0,
                rule
        );

        assertEquals(new BigDecimal("30.00"), fee.chargeFee());
        assertEquals(new BigDecimal("24.00"), fee.serviceFee());
        assertEquals(new BigDecimal("54.00"), fee.totalFee());
    }

    @Test
    void splitsFeeAcrossPriceSlots() {
        BillingService service = new BillingService();
        TariffRule rule = TariffRule.defaults();
        BillingService.FeeResult fee = service.calculateFee(
                LocalDateTime.of(2026, 6, 1, 14, 30),
                LocalDateTime.of(2026, 6, 1, 15, 30),
                30.0,
                30.0,
                rule
        );

        assertEquals(new BigDecimal("25.50"), fee.chargeFee());
        assertEquals(new BigDecimal("24.00"), fee.serviceFee());
        assertEquals(new BigDecimal("49.50"), fee.totalFee());
    }
}
