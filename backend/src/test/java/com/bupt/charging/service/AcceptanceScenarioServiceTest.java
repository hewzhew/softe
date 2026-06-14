package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.dto.AcceptanceDtos;
import com.bupt.charging.dto.ScenarioDtos;
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

    @Test
    void courseSampleReplayExposesCommandsSnapshotsTransitionsAndLegacyRows() {
        AcceptanceScenarioService service = new AcceptanceScenarioService();

        var replay = service.runCourseSampleReplay();

        assertEquals("course-sample", replay.scenario().id());
        assertEquals("课程事件序列", replay.scenario().name());
        assertEquals("06:00", replay.scenario().startTime());
        assertEquals("09:30", replay.scenario().stopTime());

        assertEquals(36, replay.commands().size());
        assertEquals(37, replay.snapshots().size());
        assertEquals(36, replay.transitions().size());
        assertEquals(36, replay.tableRows().size());
        assertTrue(replay.checks().stream().allMatch(ScenarioDtos.ScenarioCheck::passed));

        var initial = replay.snapshots().get(0);
        assertEquals(0, initial.sequence());
        assertEquals("06:00", initial.time());
        assertTrue(initial.station().waitingArea().isEmpty());

        var firstCommand = replay.commands().get(0);
        assertEquals(1, firstCommand.sequence());
        assertEquals("06:00", firstCommand.time());
        assertEquals("SubmitChargingRequest", firstCommand.type());
        assertEquals("V1", firstCommand.targetId());
        assertEquals("(A,V1,T,40)", firstCommand.sourceText());

        var firstSnapshot = replay.snapshots().get(1);
        assertEquals(1, firstSnapshot.sequence());
        assertEquals("06:00", firstSnapshot.time());
        assertEquals("V1", firstSnapshot.station().slowPiles().get(0).queue().get(0));
        assertEquals("SLOW", firstSnapshot.vehicles().get("V1").mode());
    }

    private AcceptanceDtos.AcceptanceEventRow rowAt(List<AcceptanceDtos.AcceptanceEventRow> rows, String time) {
        return rows.stream()
                .filter(row -> row.time().equals(time))
                .findFirst()
                .orElseThrow();
    }
}
