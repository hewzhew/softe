# Unified Station Runtime and Portal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first real unified station runtime: one station clock drives charging progress and queue advancement, imported course events enter the same event flow, and the frontend becomes separate station, owner, and admin workspaces instead of one stitched tab page.

**Architecture:** Keep the existing Spring Boot + Vue + Element Plus stack. Add a backend `StationClockService`, `StationRuntimeService`, and lightweight `StationEvent` model, then make station snapshot reads advance business state to the current station time. On the frontend, replace top-level tabs with a small role workspace shell, rebuild the owner side around a login/vehicle/request/status/billing state machine, and turn the station page into the place where clock, event import, event stream, and station map live together.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data JPA, H2, JUnit 5, Vue 3, Element Plus, Node built-in test runner, Vite.

---

## Scope

This plan implements the first production-looking slice of the 2026-06-15 spec. It does not implement real authentication, hardware integration, full historical replay, or a complex prediction model. It does implement the parts that matter most for the current demo:

1. One controllable station clock.
2. Business state that advances when station time advances.
3. Automatic charging completion, billing, pile release, dispatch, and next-car start.
4. A normal import path for the course event sequence.
5. Separate role workspaces instead of global tabs.
6. A real owner self-service flow.

## File Structure

Backend files to create:

- `backend/src/main/java/com/bupt/charging/config/TimeProviderConfig.java`
  - Provides the existing `TimeProvider` interface as a Spring bean.
- `backend/src/main/java/com/bupt/charging/domain/StationClock.java`
  - Persists the station clock baseline, rate, running flag, and current window.
- `backend/src/main/java/com/bupt/charging/domain/StationEvent.java`
  - Stores external and system-derived station events.
- `backend/src/main/java/com/bupt/charging/domain/StationEventSourceType.java`
  - Enum for `MANUAL_OPERATION`, `PRESET_OPERATION`, `IMPORTED_SEQUENCE`, `COURSE_PRESET`, `SYSTEM_DERIVED`.
- `backend/src/main/java/com/bupt/charging/domain/StationEventType.java`
  - Enum for `ChargeRequestSubmitted`, `PileFaulted`, `PileRecovered`, `ChargingCompleted`, `BillGenerated`.
- `backend/src/main/java/com/bupt/charging/domain/EventCommitState.java`
  - Enum for `COMMITTED` and `PROVISIONAL`.
- `backend/src/main/java/com/bupt/charging/repository/StationClockRepository.java`
  - JPA repository for the single station clock row.
- `backend/src/main/java/com/bupt/charging/repository/StationEventRepository.java`
  - JPA repository for event import and due-event lookup.
- `backend/src/main/java/com/bupt/charging/dto/RuntimeDtos.java`
  - DTOs for clock, event import, manual events, runtime event rows, and advance requests.
- `backend/src/main/java/com/bupt/charging/service/StationClockService.java`
  - Computes current station time and updates clock controls.
- `backend/src/main/java/com/bupt/charging/service/StationEventService.java`
  - Adds manual events, imports course sample events, clears event batches, and marks events applied.
- `backend/src/main/java/com/bupt/charging/service/StationRuntimeService.java`
  - Advances business state from the last runtime time to the target station time.
- `backend/src/main/java/com/bupt/charging/controller/RuntimeController.java`
  - Exposes `/api/station/clock`, `/api/station/events`, `/api/station/events/import`, and `/api/station/advance`.
- `backend/src/test/java/com/bupt/charging/service/StationClockServiceTest.java`
  - Verifies clock play, pause, rate, and manual set.
- `backend/src/test/java/com/bupt/charging/service/StationRuntimeServiceTest.java`
  - Verifies charging progress, automatic completion, billing, dispatch, and next-car start.
- `backend/src/test/java/com/bupt/charging/service/StationEventServiceTest.java`
  - Verifies course import and future event application.

Backend files to modify:

- `backend/src/main/java/com/bupt/charging/service/ChargingService.java`
  - Use station time for request, mode change, charging start, query progress, and manual end.
- `backend/src/main/java/com/bupt/charging/service/StationSnapshotService.java`
  - Include clock metadata, event summary, and charged progress in snapshots.
- `backend/src/main/java/com/bupt/charging/controller/StationController.java`
  - Advance runtime to the current station time before returning snapshot.
- `backend/src/main/java/com/bupt/charging/service/ConfigService.java`
  - Clear station clock and station events when clearing runtime data.
- `backend/src/main/java/com/bupt/charging/service/AcceptanceScenarioService.java`
  - Expose the course raw event list for reuse by the station event importer.
- `backend/src/main/java/com/bupt/charging/repository/ChargingSessionRepository.java`
  - Add query methods for all active sessions.
- `backend/src/main/java/com/bupt/charging/repository/StationConfigRepository.java`
  - No schema change expected, but used by runtime reset and tests.

Frontend files to create:

- `frontend/src/utils/hashRoute.js`
  - Tiny hash router for `/station`, `/owner`, and `/admin` without adding `vue-router`.
- `frontend/src/utils/hashRoute.test.js`
  - Tests route normalization and role labels.
- `frontend/src/utils/ownerWorkflow.js`
  - Derives owner portal stage from account, vehicle, request state, and bills.
- `frontend/src/utils/ownerWorkflow.test.js`
  - Tests anonymous, no-vehicle, ready, waiting, charging, and completed states.
- `frontend/src/utils/stationClock.js`
  - Formats clock state, rates, event sources, and polling decisions.
- `frontend/src/utils/stationClock.test.js`
  - Tests station clock display helpers.
- `frontend/src/components/shell/WorkspaceShell.vue`
  - Role-aware page shell with sidebar navigation and account switcher.
- `frontend/src/components/owner/OwnerLoginCard.vue`
  - Login and quick demo account entry.
- `frontend/src/components/owner/OwnerVehiclePanel.vue`
  - Vehicle summary and add-vehicle form.
- `frontend/src/components/owner/OwnerRequestPanel.vue`
  - Charging request task flow.
- `frontend/src/components/owner/OwnerStatusPanel.vue`
  - Current queue or charging session view.
- `frontend/src/components/owner/OwnerBillingPanel.vue`
  - Bills and details.
- `frontend/src/components/station/StationClockBar.vue`
  - Play, pause, rate, manual time, event navigation controls.
- `frontend/src/components/station/EventSourcePanel.vue`
  - Add current request, add future request, import sequence, clear runtime data.
- `frontend/src/components/station/RuntimeEventStream.vue`
  - Shows external and derived station events.

Frontend files to modify:

- `frontend/src/App.vue`
  - Replace top-level tabs with workspace shell and hash-route selection.
- `frontend/src/views/OwnerPanel.vue`
  - Rebuild as owner self-service portal using focused owner components.
- `frontend/src/views/SimulationSandbox.vue`
  - Rename visible experience to station runtime console and wire clock/event APIs.
- `frontend/src/views/AdminPanel.vue`
  - Keep existing operations, but make it an operations workspace, not a global tab pane.
- `frontend/src/api/chargingApi.js`
  - Add station clock, event, import, advance, and clear runtime API calls.
- `frontend/src/stores/stationEvents.js`
  - Extend notifications so owner, admin, and station pages refresh together.
- `frontend/src/styles.css`
  - Replace tab-oriented layout styles with role shell, owner portal, station runtime, and responsive constraints.

Docs to modify:

- `docs/demo-script.md`
  - Update the actual classroom demo flow after implementation.
- `docs/environment-setup.md`
  - Add the new station runtime startup and demo reset notes only if commands change.

## Task 1: Station Clock Foundation

**Files:**
- Create: `backend/src/main/java/com/bupt/charging/config/TimeProviderConfig.java`
- Create: `backend/src/main/java/com/bupt/charging/domain/StationClock.java`
- Create: `backend/src/main/java/com/bupt/charging/repository/StationClockRepository.java`
- Create: `backend/src/main/java/com/bupt/charging/dto/RuntimeDtos.java`
- Create: `backend/src/main/java/com/bupt/charging/service/StationClockService.java`
- Create: `backend/src/main/java/com/bupt/charging/controller/RuntimeController.java`
- Test: `backend/src/test/java/com/bupt/charging/service/StationClockServiceTest.java`

- [ ] **Step 1: Write failing clock service tests**

Create `backend/src/test/java/com/bupt/charging/service/StationClockServiceTest.java`:

```java
package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.support.TimeProvider;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:station-clock-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class StationClockServiceTest {
    @Autowired
    private StationClockService stationClockService;

    @Autowired
    private MutableTimeProvider timeProvider;

    @Test
    void pausedClockKeepsManualStationTime() {
        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 0));

        RuntimeDtos.ClockResponse clock = stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                10.0,
                false,
                LocalDateTime.of(2026, 6, 15, 6, 0),
                LocalDateTime.of(2026, 6, 15, 9, 30)
        ));

        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 5));

        RuntimeDtos.ClockResponse current = stationClockService.currentClock();
        assertFalse(current.running());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 0), current.currentTime());
        assertEquals(10.0, current.rate());
    }

    @Test
    void runningClockAdvancesByRate() {
        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 0));

        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 15, 6, 0),
                10.0,
                true,
                null,
                null
        ));

        timeProvider.setNow(LocalDateTime.of(2026, 6, 15, 8, 1));

        RuntimeDtos.ClockResponse current = stationClockService.currentClock();
        assertTrue(current.running());
        assertEquals(LocalDateTime.of(2026, 6, 15, 6, 10), current.currentTime());
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
```

- [ ] **Step 2: Run the failing clock tests**

Run:

```powershell
cd D:\softe\backend
mvn "-Dtest=StationClockServiceTest" test
```

Expected: compilation fails because `StationClockService`, `RuntimeDtos`, and `StationClock` do not exist.

- [ ] **Step 3: Add the TimeProvider bean**

Create `backend/src/main/java/com/bupt/charging/config/TimeProviderConfig.java`:

```java
package com.bupt.charging.config;

import com.bupt.charging.support.TimeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeProviderConfig {
    @Bean
    public TimeProvider timeProvider() {
        return TimeProvider.system();
    }
}
```

- [ ] **Step 4: Add the StationClock entity and repository**

Create `backend/src/main/java/com/bupt/charging/domain/StationClock.java`:

```java
package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class StationClock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime baseWallTime;

    @Column(nullable = false)
    private LocalDateTime baseStationTime;

    @Column(nullable = false)
    private double rate;

    @Column(nullable = false)
    private boolean running;

    private LocalDateTime windowStart;

    private LocalDateTime windowEnd;

    protected StationClock() {
    }

    public StationClock(LocalDateTime baseWallTime, LocalDateTime baseStationTime) {
        this.baseWallTime = baseWallTime;
        this.baseStationTime = baseStationTime;
        this.rate = 1.0;
        this.running = false;
    }

    public void configure(LocalDateTime wallTime, LocalDateTime stationTime, double rate, boolean running,
                          LocalDateTime windowStart, LocalDateTime windowEnd) {
        this.baseWallTime = wallTime;
        this.baseStationTime = stationTime;
        this.rate = rate;
        this.running = running;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getBaseWallTime() {
        return baseWallTime;
    }

    public LocalDateTime getBaseStationTime() {
        return baseStationTime;
    }

    public double getRate() {
        return rate;
    }

    public boolean isRunning() {
        return running;
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }
}
```

