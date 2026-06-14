# Simulation Playback Sandbox Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a polished “调度沙盘” playback experience that uses a unified station snapshot contract, keeps the teacher sample checks, and starts moving acceptance replay toward the same business concepts as normal use.

**Architecture:** Implement in two layers. First, create a shared backend replay/snapshot contract and make the existing teacher scenario emit complete commands, snapshots, transitions, checks, and legacy table rows. Then replace the standalone frontend acceptance page with a playback sandbox that consumes the replay bundle, while adding a current station snapshot endpoint so normal pages and sandbox share the same display vocabulary.

**Tech Stack:** Spring Boot 3, Java 17, JUnit 5, H2, Vue 3, Vite, Element Plus, Node built-in test runner, CSS/SVG/DOM animation only.

---

## Scope Check

This plan covers the first production-quality classroom version. It does not fully rewrite the backend into a complete `ChargingStationRuntime` abstraction in one pass. Instead, it creates the contracts, APIs, tests, and UI that make the runtime boundary explicit, then adds a final backend convergence task that extracts shared snapshot and command concepts without risking the existing demo.

The implementation is complete when:

- The top-level “测试用例” tab is gone.
- The first tab is “调度沙盘”.
- The user can load the course sequence, play, pause, step, reset, change speed, jump on the timeline, inspect the current command, and open result checks.
- The backend replay response includes commands, snapshots, transitions, checks, and legacy table rows.
- The old `/api/acceptance/scenario` endpoint still works.
- Normal pages can fetch a unified station snapshot through `/api/station/snapshot`.

## File Structure

Backend files:

- Create `backend/src/main/java/com/bupt/charging/dto/ScenarioDtos.java`
  - Owns the replay contract: scenario definition, commands, station snapshots, transitions, checks, and replay bundle.
- Create `backend/src/main/java/com/bupt/charging/dto/StationDtos.java`
  - Owns the current station snapshot contract shared by normal pages and sandbox concepts.
- Create `backend/src/main/java/com/bupt/charging/controller/ScenarioController.java`
  - Exposes `/api/scenarios/course-sample` and `/api/scenarios/course-sample/run`.
- Create `backend/src/main/java/com/bupt/charging/controller/StationController.java`
  - Exposes `/api/station/snapshot`.
- Create `backend/src/main/java/com/bupt/charging/service/StationSnapshotService.java`
  - Projects H2 repository state into `StationDtos.StationSnapshot`.
- Modify `backend/src/main/java/com/bupt/charging/service/AcceptanceScenarioService.java`
  - Adds `runCourseSampleReplay()` while keeping `runDefaultScenario()`.
  - Produces complete replay snapshots from the existing scenario state.
- Modify `backend/src/main/java/com/bupt/charging/dto/AcceptanceDtos.java`
  - Keep existing records unchanged for compatibility.
- Modify `backend/src/test/java/com/bupt/charging/service/AcceptanceScenarioServiceTest.java`
  - Adds replay contract tests.
- Modify `backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java`
  - Adds scenario and station snapshot endpoint smoke tests.

Frontend files:

- Create `frontend/src/utils/simulationPlayback.js`
  - Pure playback helpers for state, step, seek, speed, and clock advancement.
- Create `frontend/src/utils/simulationPlayback.test.js`
  - Unit tests for playback helper behavior.
- Create `frontend/src/views/SimulationSandbox.vue`
  - Main sandbox page replacing top-level `AcceptancePanel`.
- Create `frontend/src/components/simulation/SimulationClockBar.vue`
  - Clock and playback controls.
- Create `frontend/src/components/simulation/ScenarioLoader.vue`
  - Load/reset/result actions.
- Create `frontend/src/components/simulation/StationMap.vue`
  - Waiting area, fast piles, slow piles.
- Create `frontend/src/components/simulation/PileLane.vue`
  - Single pile lane.
- Create `frontend/src/components/simulation/VehicleToken.vue`
  - Vehicle card/token.
- Create `frontend/src/components/simulation/EventTimeline.vue`
  - Command timeline and jump control.
- Create `frontend/src/components/simulation/PlaybackInspector.vue`
  - Current command and transition explanation.
- Create `frontend/src/components/simulation/VerificationPanel.vue`
  - Checks and legacy table rows.
- Modify `frontend/src/api/chargingApi.js`
  - Adds `getCourseScenario`, `runCourseScenario`, and `getStationSnapshot`.
- Modify `frontend/src/App.vue`
  - Replaces top-level “测试用例” with “调度沙盘”.
- Modify `frontend/src/styles.css`
  - Adds responsive sandbox layout and removes acceptance-first visual language.
- Keep `frontend/src/views/AcceptancePanel.vue`
  - It can remain unused for one commit, then be deleted after `VerificationPanel` fully replaces it.

Docs:

- Modify `docs/demo-script.md`
  - Updates the demo flow to start from “调度沙盘”.

---

### Task 1: Backend Replay Contract Tests

**Files:**
- Modify: `backend/src/test/java/com/bupt/charging/service/AcceptanceScenarioServiceTest.java`
- Modify: `backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java`

- [ ] **Step 1: Add failing service test for replay bundle**

Append this test to `AcceptanceScenarioServiceTest`:

```java
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
```

Also add this import at the top of the same file:

```java
import com.bupt.charging.dto.ScenarioDtos;
```

- [ ] **Step 2: Add failing API smoke test for new scenario endpoint**

Append this test to `RestApiSmokeTest`:

```java
@Test
void courseScenarioReplayEndpointReturnsPlaybackBundle() throws Exception {
    ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/scenarios/course-sample/run",
            null,
            String.class
    );

    assertEquals(HttpStatus.OK, response.getStatusCode());
    JsonNode data = objectMapper.readTree(response.getBody()).path("data");
    assertEquals("course-sample", data.path("scenario").path("id").asText());
    assertEquals(36, data.path("commands").size());
    assertEquals(37, data.path("snapshots").size());
    assertEquals(36, data.path("transitions").size());
    assertEquals(36, data.path("tableRows").size());
    assertTrue(data.path("checks").get(0).path("passed").asBoolean());
}
```

- [ ] **Step 3: Run backend tests and verify failure**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: compilation fails because `ScenarioDtos`, `runCourseSampleReplay()`, and `/api/scenarios/course-sample/run` do not exist yet.

- [ ] **Step 4: Commit failing tests**

```powershell
cd D:\softe
git add backend/src/test/java/com/bupt/charging/service/AcceptanceScenarioServiceTest.java backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java
git commit -m "test: specify scenario replay contract"
```

---

### Task 2: Backend Replay DTOs And Scenario API

**Files:**
- Create: `backend/src/main/java/com/bupt/charging/dto/ScenarioDtos.java`
- Create: `backend/src/main/java/com/bupt/charging/controller/ScenarioController.java`
- Modify: `backend/src/main/java/com/bupt/charging/service/AcceptanceScenarioService.java`
- Test: `backend/src/test/java/com/bupt/charging/service/AcceptanceScenarioServiceTest.java`
- Test: `backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java`

- [ ] **Step 1: Create replay DTO records**

Create `ScenarioDtos.java`:

```java
package com.bupt.charging.dto;

import java.util.List;
import java.util.Map;

public final class ScenarioDtos {
    private ScenarioDtos() {
    }

    public record ScenarioDefinition(
            String id,
            String name,
            String version,
            String startTime,
            String stopTime,
            PileConfig pileConfig
    ) {
    }

    public record PileConfig(List<String> fast, List<String> slow) {
    }

    public record ScenarioCommand(
            long sequence,
            String time,
            String type,
            String targetId,
            String mode,
            String amount,
            String sourceText,
            String displayText
    ) {
    }

    public record ReplayBundle(
            ScenarioDefinition scenario,
            List<ScenarioCommand> commands,
            List<StationSnapshot> snapshots,
            List<ScenarioTransition> transitions,
            List<ScenarioCheck> checks,
            List<AcceptanceDtos.AcceptanceEventRow> tableRows
    ) {
    }

    public record StationSnapshot(
            long sequence,
            String time,
            long appliedCommandSequence,
            StationState station,
            Map<String, VehicleState> vehicles,
            List<String> ruleNotes
    ) {
    }

    public record StationState(
            List<String> waitingArea,
            List<PileState> fastPiles,
            List<PileState> slowPiles
    ) {
    }

    public record PileState(
            String id,
            String mode,
            String status,
            String currentVehicle,
            List<String> queue,
            String power
    ) {
    }

    public record VehicleState(
            String id,
            String mode,
            String state,
            String requestKwh,
            String chargedKwh,
            String queueNo,
            String position
    ) {
    }

    public record ScenarioTransition(
            long fromSequence,
            long toSequence,
            String time,
            List<TransitionChange> changes
    ) {
    }

    public record TransitionChange(
            String entityType,
            String entityId,
            String changeType,
            String before,
            String after,
            String reason
    ) {
    }

    public record ScenarioCheck(
            String id,
            String name,
            String expected,
            String actual,
            boolean passed,
            String source
    ) {
    }
}
```

