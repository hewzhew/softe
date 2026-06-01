# Charging Scheduler Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a local Spring Boot + Vue + H2 demo system for the Group 2 smart charging pile scheduling and billing assignment.

**Architecture:** The backend follows Controller-Service-Domain-Repository layering with scheduling strategies isolated behind a `SchedulingStrategy` interface. The frontend is a Vue 3 single-page app with owner, admin, and demo-console views that call REST APIs and render the current station state.

**Tech Stack:** Java 17, Spring Boot 3, Spring Data JPA, H2, JUnit 5, Maven, Vue 3, Vite, Element Plus, Axios.

---

## File Structure

Create this project layout:

```text
D:\softe
  backend\
    pom.xml
    src\main\java\com\bupt\charging\
      ChargingApplication.java
      config\
        CorsConfig.java
      controller\
        AccountController.java
        BillingController.java
        ChargingController.java
        ConfigController.java
        FaultController.java
        PileController.java
        QueueController.java
      domain\
        Bill.java
        ChargeMode.java
        ChargingPile.java
        ChargingRequest.java
        ChargingSession.java
        DetailedList.java
        FaultRecord.java
        PileStatus.java
        RequestStatus.java
        SessionStatus.java
        StationConfig.java
        TariffRule.java
        UserAccount.java
        Vehicle.java
      dto\
        ApiResult.java
        AccountDtos.java
        BillingDtos.java
        ChargingDtos.java
        ConfigDtos.java
        FaultDtos.java
        PileDtos.java
        QueueDtos.java
      repository\
        BillRepository.java
        ChargingPileRepository.java
        ChargingRequestRepository.java
        ChargingSessionRepository.java
        DetailedListRepository.java
        FaultRecordRepository.java
        StationConfigRepository.java
        TariffRuleRepository.java
        UserAccountRepository.java
        VehicleRepository.java
      service\
        AccountService.java
        BillingService.java
        ChargingService.java
        ConfigService.java
        DemoService.java
        FaultService.java
        PileService.java
        QueueService.java
        SchedulerService.java
      strategy\
        Assignment.java
        SchedulingContext.java
        SchedulingStrategy.java
        ShortestFinishTimeStrategy.java
        PriorityFaultStrategy.java
        TimeOrderFaultStrategy.java
      support\
        BusinessException.java
        GlobalExceptionHandler.java
        TimeProvider.java
    src\main\resources\
      application.yml
    src\test\java\com\bupt\charging\
      service\
        BillingServiceTest.java
        SchedulerServiceTest.java
        FaultServiceTest.java
        ChargingFlowTest.java

  frontend\
    package.json
    index.html
    vite.config.js
    src\
      main.js
      App.vue
      api\client.js
      api\chargingApi.js
      components\StatusTag.vue
      views\DemoConsole.vue
      views\OwnerPanel.vue
      views\AdminPanel.vue
      styles.css

  docs\
    demo-script.md
```

Keep generated directories out of Git: `backend/target/`, `frontend/node_modules/`, `frontend/dist/`, and H2 database files.

---

## Task 1: Backend Project Scaffold

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/bupt/charging/ChargingApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/bupt/charging/support/BusinessException.java`
- Create: `backend/src/main/java/com/bupt/charging/support/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/bupt/charging/dto/ApiResult.java`

- [ ] **Step 1: Create Maven project descriptor**

Create `backend/pom.xml` with Java 17, Spring Boot web, validation, JPA, H2, Lombok-free code, and tests:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>
    <groupId>com.bupt</groupId>
    <artifactId>charging-scheduler</artifactId>
    <version>0.1.0</version>
    <name>charging-scheduler</name>
    <properties>
        <java.version>17</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: Add Spring Boot entry point**

Create `ChargingApplication.java`:

```java
package com.bupt.charging;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ChargingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChargingApplication.class, args);
    }
}
```

- [ ] **Step 3: Configure H2 and JPA**

Create `application.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:h2:file:./data/charging-demo;MODE=MySQL;DATABASE_TO_UPPER=false
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
    properties:
      hibernate:
        format_sql: true

logging:
  level:
    org.hibernate.SQL: warn
```

- [ ] **Step 4: Add shared API wrapper and exception handling**

Create `ApiResult.java`:

```java
package com.bupt.charging.dto;

