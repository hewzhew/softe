# Acceptance Scenario Runner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a dedicated teacher acceptance scenario runner that replays the Excel event sequence with simulated time and displays acceptance-table snapshots in the Vue app.

**Architecture:** The backend adds an in-memory scenario engine isolated from JPA entities and current live demo state. The frontend adds a fourth tab that calls the new endpoint and renders the returned snapshots in a table matching the Excel columns.

**Tech Stack:** Java 17, Spring Boot 3, JUnit 5, Vue 3, Element Plus, Axios, Node test runner.

---

## File Structure

- Modify: `backend/src/test/java/com/bupt/charging/service/ChargingFlowTest.java`
  - Fix the existing date-sensitive assertion by querying the generated bill date.
- Create: `backend/src/main/java/com/bupt/charging/dto/AcceptanceDtos.java`
  - Request-free response DTOs for scenario metadata, events, pile slots, waiting area text, and sample checks.
- Create: `backend/src/main/java/com/bupt/charging/service/AcceptanceScenarioService.java`
  - In-memory simulation engine, event parser, dispatcher, fault/recovery handling, and snapshot formatter.
- Create: `backend/src/main/java/com/bupt/charging/controller/AcceptanceController.java`
  - `GET /api/acceptance/scenario` endpoint.
- Create: `backend/src/test/java/com/bupt/charging/service/AcceptanceScenarioServiceTest.java`
  - Red-green tests for sample rows, T1 fault priority dispatch, and V21 amount modification.
- Modify: `frontend/src/api/chargingApi.js`
  - Add `runAcceptanceScenario`.
- Modify: `frontend/src/App.vue`
  - Add “测试用例” tab.
- Create: `frontend/src/views/AcceptancePanel.vue`
  - Render scenario controls, sample checks, and event-result table.
- Modify: `frontend/src/styles.css`
  - Add layout styles for wide scenario tables.
- Create: `frontend/src/utils/acceptanceDisplay.js`
  - Small helpers for pass/fail labels and row flattening.
- Create: `frontend/src/utils/acceptanceDisplay.test.js`
  - Node tests for helper behavior.
- Modify: `docs/demo-script.md`
  - Add acceptance scenario walkthrough.

## Task 1: Fix Existing Date-Sensitive Flow Test

- [ ] **Step 1: Confirm the failing assertion**

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=ChargingFlowTest test
```

Expected before the fix: `vehicleCanRegisterRequestChargeAndGenerateBill` fails at the assertion that queries `LocalDate.now()`.

- [ ] **Step 2: Patch the test**

Change:

```java
assertFalse(billingService.queryBills("CAR-1", LocalDate.now()).isEmpty());
```

to:

```java
assertFalse(billingService.queryBills("CAR-1", bill.date()).isEmpty());
```

Remove the now-unused `LocalDate` import.

- [ ] **Step 3: Verify the focused test**

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=ChargingFlowTest test
```

Expected: `Failures: 0, Errors: 0`.

## Task 2: Backend Acceptance Scenario Engine

- [ ] **Step 1: Write failing service tests**

Create `AcceptanceScenarioServiceTest` with tests for:

```java
assertEquals("(V1,0.00,0.00)", rowAt("06:00").slow1().get(0));
assertEquals("(V1,0.83,1.00)", rowAt("06:05").slow1().get(0));
assertEquals("(V2,0.00,0.00)", rowAt("06:05").slow2().get(0));
assertEquals("(V13,F,110.00)", rowAt("07:05").waitingAreaText());
assertEquals("(V13,F,110.00)-(V14,F,95.00)", rowAt("07:10").waitingAreaText());
assertTrue(rowAt("09:25").waitingAreaText().contains("(V21,F,35.00)"));
```

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=AcceptanceScenarioServiceTest test
```

Expected before implementation: compilation fails because the service and DTOs do not exist.

- [ ] **Step 2: Implement DTOs**

Create records:

```java
AcceptanceScenarioResponse
AcceptanceScenarioConfig
AcceptanceEventRow
AcceptanceSampleCheck
```

Each event row exposes `time`, `event`, `fast1`, `fast2`, `slow1`, `slow2`, `slow3`, `waitingAreaText`, and `notes`.

- [ ] **Step 3: Implement the service**

`AcceptanceScenarioService.runDefaultScenario()` should:

1. Initialize the fixed acceptance config.
2. Parse the embedded event list through 09:30.
3. Advance charging completion before every event.
4. Apply the event.
5. Auto-dispatch after requests, cancellations, faults, and recovery.
6. Record one snapshot row per event.
7. Create sample checks comparing known Excel example cells.

- [ ] **Step 4: Verify service tests**

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=AcceptanceScenarioServiceTest test
```

Expected: `Failures: 0, Errors: 0`.

## Task 3: Backend API

- [ ] **Step 1: Add controller test coverage to smoke test**

Extend `RestApiSmokeTest` or create `AcceptanceControllerTest` to call:

```text
GET /api/acceptance/scenario
```

Assert the response has at least 36 rows and the first row time is `06:00`.

- [ ] **Step 2: Implement controller**

Create:

```java
@RestController
@RequestMapping("/api/acceptance")
public class AcceptanceController {
    @GetMapping("/scenario")
    public ApiResult<AcceptanceScenarioResponse> scenario()
}
```

- [ ] **Step 3: Verify API test**

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=AcceptanceControllerTest test
```

Expected: `Failures: 0, Errors: 0`.

## Task 4: Frontend Acceptance Page

- [ ] **Step 1: Write helper tests**

Create `acceptanceDisplay.test.js` for row flattening and sample-check labels.

Run:

```powershell
cd D:\softe\frontend
npm test
```

Expected before implementation: test fails because helpers do not exist or are incomplete.

- [ ] **Step 2: Implement API and helpers**

Add `api.runAcceptanceScenario = () => unwrap(http.get('/acceptance/scenario'))`.

Create helpers to map sample checks to `通过` / `需复核`.

- [ ] **Step 3: Implement `AcceptancePanel.vue`**

The page includes:

- config summary strip.
- “运行用例” and “复制结果” buttons.
- sample-check table.
- event result table with columns matching Excel.

- [ ] **Step 4: Add tab and styles**

Import the panel in `App.vue` and add a tab named `测试用例`.

Add styles for wide fixed tables and compact result cells.

- [ ] **Step 5: Verify frontend tests and build**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: both commands exit 0.

## Task 5: Documentation and Full Verification

- [ ] **Step 1: Update demo script**

Add a section explaining:

1. Start backend and frontend.
2. Open `测试用例`.
3. Click `运行用例`.
4. Check the sample-match table.
5. Copy the result table if the Excel needs manual filling.

- [ ] **Step 2: Run full backend tests**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: all backend tests pass.

- [ ] **Step 3: Run frontend verification**

Run:

```powershell
cd D:\softe\frontend
npm test
npm run build
```

Expected: all frontend checks pass.

- [ ] **Step 4: Browser verification**

Start the backend and frontend, open `http://127.0.0.1:5173/`, navigate to `测试用例`, run the scenario, and verify the table is non-empty and readable.

## Plan Self-Review

- The existing failing backend test is covered first.
- Teacher Excel sample rows are covered by backend tests before implementation.
- The acceptance runner is isolated from the existing live JPA demo.
- The page name avoids the awkward word “验收” in the UI and uses “测试用例”.
- Direct Excel write-back is intentionally out of scope for this first implementation.