- [ ] **Step 2: Add `runCourseSampleReplay()` skeleton**

In `AcceptanceScenarioService`, add imports:

```java
import com.bupt.charging.dto.ScenarioDtos;
```

Add this public method near `runDefaultScenario()`:

```java
public ScenarioDtos.ReplayBundle runCourseSampleReplay() {
    ScenarioState state = new ScenarioState();
    List<ScenarioDtos.ScenarioCommand> commands = new ArrayList<>();
    List<ScenarioDtos.StationSnapshot> snapshots = new ArrayList<>();
    List<ScenarioDtos.ScenarioTransition> transitions = new ArrayList<>();
    List<AcceptanceDtos.AcceptanceEventRow> rows = new ArrayList<>();

    snapshots.add(state.replaySnapshot(0, "06:00", 0, List.of("初始站点状态")));
    long commandSequence = 0;
    for (String rawEvent : DEFAULT_EVENTS) {
        ScenarioEvent event = ScenarioEvent.parse(rawEvent);
        commandSequence++;
        commands.add(toCommand(commandSequence, rawEvent, event));
        ScenarioDtos.StationSnapshot before = snapshots.get(snapshots.size() - 1);
        state.advanceTo(event.time);
        String notes = state.apply(event);
        AcceptanceDtos.AcceptanceEventRow row = state.snapshot(event, notes);
        rows.add(row);
        ScenarioDtos.StationSnapshot after = state.replaySnapshot(
                commandSequence,
                event.time.format(TIME_FORMAT),
                commandSequence,
                noteList(notes)
        );
        snapshots.add(after);
        transitions.add(transitionFrom(before, after, event, notes));
    }

    return new ScenarioDtos.ReplayBundle(
            scenarioDefinition(),
            commands,
            snapshots,
            transitions,
            replayChecks(sampleChecks(rows)),
            rows
    );
}
```

- [ ] **Step 3: Add helper methods in `AcceptanceScenarioService`**

Add these private methods to the outer service class:

```java
private ScenarioDtos.ScenarioDefinition scenarioDefinition() {
    return new ScenarioDtos.ScenarioDefinition(
            "course-sample",
            "课程事件序列",
            "2026-06-14",
            "06:00",
            "09:30",
            new ScenarioDtos.PileConfig(List.of("F1", "F2"), List.of("T1", "T2", "T3"))
    );
}

private ScenarioDtos.ScenarioCommand toCommand(long sequence, String rawEvent, ScenarioEvent event) {
    String type = switch (event.source + ":" + event.operation) {
        case "A:F", "A:T" -> "SubmitChargingRequest";
        case "A:O" -> "CancelChargingRequest";
        case "B:O" -> event.amount == 0.0 ? "MarkPileFault" : "RecoverPile";
        case "C:O" -> "ModifyRequestAmount";
        default -> "UnknownCommand";
    };
    String mode = "F".equals(event.operation) ? "FAST" : "T".equals(event.operation) ? "SLOW" : "";
    String display = switch (type) {
        case "SubmitChargingRequest" -> event.target + " 提交" + ("FAST".equals(mode) ? "快充" : "慢充") + "请求";
        case "CancelChargingRequest" -> event.target + " 取消当前请求";
        case "MarkPileFault" -> event.target + " 发生故障";
        case "RecoverPile" -> event.target + " 恢复运行";
        case "ModifyRequestAmount" -> event.target + " 修改请求电量为 " + formatNumber(event.amount);
        default -> rawEvent;
    };
    return new ScenarioDtos.ScenarioCommand(
            sequence,
            event.time.format(TIME_FORMAT),
            type,
            event.target,
            mode,
            formatNumber(event.amount),
            rawEvent.substring(rawEvent.indexOf('(')),
            display
    );
}

private List<String> noteList(String notes) {
    return notes == null || notes.isBlank() ? List.of() : List.of(notes);
}

private List<ScenarioDtos.ScenarioCheck> replayChecks(List<AcceptanceDtos.AcceptanceSampleCheck> checks) {
    List<ScenarioDtos.ScenarioCheck> result = new ArrayList<>();
    for (int i = 0; i < checks.size(); i++) {
        AcceptanceDtos.AcceptanceSampleCheck check = checks.get(i);
        result.add(new ScenarioDtos.ScenarioCheck(
                "check-" + String.format(Locale.ROOT, "%03d", i + 1),
                check.time() + " " + check.column(),
                check.expected(),
                check.actual(),
                check.matched(),
                "course-excel"
        ));
    }
    return result;
}

private ScenarioDtos.ScenarioTransition transitionFrom(
        ScenarioDtos.StationSnapshot before,
        ScenarioDtos.StationSnapshot after,
        ScenarioEvent event,
        String notes
) {
    List<ScenarioDtos.TransitionChange> changes = new ArrayList<>();
    changes.add(new ScenarioDtos.TransitionChange(
            event.source.equals("B") ? "pile" : "vehicle",
            event.target,
            commandChangeType(event),
            before.time(),
            after.time(),
            notes == null || notes.isBlank() ? "课程事件：" + event.rawPayload : notes
    ));
    return new ScenarioDtos.ScenarioTransition(
            before.sequence(),
            after.sequence(),
            after.time(),
            changes
    );
}

private String commandChangeType(ScenarioEvent event) {
    if ("B".equals(event.source) && event.amount == 0.0) {
        return "PILE_FAULTED";
    }
    if ("B".equals(event.source)) {
        return "PILE_RECOVERED";
    }
    if ("A".equals(event.source) && "O".equals(event.operation)) {
        return "REQUEST_CANCELLED";
    }
    if ("C".equals(event.source)) {
        return "REQUEST_AMOUNT_CHANGED";
    }
    return "REQUEST_SUBMITTED";
}
```

- [ ] **Step 4: Add replay snapshot method to `ScenarioState`**

Inside the `ScenarioState` inner class, add:

```java
private ScenarioDtos.StationSnapshot replaySnapshot(
        long sequence,
        String time,
        long appliedCommandSequence,
        List<String> ruleNotes
) {
    List<ScenarioDtos.PileState> fastPiles = new ArrayList<>();
    List<ScenarioDtos.PileState> slowPiles = new ArrayList<>();
    Map<String, ScenarioDtos.VehicleState> vehicles = new LinkedHashMap<>();

    for (ScenarioVehicle vehicle : waitingArea) {
        vehicles.put(vehicle.carId, vehicleState(vehicle, "WAITING", "WAITING_AREA"));
    }
    for (ScenarioPile pile : piles.values()) {
        ScenarioDtos.PileState pileState = pileState(pile, vehicles);
        if ("F".equals(pile.mode)) {
            fastPiles.add(pileState);
        } else {
            slowPiles.add(pileState);
        }
    }

    return new ScenarioDtos.StationSnapshot(
            sequence,
            time,
            appliedCommandSequence,
            new ScenarioDtos.StationState(
                    waitingArea.stream().map(vehicle -> vehicle.carId).toList(),
                    fastPiles,
                    slowPiles
            ),
            vehicles,
            ruleNotes
    );
}

private ScenarioDtos.PileState pileState(
        ScenarioPile pile,
        Map<String, ScenarioDtos.VehicleState> vehicles
) {
    List<String> queue = pile.queue.stream().map(vehicle -> vehicle.carId).toList();
    for (int i = 0; i < pile.queue.size(); i++) {
        ScenarioVehicle vehicle = pile.queue.get(i);
        vehicles.put(vehicle.carId, vehicleState(
                vehicle,
                i == 0 && !pile.fault ? "CHARGING" : "PILE_QUEUE",
                pile.id
        ));
    }
    return new ScenarioDtos.PileState(
            pile.id,
            "F".equals(pile.mode) ? "FAST" : "SLOW",
            pile.fault ? "FAULT" : "RUNNING",
            queue.isEmpty() || pile.fault ? null : queue.get(0),
            queue,
            formatNumber(pile.power)
    );
}

private ScenarioDtos.VehicleState vehicleState(ScenarioVehicle vehicle, String state, String position) {
    return new ScenarioDtos.VehicleState(
            vehicle.carId,
            "F".equals(vehicle.mode) ? "FAST" : "SLOW",
            state,
            formatNumber(vehicle.requestAmount),
            formatNumber(vehicle.chargedAmount),
            vehicle.mode + vehicle.sequence,
            position
    );
}
```