public record ApiResult<T>(boolean success, String message, T data) {
    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(true, "ok", data);
    }

    public static <T> ApiResult<T> fail(String message) {
        return new ApiResult<>(false, message, null);
    }
}
```

Create `BusinessException.java`:

```java
package com.bupt.charging.support;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
```

Create `GlobalExceptionHandler.java`:

```java
package com.bupt.charging.support;

import com.bupt.charging.dto.ApiResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> business(BusinessException ex) {
        return ApiResult.fail(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResult<Void> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("invalid request");
        return ApiResult.fail(message);
    }
}
```

- [ ] **Step 5: Run backend compile**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: Maven downloads dependencies and reports `BUILD SUCCESS`.

- [ ] **Step 6: Commit scaffold**

```powershell
cd D:\softe
git add backend
git commit -m "chore: scaffold spring boot backend"
```

---

## Task 2: Domain Model and Repositories

**Files:**
- Create all files under `backend/src/main/java/com/bupt/charging/domain/`
- Create all files under `backend/src/main/java/com/bupt/charging/repository/`
- Create: `backend/src/main/java/com/bupt/charging/support/TimeProvider.java`

- [ ] **Step 1: Create enums**

Create `ChargeMode.java`, `PileStatus.java`, `RequestStatus.java`, and `SessionStatus.java`:

```java
package com.bupt.charging.domain;

public enum ChargeMode {
    FAST, SLOW
}
```

```java
package com.bupt.charging.domain;

public enum PileStatus {
    OFFLINE, IDLE, WORKING, FAULT
}
```

```java
package com.bupt.charging.domain;

public enum RequestStatus {
    WAITING_AREA, PILE_QUEUE, CHARGING, FINISHED, CANCELLED, INTERRUPTED
}
```

```java
package com.bupt.charging.domain;

public enum SessionStatus {
    PENDING, CHARGING, FINISHED, INTERRUPTED
}
```

- [ ] **Step 2: Create account and vehicle entities**

Create `UserAccount.java` with fields `id`, `userName`, `passwordHash`, `status`, and `createdAt`. Create `Vehicle.java` with fields `id`, `carId`, `carCapacity`, and `owner`.

Use standard JPA annotations, no Lombok, protected no-arg constructors, and explicit getters.

- [ ] **Step 3: Create charging entities**

Create `ChargingPile.java`, `ChargingRequest.java`, and `ChargingSession.java`.

Required behavior:

```java
public boolean isAvailableForQueue() {
    return status == PileStatus.IDLE || status == PileStatus.WORKING;
}

public double requestedHours(double power) {
    return requestAmount / power;
}

public void assignToPile(String pileId, int pileQueuePosition) {
    this.assignedPileId = pileId;
    this.pileQueuePosition = pileQueuePosition;
    this.status = RequestStatus.PILE_QUEUE;
}
```

- [ ] **Step 4: Create billing and fault entities**

Create `Bill.java`, `DetailedList.java`, `FaultRecord.java`, `TariffRule.java`, and `StationConfig.java`.

Use `BigDecimal` for money fields and `double` for charging amount and duration fields. `StationConfig` should store fast pile count, slow pile count, waiting area size, queue length, fast power, and slow power.

- [ ] **Step 5: Create repositories**

Each repository extends `JpaRepository<Entity, Long>`.

Add derived queries:

```java
Optional<Vehicle> findByCarId(String carId);
boolean existsByCarId(String carId);
List<ChargingRequest> findByModeAndStatusOrderByRequestTimeAsc(ChargeMode mode, RequestStatus status);
List<ChargingRequest> findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc(String pileId, RequestStatus status);
Optional<ChargingSession> findFirstByCarIdAndStatusOrderByStartTimeDesc(String carId, SessionStatus status);
List<DetailedList> findByBillId(Long billId);
```

- [ ] **Step 6: Add time provider**

Create `TimeProvider.java`:

```java
package com.bupt.charging.support;

import java.time.LocalDateTime;

public interface TimeProvider {
    LocalDateTime now();

