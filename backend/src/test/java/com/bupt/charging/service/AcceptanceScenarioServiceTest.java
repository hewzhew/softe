package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.dto.AcceptanceDtos;
import java.util.List;
import org.junit.jupiter.api.Test;

class AcceptanceScenarioServiceTest {
    @Test
    void reproducesTeacherExampleRows() {
        AcceptanceScenarioService service = new AcceptanceScenarioService();

        AcceptanceDtos.AcceptanceScenarioResponse scenario = service.runDefaultScenario();

        assertEquals(36, scenario.rows().size());
        AcceptanceDtos.AcceptanceEventRow row0600 = rowAt(scenario.rows(), "06:00");
        assertEquals("(V1,0.00,0.00)", row0600.slow1().get(0));
        assertEquals("-", row0600.fast1().get(0));
        assertEquals("-", row0600.waitingAreaText());

        AcceptanceDtos.AcceptanceEventRow row0605 = rowAt(scenario.rows(), "06:05");
        assertEquals("(V1,0.83,1.00)", row0605.slow1().get(0));
        assertEquals("(V2,0.00,0.00)", row0605.slow2().get(0));

        assertEquals("(V13,F,110.00)", rowAt(scenario.rows(), "07:05").waitingAreaText());
        assertEquals("(V13,F,110.00)-(V14,F,95.00)", rowAt(scenario.rows(), "07:10").waitingAreaText());
        assertEquals("(V13,F,110.00)-(V14,F,95.00)", rowAt(scenario.rows(), "07:15").waitingAreaText());
    }

    @Test
    void handlesFaultPriorityRecoveryAndWaitingAreaAmountModification() {
        AcceptanceScenarioService service = new AcceptanceScenarioService();

        AcceptanceDtos.AcceptanceScenarioResponse scenario = service.runDefaultScenario();

        AcceptanceDtos.AcceptanceEventRow faultRow = rowAt(scenario.rows(), "08:25");
        assertTrue(faultRow.slow1().contains("故障"));
        assertTrue(String.join("", faultRow.slow2()).contains("V1"));

        AcceptanceDtos.AcceptanceEventRow modifyRow = rowAt(scenario.rows(), "09:25");
        assertTrue(modifyRow.waitingAreaText().contains("(V21,F,35.00)"));
        assertTrue(scenario.sampleChecks().stream().allMatch(AcceptanceDtos.AcceptanceSampleCheck::matched));
    }

    private AcceptanceDtos.AcceptanceEventRow rowAt(List<AcceptanceDtos.AcceptanceEventRow> rows, String time) {
        return rows.stream()
                .filter(row -> row.time().equals(time))
                .findFirst()
                .orElseThrow();
    }
}