Create `backend/src/main/java/com/bupt/charging/repository/StationClockRepository.java`:

```java
package com.bupt.charging.repository;

import com.bupt.charging.domain.StationClock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationClockRepository extends JpaRepository<StationClock, Long> {
    Optional<StationClock> findFirstByOrderByIdAsc();
}
```

- [ ] **Step 5: Add runtime DTOs**

Create `backend/src/main/java/com/bupt/charging/dto/RuntimeDtos.java`:

```java
package com.bupt.charging.dto;

import com.bupt.charging.domain.ChargeMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.List;

public final class RuntimeDtos {
    private RuntimeDtos() {
    }

    public record SetClockRequest(
            LocalDateTime currentTime,
            double rate,
            boolean running,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    ) {
    }

    public record ClockResponse(
            LocalDateTime currentTime,
            double rate,
            boolean running,
            LocalDateTime windowStart,
            LocalDateTime windowEnd
    ) {
    }

    public record AdvanceRequest(LocalDateTime toTime) {
    }

    public record ManualChargeRequestEvent(
            LocalDateTime eventTime,
            @NotBlank String carId,
            String ownerName,
            double carCapacity,
            ChargeMode mode,
            @Positive double requestAmount,
            String sourceName
    ) {
    }

    public record ImportEventsRequest(String sourceType, String sourceName, boolean resetBeforeImport) {
    }

    public record RuntimeEventRow(
            Long id,
            LocalDateTime eventTime,
            String sourceType,
            String sourceName,
            String eventType,
            String targetId,
            String mode,
            double amount,
            boolean applied,
            String rawText
    ) {
    }

    public record ImportEventsResponse(String sourceName, int eventCount, List<RuntimeEventRow> events) {
    }
}
```

- [ ] **Step 6: Implement StationClockService**

Create `backend/src/main/java/com/bupt/charging/service/StationClockService.java`:

```java
package com.bupt.charging.service;

import com.bupt.charging.domain.StationClock;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.repository.StationClockRepository;
import com.bupt.charging.support.TimeProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StationClockService {
    private final StationClockRepository clockRepository;
    private final TimeProvider timeProvider;

    public StationClockService(StationClockRepository clockRepository, TimeProvider timeProvider) {
        this.clockRepository = clockRepository;
        this.timeProvider = timeProvider;
    }

    @Transactional
    public RuntimeDtos.ClockResponse setClock(RuntimeDtos.SetClockRequest request) {
        LocalDateTime wallNow = timeProvider.now();
        LocalDateTime stationTime = request.currentTime() == null ? wallNow : request.currentTime();
        double rate = request.rate() > 0 ? request.rate() : 1.0;
        StationClock clock = loadClock();
        clock.configure(wallNow, stationTime, rate, request.running(), request.windowStart(), request.windowEnd());
        return toResponse(clockRepository.save(clock), wallNow);
    }

    @Transactional
    public RuntimeDtos.ClockResponse play() {
        LocalDateTime stationNow = currentStationTime();
        return setClock(new RuntimeDtos.SetClockRequest(stationNow, currentClock().rate(), true, currentClock().windowStart(), currentClock().windowEnd()));
    }

    @Transactional
    public RuntimeDtos.ClockResponse pause() {
        RuntimeDtos.ClockResponse current = currentClock();
        return setClock(new RuntimeDtos.SetClockRequest(current.currentTime(), current.rate(), false, current.windowStart(), current.windowEnd()));
    }

    @Transactional
    public RuntimeDtos.ClockResponse currentClock() {
        return toResponse(loadClock(), timeProvider.now());
    }

    @Transactional
    public LocalDateTime currentStationTime() {
        return currentClock().currentTime();
    }

    private StationClock loadClock() {
        return clockRepository.findFirstByOrderByIdAsc()
                .orElseGet(() -> clockRepository.save(new StationClock(timeProvider.now(), timeProvider.now())));
    }

    private RuntimeDtos.ClockResponse toResponse(StationClock clock, LocalDateTime wallNow) {
        LocalDateTime stationTime = clock.getBaseStationTime();
        if (clock.isRunning()) {
            long elapsedMillis = Duration.between(clock.getBaseWallTime(), wallNow).toMillis();
            stationTime = stationTime.plusNanos(Math.round(elapsedMillis * clock.getRate()) * 1_000_000L);
        }
        return new RuntimeDtos.ClockResponse(
                stationTime,
                clock.getRate(),
                clock.isRunning(),
                clock.getWindowStart(),
                clock.getWindowEnd()
        );
    }
}
```

- [ ] **Step 7: Add clock endpoints**

Create `backend/src/main/java/com/bupt/charging/controller/RuntimeController.java` with clock endpoints first:

```java
package com.bupt.charging.controller;

import com.bupt.charging.dto.ApiResult;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.service.StationClockService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/station")
public class RuntimeController {
    private final StationClockService stationClockService;

    public RuntimeController(StationClockService stationClockService) {
        this.stationClockService = stationClockService;
    }

    @GetMapping("/clock")
    public ApiResult<RuntimeDtos.ClockResponse> clock() {
        return ApiResult.ok(stationClockService.currentClock());
    }

    @PatchMapping("/clock")
    public ApiResult<RuntimeDtos.ClockResponse> setClock(@Valid @RequestBody RuntimeDtos.SetClockRequest request) {
        return ApiResult.ok(stationClockService.setClock(request));
    }

    @PostMapping("/clock/play")
    public ApiResult<RuntimeDtos.ClockResponse> play() {
        return ApiResult.ok(stationClockService.play());
    }

    @PostMapping("/clock/pause")
    public ApiResult<RuntimeDtos.ClockResponse> pause() {
        return ApiResult.ok(stationClockService.pause());
    }
}
```

- [ ] **Step 8: Verify clock tests pass**

Run:

```powershell
cd D:\softe\backend
mvn "-Dtest=StationClockServiceTest" test
```

Expected: `StationClockServiceTest` passes.

- [ ] **Step 9: Commit station clock foundation**

Run:

```powershell
git add backend/src/main/java/com/bupt/charging/config/TimeProviderConfig.java backend/src/main/java/com/bupt/charging/domain/StationClock.java backend/src/main/java/com/bupt/charging/repository/StationClockRepository.java backend/src/main/java/com/bupt/charging/dto/RuntimeDtos.java backend/src/main/java/com/bupt/charging/service/StationClockService.java backend/src/main/java/com/bupt/charging/controller/RuntimeController.java backend/src/test/java/com/bupt/charging/service/StationClockServiceTest.java
git commit -m "feat: add station clock runtime"
```

## Task 2: Runtime Charging Progress and Automatic Queue Advance

**Files:**
- Modify: `backend/src/main/java/com/bupt/charging/service/ChargingService.java`
- Modify: `backend/src/main/java/com/bupt/charging/service/StationSnapshotService.java`
- Modify: `backend/src/main/java/com/bupt/charging/controller/StationController.java`
- Modify: `backend/src/main/java/com/bupt/charging/repository/ChargingSessionRepository.java`
- Create: `backend/src/main/java/com/bupt/charging/service/StationRuntimeService.java`
- Test: `backend/src/test/java/com/bupt/charging/service/StationRuntimeServiceTest.java`

- [ ] **Step 1: Write failing runtime service test**

Create `backend/src/test/java/com/bupt/charging/service/StationRuntimeServiceTest.java`:

```java
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
}
```

- [ ] **Step 2: Run the failing runtime test**

Run:

```powershell
cd D:\softe\backend
mvn "-Dtest=StationRuntimeServiceTest" test
```

Expected: compilation fails because `StationRuntimeService` does not exist and `queryCarState` currently only returns active requests.

- [ ] **Step 3: Add active session repository queries**

Modify `ChargingSessionRepository.java`:

```java
List<ChargingSession> findByStatusOrderByStartTimeAsc(SessionStatus status);
```

Add the import:

```java
import java.util.List;
```

- [ ] **Step 4: Make ChargingService use station time**

Inject `StationClockService` into `ChargingService`:

```java
private final StationClockService stationClockService;
```

Add it to the constructor parameter list and assignment.

Replace `LocalDateTime.now()` in `submitRequest`, `modifyMode`, and `startCharging` with:

```java
stationClockService.currentStationTime()
```

Add an overload for runtime-controlled charging start:

```java
@Transactional
public void startChargingAt(String carId, String pileId, LocalDateTime startTime) {
    ChargingRequest request = activeRequest(carId);
    if (request.getStatus() != RequestStatus.PILE_QUEUE || !pileId.equals(request.getAssignedPileId())) {
        throw new BusinessException("car is not assigned to this pile");
    }
    if (request.getPileQueuePosition() != 1) {
        throw new BusinessException("car is not first in pile queue");
    }
    ChargingPile pile = pileRepository.findByPileId(pileId)
            .orElseThrow(() -> new BusinessException("pile not found"));
    request.startCharging();
    pile.markWorking(carId);
    sessionRepository.save(new ChargingSession(request.getId(), carId, pileId, startTime));
    requestRepository.save(request);
    pileRepository.save(pile);
}
```

Change existing `startCharging` to:

```java
@Transactional
public void startCharging(String carId, String pileId) {
    startChargingAt(carId, pileId, stationClockService.currentStationTime());
}
```

Update `queryCarState` so it can return the latest finished request:

```java
ChargingRequest request = requestRepository.findFirstByCarIdOrderByRequestTimeDesc(carId)
        .orElseThrow(() -> new BusinessException("request not found"));
```

Keep the `before` calculation only for waiting and pile queue states.

Update `queryChargingState` to calculate visible charged amount:

```java
ChargingPile pile = pileRepository.findByPileId(session.getPileId())
        .orElseThrow(() -> new BusinessException("pile not found"));
LocalDateTime now = stationClockService.currentStationTime();
double elapsedHours = Math.max(0.0, java.time.Duration.between(session.getStartTime(), now).toSeconds() / 3600.0);
double chargedAmount = Math.min(request.getRequestAmount(), elapsedHours * pile.getPower());
```

Return `chargedAmount` instead of `session.getChargeAmount()`.

Also update `StationSnapshotService` so station maps show live charging progress instead of always rendering `0.00`.

Inject:

```java
private final ChargingSessionRepository sessionRepository;
private final StationClockService stationClockService;
```

Add them to the constructor. Replace the `vehicleState` charged amount line with:

```java
format(chargedAmount(request))
```

Add helper:

```java
private double chargedAmount(ChargingRequest request) {
    if (request.getStatus() != RequestStatus.CHARGING || request.getAssignedPileId() == null) {
        return 0.0;
    }
    ChargingPile pile = pileRepository.findByPileId(request.getAssignedPileId()).orElse(null);
    ChargingSession session = sessionRepository.findFirstByCarIdAndStatusOrderByStartTimeDesc(
            request.getCarId(),
            SessionStatus.CHARGING
    ).orElse(null);
    if (pile == null || session == null) {
        return 0.0;
    }
    double elapsedHours = Math.max(0.0, java.time.Duration.between(
            session.getStartTime(),
            stationClockService.currentStationTime()
    ).toSeconds() / 3600.0);
    return Math.min(request.getRequestAmount(), elapsedHours * pile.getPower());
}
```

Add imports:

```java
import com.bupt.charging.domain.ChargingSession;
import com.bupt.charging.domain.SessionStatus;
```