    static TimeProvider system() {
        return LocalDateTime::now;
    }
}
```

- [ ] **Step 7: Run repository compile**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 8: Commit domain model**

```powershell
cd D:\softe
git add backend/src/main/java/com/bupt/charging/domain backend/src/main/java/com/bupt/charging/repository backend/src/main/java/com/bupt/charging/support/TimeProvider.java
git commit -m "feat: add charging domain model"
```

---

## Task 3: Billing Service with Tests

**Files:**
- Create: `backend/src/test/java/com/bupt/charging/service/BillingServiceTest.java`
- Create: `backend/src/main/java/com/bupt/charging/service/BillingService.java`

- [ ] **Step 1: Write failing billing tests**

Create `BillingServiceTest.java` with tests:

```java
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
```

- [ ] **Step 2: Run tests and verify failure**

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=BillingServiceTest test
```

Expected: FAIL because `BillingService` does not exist or `calculateFee` is not implemented.

- [ ] **Step 3: Implement fee calculation**

Create `BillingService.java` with:

```java
public FeeResult calculateFee(LocalDateTime start, LocalDateTime end, double amount, double power, TariffRule rule) {
    if (!end.isAfter(start)) {
        throw new BusinessException("end time must be after start time");
    }
    double totalMinutes = Duration.between(start, end).toMinutes();
    BigDecimal chargeFee = BigDecimal.ZERO;
    LocalDateTime cursor = start;
    while (cursor.isBefore(end)) {
        LocalDateTime next = nextPriceBoundary(cursor, end);
        double minutes = Duration.between(cursor, next).toMinutes();
        double slotAmount = amount * (minutes / totalMinutes);
        chargeFee = chargeFee.add(rule.priceAt(cursor.toLocalTime()).multiply(BigDecimal.valueOf(slotAmount)));
        cursor = next;
    }
    BigDecimal serviceFee = rule.getServicePrice().multiply(BigDecimal.valueOf(amount));
    return new FeeResult(money(chargeFee), money(serviceFee), money(chargeFee.add(serviceFee)));
}
```

Also implement `nextPriceBoundary`, `money`, and record:

```java
public record FeeResult(BigDecimal chargeFee, BigDecimal serviceFee, BigDecimal totalFee) {}
```

- [ ] **Step 4: Run billing tests**

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=BillingServiceTest test
```

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit billing service**

```powershell
cd D:\softe
git add backend/src/main/java/com/bupt/charging/service/BillingService.java backend/src/test/java/com/bupt/charging/service/BillingServiceTest.java
git commit -m "feat: implement time-of-use billing"
```

---

## Task 4: Scheduler Service with Tests

**Files:**
- Create: `backend/src/test/java/com/bupt/charging/service/SchedulerServiceTest.java`
- Create: `backend/src/main/java/com/bupt/charging/strategy/Assignment.java`
- Create: `backend/src/main/java/com/bupt/charging/strategy/SchedulingContext.java`
- Create: `backend/src/main/java/com/bupt/charging/strategy/SchedulingStrategy.java`
- Create: `backend/src/main/java/com/bupt/charging/strategy/ShortestFinishTimeStrategy.java`
- Create: `backend/src/main/java/com/bupt/charging/service/SchedulerService.java`

- [ ] **Step 1: Write failing scheduler tests**

Create tests for:

```java
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
```

Also test that a slow request never assigns to a fast pile.

- [ ] **Step 2: Run scheduler tests and verify failure**

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=SchedulerServiceTest test
```

Expected: FAIL because strategy classes are missing.

- [ ] **Step 3: Implement strategy types**

Create:

```java
public record Assignment(String pileId, int queuePosition, double expectedFinishHours) {}
```

```java
public interface SchedulingStrategy {
    Optional<Assignment> select(
            ChargingRequest request,
            List<ChargingPile> candidatePiles,
            Map<String, List<ChargingRequest>> currentQueues
    );
}
```

Implement `ShortestFinishTimeStrategy` by filtering same mode, skipping fault/offline piles, calculating:

```java
double waitingHours = queue.stream()
        .mapToDouble(item -> item.getRequestAmount() / pile.getPower())
        .sum();
double finishHours = waitingHours + request.getRequestAmount() / pile.getPower();
```

Return the minimum finish time. Queue position is `queue.size() + 1`.

- [ ] **Step 4: Implement SchedulerService**