- [ ] **Step 5: Verify `ScenarioEvent` keeps raw event text**

`ScenarioEvent` already exposes the text inside the line as `rawPayload`, for example `(A,V1,T,40)`. Keep that field and use `event.rawPayload` as the source text for replay commands and transition reasons.

The class should retain this shape:

```java
private static final class ScenarioEvent {
    private final LocalDateTime time;
    private final String rawPayload;
    private final String source;
    private final String target;
    private final String operation;
    private final double amount;
}
```

- [ ] **Step 6: Add scenario controller**

Create `ScenarioController.java`:

```java
package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.ScenarioDtos;
import com.bupt.charging.service.AcceptanceScenarioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scenarios")
public class ScenarioController {
    private final AcceptanceScenarioService acceptanceScenarioService;

    public ScenarioController(AcceptanceScenarioService acceptanceScenarioService) {
        this.acceptanceScenarioService = acceptanceScenarioService;
    }

    @GetMapping("/course-sample")
    public ApiResult<ScenarioDtos.ReplayBundle> courseSampleDefinition() {
        return ApiResult.ok(acceptanceScenarioService.runCourseSampleReplay());
    }

    @PostMapping("/course-sample/run")
    public ApiResult<ScenarioDtos.ReplayBundle> runCourseSample() {
        return ApiResult.ok(acceptanceScenarioService.runCourseSampleReplay());
    }
}
```

- [ ] **Step 7: Run backend tests and verify pass**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: all tests pass.

- [ ] **Step 8: Commit backend replay API**

```powershell
cd D:\softe
git add backend/src/main/java/com/bupt/charging/dto/ScenarioDtos.java backend/src/main/java/com/bupt/charging/controller/ScenarioController.java backend/src/main/java/com/bupt/charging/service/AcceptanceScenarioService.java backend/src/test/java/com/bupt/charging/service/AcceptanceScenarioServiceTest.java backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java
git commit -m "feat: expose course scenario replay bundle"
```

---

### Task 3: Current Station Snapshot API

**Files:**
- Create: `backend/src/main/java/com/bupt/charging/dto/StationDtos.java`
- Create: `backend/src/main/java/com/bupt/charging/service/StationSnapshotService.java`
- Create: `backend/src/main/java/com/bupt/charging/controller/StationController.java`
- Modify: `backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java`

- [ ] **Step 1: Add failing smoke test for station snapshot**

Append this test to `RestApiSmokeTest`:

```java
@Test
void stationSnapshotEndpointProjectsCurrentBusinessData() throws Exception {
    ResponseEntity<String> seedResponse = restTemplate.postForEntity("/api/demo/seed", null, String.class);
    assertEquals(HttpStatus.OK, seedResponse.getStatusCode());

    ResponseEntity<String> response = restTemplate.getForEntity("/api/station/snapshot", String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    JsonNode data = objectMapper.readTree(response.getBody()).path("data");
    assertTrue(data.path("station").path("waitingArea").size() > 0);
    assertTrue(data.path("station").has("fastPiles"));
    assertTrue(data.path("station").has("slowPiles"));
    assertTrue(data.path("vehicles").has("CAR-1"));
}
```

- [ ] **Step 2: Run test and verify failure**

Run:

```powershell
cd D:\softe\backend
mvn test -Dtest=RestApiSmokeTest#stationSnapshotEndpointProjectsCurrentBusinessData
```

Expected: FAIL with `404` because `/api/station/snapshot` does not exist.

- [ ] **Step 3: Create station DTO records**

Create `StationDtos.java`:

```java
package com.bupt.charging.dto;

import java.util.List;
import java.util.Map;

public final class StationDtos {
    private StationDtos() {
    }

    public record StationSnapshot(
            String time,
            StationState station,
            Map<String, VehicleState> vehicles,
            Metrics metrics
    ) {
    }

    public record StationState(
            List<String> waitingArea,
            List<PileState> fastPiles,
            List<PileState> slowPiles
    ) {
    }

    public record PileState(
            String id,
            String mode,
            String status,
            String currentVehicle,
            List<String> queue,
            String power
    ) {
    }

    public record VehicleState(
            String id,
            String mode,
            String state,
            String requestKwh,
            String chargedKwh,
            String queueNo,
            String position
    ) {
    }

    public record Metrics(
            int waitingCount,
            int pileQueueCount,
            int faultPileCount,
            int activePileCount
    ) {
    }
}
```

- [ ] **Step 4: Implement station snapshot service**

Create `StationSnapshotService.java`:

```java
package com.bupt.charging.service;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.domain.PileStatus;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.dto.StationDtos;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StationSnapshotService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChargingPileRepository pileRepository;
    private final ChargingRequestRepository requestRepository;

    public StationSnapshotService(
            ChargingPileRepository pileRepository,
            ChargingRequestRepository requestRepository
    ) {
        this.pileRepository = pileRepository;
        this.requestRepository = requestRepository;
    }

    public StationDtos.StationSnapshot currentSnapshot() {
        List<ChargingPile> piles = pileRepository.findAll().stream()
                .sorted((left, right) -> left.getPileId().compareTo(right.getPileId()))
                .toList();
        Map<String, StationDtos.VehicleState> vehicles = new LinkedHashMap<>();
        List<String> waitingArea = requestRepository
                .findByStatusOrderByRequestTimeAsc(RequestStatus.WAITING_AREA)
                .stream()
                .map(request -> {
                    vehicles.put(request.getCarId(), vehicleState(request, "WAITING_AREA"));
                    return request.getCarId();
                })
                .toList();
        List<StationDtos.PileState> fastPiles = piles.stream()
                .filter(pile -> pile.getMode() == ChargeMode.FAST)
                .map(pile -> pileState(pile, vehicles))
                .toList();
        List<StationDtos.PileState> slowPiles = piles.stream()
                .filter(pile -> pile.getMode() == ChargeMode.SLOW)
                .map(pile -> pileState(pile, vehicles))
                .toList();
        int pileQueueCount = vehicles.values().stream()
                .filter(vehicle -> !"WAITING_AREA".equals(vehicle.position()))
                .toList()
                .size();
        int faultPileCount = (int) piles.stream()
                .filter(pile -> pile.getWorkingState() == PileStatus.FAULT)
                .count();

        return new StationDtos.StationSnapshot(
                LocalDateTime.now().format(TIME_FORMAT),
                new StationDtos.StationState(waitingArea, fastPiles, slowPiles),
                vehicles,
                new StationDtos.Metrics(waitingArea.size(), pileQueueCount, faultPileCount, piles.size())
        );
    }

    private StationDtos.PileState pileState(
            ChargingPile pile,
            Map<String, StationDtos.VehicleState> vehicles
    ) {
        List<ChargingRequest> queue = requestRepository
                .findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc(
                        pile.getPileId(),
                        RequestStatus.PILE_QUEUE
                );
        for (ChargingRequest request : queue) {
            vehicles.put(request.getCarId(), vehicleState(request, pile.getPileId()));
        }
        return new StationDtos.PileState(
                pile.getPileId(),
                pile.getMode().name(),
                pile.getWorkingState().name(),
                pile.getCurrentCarId(),
                queue.stream().map(ChargingRequest::getCarId).toList(),
                String.format(java.util.Locale.ROOT, "%.2f", pile.getPower())
        );
    }

    private StationDtos.VehicleState vehicleState(ChargingRequest request, String position) {
        return new StationDtos.VehicleState(
                request.getCarId(),
                request.getMode().name(),
                request.getStatus().name(),
                String.format(java.util.Locale.ROOT, "%.2f", request.getRequestAmount()),
                "0.00",
                request.getQueueNum(),
                position
        );
    }
}
```

- [ ] **Step 5: If repository method is missing, add it**

If `ChargingRequestRepository` does not have `findByStatusOrderByRequestTimeAsc`, add:

```java
List<ChargingRequest> findByStatusOrderByRequestTimeAsc(RequestStatus status);
```

Required imports in `ChargingRequestRepository.java`:

```java
import com.bupt.charging.domain.RequestStatus;
import java.util.List;
```

- [ ] **Step 6: Add station controller**

Create `StationController.java`:

```java
package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.StationDtos;
import com.bupt.charging.service.StationSnapshotService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/station")
public class StationController {
    private final StationSnapshotService stationSnapshotService;

    public StationController(StationSnapshotService stationSnapshotService) {
        this.stationSnapshotService = stationSnapshotService;
    }

    @GetMapping("/snapshot")
    public ApiResult<StationDtos.StationSnapshot> snapshot() {
        return ApiResult.ok(stationSnapshotService.currentSnapshot());
    }
}
```

- [ ] **Step 7: Run backend tests and verify pass**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: all tests pass.

- [ ] **Step 8: Commit station snapshot API**

```powershell
cd D:\softe
git add backend/src/main/java/com/bupt/charging/dto/StationDtos.java backend/src/main/java/com/bupt/charging/service/StationSnapshotService.java backend/src/main/java/com/bupt/charging/controller/StationController.java backend/src/main/java/com/bupt/charging/repository/ChargingRequestRepository.java backend/src/test/java/com/bupt/charging/controller/RestApiSmokeTest.java
git commit -m "feat: expose unified station snapshot"
```

---

### Task 4: Frontend Playback Helper

**Files:**
- Create: `frontend/src/utils/simulationPlayback.js`
- Create: `frontend/src/utils/simulationPlayback.test.js`

- [ ] **Step 1: Write failing playback tests**

Create `simulationPlayback.test.js`:

```js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  createPlaybackState,
  loadReplayBundle,
  pausePlayback,
  playPlayback,
  resetPlayback,
  seekToSequence,
  setPlaybackSpeed,
  stepBackward,
  stepForward
} from './simulationPlayback.js'

const bundle = {
  scenario: { startTime: '06:00', stopTime: '09:30', name: '课程事件序列' },
  commands: [
    { sequence: 1, time: '06:00', displayText: 'V1 提交慢充请求' },
    { sequence: 2, time: '06:05', displayText: 'V2 提交慢充请求' }
  ],
  snapshots: [
    { sequence: 0, time: '06:00', station: { waitingArea: [], fastPiles: [], slowPiles: [] }, vehicles: {} },
    { sequence: 1, time: '06:00', station: { waitingArea: [], fastPiles: [], slowPiles: [] }, vehicles: { V1: { id: 'V1' } } },
    { sequence: 2, time: '06:05', station: { waitingArea: [], fastPiles: [], slowPiles: [] }, vehicles: { V1: { id: 'V1' }, V2: { id: 'V2' } } }
  ],
  transitions: [
    { fromSequence: 0, toSequence: 1, changes: [] },
    { fromSequence: 1, toSequence: 2, changes: [] }
  ],
  checks: [],
  tableRows: []
}

describe('simulation playback helpers', () => {
  it('loads a replay bundle at the initial snapshot', () => {
    const state = loadReplayBundle(createPlaybackState(), bundle)

    assert.equal(state.status, 'loaded')
    assert.equal(state.currentSequence, 0)
    assert.equal(state.currentTime, '06:00')
    assert.equal(state.currentSnapshot.sequence, 0)
  })

  it('steps forward and backward through snapshots', () => {
    let state = loadReplayBundle(createPlaybackState(), bundle)
    state = stepForward(state)
    assert.equal(state.status, 'paused')
    assert.equal(state.currentSequence, 1)
    assert.equal(state.currentCommand.displayText, 'V1 提交慢充请求')

    state = stepForward(state)
    assert.equal(state.currentSequence, 2)
    assert.equal(state.status, 'completed')

    state = stepBackward(state)
    assert.equal(state.status, 'paused')
    assert.equal(state.currentSequence, 1)
  })

  it('supports play pause speed seek and reset', () => {
    let state = loadReplayBundle(createPlaybackState(), bundle)
    state = playPlayback(state)
    assert.equal(state.status, 'playing')

    state = setPlaybackSpeed(state, 5)
    assert.equal(state.speed, 5)

    state = pausePlayback(state)
    assert.equal(state.status, 'paused')

    state = seekToSequence(state, 2)
    assert.equal(state.currentSequence, 2)
    assert.equal(state.status, 'completed')

    state = resetPlayback(state)
    assert.equal(state.status, 'loaded')
    assert.equal(state.currentSequence, 0)
  })
})
```

- [ ] **Step 2: Run frontend tests and verify failure**

Run:

```powershell
cd D:\softe\frontend
npm test
```

Expected: FAIL because `simulationPlayback.js` does not exist.

- [ ] **Step 3: Implement playback helper**

Create `simulationPlayback.js`:

```js
const SPEEDS = [0.5, 1, 2, 5, 10]

export function createPlaybackState() {
  return {
    status: 'empty',
    bundle: null,
    currentSequence: 0,
    currentTime: '',
    speed: 1,
    currentSnapshot: null,
    currentCommand: null,
    currentTransition: null
  }
}

export function loadReplayBundle(state, bundle) {
  const initialSnapshot = bundle.snapshots?.[0] || null
  return derive({
    ...state,
    status: 'loaded',
    bundle,
    currentSequence: initialSnapshot?.sequence ?? 0,
    currentTime: initialSnapshot?.time || bundle.scenario?.startTime || '',
    speed: 1
  })
}

export function playPlayback(state) {
  if (!state.bundle || state.status === 'completed') {
    return state
  }
  return { ...state, status: 'playing' }
}

export function pausePlayback(state) {
  if (state.status !== 'playing') {
    return state
  }
  return { ...state, status: 'paused' }
}

export function stepForward(state) {
  if (!state.bundle) {
    return state
  }
  const maxSequence = maxSnapshotSequence(state.bundle)
  return derive({
    ...state,
    currentSequence: Math.min(state.currentSequence + 1, maxSequence)
  })
}

export function stepBackward(state) {
  if (!state.bundle) {
    return state
  }
  return derive({
    ...state,
    currentSequence: Math.max(state.currentSequence - 1, 0),
    status: 'paused'
  })
}

export function seekToSequence(state, sequence) {
  if (!state.bundle) {
    return state
  }
  const maxSequence = maxSnapshotSequence(state.bundle)
  return derive({
    ...state,
    currentSequence: Math.max(0, Math.min(sequence, maxSequence))
  })
}

export function setPlaybackSpeed(state, speed) {
  return SPEEDS.includes(speed) ? { ...state, speed } : state
}

export function resetPlayback(state) {
  if (!state.bundle) {
    return createPlaybackState()
  }
  return loadReplayBundle(createPlaybackState(), state.bundle)
}

function derive(state) {
  const snapshots = state.bundle?.snapshots || []
  const commands = state.bundle?.commands || []
  const transitions = state.bundle?.transitions || []
  const currentSnapshot = snapshots.find((item) => item.sequence === state.currentSequence) || snapshots[0] || null
  const currentCommand = commands.find((item) => item.sequence === state.currentSequence) || null
  const currentTransition = transitions.find((item) => item.toSequence === state.currentSequence) || null
  const maxSequence = maxSnapshotSequence(state.bundle)
  const status = state.currentSequence >= maxSequence && maxSequence > 0 ? 'completed' : state.status === 'playing' ? 'playing' : state.currentSequence === 0 ? 'loaded' : 'paused'

  return {
    ...state,
    status,
    currentSnapshot,
    currentCommand,
    currentTransition,
    currentTime: currentSnapshot?.time || state.currentTime
  }
}

function maxSnapshotSequence(bundle) {
  return Math.max(0, ...(bundle?.snapshots || []).map((item) => item.sequence))
}
```

- [ ] **Step 4: Run frontend tests and verify pass**

Run:

```powershell
cd D:\softe\frontend
npm test
```

Expected: all frontend unit tests pass.

- [ ] **Step 5: Commit playback helper**

```powershell
cd D:\softe
git add frontend/src/utils/simulationPlayback.js frontend/src/utils/simulationPlayback.test.js
git commit -m "feat: add simulation playback state helpers"
```

---

### Task 5: Frontend API Client And App Entry

**Files:**
- Modify: `frontend/src/api/chargingApi.js`
- Modify: `frontend/src/App.vue`
- Create: `frontend/src/views/SimulationSandbox.vue`

