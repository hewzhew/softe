package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.strategy.Assignment;
import com.bupt.charging.strategy.PileQueueLoad;
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
        Map<String, PileQueueLoad> loads = Map.of(
                "F-1", new PileQueueLoad(1, 2.0),
                "F-2", PileQueueLoad.empty()
        );

        Assignment assignment = new ShortestFinishTimeStrategy()
                .select(request, List.of(f1, f2), loads)
                .orElseThrow();

        assertEquals("F-2", assignment.pileId());
        assertEquals(1, assignment.queuePosition());
    }

    @Test
    void slowRequestDoesNotAssignToFastPile() {
        ChargingRequest request = request("CAR-3", ChargeMode.SLOW, 20.0, LocalDateTime.of(2026, 6, 1, 9, 0));
        ChargingPile f1 = pile("F-1", ChargeMode.FAST, 30.0);

        assertTrue(new ShortestFinishTimeStrategy()
                .select(request, List.of(f1), Map.of("F-1", PileQueueLoad.empty()))
                .isEmpty());
    }

    @Test
    void activeChargingLoadMakesSchedulerChooseIdleSlowPile() {
        ChargingRequest request = request("V21", ChargeMode.SLOW, 35.0, LocalDateTime.of(2026, 6, 2, 0, 30));
        ChargingPile t1 = pile("T-1", ChargeMode.SLOW, 10.0);
        ChargingPile t2 = pile("T-2", ChargeMode.SLOW, 10.0);
        ChargingPile t3 = pile("T-3", ChargeMode.SLOW, 10.0);
        Map<String, PileQueueLoad> loads = Map.of(
                "T-1", new PileQueueLoad(1, 9.5),
                "T-2", PileQueueLoad.empty(),
                "T-3", PileQueueLoad.empty()
        );

        Assignment assignment = new ShortestFinishTimeStrategy()
                .select(request, List.of(t1, t2, t3), loads)
                .orElseThrow();

        assertEquals("T-2", assignment.pileId());
        assertEquals(1, assignment.queuePosition());
    }

    private ChargingRequest request(String carId, ChargeMode mode, double amount, LocalDateTime requestTime) {
        return new ChargingRequest(carId, 80.0, amount, mode, requestTime, mode.name() + "-" + carId, 1);
    }

    private ChargingPile pile(String pileId, ChargeMode mode, double power) {
        return new ChargingPile(pileId, mode, power);
    }
}