`SchedulerService` should load waiting requests by mode, load candidate piles, read pile queues, call `ShortestFinishTimeStrategy`, assign requests to piles, and save changes.

Expose:

```java
public Optional<Assignment> dispatchOne(ChargeMode mode)
public List<Assignment> dispatchAll()
```

- [ ] **Step 5: Run scheduler tests**

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=SchedulerServiceTest test
```

Expected: tests pass.

- [ ] **Step 6: Commit scheduler**

```powershell
cd D:\softe
git add backend/src/main/java/com/bupt/charging/strategy backend/src/main/java/com/bupt/charging/service/SchedulerService.java backend/src/test/java/com/bupt/charging/service/SchedulerServiceTest.java
git commit -m "feat: implement shortest finish scheduling"
```

---

## Task 5: Account, Config, Pile, Queue, and Charging Services

**Files:**
- Create service files listed in the File Structure section.
- Create DTO files `AccountDtos.java`, `ConfigDtos.java`, `PileDtos.java`, `QueueDtos.java`, `ChargingDtos.java`.
- Create tests in `ChargingFlowTest.java`.

- [ ] **Step 1: Write failing flow test**

Create `ChargingFlowTest.java` as `@SpringBootTest` with transactional cleanup using repositories.

Test scenario:

```java
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
```

- [ ] **Step 2: Run flow test and verify failure**

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=ChargingFlowTest test
```

Expected: FAIL because services and DTOs are missing.

- [ ] **Step 3: Implement ConfigService**

`ConfigService` responsibilities:

- `initialize(UpdateConfigRequest request)`: save station config and create pile records `F-1..F-n`, `T-1..T-n`.
- `resetDemoData()`: delete requests, sessions, bills, details, faults, piles, vehicles, accounts, config, and tariff data in dependency-safe order.
- `currentConfig()`: return saved config or defaults.

- [ ] **Step 4: Implement AccountService**

`createNewAccount` validates `carId` uniqueness and `carCapacity > 0`, creates `UserAccount` and `Vehicle`.

`setPassword` finds vehicle/account by carId, stores a simple non-plaintext value:

```java
String hash = Base64.getEncoder().encodeToString(("demo:" + password).getBytes(StandardCharsets.UTF_8));
```

This is acceptable for classroom demo; note in README that it is not production password security.

- [ ] **Step 5: Implement PileService and QueueService**

`PileService` supports power on, start, power off, find all, find by id, mark fault, recover.

`QueueService` returns:

- waiting area fast and slow queues.
- each pile queue.
- wait time = sum of request amount / pile power for vehicles before the item.

- [ ] **Step 6: Implement ChargingService**

Required methods:

```java
submitRequest(String carId, double requestAmount, ChargeMode mode)
modifyAmount(String carId, double amount)
modifyMode(String carId, ChargeMode mode)
queryCarState(String carId)
startCharging(String carId, String pileId)
queryChargingState(String carId)
endCharging(String carId, String pileId, double actualAmount)
```

Rules:

- Reject request amount <= 0.
- Reject request amount greater than vehicle capacity.
- Reject new request if latest request is waiting, queued, or charging.
- `submitRequest` creates queue number using next sequence for mode.
- `modifyMode` only allowed before charging; it creates a new queue number in the new mode.
- `startCharging` only allowed when request is assigned to the pile and is first in that pile queue.
- `endCharging` creates bill/detail, marks request finished, releases pile if no more charging session, and triggers scheduler.

- [ ] **Step 7: Run flow test**

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=ChargingFlowTest test
```

Expected: test passes.

- [ ] **Step 8: Run full backend tests**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: all backend tests pass.

- [ ] **Step 9: Commit services**

```powershell
cd D:\softe
git add backend/src/main/java/com/bupt/charging/service backend/src/main/java/com/bupt/charging/dto backend/src/test/java/com/bupt/charging/service
git commit -m "feat: implement core charging services"
```

---

## Task 6: Fault Handling with Tests

**Files:**
- Create: `backend/src/test/java/com/bupt/charging/service/FaultServiceTest.java`
- Create: `backend/src/main/java/com/bupt/charging/service/FaultService.java`
- Create: `backend/src/main/java/com/bupt/charging/strategy/PriorityFaultStrategy.java`
- Create: `backend/src/main/java/com/bupt/charging/strategy/TimeOrderFaultStrategy.java`
- Create: `backend/src/main/java/com/bupt/charging/dto/FaultDtos.java`

- [ ] **Step 1: Write failing fault tests**

Create tests:

```java
@Test
void priorityFaultDispatchesFaultQueueBeforeWaitingArea() {
    demo.seedFaultScenario();
    FaultDtos.FaultResult result = faultService.handleFault("F-1", "PRIORITY");

    assertEquals("F-1", result.faultPileId());
    assertTrue(result.movedCars().contains("CAR-FAULT-QUEUE-1"));
    assertFalse(result.movedCars().contains("CAR-WAITING-NEW"));
}