- [ ] **Step 5: Implement StationRuntimeService**

Create `backend/src/main/java/com/bupt/charging/service/StationRuntimeService.java`:

```java
package com.bupt.charging.service;

import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.domain.ChargingSession;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.domain.SessionStatus;
import com.bupt.charging.dto.BillingDtos;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
import com.bupt.charging.repository.ChargingSessionRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StationRuntimeService {
    private final ChargingSessionRepository sessionRepository;
    private final ChargingRequestRepository requestRepository;
    private final ChargingPileRepository pileRepository;
    private final BillingService billingService;
    private final SchedulerService schedulerService;
    private final ChargingService chargingService;

    public StationRuntimeService(
            ChargingSessionRepository sessionRepository,
            ChargingRequestRepository requestRepository,
            ChargingPileRepository pileRepository,
            BillingService billingService,
            SchedulerService schedulerService,
            ChargingService chargingService
    ) {
        this.sessionRepository = sessionRepository;
        this.requestRepository = requestRepository;
        this.pileRepository = pileRepository;
        this.billingService = billingService;
        this.schedulerService = schedulerService;
        this.chargingService = chargingService;
    }

    @Transactional
    public void advanceTo(LocalDateTime targetTime) {
        schedulerService.dispatchAll();
        startIdlePileHeads(targetTime);

        boolean changed;
        do {
            changed = false;
            List<ChargingSession> activeSessions = sessionRepository.findByStatusOrderByStartTimeAsc(SessionStatus.CHARGING);
            for (ChargingSession session : activeSessions) {
                ChargingRequest request = requestRepository.findById(session.getRequestId()).orElse(null);
                ChargingPile pile = pileRepository.findByPileId(session.getPileId()).orElse(null);
                if (request == null || pile == null) {
                    continue;
                }
                LocalDateTime finishTime = finishTime(session, request, pile);
                if (!finishTime.isAfter(targetTime)) {
                    finishSession(session, request, pile, finishTime);
                    schedulerService.dispatchAll();
                    startIdlePileHeads(finishTime);
                    changed = true;
                    break;
                }
            }
        } while (changed);

        schedulerService.dispatchAll();
        startIdlePileHeads(targetTime);
    }

    private LocalDateTime finishTime(ChargingSession session, ChargingRequest request, ChargingPile pile) {
        long seconds = Math.max(1L, Math.round((request.getRequestAmount() / pile.getPower()) * 3600.0));
        return session.getStartTime().plusSeconds(seconds);
    }

    private void finishSession(ChargingSession session, ChargingRequest request, ChargingPile pile, LocalDateTime finishTime) {
        double amount = request.getRequestAmount();
        session.finish(finishTime, amount);
        request.finish();
        pile.addChargingStats(Duration.between(session.getStartTime(), finishTime).toSeconds() / 3600.0, amount);
        pile.release();
        BillingDtos.BillResponse ignored = billingService.createBillForSession(session, pile, amount, finishTime);
        sessionRepository.save(session);
        requestRepository.save(request);
        pileRepository.save(pile);
    }

    private void startIdlePileHeads(LocalDateTime startTime) {
        for (ChargingPile pile : pileRepository.findAll()) {
            if (pile.getCurrentCarId() != null) {
                continue;
            }
            List<ChargingRequest> queue = requestRepository.findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc(
                    pile.getPileId(),
                    RequestStatus.PILE_QUEUE
            );
            if (!queue.isEmpty() && queue.get(0).getPileQueuePosition() == 1) {
                chargingService.startChargingAt(queue.get(0).getCarId(), pile.getPileId(), startTime);
            }
        }
    }
}
```

- [ ] **Step 6: Advance runtime before station snapshots**

Modify `StationController.java` constructor to inject `StationRuntimeService` and `StationClockService`:

```java
private final StationRuntimeService stationRuntimeService;
private final StationClockService stationClockService;
```

Update the constructor:

```java
public StationController(
        StationSnapshotService stationSnapshotService,
        StationRuntimeService stationRuntimeService,
        StationClockService stationClockService
) {
    this.stationSnapshotService = stationSnapshotService;
    this.stationRuntimeService = stationRuntimeService;
    this.stationClockService = stationClockService;
}
```

Update `snapshot()`:

```java
@GetMapping("/snapshot")
public ApiResult<StationDtos.StationSnapshot> snapshot() {
    stationRuntimeService.advanceTo(stationClockService.currentStationTime());
    return ApiResult.ok(stationSnapshotService.currentSnapshot());
}
```

- [ ] **Step 7: Add advance endpoint**

Extend `RuntimeController` constructor with `StationRuntimeService`.

Add:

```java
private final StationRuntimeService stationRuntimeService;
```

Update constructor:

```java
public RuntimeController(StationClockService stationClockService, StationRuntimeService stationRuntimeService) {
    this.stationClockService = stationClockService;
    this.stationRuntimeService = stationRuntimeService;
}
```

Add endpoint:

```java
@PostMapping("/advance")
public ApiResult<RuntimeDtos.ClockResponse> advance(@Valid @RequestBody RuntimeDtos.AdvanceRequest request) {
    stationRuntimeService.advanceTo(request.toTime());
    stationClockService.setClock(new RuntimeDtos.SetClockRequest(request.toTime(), 1.0, false, null, null));
    return ApiResult.ok(stationClockService.currentClock());
}
```

- [ ] **Step 8: Verify runtime tests pass**

Run:

```powershell
cd D:\softe\backend
mvn "-Dtest=StationRuntimeServiceTest,ChargingFlowTest" test
```

Expected: both tests pass. `ChargingFlowTest` proves manual start/end still works.

- [ ] **Step 9: Commit runtime advance**

Run:

```powershell
git add backend/src/main/java/com/bupt/charging/service/ChargingService.java backend/src/main/java/com/bupt/charging/service/StationRuntimeService.java backend/src/main/java/com/bupt/charging/controller/StationController.java backend/src/main/java/com/bupt/charging/controller/RuntimeController.java backend/src/main/java/com/bupt/charging/repository/ChargingSessionRepository.java backend/src/test/java/com/bupt/charging/service/StationRuntimeServiceTest.java
git commit -m "feat: advance station runtime by clock"
```

## Task 3: Station Events and Course Import

**Files:**
- Create: `backend/src/main/java/com/bupt/charging/domain/StationEvent.java`
- Create: `backend/src/main/java/com/bupt/charging/domain/StationEventSourceType.java`
- Create: `backend/src/main/java/com/bupt/charging/domain/StationEventType.java`
- Create: `backend/src/main/java/com/bupt/charging/domain/EventCommitState.java`
- Create: `backend/src/main/java/com/bupt/charging/repository/StationEventRepository.java`
- Create: `backend/src/main/java/com/bupt/charging/service/StationEventService.java`
- Modify: `backend/src/main/java/com/bupt/charging/service/StationRuntimeService.java`
- Modify: `backend/src/main/java/com/bupt/charging/service/AcceptanceScenarioService.java`
- Modify: `backend/src/main/java/com/bupt/charging/service/FaultService.java`
- Modify: `backend/src/main/java/com/bupt/charging/service/ConfigService.java`
- Modify: `backend/src/main/java/com/bupt/charging/controller/RuntimeController.java`
- Test: `backend/src/test/java/com/bupt/charging/service/StationEventServiceTest.java`

- [ ] **Step 1: Write failing station event test**

Create `backend/src/test/java/com/bupt/charging/service/StationEventServiceTest.java`:

```java
package com.bupt.charging.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.dto.ConfigDtos;
import com.bupt.charging.dto.RuntimeDtos;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:station-event-test;MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class StationEventServiceTest {
    @Autowired
    private ConfigService configService;

    @Autowired
    private StationClockService stationClockService;

    @Autowired
    private StationEventService stationEventService;

    @Autowired
    private StationRuntimeService stationRuntimeService;

    @Autowired
    private ChargingService chargingService;

    @Test
    void importedCourseEventsApplyWhenStationTimeReachesEventTime() {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(2, 3, 10, 3, 30.0, 10.0));
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 1, 6, 0),
                1.0,
                false,
                null,
                null
        ));

        RuntimeDtos.ImportEventsResponse response = stationEventService.importCourseSample(true);
        assertEquals(36, response.eventCount());

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 6, 0));
        assertEquals(RequestStatus.CHARGING, chargingService.queryCarState("V1").carState());

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 6, 10));
        assertFalse(stationEventService.events().isEmpty());
        assertEquals(RequestStatus.CHARGING, chargingService.queryCarState("V3").carState());

        stationRuntimeService.advanceTo(LocalDateTime.of(2026, 6, 1, 6, 25));
        assertEquals(RequestStatus.CHARGING, chargingService.queryCarState("V5").carState());
    }
}
```

- [ ] **Step 2: Run the failing station event test**

Run:

```powershell
cd D:\softe\backend
mvn "-Dtest=StationEventServiceTest" test
```

Expected: compilation fails because event domain, repository, and service do not exist.

- [ ] **Step 3: Add station event enums**

Create `StationEventSourceType.java`:

```java
package com.bupt.charging.domain;

public enum StationEventSourceType {
    MANUAL_OPERATION,
    PRESET_OPERATION,
    IMPORTED_SEQUENCE,
    COURSE_PRESET,
    SYSTEM_DERIVED
}
```

Create `StationEventType.java`:

```java
package com.bupt.charging.domain;

public enum StationEventType {
    ChargeRequestSubmitted,
    ChargeRequestCancelled,
    RequestedAmountChanged,
    PileFaulted,
    PileRecovered,
    ChargingCompleted,
    BillGenerated
}
```

Create `EventCommitState.java`:

```java
package com.bupt.charging.domain;

public enum EventCommitState {
    COMMITTED,
    PROVISIONAL
}
```

- [ ] **Step 4: Add station event entity and repository**

Create `StationEvent.java`:

```java
package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class StationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime eventTime;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StationEventSourceType sourceType;

    @Column(nullable = false)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventCommitState commitState;

    @Column(nullable = false)
    private String targetId;

    private String ownerName;

    private Double carCapacity;

    private String mode;

    private Double amount;

    @Column(nullable = false)
    private long sequence;

    @Column(nullable = false)
    private boolean applied;

    @Column(length = 1000)
    private String rawText;

    protected StationEvent() {
    }

    public StationEvent(LocalDateTime eventTime, LocalDateTime receivedAt, StationEventSourceType sourceType,
                        String sourceName, StationEventType eventType, EventCommitState commitState,
                        String targetId, String ownerName, Double carCapacity, String mode, Double amount,
                        long sequence, String rawText) {
        this.eventTime = eventTime;
        this.receivedAt = receivedAt;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.eventType = eventType;
        this.commitState = commitState;
        this.targetId = targetId;
        this.ownerName = ownerName;
        this.carCapacity = carCapacity;
        this.mode = mode;
        this.amount = amount;
        this.sequence = sequence;
        this.rawText = rawText;
    }

    public void markApplied() {
        this.applied = true;
    }

    public Long getId() { return id; }
    public LocalDateTime getEventTime() { return eventTime; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public StationEventSourceType getSourceType() { return sourceType; }
    public String getSourceName() { return sourceName; }
    public StationEventType getEventType() { return eventType; }
    public EventCommitState getCommitState() { return commitState; }
    public String getTargetId() { return targetId; }
    public String getOwnerName() { return ownerName; }
    public Double getCarCapacity() { return carCapacity; }
    public String getMode() { return mode; }
    public Double getAmount() { return amount; }
    public long getSequence() { return sequence; }
    public boolean isApplied() { return applied; }
    public String getRawText() { return rawText; }
}
```

