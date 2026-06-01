package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.strategy.Assignment;
import com.bupt.charging.strategy.ShortestFinishTimeStrategy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchedulerServiceTest {
    @Test
    void assignsFastRequestToFastPileWithShortestFinishTime() {
        ChargingRequest request = request("CAR-1", ChargeMode.FAST, 30.0, LocalDateTime.of(2026, 6, 1, 9, 0));
        ChargingPile f1 = pile("F-1", ChargeMode.FAST, 30.0);
        ChargingPile f2 = pile("F-2", ChargeMode.FAST, 30.0);
        Map<String, List<ChargingRequest>> queues = Map.of(
                "F-1", List.of(request("CAR-2", ChargeMode.FAST, 60.0, LocalDateTime.of(2026, 6, 1, 8, 0))),
                "F-2", List.of()
        );

        Assignment assignment = new ShortestFinishTimeStrategy()
                .select(request, List.of(f1, f2), queues)
                .orElseThrow();

        assertEquals("F-2", assignment.pileId());
        assertEquals(1, assignment.queuePosition());
    }

    @Test
    void slowRequestDoesNotAssignToFastPile() {
        ChargingRequest request = request("CAR-3", ChargeMode.SLOW, 20.0, LocalDateTime.of(2026, 6, 1, 9, 0));
        ChargingPile f1 = pile("F-1", ChargeMode.FAST, 30.0);

        assertTrue(new ShortestFinishTimeStrategy()
                .select(request, List.of(f1), Map.of("F-1", List.of()))
                .isEmpty());
    }

    private ChargingRequest request(String carId, ChargeMode mode, double amount, LocalDateTime requestTime) {
        return new ChargingRequest(carId, 80.0, amount, mode, requestTime, mode.name() + "-" + carId, 1);
    }

    private ChargingPile pile(String pileId, ChargeMode mode, double power) {
        return new ChargingPile(pileId, mode, power);
    }
}