@Test
void timeOrderFaultReordersUnstartedCarsByRequestTime() {
    demo.seedTimeOrderScenario();
    FaultDtos.FaultResult result = faultService.handleFault("F-1", "TIME_ORDER");

    assertEquals(List.of("CAR-EARLY", "CAR-MIDDLE", "CAR-LATE"), result.reorderedCars());
}

@Test
void chargingFaultCreatesDetailForChargedPart() {
    demo.seedChargingSessionOnPile("F-1", "CAR-CHARGING", 12.0);
    FaultDtos.FaultResult result = faultService.handleFault("F-1", "PRIORITY");

    assertEquals(1, result.generatedDetailCount());
}
```

- [ ] **Step 2: Run fault tests and verify failure**

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=FaultServiceTest test
```

Expected: FAIL because fault handling is missing.

- [ ] **Step 3: Implement fault DTOs and strategies**

`PriorityFaultStrategy` sorts affected requests by:

1. fault pile queue item before normal waiting area item.
2. earlier request time.
3. smaller queue number sequence.

`TimeOrderFaultStrategy` sorts by request time ascending, then queue number sequence ascending.

- [ ] **Step 4: Implement FaultService**

`handleFault(pileId, strategy)`:

1. Find pile and mark `FAULT`.
2. Create `FaultRecord`.
3. If a charging session exists on the pile, interrupt it and call `BillingService` to create detail for actual charged amount so far.
4. Collect affected unstarted requests from the fault pile queue.
5. For `TIME_ORDER`, also collect unstarted requests from other same-mode pile queues.
6. Clear affected pile assignments.
7. Reassign requests to same-mode non-fault piles with available queue capacity.
8. Save `FaultRecord` result summary.
9. Return moved/reordered cars and generated detail count.

`recoverPile(pileId)`:

1. Mark pile `IDLE`.
2. Re-run time-order redistribution for same-mode unstarted pile-queue vehicles.
3. Return latest queue state.

- [ ] **Step 5: Run fault tests**

Run:

```powershell
cd D:\softe\backend
mvn -Dtest=FaultServiceTest test
```

Expected: fault tests pass.

- [ ] **Step 6: Run full backend tests**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: all backend tests pass.

- [ ] **Step 7: Commit fault handling**

```powershell
cd D:\softe
git add backend/src/main/java/com/bupt/charging/service/FaultService.java backend/src/main/java/com/bupt/charging/strategy/PriorityFaultStrategy.java backend/src/main/java/com/bupt/charging/strategy/TimeOrderFaultStrategy.java backend/src/main/java/com/bupt/charging/dto/FaultDtos.java backend/src/test/java/com/bupt/charging/service/FaultServiceTest.java
git commit -m "feat: implement fault rescheduling"
```

---

## Task 7: REST Controllers and CORS

**Files:**
- Create controller files listed in File Structure.
- Create: `backend/src/main/java/com/bupt/charging/config/CorsConfig.java`

- [ ] **Step 1: Add CORS config**

Create `CorsConfig.java`:

```java
package com.bupt.charging.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
    @Bean
    WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*");
            }
        };
    }
}
```

- [ ] **Step 2: Implement controllers**

Each controller returns `ApiResult.ok(...)`.

Map endpoints exactly as the design spec:

```java
@PostMapping("/api/accounts")
public ApiResult<AccountDtos.AccountResponse> create(@Valid @RequestBody AccountDtos.CreateAccountRequest request)
```

```java
@PostMapping("/api/charging/requests")
public ApiResult<ChargingDtos.RequestResponse> submit(@Valid @RequestBody ChargingDtos.SubmitRequest request)
```