- [ ] **Step 1: Add scenario and station API methods**

Modify the bottom of the `api` object in `chargingApi.js`:

```js
  runAcceptanceScenario: () => unwrap(http.get('/acceptance/scenario')),
  getCourseScenario: () => unwrap(http.get('/scenarios/course-sample')),
  runCourseScenario: () => unwrap(http.post('/scenarios/course-sample/run')),
  getStationSnapshot: () => unwrap(http.get('/station/snapshot'))
```

Ensure there is a comma after the previous property before adding new methods.

- [ ] **Step 2: Create temporary sandbox page**

Create `SimulationSandbox.vue`:

```vue
<template>
  <div class="simulation-sandbox">
    <el-card>
      <div class="card-heading compact">
        <div>
          <p class="eyebrow">课程事件序列</p>
          <h2>调度沙盘</h2>
        </div>
        <el-button type="primary" :loading="loading" @click="loadScenario">
          加载课程事件序列
        </el-button>
      </div>
      <el-empty v-if="!bundle" description="加载后显示 06:00 到 09:30 的运行回放" />
      <div v-else class="kpi-strip">
        <div class="kpi-item">
          <span>开始时间</span>
          <strong>{{ bundle.scenario.startTime }}</strong>
        </div>
        <div class="kpi-item">
          <span>结束时间</span>
          <strong>{{ bundle.scenario.stopTime }}</strong>
        </div>
        <div class="kpi-item">
          <span>命令数量</span>
          <strong>{{ bundle.commands.length }}</strong>
        </div>
        <div class="kpi-item">
          <span>快照数量</span>
          <strong>{{ bundle.snapshots.length }}</strong>
        </div>
        <div class="kpi-item">
          <span>核对结果</span>
          <strong>{{ passedChecks }}/{{ bundle.checks.length }}</strong>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/chargingApi'

const loading = ref(false)
const bundle = ref(null)

const passedChecks = computed(() => (bundle.value?.checks || []).filter((check) => check.passed).length)

async function loadScenario() {
  loading.value = true
  try {
    bundle.value = await api.runCourseScenario()
    ElMessage.success('课程事件序列已加载')
  } catch (error) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}
</script>
```

- [ ] **Step 3: Replace top-level acceptance tab**

Modify `App.vue`:

```vue
<template>
  <el-container class="app-shell">
    <el-header class="app-header">
      <div>
        <h1>波普特大学充电站</h1>
        <p>调度运行 · 分时计费 · 故障重排</p>
      </div>
      <el-tag type="success" effect="dark">站点运行中</el-tag>
    </el-header>

    <el-main>
      <el-tabs v-model="activeTab" class="main-tabs">
        <el-tab-pane label="调度沙盘" name="simulation">
          <SimulationSandbox />
        </el-tab-pane>
        <el-tab-pane label="车主自助" name="owner">
          <OwnerPanel />
        </el-tab-pane>
        <el-tab-pane label="运营管理" name="admin">
          <AdminPanel />
        </el-tab-pane>
      </el-tabs>
    </el-main>
  </el-container>
</template>

<script setup>
import { ref } from 'vue'
import SimulationSandbox from './views/SimulationSandbox.vue'
import OwnerPanel from './views/OwnerPanel.vue'
import AdminPanel from './views/AdminPanel.vue'

const activeTab = ref('simulation')
</script>
```

- [ ] **Step 4: Run frontend tests and build**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: unit tests pass and Vite build succeeds.

- [ ] **Step 5: Commit app entry update**

```powershell
cd D:\softe
git add frontend/src/api/chargingApi.js frontend/src/App.vue frontend/src/views/SimulationSandbox.vue
git commit -m "feat: add simulation sandbox entry"
```

---

### Task 6: Simulation Controls And Loader Components

**Files:**
- Create: `frontend/src/components/simulation/SimulationClockBar.vue`
- Create: `frontend/src/components/simulation/ScenarioLoader.vue`
- Modify: `frontend/src/views/SimulationSandbox.vue`

- [ ] **Step 1: Create clock bar**

Create `SimulationClockBar.vue`:

```vue
<template>
  <div class="simulation-clock-bar">
    <div class="clock-readout">
      <span>模拟时间</span>
      <strong>{{ currentTime || '--:--' }}</strong>
    </div>
    <div class="action-row">
      <el-button :disabled="!canStepBack" @click="$emit('step-back')">上一步</el-button>
      <el-button type="primary" :disabled="!canPlay" @click="$emit(playing ? 'pause' : 'play')">
        {{ playing ? '暂停' : '播放' }}
      </el-button>
      <el-button :disabled="!canStepForward" @click="$emit('step-forward')">下一步</el-button>
      <el-button :disabled="!canReset" @click="$emit('reset')">重置</el-button>
    </div>
    <el-segmented :model-value="speed" :options="speedOptions" @update:model-value="$emit('speed', $event)" />
  </div>
</template>

<script setup>
defineProps({
  currentTime: { type: String, default: '' },
  playing: { type: Boolean, default: false },
  canPlay: { type: Boolean, default: false },
  canStepBack: { type: Boolean, default: false },
  canStepForward: { type: Boolean, default: false },
  canReset: { type: Boolean, default: false },
  speed: { type: Number, default: 1 }
})

defineEmits(['play', 'pause', 'step-back', 'step-forward', 'reset', 'speed'])

const speedOptions = [
  { label: '0.5x', value: 0.5 },
  { label: '1x', value: 1 },
  { label: '2x', value: 2 },
  { label: '5x', value: 5 },
  { label: '10x', value: 10 }
]
</script>
```

- [ ] **Step 2: Create scenario loader**

Create `ScenarioLoader.vue`:

```vue
<template>
  <el-card class="scenario-loader">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">事件序列</p>
        <h2>{{ scenarioName }}</h2>
      </div>
      <div class="action-row">
        <el-button type="primary" :loading="loading" @click="$emit('load')">加载课程事件序列</el-button>
        <el-button :disabled="!loaded" @click="$emit('copy')">复制结果</el-button>
      </div>
    </div>
    <div class="scenario-meta">
      <span>{{ timeRange }}</span>
      <span>{{ commandCount }} 个命令</span>
      <span>{{ snapshotCount }} 个快照</span>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  loading: { type: Boolean, default: false },
  loaded: { type: Boolean, default: false },
  scenario: { type: Object, default: null },
  commandCount: { type: Number, default: 0 },
  snapshotCount: { type: Number, default: 0 }
})

defineEmits(['load', 'copy'])

const scenarioName = computed(() => props.scenario?.name || '课程事件序列')
const timeRange = computed(() => {
  if (!props.scenario) {
    return '尚未加载'
  }
  return `${props.scenario.startTime} - ${props.scenario.stopTime}`
})
</script>
```

- [ ] **Step 3: Wire controls into sandbox**

Modify `SimulationSandbox.vue` to import playback helpers and the two components:

```js
import { computed, ref } from 'vue'
import {
  createPlaybackState,
  loadReplayBundle,
  pausePlayback,
  playPlayback,
  resetPlayback,
  setPlaybackSpeed,
  stepBackward,
  stepForward
} from '../utils/simulationPlayback'
import ScenarioLoader from '../components/simulation/ScenarioLoader.vue'
import SimulationClockBar from '../components/simulation/SimulationClockBar.vue'
```

Use this state:

```js
const loading = ref(false)
const playback = ref(createPlaybackState())
const bundle = computed(() => playback.value.bundle)
const scenario = computed(() => bundle.value?.scenario || null)
const loaded = computed(() => Boolean(bundle.value))
const playing = computed(() => playback.value.status === 'playing')
const canPlay = computed(() => ['loaded', 'paused'].includes(playback.value.status))
const canStepBack = computed(() => loaded.value && playback.value.currentSequence > 0)
const canStepForward = computed(() => loaded.value && playback.value.status !== 'completed')
const canReset = computed(() => loaded.value)
```

Use these handlers:

```js
async function loadScenario() {
  loading.value = true
  try {
    playback.value = loadReplayBundle(createPlaybackState(), await api.runCourseScenario())
    ElMessage.success('课程事件序列已加载')
  } catch (error) {
    ElMessage.error(error.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function play() {
  playback.value = playPlayback(playback.value)
}

function pause() {
  playback.value = pausePlayback(playback.value)
}

function stepBack() {
  playback.value = stepBackward(playback.value)
}

function stepForwardAction() {
  playback.value = stepForward(playback.value)
}

function reset() {
  playback.value = resetPlayback(playback.value)
}

function setSpeed(speed) {
  playback.value = setPlaybackSpeed(playback.value, speed)
}
```