Create `StationEventRepository.java`:

```java
package com.bupt.charging.repository;

import com.bupt.charging.domain.StationEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationEventRepository extends JpaRepository<StationEvent, Long> {
    List<StationEvent> findByAppliedFalseAndEventTimeLessThanEqualOrderByEventTimeAscSequenceAsc(LocalDateTime time);

    List<StationEvent> findAllByOrderByEventTimeAscSequenceAsc();

    long countByEventTime(LocalDateTime eventTime);
}
```

- [ ] **Step 5: Expose course raw events**

Modify `AcceptanceScenarioService.java` by adding:

```java
public List<String> courseSampleRawEvents() {
    return DEFAULT_EVENTS;
}
```

- [ ] **Step 6: Implement StationEventService**

Create `StationEventService.java`:

```java
package com.bupt.charging.service;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.EventCommitState;
import com.bupt.charging.domain.StationEvent;
import com.bupt.charging.domain.StationEventSourceType;
import com.bupt.charging.domain.StationEventType;
import com.bupt.charging.dto.RuntimeDtos;
import com.bupt.charging.repository.StationEventRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StationEventService {
    private static final LocalDate COURSE_DATE = LocalDate.of(2026, 6, 1);

    private final StationEventRepository eventRepository;
    private final AcceptanceScenarioService acceptanceScenarioService;
    private final StationClockService stationClockService;

    public StationEventService(
            StationEventRepository eventRepository,
            AcceptanceScenarioService acceptanceScenarioService,
            StationClockService stationClockService
    ) {
        this.eventRepository = eventRepository;
        this.acceptanceScenarioService = acceptanceScenarioService;
        this.stationClockService = stationClockService;
    }

    @Transactional
    public RuntimeDtos.RuntimeEventRow addManualChargeRequest(RuntimeDtos.ManualChargeRequestEvent request) {
        LocalDateTime eventTime = request.eventTime() == null ? stationClockService.currentStationTime() : request.eventTime();
        long sequence = eventRepository.countByEventTime(eventTime) + 1;
        StationEvent event = eventRepository.save(new StationEvent(
                eventTime,
                stationClockService.currentStationTime(),
                StationEventSourceType.MANUAL_OPERATION,
                request.sourceName() == null || request.sourceName().isBlank() ? "手动添加" : request.sourceName(),
                StationEventType.ChargeRequestSubmitted,
                EventCommitState.COMMITTED,
                request.carId(),
                request.ownerName() == null || request.ownerName().isBlank() ? request.carId() : request.ownerName(),
                request.carCapacity() > 0 ? request.carCapacity() : 80.0,
                request.mode().name(),
                request.requestAmount(),
                sequence,
                "manual"
        ));
        return toRow(event);
    }

    @Transactional
    public RuntimeDtos.ImportEventsResponse importCourseSample(boolean resetBeforeImport) {
        if (resetBeforeImport) {
            eventRepository.deleteAll();
        }
        List<StationEvent> saved = acceptanceScenarioService.courseSampleRawEvents().stream()
                .map(this::courseEvent)
                .map(eventRepository::save)
                .toList();
        return new RuntimeDtos.ImportEventsResponse("课程样例：第二次作业验收用例", saved.size(), saved.stream().map(this::toRow).toList());
    }

    @Transactional(readOnly = true)
    public List<RuntimeDtos.RuntimeEventRow> events() {
        return eventRepository.findAllByOrderByEventTimeAscSequenceAsc().stream().map(this::toRow).toList();
    }

    @Transactional
    public List<StationEvent> dueEvents(LocalDateTime time) {
        return eventRepository.findByAppliedFalseAndEventTimeLessThanEqualOrderByEventTimeAscSequenceAsc(time);
    }

    @Transactional
    public void markApplied(StationEvent event) {
        event.markApplied();
        eventRepository.save(event);
    }

    private StationEvent courseEvent(String raw) {
        String[] parts = raw.split(" ", 2);
        LocalDateTime eventTime = LocalDateTime.of(COURSE_DATE, LocalTime.parse(parts[0]));
        String body = parts[1].substring(1, parts[1].length() - 1);
        String[] fields = body.split(",");
        double amount = Double.parseDouble(fields[3]);
        StationEventType eventType = switch (fields[0]) {
            case "B" -> amount == 0.0 ? StationEventType.PileFaulted : StationEventType.PileRecovered;
            case "C" -> StationEventType.RequestedAmountChanged;
            default -> "O".equals(fields[2]) ? StationEventType.ChargeRequestCancelled : StationEventType.ChargeRequestSubmitted;
        };
        String mode = "F".equals(fields[2]) ? ChargeMode.FAST.name() : "T".equals(fields[2]) ? ChargeMode.SLOW.name() : fields[2];
        return new StationEvent(
                eventTime,
                stationClockService.currentStationTime(),
                StationEventSourceType.COURSE_PRESET,
                "课程样例：第二次作业验收用例",
                eventType,
                EventCommitState.COMMITTED,
                fields[1],
                fields[1],
                120.0,
                mode,
                amount,
                eventRepository.countByEventTime(eventTime) + 1,
                raw
        );
    }

    private RuntimeDtos.RuntimeEventRow toRow(StationEvent event) {
        return new RuntimeDtos.RuntimeEventRow(
                event.getId(),
                event.getEventTime(),
                event.getSourceType().name(),
                event.getSourceName(),
                event.getEventType().name(),
                event.getTargetId(),
                event.getMode(),
                event.getAmount() == null ? 0.0 : event.getAmount(),
                event.isApplied(),
                event.getRawText()
        );
    }
}
```

- [ ] **Step 7: Add station-time cancellation, modification, and fault helpers**

Add to `ChargingService`:

```java
@Transactional
public void cancelRequestAt(String carId, LocalDateTime time) {
    ChargingRequest request = activeRequest(carId);
    if (request.getStatus() == RequestStatus.CHARGING) {
        ChargingSession session = sessionRepository.findFirstByCarIdAndStatusOrderByStartTimeDesc(carId, SessionStatus.CHARGING)
                .orElseThrow(() -> new BusinessException("charging session not found"));
        ChargingPile pile = pileRepository.findByPileId(session.getPileId())
                .orElseThrow(() -> new BusinessException("pile not found"));
        double elapsedHours = Math.max(0.0, java.time.Duration.between(session.getStartTime(), time).toSeconds() / 3600.0);
        double amount = Math.min(request.getRequestAmount(), elapsedHours * pile.getPower());
        session.interrupt(time.isAfter(session.getStartTime()) ? time : session.getStartTime().plusSeconds(1), amount);
        billingService.createBillForSession(session, pile, amount, session.getEndTime());
        pile.release();
        sessionRepository.save(session);
        pileRepository.save(pile);
    }
    request.interrupt();
    requestRepository.save(request);
    schedulerService.dispatchAll();
}

@Transactional
public ChargingDtos.RequestResponse modifyAmountAt(String carId, double amount, LocalDateTime time) {
    ChargingRequest request = activeRequest(carId);
    if (request.getStatus() == RequestStatus.CHARGING) {
        throw new BusinessException("charging request cannot be modified after charging starts");
    }
    if (amount <= 0 || amount > request.getCarCapacity()) {
        throw new BusinessException("invalid request amount");
    }
    request.changeAmount(amount);
    schedulerService.dispatchAll();
    return toRequestResponse(requestRepository.save(request));
}
```

Change existing `modifyAmount` to call `modifyAmountAt(carId, amount, stationClockService.currentStationTime())`.

Modify `FaultService` so manual and imported fault events use station time. Inject `StationClockService` and change existing public methods:

```java
@Transactional
public FaultDtos.FaultResult handleFault(String pileId, String strategy) {
    return handleFaultAt(pileId, strategy, stationClockService.currentStationTime());
}

@Transactional
public FaultDtos.FaultResult handleFaultAt(String pileId, String strategy, LocalDateTime now) {
    ChargingPile faultPile = pileRepository.findByPileId(pileId)
            .orElseThrow(() -> new BusinessException("pile not found"));
    ChargeMode mode = faultPile.getMode();
    FaultRecord faultRecord = faultRecordRepository.save(new FaultRecord(pileId, strategy, now));

    int generatedDetailCount = interruptCurrentSessionIfNeeded(faultPile, now);
    List<ChargingRequest> candidates = collectCandidates(faultPile, strategy);
    List<ChargingRequest> ordered = orderCandidates(candidates, strategy);
    List<String> reorderedCars = ordered.stream().map(ChargingRequest::getCarId).toList();

    for (ChargingRequest request : ordered) {
        request.requeueAfterFault();
    }
    requestRepository.saveAll(ordered);

    List<String> movedCars = reassign(mode, ordered);
    faultPile.markFault();
    pileRepository.save(faultPile);
    faultRecord.updateResult(String.join(",", movedCars));
    faultRecordRepository.save(faultRecord);

    return new FaultDtos.FaultResult(pileId, strategy, movedCars, reorderedCars, generatedDetailCount);
}

@Transactional
public FaultDtos.FaultResult recoverPile(String pileId) {
    return recoverPileAt(pileId, stationClockService.currentStationTime());
}

@Transactional
public FaultDtos.FaultResult recoverPileAt(String pileId, LocalDateTime recoveredAt) {
    ChargingPile pile = pileRepository.findByPileId(pileId)
            .orElseThrow(() -> new BusinessException("pile not found"));
    pile.recover();
    pileRepository.save(pile);
    faultRecordRepository.findFirstByPileIdAndStatusOrderByFaultTimeDesc(pileId, "OPEN")
            .ifPresent(record -> {
                record.close(recoveredAt, "recovered");
                faultRecordRepository.save(record);
            });
    return new FaultDtos.FaultResult(pileId, "RECOVER", List.of(), List.of(), 0);
}
```

- [ ] **Step 8: Apply due events in StationRuntimeService**

Inject `StationEventService`, `AccountService`, and `VehicleRepository` into `StationRuntimeService`.
Also inject `FaultService`.

At the start of `advanceTo`, before `schedulerService.dispatchAll()`, add:

```java
applyDueEvents(targetTime);
```

Add helper:

```java
private void applyDueEvents(LocalDateTime targetTime) {
    for (StationEvent event : stationEventService.dueEvents(targetTime)) {
        switch (event.getEventType()) {
            case ChargeRequestSubmitted -> {
                if (vehicleRepository.findByCarId(event.getTargetId()).isEmpty()) {
                    accountService.createNewAccount(
                            event.getTargetId(),
                            event.getOwnerName() == null ? event.getTargetId() : event.getOwnerName(),
                            event.getCarCapacity() == null ? 120.0 : event.getCarCapacity()
                    );
                }
                chargingService.submitRequestAt(
                        event.getTargetId(),
                        event.getAmount() == null ? 30.0 : event.getAmount(),
                        ChargeMode.valueOf(event.getMode()),
                        event.getEventTime()
                );
            }
            case ChargeRequestCancelled -> chargingService.cancelRequestAt(event.getTargetId(), event.getEventTime());
            case RequestedAmountChanged -> chargingService.modifyAmountAt(event.getTargetId(), event.getAmount(), event.getEventTime());
            case PileFaulted -> faultService.handleFaultAt(event.getTargetId(), "PRIORITY", event.getEventTime());
            case PileRecovered -> faultService.recoverPileAt(event.getTargetId(), event.getEventTime());
            default -> {
            }
        }
        stationEventService.markApplied(event);
    }
}
```