```java
@PostMapping("/api/faults")
public ApiResult<FaultDtos.FaultResult> fault(@Valid @RequestBody FaultDtos.CreateFaultRequest request)
```

- [ ] **Step 3: Add demo seed endpoints**

Add to `ConfigController`:

```java
@PostMapping("/api/demo/reset")
public ApiResult<Void> reset()

@PostMapping("/api/demo/seed")
public ApiResult<ConfigDtos.SystemSnapshot> seed()
```

`SystemSnapshot` should combine config, pile states, queue state, active sessions, and recent bills.

- [ ] **Step 4: Run backend tests**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: tests pass.

- [ ] **Step 5: Start backend manually**

Run:

```powershell
cd D:\softe\backend
mvn spring-boot:run
```

Expected: server starts on `http://localhost:8080`. Stop with `Ctrl+C` after verifying startup.

- [ ] **Step 6: Commit controllers**

```powershell
cd D:\softe
git add backend/src/main/java/com/bupt/charging/controller backend/src/main/java/com/bupt/charging/config
git commit -m "feat: expose charging REST APIs"
```

---

## Task 8: Frontend Scaffold and API Client

**Files:**
- Create all frontend files listed in File Structure except views, which are filled in later tasks.

- [ ] **Step 1: Create frontend package**

Create `frontend/package.json`:

```json
{
  "name": "charging-scheduler-frontend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite --host 127.0.0.1",
    "build": "vite build",
    "preview": "vite preview --host 127.0.0.1"
  },
  "dependencies": {
    "@vitejs/plugin-vue": "^5.1.4",
    "axios": "^1.7.7",
    "element-plus": "^2.8.6",
    "vue": "^3.5.12"
  },
  "devDependencies": {
    "vite": "^5.4.10"
  }
}
```

- [ ] **Step 2: Add Vite entry files**

Create `index.html`, `vite.config.js`, `src/main.js`, `src/App.vue`, and `src/styles.css`.

`App.vue` should provide tabs for `演示控制台`, `车主端`, and `管理员端`.

- [ ] **Step 3: Add API client**

Create `src/api/client.js`:

```js
import axios from 'axios'

export const http = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 8000
})

export async function unwrap(promise) {
  const response = await promise
  if (!response.data.success) {
    throw new Error(response.data.message || '请求失败')
  }
  return response.data.data
}
```

Create `src/api/chargingApi.js` with functions for every backend endpoint:

```js
export const api = {
  getConfig: () => unwrap(http.get('/config')),
  updateConfig: (payload) => unwrap(http.put('/config', payload)),
  resetDemo: () => unwrap(http.post('/demo/reset')),
  seedDemo: () => unwrap(http.post('/demo/seed')),
  createAccount: (payload) => unwrap(http.post('/accounts', payload)),
  submitRequest: (payload) => unwrap(http.post('/charging/requests', payload)),
  getPiles: () => unwrap(http.get('/piles')),
  getQueues: () => unwrap(http.get('/queues')),
  dispatch: () => unwrap(http.post('/scheduler/dispatch')),
  createFault: (payload) => unwrap(http.post('/faults', payload))
}
```

- [ ] **Step 4: Install and build frontend**

Run:

```powershell
cd D:\softe\frontend
npm install
npm run build
```

Expected: Vite build succeeds and creates `frontend/dist`.

- [ ] **Step 5: Commit frontend scaffold**

```powershell
cd D:\softe
git add frontend
git commit -m "chore: scaffold vue frontend"
```

---

## Task 9: Frontend Demo, Owner, and Admin Views

**Files:**
- Create/modify: `frontend/src/views/DemoConsole.vue`
- Create/modify: `frontend/src/views/OwnerPanel.vue`
- Create/modify: `frontend/src/views/AdminPanel.vue`
- Create/modify: `frontend/src/components/StatusTag.vue`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: Implement StatusTag**

`StatusTag.vue` maps backend states to Element Plus tags:

```js
const typeMap = {
  IDLE: 'success',
  WORKING: 'warning',
  FAULT: 'danger',
  OFFLINE: 'info',
  WAITING_AREA: 'info',
  PILE_QUEUE: 'warning',
  CHARGING: 'success',
  FINISHED: 'success',
  INTERRUPTED: 'danger'
}
```