Use this template shape:

```vue
<template>
  <div class="simulation-sandbox stack">
    <ScenarioLoader
      :loading="loading"
      :loaded="loaded"
      :scenario="scenario"
      :command-count="bundle?.commands?.length || 0"
      :snapshot-count="bundle?.snapshots?.length || 0"
      @load="loadScenario"
      @copy="copyRows"
    />

    <SimulationClockBar
      :current-time="playback.currentTime"
      :playing="playing"
      :can-play="canPlay"
      :can-step-back="canStepBack"
      :can-step-forward="canStepForward"
      :can-reset="canReset"
      :speed="playback.speed"
      @play="play"
      @pause="pause"
      @step-back="stepBack"
      @step-forward="stepForwardAction"
      @reset="reset"
      @speed="setSpeed"
    />
  </div>
</template>
```

Keep the existing `copyRows` function using `bundle.value.tableRows`.

- [ ] **Step 4: Run frontend tests and build**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: unit tests pass and Vite build succeeds.

- [ ] **Step 5: Commit controls**

```powershell
cd D:\softe
git add frontend/src/components/simulation/SimulationClockBar.vue frontend/src/components/simulation/ScenarioLoader.vue frontend/src/views/SimulationSandbox.vue
git commit -m "feat: add sandbox playback controls"
```

---

### Task 7: Station Map Components

**Files:**
- Create: `frontend/src/components/simulation/StationMap.vue`
- Create: `frontend/src/components/simulation/PileLane.vue`
- Create: `frontend/src/components/simulation/VehicleToken.vue`
- Modify: `frontend/src/views/SimulationSandbox.vue`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Create vehicle token component**

Create `VehicleToken.vue`:

```vue
<template>
  <div class="vehicle-token" :class="[`vehicle-${vehicle.mode?.toLowerCase() || 'unknown'}`]">
    <strong>{{ vehicle.id }}</strong>
    <span>{{ modeLabel }}</span>
    <small>{{ vehicle.chargedKwh || '0.00' }} / {{ vehicle.requestKwh || '0.00' }} 度</small>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  vehicle: { type: Object, required: true }
})

const modeLabel = computed(() => props.vehicle.mode === 'FAST' ? '快充' : props.vehicle.mode === 'SLOW' ? '慢充' : '未知')
</script>
```

- [ ] **Step 2: Create pile lane component**

Create `PileLane.vue`:

```vue
<template>
  <section class="pile-lane" :class="{ fault: pile.status === 'FAULT' }">
    <header>
      <div>
        <strong>{{ pile.id }}</strong>
        <span>{{ pile.mode === 'FAST' ? '快充' : '慢充' }} · {{ pile.power }} kW</span>
      </div>
      <el-tag :type="pile.status === 'FAULT' ? 'danger' : 'success'" effect="plain">
        {{ pile.status === 'FAULT' ? '故障' : '运行' }}
      </el-tag>
    </header>
    <div class="pile-queue">
      <VehicleToken
        v-for="carId in pile.queue"
        :key="carId"
        :vehicle="vehicles[carId] || { id: carId, mode: pile.mode }"
      />
      <div v-if="!pile.queue?.length" class="empty-slot">空闲</div>
    </div>
  </section>
</template>

<script setup>
import VehicleToken from './VehicleToken.vue'

defineProps({
  pile: { type: Object, required: true },
  vehicles: { type: Object, default: () => ({}) }
})
</script>
```

- [ ] **Step 3: Create station map component**

Create `StationMap.vue`:

```vue
<template>
  <el-card class="station-map-card">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">站点状态</p>
        <h2>充电站沙盘</h2>
      </div>
      <el-tag effect="plain">{{ snapshot?.time || '--:--' }}</el-tag>
    </div>

    <div v-if="!snapshot" class="sandbox-empty">加载课程事件序列后显示站点状态</div>
    <div v-else class="station-map">
      <section class="waiting-area">
        <h3>等候区</h3>
        <div class="waiting-list">
          <VehicleToken
            v-for="carId in snapshot.station.waitingArea"
            :key="carId"
            :vehicle="snapshot.vehicles[carId] || { id: carId }"
          />
          <div v-if="!snapshot.station.waitingArea.length" class="empty-slot">暂无等候车辆</div>
        </div>
      </section>

      <section class="pile-area">
        <h3>快充区</h3>
        <PileLane
          v-for="pile in snapshot.station.fastPiles"
          :key="pile.id"
          :pile="pile"
          :vehicles="snapshot.vehicles"
        />
      </section>

      <section class="pile-area">
        <h3>慢充区</h3>
        <PileLane
          v-for="pile in snapshot.station.slowPiles"
          :key="pile.id"
          :pile="pile"
          :vehicles="snapshot.vehicles"
        />
      </section>
    </div>
  </el-card>
</template>

<script setup>
import PileLane from './PileLane.vue'
import VehicleToken from './VehicleToken.vue'

defineProps({
  snapshot: { type: Object, default: null }
})
</script>
```

- [ ] **Step 4: Add map to sandbox**

In `SimulationSandbox.vue`, import:

```js
import StationMap from '../components/simulation/StationMap.vue'
```

Add to template below `SimulationClockBar`:

```vue
<StationMap :snapshot="playback.currentSnapshot" />
```

- [ ] **Step 5: Add sandbox CSS**

Append to `styles.css`:

```css
.simulation-clock-bar,
.scenario-loader,
.station-map-card {
  border-radius: 6px;
}

.simulation-clock-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid #dbe3ec;
  background: #fff;
}

.clock-readout span,
.scenario-meta span {
  color: #64748b;
  font-size: 12px;
}

.clock-readout strong {
  display: block;
  margin-top: 4px;
  font-size: 28px;
  font-weight: 750;
}

.scenario-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.station-map {
  display: grid;
  grid-template-columns: minmax(220px, 0.8fr) minmax(260px, 1fr) minmax(320px, 1.2fr);
  gap: 16px;
  align-items: stretch;
}

.waiting-area,
.pile-area {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e4eaf1;
  border-radius: 6px;
  background: #f8fafc;
}

.waiting-area h3,
.pile-area h3 {
  margin: 0 0 12px;
  font-size: 15px;
}

.waiting-list,
.pile-queue {
  display: grid;
  gap: 8px;
}

.pile-lane {
  display: grid;
  gap: 10px;
  padding: 10px;
  border: 1px solid #dbe3ec;
  border-radius: 6px;
  background: #fff;
}

.pile-lane + .pile-lane {
  margin-top: 10px;
}

.pile-lane header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.pile-lane header strong,
.pile-lane header span {
  display: block;
}

.pile-lane header span {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.pile-lane.fault {
  border-color: #fecaca;
  background: #fff7f7;
}

.vehicle-token {
  min-height: 54px;
  padding: 9px 10px;
  border: 1px solid #dbe3ec;
  border-left: 4px solid #409eff;
  border-radius: 6px;
  background: #fff;
}

.vehicle-token strong,
.vehicle-token span,
.vehicle-token small {
  display: block;
}

.vehicle-token span,
.vehicle-token small {
  color: #64748b;
  font-size: 12px;
}

.vehicle-slow {
  border-left-color: #67c23a;
}

.empty-slot,
.sandbox-empty {
  padding: 14px;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  color: #64748b;
  text-align: center;
}

@media (max-width: 1100px) {
  .station-map {
    grid-template-columns: 1fr;
  }

  .simulation-clock-bar {
    align-items: flex-start;
    flex-direction: column;
  }
}
```

- [ ] **Step 6: Run frontend tests and build**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: unit tests pass and Vite build succeeds.

- [ ] **Step 7: Commit station map**

```powershell
cd D:\softe
git add frontend/src/components/simulation/StationMap.vue frontend/src/components/simulation/PileLane.vue frontend/src/components/simulation/VehicleToken.vue frontend/src/views/SimulationSandbox.vue frontend/src/styles.css
git commit -m "feat: render simulation station map"
```

---

### Task 8: Timeline, Inspector, And Verification Panel

**Files:**
- Create: `frontend/src/components/simulation/EventTimeline.vue`
- Create: `frontend/src/components/simulation/PlaybackInspector.vue`
- Create: `frontend/src/components/simulation/VerificationPanel.vue`
- Modify: `frontend/src/views/SimulationSandbox.vue`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Create event timeline**