Add `submitRequestAt` to `ChargingService`:

```java
@Transactional
public ChargingDtos.RequestResponse submitRequestAt(String carId, double requestAmount, ChargeMode mode, LocalDateTime requestTime) {
    Vehicle vehicle = vehicleRepository.findByCarId(carId)
            .orElseThrow(() -> new BusinessException("vehicle not found"));
    if (requestAmount <= 0 || requestAmount > vehicle.getCarCapacity()) {
        throw new BusinessException("invalid request amount");
    }
    requestRepository.findFirstByCarIdAndStatusInOrderByRequestTimeDesc(carId, ACTIVE_REQUEST_STATUSES)
            .ifPresent(existing -> {
                throw new BusinessException("car already has active request");
            });
    long sequence = requestRepository.countByMode(mode) + 1;
    ChargingRequest request = requestRepository.save(new ChargingRequest(
            carId,
            vehicle.getCarCapacity(),
            requestAmount,
            mode,
            requestTime,
            queuePrefix(mode) + sequence,
            sequence
    ));
    return toRequestResponse(request);
}
```

Change existing `submitRequest` to call this helper with station time.

- [ ] **Step 9: Add event endpoints**

Extend `RuntimeController` constructor with `StationEventService` and `ConfigService`.

Add:

```java
@GetMapping("/events")
public ApiResult<List<RuntimeDtos.RuntimeEventRow>> events() {
    return ApiResult.ok(stationEventService.events());
}

@PostMapping("/events")
public ApiResult<RuntimeDtos.RuntimeEventRow> addEvent(@Valid @RequestBody RuntimeDtos.ManualChargeRequestEvent request) {
    return ApiResult.ok(stationEventService.addManualChargeRequest(request));
}

@PostMapping("/events/import")
public ApiResult<RuntimeDtos.ImportEventsResponse> importEvents(@RequestBody RuntimeDtos.ImportEventsRequest request) {
    if (request.resetBeforeImport()) {
        configService.resetDemoData();
        configService.initialize(new ConfigDtos.UpdateConfigRequest(2, 3, 10, 3, 30.0, 10.0));
        stationClockService.setClock(new RuntimeDtos.SetClockRequest(
                LocalDateTime.of(2026, 6, 1, 6, 0),
                10.0,
                false,
                LocalDateTime.of(2026, 6, 1, 6, 0),
                LocalDateTime.of(2026, 6, 1, 9, 30)
        ));
    }
    return ApiResult.ok(stationEventService.importCourseSample(request.resetBeforeImport()));
}
```

Add imports:

```java
import com.bupt.charging.dto.ConfigDtos;
import java.time.LocalDateTime;
import java.util.List;
```

- [ ] **Step 10: Clear event tables in ConfigService**

Inject `StationEventRepository` and `StationClockRepository` into `ConfigService`.

In `resetDemoData()`, after deleting fault records and before deleting tariffs/config:

```java
stationEventRepository.deleteAll();
stationClockRepository.deleteAll();
```

- [ ] **Step 11: Verify station event tests pass**

Run:

```powershell
cd D:\softe\backend
mvn "-Dtest=StationEventServiceTest,StationRuntimeServiceTest" test
```

Expected: both tests pass.

- [ ] **Step 12: Commit station events**

Run:

```powershell
git add backend/src/main/java/com/bupt/charging/domain/StationEvent.java backend/src/main/java/com/bupt/charging/domain/StationEventSourceType.java backend/src/main/java/com/bupt/charging/domain/StationEventType.java backend/src/main/java/com/bupt/charging/domain/EventCommitState.java backend/src/main/java/com/bupt/charging/repository/StationEventRepository.java backend/src/main/java/com/bupt/charging/service/StationEventService.java backend/src/main/java/com/bupt/charging/service/StationRuntimeService.java backend/src/main/java/com/bupt/charging/service/AcceptanceScenarioService.java backend/src/main/java/com/bupt/charging/service/FaultService.java backend/src/main/java/com/bupt/charging/service/ConfigService.java backend/src/main/java/com/bupt/charging/service/ChargingService.java backend/src/main/java/com/bupt/charging/controller/RuntimeController.java backend/src/test/java/com/bupt/charging/service/StationEventServiceTest.java
git commit -m "feat: import station event sequences"
```

## Task 4: Frontend API and State Utilities

**Files:**
- Modify: `frontend/src/api/chargingApi.js`
- Create: `frontend/src/utils/hashRoute.js`
- Create: `frontend/src/utils/hashRoute.test.js`
- Create: `frontend/src/utils/ownerWorkflow.js`
- Create: `frontend/src/utils/ownerWorkflow.test.js`
- Create: `frontend/src/utils/stationClock.js`
- Create: `frontend/src/utils/stationClock.test.js`

- [ ] **Step 1: Write route utility tests**

Create `frontend/src/utils/hashRoute.test.js`:

```js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { ROUTES, normalizeRoute, routeLabel } from './hashRoute.js'

describe('hashRoute', () => {
  it('normalizes known workspace routes', () => {
    assert.equal(normalizeRoute('#/owner'), ROUTES.OWNER)
    assert.equal(normalizeRoute('/admin'), ROUTES.ADMIN)
    assert.equal(normalizeRoute('station'), ROUTES.STATION)
  })

  it('falls back to station route', () => {
    assert.equal(normalizeRoute('#/unknown'), ROUTES.STATION)
    assert.equal(normalizeRoute(''), ROUTES.STATION)
  })

  it('labels routes for the workspace shell', () => {
    assert.equal(routeLabel(ROUTES.OWNER), '车主自助')
    assert.equal(routeLabel(ROUTES.ADMIN), '运营管理')
    assert.equal(routeLabel(ROUTES.STATION), '站点运行')
  })
})
```

- [ ] **Step 2: Write owner workflow tests**

Create `frontend/src/utils/ownerWorkflow.test.js`:

```js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { OWNER_STAGES, deriveOwnerStage, ownerPrimaryAction } from './ownerWorkflow.js'

describe('ownerWorkflow', () => {
  it('requires login before showing owner tasks', () => {
    const stage = deriveOwnerStage({ owner: null, vehicle: null, carState: null, bills: [] })
    assert.equal(stage, OWNER_STAGES.ANONYMOUS)
    assert.equal(ownerPrimaryAction(stage), '登录或创建账户')
  })

  it('asks a logged-in owner without vehicle to add one', () => {
    const stage = deriveOwnerStage({ owner: { name: 'Alice' }, vehicle: null, carState: null, bills: [] })
    assert.equal(stage, OWNER_STAGES.NO_VEHICLE)
    assert.equal(ownerPrimaryAction(stage), '添加车辆')
  })

  it('shows request entry when vehicle has no active request', () => {
    const stage = deriveOwnerStage({ owner: { name: 'Alice' }, vehicle: { carId: 'CAR-1' }, carState: null, bills: [] })
    assert.equal(stage, OWNER_STAGES.READY)
    assert.equal(ownerPrimaryAction(stage), '发起充电申请')
  })

  it('maps waiting charging and completed states', () => {
    assert.equal(deriveOwnerStage({ owner: {}, vehicle: {}, carState: { carState: 'WAITING_AREA' }, bills: [] }), OWNER_STAGES.WAITING)
    assert.equal(deriveOwnerStage({ owner: {}, vehicle: {}, carState: { carState: 'CHARGING' }, bills: [] }), OWNER_STAGES.CHARGING)
    assert.equal(deriveOwnerStage({ owner: {}, vehicle: {}, carState: { carState: 'FINISHED' }, bills: [] }), OWNER_STAGES.COMPLETED)
  })
})
```

- [ ] **Step 3: Write station clock helper tests**

Create `frontend/src/utils/stationClock.test.js`:

```js
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { CLOCK_RATES, formatClockStatus, formatRuntimeEvent, shouldPollRuntime } from './stationClock.js'

describe('stationClock', () => {
  it('formats running and paused clock states', () => {
    assert.equal(formatClockStatus({ running: true, rate: 10 }), '运行中 · 10x')
    assert.equal(formatClockStatus({ running: false, rate: 1 }), '已暂停 · 1x')
  })

  it('exposes supported rates', () => {
    assert.deepEqual(CLOCK_RATES, [1, 5, 10, 60])
  })

  it('polls while running', () => {
    assert.equal(shouldPollRuntime({ running: true }), true)
    assert.equal(shouldPollRuntime({ running: false }), false)
  })

  it('formats event rows', () => {
    assert.equal(formatRuntimeEvent({ eventTime: '2026-06-15T06:00:00', targetId: 'CAR-1', eventType: 'ChargeRequestSubmitted' }), '06:00:00 CAR-1 ChargeRequestSubmitted')
  })
})
```

- [ ] **Step 4: Run failing frontend utility tests**

Run:

```powershell
cd D:\softe\frontend
npm test -- src/utils/hashRoute.test.js src/utils/ownerWorkflow.test.js src/utils/stationClock.test.js
```

Expected: imports fail because the utilities do not exist.

- [ ] **Step 5: Implement hash route utility**

Create `frontend/src/utils/hashRoute.js`:

```js
export const ROUTES = Object.freeze({
  STATION: '/station',
  OWNER: '/owner',
  ADMIN: '/admin'
})

const LABELS = Object.freeze({
  [ROUTES.STATION]: '站点运行',
  [ROUTES.OWNER]: '车主自助',
  [ROUTES.ADMIN]: '运营管理'
})

export function normalizeRoute(value) {
  const normalized = String(value || '')
    .replace(/^#/, '')
    .replace(/^\//, '')
    .trim()
  const route = `/${normalized || 'station'}`
  return Object.values(ROUTES).includes(route) ? route : ROUTES.STATION
}

export function routeLabel(route) {
  return LABELS[normalizeRoute(route)] || LABELS[ROUTES.STATION]
}

export function setHashRoute(route) {
  window.location.hash = normalizeRoute(route)
}
```

- [ ] **Step 6: Implement owner workflow utility**

Create `frontend/src/utils/ownerWorkflow.js`:

```js
export const OWNER_STAGES = Object.freeze({
  ANONYMOUS: 'Anonymous',
  NO_VEHICLE: 'OwnerNoVehicle',
  READY: 'OwnerReady',
  WAITING: 'OwnerWaiting',
  CHARGING: 'OwnerCharging',
  COMPLETED: 'OwnerCompleted'
})

export function deriveOwnerStage({ owner, vehicle, carState, bills = [] } = {}) {
  if (!owner) {
    return OWNER_STAGES.ANONYMOUS
  }
  if (!vehicle) {
    return OWNER_STAGES.NO_VEHICLE
  }
  if (carState?.carState === 'WAITING_AREA' || carState?.carState === 'PILE_QUEUE') {
    return OWNER_STAGES.WAITING
  }
  if (carState?.carState === 'CHARGING') {
    return OWNER_STAGES.CHARGING
  }
  if (carState?.carState === 'FINISHED' || bills.length > 0) {
    return OWNER_STAGES.COMPLETED
  }
  return OWNER_STAGES.READY
}

export function ownerPrimaryAction(stage) {
  const labels = {
    [OWNER_STAGES.ANONYMOUS]: '登录或创建账户',
    [OWNER_STAGES.NO_VEHICLE]: '添加车辆',
    [OWNER_STAGES.READY]: '发起充电申请',
    [OWNER_STAGES.WAITING]: '查看排队状态',
    [OWNER_STAGES.CHARGING]: '查看充电进度',
    [OWNER_STAGES.COMPLETED]: '查看账单'
  }
  return labels[stage] || labels[OWNER_STAGES.ANONYMOUS]
}
```

- [ ] **Step 7: Implement station clock utility**

Create `frontend/src/utils/stationClock.js`:

```js
export const CLOCK_RATES = Object.freeze([1, 5, 10, 60])

export function formatClockStatus(clock = {}) {
  const rate = Number.isFinite(Number(clock.rate)) ? Number(clock.rate) : 1
  return `${clock.running ? '运行中' : '已暂停'} · ${rate}x`
}

export function shouldPollRuntime(clock = {}) {
  return Boolean(clock.running)
}

export function formatRuntimeEvent(event = {}) {
  const time = String(event.eventTime || '').replace('T', ' ').slice(11, 19) || '--:--:--'
  return `${time} ${event.targetId || '-'} ${event.eventType || '-'}`
}
```

- [ ] **Step 8: Extend API client**

Add to `frontend/src/api/chargingApi.js`:

```js
  getStationClock: () => unwrap(http.get('/station/clock')),
  setStationClock: (payload) => unwrap(http.patch('/station/clock', payload)),
  playStationClock: () => unwrap(http.post('/station/clock/play')),
  pauseStationClock: () => unwrap(http.post('/station/clock/pause')),
  advanceStation: (payload) => unwrap(http.post('/station/advance', payload)),
  getStationEvents: () => unwrap(http.get('/station/events')),
  addStationEvent: (payload) => unwrap(http.post('/station/events', payload)),
  importStationEvents: (payload) => unwrap(http.post('/station/events/import', payload)),
```

- [ ] **Step 9: Verify frontend utility tests pass**

Run:

```powershell
cd D:\softe\frontend
npm test -- src/utils/hashRoute.test.js src/utils/ownerWorkflow.test.js src/utils/stationClock.test.js
```

Expected: utility tests pass.

- [ ] **Step 10: Commit frontend utilities**

Run:

```powershell
git add frontend/src/api/chargingApi.js frontend/src/utils/hashRoute.js frontend/src/utils/hashRoute.test.js frontend/src/utils/ownerWorkflow.js frontend/src/utils/ownerWorkflow.test.js frontend/src/utils/stationClock.js frontend/src/utils/stationClock.test.js
git commit -m "feat: add runtime frontend state utilities"
```

## Task 5: Replace Global Tabs With Role Workspace Shell

**Files:**
- Create: `frontend/src/components/shell/WorkspaceShell.vue`
- Modify: `frontend/src/App.vue`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Create workspace shell component**

Create `frontend/src/components/shell/WorkspaceShell.vue`:

```vue
<template>
  <el-container class="workspace-shell">
    <el-aside class="workspace-sidebar" width="230px">
      <div class="workspace-brand">
        <strong>波普特大学充电站</strong>
        <span>调度计费系统</span>
      </div>

      <nav class="workspace-nav">
        <button
          v-for="item in items"
          :key="item.route"
          type="button"
          :class="{ active: item.route === activeRoute }"
          @click="$emit('navigate', item.route)"
        >
          <span>{{ item.label }}</span>
          <small>{{ item.description }}</small>
        </button>
      </nav>
    </el-aside>

    <el-container>
      <el-header class="workspace-header">
        <div>
          <p class="eyebrow">{{ routeLabel(activeRoute) }}</p>
          <h1>{{ title }}</h1>
        </div>
        <el-tag effect="plain">H2 本机数据</el-tag>
      </el-header>
      <el-main class="workspace-main">
        <slot />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { ROUTES, routeLabel } from '../../utils/hashRoute'

const props = defineProps({
  activeRoute: { type: String, default: ROUTES.STATION }
})

defineEmits(['navigate'])

const items = [
  { route: ROUTES.STATION, label: '站点运行', description: '时钟、事件流、站点地图' },
  { route: ROUTES.OWNER, label: '车主自助', description: '车辆、申请、状态、账单' },
  { route: ROUTES.ADMIN, label: '运营管理', description: '队列、充电桩、故障' }
]

const title = computed(() => {
  if (props.activeRoute === ROUTES.OWNER) {
    return '车主自助门户'
  }
  if (props.activeRoute === ROUTES.ADMIN) {
    return '运营管理工作台'
  }
  return '站点运行控制台'
})
</script>
```

- [ ] **Step 2: Replace App.vue tabs**

Replace `frontend/src/App.vue` with:

```vue
<template>
  <WorkspaceShell :active-route="activeRoute" @navigate="navigate">
    <SimulationSandbox v-if="activeRoute === ROUTES.STATION" />
    <OwnerPanel v-else-if="activeRoute === ROUTES.OWNER" />
    <AdminPanel v-else />
  </WorkspaceShell>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from './components/shell/WorkspaceShell.vue'
import SimulationSandbox from './views/SimulationSandbox.vue'
import OwnerPanel from './views/OwnerPanel.vue'
import AdminPanel from './views/AdminPanel.vue'
import { ROUTES, normalizeRoute, setHashRoute } from './utils/hashRoute'

const activeRoute = ref(normalizeRoute(window.location.hash))

function syncRoute() {
  activeRoute.value = normalizeRoute(window.location.hash)
}

function navigate(route) {
  setHashRoute(route)
  syncRoute()
}

onMounted(() => {
  if (!window.location.hash) {
    setHashRoute(ROUTES.STATION)
  }
  window.addEventListener('hashchange', syncRoute)
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncRoute)
})
</script>
```

- [ ] **Step 3: Add shell CSS**

In `frontend/src/styles.css`, replace `.app-shell`, `.app-header`, `.main-tabs`, and generic `.el-main` tab-oriented rules with:

```css
.workspace-shell {
  min-height: 100vh;
}

.workspace-sidebar {
  border-right: 1px solid #dbe3ec;
  background: #10253a;
  color: #fff;
}

.workspace-brand {
  display: grid;
  gap: 4px;
  padding: 22px 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.workspace-brand strong {
  font-size: 18px;
  font-weight: 700;
}

.workspace-brand span {
  color: #b8c7d6;
  font-size: 12px;
}

.workspace-nav {
  display: grid;
  gap: 8px;
  padding: 14px;
}

.workspace-nav button {
  display: grid;
  gap: 4px;
  width: 100%;
  padding: 12px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #d8e4ef;
  cursor: pointer;
  text-align: left;
}

.workspace-nav button.active {
  border-color: #6fb3ff;
  background: #193a59;
  color: #fff;
}

.workspace-nav small {
  color: #aebfd0;
  font-size: 12px;
}

.workspace-header {
  height: 78px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  border-bottom: 1px solid #dbe3ec;
  background: #fff;
}

.workspace-header h1 {
  margin: 2px 0 0;
  color: #172033;
  font-size: 22px;
  font-weight: 700;
}

.workspace-main {
  max-width: 1480px;
  width: 100%;
  margin: 0 auto;
  padding: 24px;
}
```

Add mobile behavior:

```css
@media (max-width: 900px) {
  .workspace-shell {
    display: block;
  }

  .workspace-sidebar {
    width: 100% !important;
  }

  .workspace-nav {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .workspace-main {
    padding: 14px;
  }
}
```

- [ ] **Step 4: Build frontend**

Run:

```powershell
cd D:\softe\frontend
npm run build
```

Expected: build succeeds and no top-level `el-tabs` remains in `App.vue`.

- [ ] **Step 5: Commit role shell**

Run:

```powershell
git add frontend/src/components/shell/WorkspaceShell.vue frontend/src/App.vue frontend/src/styles.css
git commit -m "feat: replace tabs with role workspace shell"
```

## Task 6: Rebuild Owner Self-Service Portal

**Files:**
- Create: `frontend/src/components/owner/OwnerLoginCard.vue`
- Create: `frontend/src/components/owner/OwnerVehiclePanel.vue`
- Create: `frontend/src/components/owner/OwnerRequestPanel.vue`
- Create: `frontend/src/components/owner/OwnerStatusPanel.vue`
- Create: `frontend/src/components/owner/OwnerBillingPanel.vue`
- Modify: `frontend/src/views/OwnerPanel.vue`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Create owner login card**

Create `OwnerLoginCard.vue`:

```vue
<template>
  <el-card class="owner-portal-card" shadow="never">
    <div class="card-heading">
      <div>
        <p class="eyebrow">账户入口</p>
        <h2>登录车主自助门户</h2>
      </div>
    </div>

    <el-form label-width="82px">
      <el-form-item label="车辆编号">
        <el-input :model-value="carId" placeholder="如 CAR-1" @update:model-value="$emit('update:carId', $event)" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input :model-value="password" show-password @update:model-value="$emit('update:password', $event)" />
      </el-form-item>
      <div class="action-row">
        <el-button type="primary" @click="$emit('login')">登录</el-button>
        <el-button @click="$emit('quickLogin')">使用示例账户</el-button>
      </div>
    </el-form>
  </el-card>
</template>

<script setup>
defineProps({
  carId: { type: String, default: '' },
  password: { type: String, default: '' }
})

defineEmits(['update:carId', 'update:password', 'login', 'quickLogin'])
</script>
```

- [ ] **Step 2: Create vehicle panel**

Create `OwnerVehiclePanel.vue`:

```vue
<template>
  <el-card class="owner-portal-card" shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">我的车辆</p>
        <h2>{{ vehicle?.carId || '尚未登记车辆' }}</h2>
      </div>
      <el-tag v-if="vehicle" effect="plain">{{ formatKwh(vehicle.carCapacity) }}</el-tag>
    </div>

    <el-form v-if="!vehicle" label-width="92px">
      <el-form-item label="车辆编号">
        <el-input :model-value="form.carId" placeholder="如 CAR-1" @update:model-value="$emit('update:carId', $event)" />
      </el-form-item>
      <el-form-item label="车主姓名">
        <el-input :model-value="form.userName" placeholder="如 Alice" @update:model-value="$emit('update:userName', $event)" />
      </el-form-item>
      <el-form-item label="电池容量">
        <el-input-number :model-value="form.carCapacity" :min="1" :max="200" @update:model-value="$emit('update:carCapacity', $event)" />
      </el-form-item>
      <el-button type="primary" @click="$emit('createVehicle')">添加车辆</el-button>
    </el-form>

    <el-descriptions v-else :column="3" border>
      <el-descriptions-item label="车辆">{{ vehicle.carId }}</el-descriptions-item>
      <el-descriptions-item label="车主">{{ owner?.name || '-' }}</el-descriptions-item>
      <el-descriptions-item label="容量">{{ formatKwh(vehicle.carCapacity) }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup>
import { formatKwh } from '../../utils/display'

defineProps({
  owner: { type: Object, default: null },
  vehicle: { type: Object, default: null },
  form: { type: Object, required: true }
})

defineEmits(['update:carId', 'update:userName', 'update:carCapacity', 'createVehicle'])
</script>
```