- [ ] **Step 2: Implement DemoConsole**

Include:

- Config form for pile counts, queue length, powers, waiting area size.
- Buttons: reset, seed, dispatch, refresh.
- Tables: charging piles, waiting queues, pile queues, recent bills.

On mount, call `refresh()` which loads config, piles, and queues.

- [ ] **Step 3: Implement OwnerPanel**

Include:

- Account creation form.
- Password form.
- Charging request form.
- Modify amount/mode controls.
- Car state card.
- Start/end charging controls.
- Bill and detail table.

Use `ElMessage.success` and `ElMessage.error` for action results.

- [ ] **Step 4: Implement AdminPanel**

Include:

- Charging pile table.
- Per-row buttons: power on, start pile, power off, fault, recover.
- Fault dialog with strategy select: `PRIORITY` and `TIME_ORDER`.
- Tariff/config panel.
- Queue status table.

- [ ] **Step 5: Run frontend build**

Run:

```powershell
cd D:\softe\frontend
npm run build
```

Expected: build succeeds.

- [ ] **Step 6: Run full local smoke test**

Terminal 1:

```powershell
cd D:\softe\backend
mvn spring-boot:run
```

Terminal 2:

```powershell
cd D:\softe\frontend
npm run dev
```

Open `http://127.0.0.1:5173`, reset demo data, seed demo data, dispatch, and trigger one fault.

- [ ] **Step 7: Commit frontend views**

```powershell
cd D:\softe
git add frontend/src
git commit -m "feat: add charging demo interface"
```

---

## Task 10: Documentation, Demo Script, and Final Verification

**Files:**
- Create: `README.md`
- Create: `docs/demo-script.md`
- Modify: `docs/environment-setup.md`

- [ ] **Step 1: Create README**

README must include:

- Project purpose.
- Tech stack.
- How it maps to the design document.
- Backend run commands.
- Frontend run commands.
- H2 console URL.
- Demo account/request flow.
- Note that password hashing is simplified for classroom demo.

- [ ] **Step 2: Create demo script**

Create `docs/demo-script.md` with this exact flow:

1. Reset demo data.
2. Initialize 2 fast piles, 3 slow piles, waiting area 10, queue length 2.
3. Seed demo vehicles.
4. Submit one extra fast request and one slow request.
5. Dispatch all.
6. Start charging the first fast vehicle.
7. Query queue and pile state.
8. End charging and show bill/detail.
9. Reset or seed fault scenario.
10. Trigger `PRIORITY` fault dispatch.
11. Reset or seed time-order scenario.
12. Trigger `TIME_ORDER` fault dispatch.

- [ ] **Step 3: Run backend tests**

Run:

```powershell
cd D:\softe\backend
mvn test
```

Expected: all tests pass.

- [ ] **Step 4: Run frontend build**

Run:

```powershell
cd D:\softe\frontend
npm run build
```

Expected: Vite build succeeds.

- [ ] **Step 5: Verify Git status**

Run:

```powershell
cd D:\softe
git status --short --branch
```

Expected: no uncommitted files except runtime H2 database files ignored by `.gitignore`.

- [ ] **Step 6: Commit documentation**

```powershell
cd D:\softe
git add README.md docs
git commit -m "docs: add running and demo instructions"
```

---

## Spec Coverage Review

This plan covers:

- Spring Boot + Vue + H2 scaffold: Tasks 1 and 8.
- Controller-Service-Domain-Repository backend layering: Tasks 1, 2, 5, 7.
- Charging account, request, queue, start/end flow: Task 5.
- Time-of-use billing and bill/detail generation: Task 3 and Task 5.
- Shortest finish time ordinary scheduling: Task 4.
- Pile administration and queue monitoring: Tasks 5, 7, 9.
- Priority and time-order fault rescheduling: Task 6.
- Demo console and classroom walkthrough: Tasks 9 and 10.
- Verification commands: Tasks 1, 3, 4, 5, 6, 7, 8, 9, 10.

The optional shortest-single and shortest-batch bonus strategies are intentionally not in the first implementation plan. The strategy package keeps a place for them after the core system is stable.