Create `EventTimeline.vue`:

```vue
<template>
  <el-card>
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">时间轴</p>
        <h2>事件进度</h2>
      </div>
      <el-tag effect="plain">{{ currentSequence }} / {{ commands.length }}</el-tag>
    </div>
    <div class="event-timeline">
      <button
        v-for="command in commands"
        :key="command.sequence"
        type="button"
        class="timeline-point"
        :class="{ active: command.sequence === currentSequence, past: command.sequence < currentSequence }"
        @click="$emit('seek', command.sequence)"
      >
        <span>{{ command.time }}</span>
        <strong>{{ command.targetId }}</strong>
      </button>
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  commands: { type: Array, default: () => [] },
  currentSequence: { type: Number, default: 0 }
})

defineEmits(['seek'])
</script>
```

- [ ] **Step 2: Create playback inspector**

Create `PlaybackInspector.vue`:

```vue
<template>
  <el-card>
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">当前步骤</p>
        <h2>{{ command?.displayText || '等待加载' }}</h2>
      </div>
      <el-tag v-if="command" effect="plain">{{ command.time }}</el-tag>
    </div>
    <el-descriptions v-if="command" :column="1" border>
      <el-descriptions-item label="命令">{{ command.type }}</el-descriptions-item>
      <el-descriptions-item label="对象">{{ command.targetId }}</el-descriptions-item>
      <el-descriptions-item label="来源">{{ command.sourceText }}</el-descriptions-item>
    </el-descriptions>
    <el-empty v-else description="选择事件后显示规则说明" />

    <div v-if="transition?.changes?.length" class="change-list">
      <h3>状态变化</h3>
      <div v-for="change in transition.changes" :key="`${change.entityType}-${change.entityId}-${change.changeType}`" class="change-item">
        <strong>{{ change.entityId }}</strong>
        <span>{{ change.reason }}</span>
      </div>
    </div>
  </el-card>
</template>

<script setup>
defineProps({
  command: { type: Object, default: null },
  transition: { type: Object, default: null }
})
</script>
```

- [ ] **Step 3: Create verification panel**

Create `VerificationPanel.vue`:

```vue
<template>
  <el-card>
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">运行结果</p>
        <h2>结果核对</h2>
      </div>
      <el-button :disabled="!tableRows.length" @click="$emit('copy')">复制结果</el-button>
    </div>

    <el-collapse>
      <el-collapse-item title="关键项核对" name="checks">
        <el-table :data="checks" border empty-text="暂无核对项">
          <el-table-column prop="name" label="检查项" min-width="160" />
          <el-table-column prop="expected" label="预期" min-width="220" />
          <el-table-column prop="actual" label="实际" min-width="220" />
          <el-table-column label="结果" width="100">
            <template #default="{ row }">
              <el-tag :type="row.passed ? 'success' : 'warning'" effect="plain">
                {{ row.passed ? '通过' : '需复核' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
      <el-collapse-item title="表格结果" name="table">
        <el-table :data="flattenedRows" border height="420" empty-text="暂无表格结果">
          <el-table-column prop="time" label="时刻" width="82" fixed />
          <el-table-column prop="event" label="事件" width="150" fixed />
          <el-table-column prop="slot" label="位" width="54" />
          <el-table-column prop="fast1" label="快充1" min-width="150" />
          <el-table-column prop="fast2" label="快充2" min-width="150" />
          <el-table-column prop="slow1" label="慢充1" min-width="150" />
          <el-table-column prop="slow2" label="慢充2" min-width="150" />
          <el-table-column prop="slow3" label="慢充3" min-width="150" />
          <el-table-column prop="waitingAreaText" label="等候区" min-width="320" show-overflow-tooltip />
          <el-table-column prop="notes" label="备注" min-width="180" show-overflow-tooltip />
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { flattenScenarioRows } from '../../utils/acceptanceDisplay'

const props = defineProps({
  checks: { type: Array, default: () => [] },
  tableRows: { type: Array, default: () => [] }
})

defineEmits(['copy'])

const flattenedRows = computed(() => flattenScenarioRows(props.tableRows))
</script>
```

- [ ] **Step 4: Wire timeline inspector and verification into sandbox**

In `SimulationSandbox.vue`, import:

```js
import EventTimeline from '../components/simulation/EventTimeline.vue'
import PlaybackInspector from '../components/simulation/PlaybackInspector.vue'
import VerificationPanel from '../components/simulation/VerificationPanel.vue'
import { seekToSequence } from '../utils/simulationPlayback'
```

Add handler:

```js
function seek(sequence) {
  playback.value = seekToSequence(playback.value, sequence)
}
```

Add template section:

```vue
<div class="simulation-layout">
  <div class="simulation-main">
    <StationMap :snapshot="playback.currentSnapshot" />
    <EventTimeline
      :commands="bundle?.commands || []"
      :current-sequence="playback.currentSequence"
      @seek="seek"
    />
    <VerificationPanel
      :checks="bundle?.checks || []"
      :table-rows="bundle?.tableRows || []"
      @copy="copyRows"
    />
  </div>
  <aside class="simulation-side">
    <PlaybackInspector
      :command="playback.currentCommand"
      :transition="playback.currentTransition"
    />
  </aside>
</div>
```

- [ ] **Step 5: Add layout CSS**

Append:

```css
.simulation-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.simulation-main {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.simulation-side {
  position: sticky;
  top: 16px;
  min-width: 0;
}

.event-timeline {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 4px;
}

.timeline-point {
  min-width: 86px;
  padding: 8px;
  border: 1px solid #dbe3ec;
  border-radius: 6px;
  background: #fff;
  color: #1f2933;
  cursor: pointer;
  text-align: left;
}

.timeline-point span,
.timeline-point strong {
  display: block;
}

.timeline-point span {
  color: #64748b;
  font-size: 12px;
}

.timeline-point.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.timeline-point.past {
  border-color: #b7e3c0;
}

.change-list {
  margin-top: 14px;
}

.change-list h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.change-item {
  padding: 10px;
  border: 1px solid #e4eaf1;
  border-radius: 6px;
  background: #f8fafc;
}

.change-item + .change-item {
  margin-top: 8px;
}

.change-item strong,
.change-item span {
  display: block;
}

.change-item span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

@media (max-width: 1200px) {
  .simulation-layout {
    grid-template-columns: 1fr;
  }

  .simulation-side {
    position: static;
  }
}
```

- [ ] **Step 6: Run frontend tests and build**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: unit tests pass and Vite build succeeds.

- [ ] **Step 7: Commit timeline and verification**

```powershell
cd D:\softe
git add frontend/src/components/simulation/EventTimeline.vue frontend/src/components/simulation/PlaybackInspector.vue frontend/src/components/simulation/VerificationPanel.vue frontend/src/views/SimulationSandbox.vue frontend/src/styles.css
git commit -m "feat: add sandbox timeline and verification"
```

---

### Task 9: Timed Playback Loop

**Files:**
- Modify: `frontend/src/utils/simulationPlayback.js`
- Modify: `frontend/src/utils/simulationPlayback.test.js`
- Modify: `frontend/src/views/SimulationSandbox.vue`

- [ ] **Step 1: Add pure helper test for advancing by elapsed time**

Append to `simulationPlayback.test.js`:

```js
import { advancePlaybackByMs } from './simulationPlayback.js'

it('advances playing state by real elapsed milliseconds and speed', () => {
  let state = loadReplayBundle(createPlaybackState(), bundle)
  state = playPlayback(state)

  state = advancePlaybackByMs(state, 60_000)
  assert.equal(state.currentSequence, 1)
  assert.equal(state.status, 'playing')

  state = setPlaybackSpeed(state, 5)
  state = advancePlaybackByMs(state, 60_000)
  assert.equal(state.currentSequence, 2)
  assert.equal(state.status, 'completed')
})
```

- [ ] **Step 2: Run frontend tests and verify failure**

Run:

```powershell
cd D:\softe\frontend
npm test
```

Expected: FAIL because `advancePlaybackByMs` is not exported.

- [ ] **Step 3: Implement time advancement helper**

Add to `simulationPlayback.js`:

```js
export function advancePlaybackByMs(state, elapsedMs) {
  if (state.status !== 'playing' || !state.bundle) {
    return state
  }
  const commands = state.bundle.commands || []
  const currentMinutes = timeToMinutes(state.currentTime || state.bundle.scenario?.startTime || '00:00')
  const targetMinutes = currentMinutes + (elapsedMs / 60000) * state.speed
  const nextCommand = commands.find((command) => {
    return command.sequence > state.currentSequence && timeToMinutes(command.time) <= targetMinutes
  })
  if (!nextCommand) {
    return {
      ...state,
      currentTime: minutesToTime(targetMinutes)
    }
  }
  return derive({
    ...state,
    currentSequence: nextCommand.sequence
  })
}

function timeToMinutes(value) {
  const [hour, minute] = value.split(':').map(Number)
  return hour * 60 + minute
}

function minutesToTime(value) {
  const minutes = Math.floor(value)
  const hour = Math.floor(minutes / 60)
  const minute = minutes % 60
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}`
}
```

- [ ] **Step 4: Wire interval in sandbox**

In `SimulationSandbox.vue`, import:

```js
import { onBeforeUnmount } from 'vue'
import { advancePlaybackByMs } from '../utils/simulationPlayback'
```

Add interval state and functions:

```js
let intervalId = null

function startTimer() {
  stopTimer()
  intervalId = window.setInterval(() => {
    playback.value = advancePlaybackByMs(playback.value, 1000)
    if (playback.value.status === 'completed') {
      stopTimer()
    }
  }, 1000)
}

function stopTimer() {
  if (intervalId !== null) {
    window.clearInterval(intervalId)
    intervalId = null
  }
}

onBeforeUnmount(stopTimer)
```

Update handlers:

```js
function play() {
  playback.value = playPlayback(playback.value)
  startTimer()
}

function pause() {
  playback.value = pausePlayback(playback.value)
  stopTimer()
}

function stepBack() {
  stopTimer()
  playback.value = stepBackward(playback.value)
}

function stepForwardAction() {
  stopTimer()
  playback.value = stepForward(playback.value)
}

function reset() {
  stopTimer()
  playback.value = resetPlayback(playback.value)
}

function seek(sequence) {
  stopTimer()
  playback.value = seekToSequence(playback.value, sequence)
}
```

- [ ] **Step 5: Run frontend tests and build**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: unit tests pass and Vite build succeeds.

- [ ] **Step 6: Commit timed playback**

```powershell
cd D:\softe
git add frontend/src/utils/simulationPlayback.js frontend/src/utils/simulationPlayback.test.js frontend/src/views/SimulationSandbox.vue
git commit -m "feat: add timed sandbox playback"
```

---

### Task 10: Copy Result, Remove Unused Acceptance Page, And Demo Text

**Files:**
- Modify: `frontend/src/views/SimulationSandbox.vue`
- Delete: `frontend/src/views/AcceptancePanel.vue`
- Modify: `docs/demo-script.md`

- [ ] **Step 1: Ensure copy result uses replay table rows**

In `SimulationSandbox.vue`, implement:

```js
async function copyRows() {
  const rows = bundle.value?.tableRows || []
  const header = ['时刻', '事件', '位', '快充1', '快充2', '慢充1', '慢充2', '慢充3', '等候区', '备注']
  const lines = [
    header.join('\t'),
    ...flattenScenarioRows(rows).map((row) => [
      row.time,
      row.event,
      row.slot,
      row.fast1,
      row.fast2,
      row.slow1,
      row.slow2,
      row.slow3,
      row.waitingAreaText,
      row.notes
    ].join('\t'))
  ]

  try {
    await navigator.clipboard.writeText(lines.join('\n'))
    ElMessage.success('结果已复制')
  } catch {
    ElMessage.warning('当前浏览器未允许复制')
  }
}
```

Import:

```js
import { flattenScenarioRows } from '../utils/acceptanceDisplay'
```

- [ ] **Step 2: Delete unused acceptance page**

Delete:

```text
frontend/src/views/AcceptancePanel.vue
```

Check that no import remains:

```powershell
cd D:\softe
rg "AcceptancePanel|测试用例|验收流程|生成演示数据|执行一次调度" frontend/src
```

Expected: no matches for `AcceptancePanel`, no top-level UI copy using the listed awkward phrases.

- [ ] **Step 3: Update demo script**

In `docs/demo-script.md`, replace the old acceptance section with:

```markdown
## 演示五：调度沙盘回放

1. 打开系统后进入“调度沙盘”。
2. 点击“加载课程事件序列”，确认时间范围为 06:00 到 09:30。
3. 点击“播放”，观察车辆进入等候区、快充区和慢充区。
4. 在故障事件处点击“暂停”，说明故障桩、受影响车辆和重调度说明。
5. 使用“下一步”逐步讲解关键事件。
6. 播放结束后展开“结果核对”，说明关键项通过。
7. 点击“复制结果”，展示可粘贴到表格的运行结果。
```

- [ ] **Step 4: Run frontend tests and build**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: unit tests pass and Vite build succeeds.

- [ ] **Step 5: Commit copy and docs**

```powershell
cd D:\softe
git add frontend/src/views/SimulationSandbox.vue docs/demo-script.md
git rm frontend/src/views/AcceptancePanel.vue
git commit -m "chore: replace acceptance page with sandbox demo flow"
```

---

### Task 11: Full Verification And Browser QA

**Files:**
- Verify only unless defects are found.

- [ ] **Step 1: Run backend tests**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: all backend tests pass.

- [ ] **Step 2: Run frontend tests and build**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: frontend tests pass and Vite build succeeds.

- [ ] **Step 3: Start backend**

Run:

```powershell
cd D:\softe\backend
mvn spring-boot:run
```

Expected: Spring Boot starts on `http://127.0.0.1:8080`.

- [ ] **Step 4: Start frontend**

Run in a second terminal:

```powershell
cd D:\softe\frontend
npm run dev -- --port 5173
```

Expected: Vite starts on `http://127.0.0.1:5173`.

- [ ] **Step 5: Browser verify desktop**

Open `http://127.0.0.1:5173`.

Verify:

- The first tab is `调度沙盘`.
- Top header says `波普特大学充电站` and `调度运行 · 分时计费 · 故障重排`.
- No top-level tab says `测试用例`.
- Click `加载课程事件序列`.
- Confirm command count is `36`, snapshot count is `37`.
- Click `播放`, then `暂停`.
- Click `下一步` and `上一步`.
- Click a timeline point.
- Open `结果核对`; checks show `通过`.
- Click `复制结果`; browser either copies or shows the expected permission warning.

- [ ] **Step 6: Browser verify narrow viewport**

Use a narrow viewport around `390x844`.

Verify:

- Playback controls remain visible.
- Station map stacks vertically.
- Vehicle tokens do not overflow their cards.
- Timeline scrolls horizontally instead of overlapping text.

- [ ] **Step 7: Verify normal pages still work**

In the same browser:

- Switch to `车主自助`.
- Register or query an existing demo vehicle.
- Switch to `运营管理`.
- Confirm the page loads piles and queues.
- Use `/api/station/snapshot` through the browser network panel or direct URL to confirm the endpoint responds.

- [ ] **Step 8: Commit any QA fixes**

If QA required fixes:

```powershell
cd D:\softe
git add backend/src/main/java/com/bupt/charging/dto/ScenarioDtos.java backend/src/main/java/com/bupt/charging/service/AcceptanceScenarioService.java backend/src/main/java/com/bupt/charging/controller/ScenarioController.java backend/src/main/java/com/bupt/charging/dto/StationDtos.java backend/src/main/java/com/bupt/charging/service/StationSnapshotService.java backend/src/main/java/com/bupt/charging/controller/StationController.java frontend/src/utils/simulationPlayback.js frontend/src/views/SimulationSandbox.vue frontend/src/components/simulation frontend/src/styles.css docs/demo-script.md
git commit -m "fix: polish sandbox verification issues"
```

If QA required no fixes, do not create an empty commit.

---

## Final Completion Checklist

- [ ] `mvn test` passes in `D:\softe\backend`.
- [ ] `npm test` passes in `D:\softe\frontend`.
- [ ] `npm run build` passes in `D:\softe\frontend`.
- [ ] Browser desktop QA passes.
- [ ] Browser narrow viewport QA passes.
- [ ] `rg "AcceptancePanel|测试用例|验收流程|生成演示数据|执行一次调度" frontend/src` has no unwanted top-level UI copy.
- [ ] `git status --short --branch` shows only intentional changes or a clean tree, ignoring `作业验收用例.xlsx` if it remains untracked.