- [ ] **Step 3: Create request panel**

Create `OwnerRequestPanel.vue`:

```vue
<template>
  <el-card class="owner-portal-card" shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">充电申请</p>
        <h2>发起一次充电</h2>
      </div>
    </div>

    <el-form label-width="92px">
      <el-form-item label="车辆">
        <el-input :model-value="vehicle?.carId || '-'" disabled />
      </el-form-item>
      <el-form-item label="充电模式">
        <el-segmented :model-value="form.mode" :options="modeOptions" @update:model-value="$emit('update:mode', $event)" />
      </el-form-item>
      <el-form-item label="申请电量">
        <el-input-number :model-value="form.requestAmount" :min="1" :max="200" @update:model-value="$emit('update:requestAmount', $event)" />
      </el-form-item>
      <el-alert title="提交后将进入当前站点队列，系统会根据站点时钟自动推进充电状态。" type="info" :closable="false" />
      <div class="action-row request-actions">
        <el-button type="primary" :disabled="!vehicle" @click="$emit('submitRequest')">确认提交</el-button>
      </div>
    </el-form>
  </el-card>
</template>

<script setup>
defineProps({
  vehicle: { type: Object, default: null },
  form: { type: Object, required: true }
})

defineEmits(['update:mode', 'update:requestAmount', 'submitRequest'])

const modeOptions = [
  { label: '快充', value: 'FAST' },
  { label: '慢充', value: 'SLOW' }
]
</script>
```

- [ ] **Step 4: Create status panel**

Create `OwnerStatusPanel.vue`:

```vue
<template>
  <el-card class="owner-portal-card" shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">当前状态</p>
        <h2>{{ title }}</h2>
      </div>
      <StatusTag v-if="carState?.carState" :value="carState.carState" />
    </div>

    <el-alert :title="nextOwnerAction(carState)" type="info" :closable="false" show-icon />

    <div class="metric-grid owner-state-grid">
      <div class="metric-item">
        <span>排队号</span>
        <strong>{{ carState?.queueNum || '-' }}</strong>
      </div>
      <div class="metric-item">
        <span>前方车辆</span>
        <strong>{{ carState?.carNumberBeforePosition ?? '-' }}</strong>
      </div>
      <div class="metric-item">
        <span>充电桩</span>
        <strong>{{ carState?.assignedPileId || '-' }}</strong>
      </div>
    </div>

    <div class="action-row">
      <el-button @click="$emit('refresh')">刷新状态</el-button>
      <el-button type="primary" :disabled="!canStart" @click="$emit('startCharging')">开始充电</el-button>
      <el-button type="success" :disabled="!canEnd" @click="$emit('endCharging')">结束并结算</el-button>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import StatusTag from '../StatusTag.vue'
import { nextOwnerAction } from '../../utils/display'

const props = defineProps({
  carState: { type: Object, default: null }
})

defineEmits(['refresh', 'startCharging', 'endCharging'])

const title = computed(() => props.carState?.carState ? '本次充电服务' : '暂无进行中的服务')
const canStart = computed(() => props.carState?.carState === 'PILE_QUEUE' && (props.carState?.carNumberBeforePosition ?? 0) === 0)
const canEnd = computed(() => props.carState?.carState === 'CHARGING')
</script>
```

- [ ] **Step 5: Create billing panel**

Create `OwnerBillingPanel.vue`:

```vue
<template>
  <el-card class="owner-portal-card" shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">账单记录</p>
        <h2>历史账单</h2>
      </div>
      <el-button size="small" @click="$emit('refreshBills')">刷新</el-button>
    </div>

    <el-table :data="bills" height="260" border empty-text="暂无账单">
      <el-table-column prop="billId" label="账单号" width="90" />
      <el-table-column prop="date" label="日期" width="120" />
      <el-table-column prop="pileId" label="充电桩" width="100" />
      <el-table-column label="总费用" width="120">
        <template #default="{ row }">{{ formatCurrency(row.totalFee) }}</template>
      </el-table-column>
      <el-table-column label="操作">
        <template #default="{ row }">
          <el-button size="small" text @click="$emit('loadDetails', row)">查看详单</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { formatCurrency } from '../../utils/display'

defineProps({
  bills: { type: Array, default: () => [] }
})

defineEmits(['refreshBills', 'loadDetails'])
</script>
```

- [ ] **Step 6: Replace OwnerPanel**

Replace `frontend/src/views/OwnerPanel.vue` with a portal that composes the new components:

```vue
<template>
  <div class="owner-portal">
    <OwnerLoginCard
      v-if="stage === OWNER_STAGES.ANONYMOUS"
      v-model:car-id="ownerForm.carId"
      v-model:password="ownerForm.password"
      @login="login"
      @quick-login="quickLogin"
    />

    <template v-else>
      <section class="owner-hero">
        <div>
          <p class="eyebrow">车主自助</p>
          <h2>{{ owner?.name || ownerForm.userName }}</h2>
          <span>{{ ownerPrimaryAction(stage) }}</span>
        </div>
        <el-button @click="logout">退出</el-button>
      </section>

      <OwnerVehiclePanel
        :owner="owner"
        :vehicle="vehicle"
        :form="ownerForm"
        v-model:car-id="ownerForm.carId"
        v-model:user-name="ownerForm.userName"
        v-model:car-capacity="ownerForm.carCapacity"
        @create-vehicle="createVehicle"
      />

      <OwnerRequestPanel
        v-if="stage === OWNER_STAGES.READY"
        :vehicle="vehicle"
        :form="ownerForm"
        @update:mode="ownerForm.mode = $event"
        @update:request-amount="ownerForm.requestAmount = $event"
        @submit-request="submitRequest"
      />

      <OwnerStatusPanel
        v-if="[OWNER_STAGES.WAITING, OWNER_STAGES.CHARGING, OWNER_STAGES.COMPLETED].includes(stage)"
        :car-state="carState"
        @refresh="queryState"
        @start-charging="startCharging"
        @end-charging="endCharging"
      />

      <OwnerBillingPanel
        :bills="bills"
        @refresh-bills="queryBills"
        @load-details="loadDetails"
      />
    </template>
  </div>
</template>
```

Use the existing action methods from the old `OwnerPanel.vue`, but change state:

```js
const owner = ref(null)
const vehicle = ref(null)

const stage = computed(() => deriveOwnerStage({
  owner: owner.value,
  vehicle: vehicle.value,
  carState,
  bills: bills.value
}))

function login() {
  owner.value = { name: ownerForm.userName || '车主用户' }
  vehicle.value = ownerForm.carId ? { carId: ownerForm.carId, carCapacity: ownerForm.carCapacity } : null
  queryState({ silent: true })
  queryBills()
}

function quickLogin() {
  ownerForm.carId = ownerForm.carId || 'CAR-1'
  ownerForm.userName = ownerForm.userName || 'Alice'
  ownerForm.password = ownerForm.password || '123456'
  login()
}

function logout() {
  owner.value = null
  vehicle.value = null
  clearOwnerState()
}

async function createVehicle() {
  const account = await runAction(() => api.createAccount(ownerForm), '车辆已添加')
  if (account) {
    owner.value = { name: account.userName || ownerForm.userName }
    vehicle.value = { carId: account.carId || ownerForm.carId, carCapacity: account.carCapacity || ownerForm.carCapacity }
  }
}
```

Keep `submitRequest`, `queryState`, `startCharging`, `endCharging`, `queryBills`, and `loadDetails` behavior from the existing file, but after successful submit call:

```js
Object.assign(carState, result)
await queryState({ silent: true })
```

- [ ] **Step 7: Add owner portal CSS**

Add to `frontend/src/styles.css`:

```css
.owner-portal {
  display: grid;
  gap: 16px;
}

.owner-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  border: 1px solid #dbe3ec;
  border-radius: 6px;
  background: #fff;
}

.owner-hero h2 {
  margin: 2px 0 4px;
  color: #172033;
  font-size: 22px;
  font-weight: 700;
}

.owner-hero span {
  color: #64748b;
  font-size: 13px;
}

.owner-portal-card {
  border-radius: 6px;
}

.request-actions {
  margin-top: 14px;
}

.owner-state-grid {
  margin: 14px 0;
}
```

- [ ] **Step 8: Verify frontend tests and build**

Run:

```powershell
cd D:\softe\frontend
npm test -- src/utils/ownerWorkflow.test.js
npm run build
```

Expected: owner workflow tests pass and Vite build succeeds.

- [ ] **Step 9: Commit owner portal**

Run:

```powershell
git add frontend/src/components/owner/OwnerLoginCard.vue frontend/src/components/owner/OwnerVehiclePanel.vue frontend/src/components/owner/OwnerRequestPanel.vue frontend/src/components/owner/OwnerStatusPanel.vue frontend/src/components/owner/OwnerBillingPanel.vue frontend/src/views/OwnerPanel.vue frontend/src/styles.css
git commit -m "feat: rebuild owner self service portal"
```

## Task 7: Station Runtime Console UI

**Files:**
- Create: `frontend/src/components/station/StationClockBar.vue`
- Create: `frontend/src/components/station/EventSourcePanel.vue`
- Create: `frontend/src/components/station/RuntimeEventStream.vue`
- Modify: `frontend/src/views/SimulationSandbox.vue`
- Modify: `frontend/src/components/simulation/StationMap.vue`
- Modify: `frontend/src/stores/stationEvents.js`
- Modify: `frontend/src/styles.css`

- [ ] **Step 1: Create StationClockBar**

Create `StationClockBar.vue`:

```vue
<template>
  <el-card class="station-clock-bar" shadow="never">
    <div class="clock-main">
      <div>
        <p class="eyebrow">站点时钟</p>
        <h2>{{ displayTime }}</h2>
        <span>{{ formatClockStatus(clock) }}</span>
      </div>
      <div class="clock-actions">
        <el-button :type="clock?.running ? 'warning' : 'primary'" @click="$emit(clock?.running ? 'pause' : 'play')">
          {{ clock?.running ? '暂停' : '开始' }}
        </el-button>
        <el-select :model-value="clock?.rate || 1" style="width: 96px" @update:model-value="$emit('rate', $event)">
          <el-option v-for="rate in CLOCK_RATES" :key="rate" :label="`${rate}x`" :value="rate" />
        </el-select>
        <el-date-picker
          :model-value="clock?.currentTime"
          type="datetime"
          value-format="YYYY-MM-DDTHH:mm:ss"
          placeholder="指定站点时间"
          @update:model-value="$emit('time', $event)"
        />
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { CLOCK_RATES, formatClockStatus } from '../../utils/stationClock'
import { formatDateTime } from '../../utils/display'

const props = defineProps({
  clock: { type: Object, default: null }
})

defineEmits(['play', 'pause', 'rate', 'time'])

const displayTime = computed(() => formatDateTime(props.clock?.currentTime))
</script>
```

- [ ] **Step 2: Create EventSourcePanel**

Create `EventSourcePanel.vue`:

```vue
<template>
  <el-card class="event-source-panel" shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">事件来源</p>
        <h2>导入与添加</h2>
      </div>
    </div>

    <div class="action-row">
      <el-button type="primary" @click="$emit('addCurrent')">添加当前请求</el-button>
      <el-button @click="$emit('addFuture')">添加未来请求</el-button>
      <el-button @click="$emit('importCourse')">导入课程样例</el-button>
      <el-button type="danger" plain @click="$emit('clear')">清空运行数据</el-button>
    </div>
  </el-card>
</template>

<script setup>
defineEmits(['addCurrent', 'addFuture', 'importCourse', 'clear'])
</script>
```

- [ ] **Step 3: Create RuntimeEventStream**

Create `RuntimeEventStream.vue`:

```vue
<template>
  <el-card class="runtime-event-stream" shadow="never">
    <div class="card-heading compact">
      <div>
        <p class="eyebrow">事件流</p>
        <h2>站点事件</h2>
      </div>
      <el-tag effect="plain">{{ events.length }}</el-tag>
    </div>

    <div class="runtime-event-list">
      <div v-for="event in events" :key="event.id" class="runtime-event-row">
        <strong>{{ formatRuntimeEvent(event) }}</strong>
        <span>{{ event.sourceName || event.sourceType }} · {{ event.applied ? '已应用' : '待应用' }}</span>
      </div>
      <div v-if="events.length === 0" class="sandbox-empty">暂无事件</div>
    </div>
  </el-card>
</template>

<script setup>
import { formatRuntimeEvent } from '../../utils/stationClock'

defineProps({
  events: { type: Array, default: () => [] }
})
</script>
```

- [ ] **Step 4: Wire station runtime console**

In `SimulationSandbox.vue`, move away from `RuntimeModeSwitch`. The station page should directly show the unified runtime controls:

```vue
<template>
  <div class="station-runtime-console">
    <StationClockBar
      :clock="clock"
      @play="playClock"
      @pause="pauseClock"
      @rate="setRate"
      @time="setStationTime"
    />

    <div class="station-runtime-grid">
      <aside class="station-runtime-side">
        <EventSourcePanel
          @add-current="addCurrentRequest"
          @add-future="addFutureRequest"
          @import-course="importCourseEvents"
          @clear="clearRuntimeData"
        />
        <RuntimeEventStream :events="runtimeEvents" />
      </aside>

      <main class="station-runtime-main">
        <StationMap :snapshot="liveSnapshot" mode="LIVE" />
        <VerificationPanel
          v-if="courseBundle"
          :checks="courseBundle.checks || []"
          :table-rows="courseBundle.tableRows || []"
          @copy="copyRows"
        />
      </main>
    </div>
  </div>
</template>
```

Keep the existing course replay copy helper, but use it only for optional course checking after importing course events.

Add script state:

```js
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { notifyStationChanged } from '../stores/stationEvents'

const clock = ref(null)
const runtimeEvents = ref([])
const liveSnapshot = ref(null)
const courseBundle = ref(null)
const runtimeTimerId = ref(null)
```

Add loaders:

```js
async function refreshRuntime() {
  clock.value = await api.getStationClock()
  liveSnapshot.value = await api.getStationSnapshot()
  runtimeEvents.value = await api.getStationEvents()
}
```

Add clock actions:

```js
async function playClock() {
  clock.value = await api.playStationClock()
  startRuntimePolling()
}

async function pauseClock() {
  clock.value = await api.pauseStationClock()
  stopRuntimePolling()
  await refreshRuntime()
}

async function setRate(rate) {
  clock.value = await api.setStationClock({ ...clock.value, rate, running: clock.value?.running ?? false })
}

async function setStationTime(currentTime) {
  clock.value = await api.setStationClock({ ...clock.value, currentTime, running: false })
  await refreshRuntime()
}
```

Add event source actions:

```js
async function importCourseEvents() {
  await api.importStationEvents({
    sourceType: 'COURSE_PRESET',
    sourceName: '课程样例：第二次作业验收用例',
    resetBeforeImport: true
  })
  courseBundle.value = await api.runCourseScenario()
  await api.setStationClock({
    currentTime: '2026-06-01T06:00:00',
    rate: 10,
    running: false,
    windowStart: '2026-06-01T06:00:00',
    windowEnd: '2026-06-01T09:30:00'
  })
  await refreshRuntime()
}

async function addCurrentRequest() {
  await api.addStationEvent({
    carId: `CAR-${Date.now().toString().slice(-4)}`,
    ownerName: '临时车主',
    carCapacity: 80,
    mode: 'FAST',
    requestAmount: 30,
    sourceName: '手动添加'
  })
  await refreshRuntime()
}

async function addFutureRequest() {
  const currentTime = clock.value?.currentTime || new Date().toISOString().slice(0, 19)
  await api.addStationEvent({
    eventTime: currentTime,
    carId: `CAR-${Date.now().toString().slice(-4)}`,
    ownerName: '计划订单',
    carCapacity: 80,
    mode: 'SLOW',
    requestAmount: 20,
    sourceName: '计划订单'
  })
  await refreshRuntime()
}

async function clearRuntimeData() {
  await api.resetDemo()
  courseBundle.value = null
  stopRuntimePolling()
  notifyStationChanged('reset')
  await refreshRuntime()
}
```

Add polling:

```js
function startRuntimePolling() {
  stopRuntimePolling()
  runtimeTimerId.value = window.setInterval(refreshRuntime, 1000)
}

function stopRuntimePolling() {
  if (runtimeTimerId.value !== null) {
    window.clearInterval(runtimeTimerId.value)
    runtimeTimerId.value = null
  }
}

onMounted(refreshRuntime)

onBeforeUnmount(stopRuntimePolling)
```

- [ ] **Step 5: Add station runtime CSS**

Add:

```css
.station-runtime-console {
  display: grid;
  gap: 16px;
}

.station-runtime-grid {
  display: grid;
  grid-template-columns: 360px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.station-runtime-side,
.station-runtime-main {
  display: grid;
  gap: 16px;
  min-width: 0;
}

.clock-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.clock-main h2 {
  margin: 2px 0 4px;
  color: #172033;
  font-size: 24px;
  font-weight: 700;
}

.clock-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.runtime-event-list {
  display: grid;
  gap: 8px;
  max-height: 460px;
  overflow: auto;
}

.runtime-event-row {
  padding: 10px;
  border: 1px solid #e4eaf1;
  border-radius: 6px;
  background: #f8fafc;
}

.runtime-event-row strong,
.runtime-event-row span {
  display: block;
}

.runtime-event-row span {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}
```

- [ ] **Step 6: Verify frontend build**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: frontend tests pass and build succeeds.

- [ ] **Step 7: Commit station runtime UI**

Run:

```powershell
git add frontend/src/components/station/StationClockBar.vue frontend/src/components/station/EventSourcePanel.vue frontend/src/components/station/RuntimeEventStream.vue frontend/src/views/SimulationSandbox.vue frontend/src/components/simulation/StationMap.vue frontend/src/stores/stationEvents.js frontend/src/styles.css
git commit -m "feat: rebuild station runtime console"
```

## Task 8: Integration Verification and Demo Script

**Files:**
- Modify: `docs/demo-script.md`
- Modify: `docs/environment-setup.md` if commands change during verification.

- [ ] **Step 1: Run full backend tests**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: all backend tests pass.

- [ ] **Step 2: Run full frontend tests and build**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: all frontend tests pass and Vite build succeeds.

- [ ] **Step 3: Start local servers**

If no servers are running, use two terminals:

```powershell
cd D:\softe\backend
mvn spring-boot:run
```

```powershell
cd D:\softe\frontend
npm run dev
```

Open:

```text
http://127.0.0.1:5173/#/station
```

- [ ] **Step 4: Browser verification checklist**

Use the in-app browser and verify:

1. The top-level interface is a role workspace shell, not `el-tabs`.
2. `/station` shows station time, rate, event source panel, event stream, and station map.
3. Importing the course sample creates event rows and sets station time to `2026-06-01 06:00:00`.
4. Pressing play at `10x` visibly advances station time.
5. Vehicles appear, enter piles, charge, complete, leave, generate bills, and the next vehicle starts.
6. `/owner` first shows login or demo-account entry.
7. After login, owner sees vehicle, current state, charging request entry, and bills as separate areas.
8. Submitting a request moves the owner view to current state instead of leaving the user on a blank form.
9. `/admin` remains usable for pile and queue operations.
10. Clearing runtime data refreshes station, owner, and admin state.

- [ ] **Step 5: Update demo script**

Update `docs/demo-script.md` with this flow:

```markdown
## 演示一：站点运行控制台

1. 进入 `#/station`，说明系统维护统一站点时钟和事件流。
2. 点击“导入课程样例”，说明课程样例只是导入事件序列。
3. 设置倍率为 `10x` 或 `60x`，点击开始。
4. 观察车辆按站点时间提交申请、进入队列、开始充电、完成离场。
5. 暂停在某个时刻，查看事件流和站点地图一致。

## 演示二：车主自助门户

1. 进入 `#/owner`，使用示例账户登录。
2. 查看车辆信息。
3. 发起充电申请。
4. 提交后进入当前状态，查看排队号、前方车辆和充电桩。
5. 充电完成后查看账单。

## 演示三：运营管理工作台

1. 进入 `#/admin`。
2. 查看队列和充电桩状态。
3. 执行调度或处理故障。
4. 返回 `#/station`，确认站点运行台同步更新。
```

- [ ] **Step 6: Commit docs and final verification fixes**

Run:

```powershell
git add docs/demo-script.md docs/environment-setup.md
git commit -m "docs: update unified runtime demo flow"
```

If `docs/environment-setup.md` did not change, run:

```powershell
git add docs/demo-script.md
git commit -m "docs: update unified runtime demo flow"
```

## Plan Self-Review

Spec coverage:

- Unified station clock is implemented by Tasks 1, 2, and 7.
- Runtime progression, charging completion, billing, pile release, and queue advancement are implemented by Task 2.
- Imported course events as normal event source are implemented by Task 3 and surfaced by Task 7.
- Owner portal state machine and non-stitched user flow are implemented by Tasks 4 and 6.
- Separate station, owner, and admin workspaces replace global tabs in Task 5.
- Runtime event stream and station console are implemented by Task 7.
- Full verification and demo script are covered by Task 8.

Type consistency:

- Backend DTO class is `RuntimeDtos`.
- Station clock service method names are `currentClock`, `currentStationTime`, `setClock`, `play`, and `pause`.
- Runtime advancement method is `StationRuntimeService.advanceTo(LocalDateTime targetTime)`.
- Frontend route constants are `ROUTES.STATION`, `ROUTES.OWNER`, and `ROUTES.ADMIN`.
- Owner workflow stages match the spec names: `Anonymous`, `OwnerNoVehicle`, `OwnerReady`, `OwnerWaiting`, `OwnerCharging`, and `OwnerCompleted`.

Known constraints:

- This first implementation uses a simple hash router and local demo login to avoid adding authentication and routing dependencies.
- Event payload is modeled with explicit columns, not arbitrary JSON, because the first event source is course and manual charge-request input.
- Course result checking remains available through the existing replay service while the runtime event import proves the unified path.

Verification commands:

```powershell
cd D:\softe\backend
mvn test
```

```powershell
cd D:\softe\frontend
npm test
npm run build
```
