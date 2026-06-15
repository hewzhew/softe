package com.bupt.charging.service;

import com.bupt.charging.domain.TariffRule;
import com.bupt.charging.dto.AcceptanceDtos;
import com.bupt.charging.dto.ScenarioDtos;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AcceptanceScenarioService {
    private static final LocalDate SCENARIO_DATE = LocalDate.of(2026, 6, 1);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final int QUEUE_LENGTH = 3;
    private static final int WAITING_AREA_SIZE = 10;
    private static final double FAST_POWER = 30.0;
    private static final double SLOW_POWER = 10.0;
    private static final TariffRule TARIFF_RULE = TariffRule.defaults();
    private static final BillingService BILLING_SERVICE = new BillingService();
    private static final List<String> DEFAULT_EVENTS = List.of(
            "06:00 (A,V1,T,40)",
            "06:05 (A,V2,T,30)",
            "06:10 (A,V3,F,100)",
            "06:15 (A,V4,F,120)",
            "06:20 (A,V2,O,0)",
            "06:25 (A,V5,T,20)",
            "06:30 (A,V6,T,20)",
            "06:35 (A,V7,F,110)",
            "06:40 (A,V8,T,20)",
            "06:45 (A,V9,F,105)",
            "06:50 (A,V10,T,10)",
            "06:55 (A,V11,F,110)",
            "07:00 (A,V12,F,90)",
            "07:05 (A,V13,F,110)",
            "07:10 (A,V14,F,95)",
            "07:15 (A,V15,T,10)",
            "07:20 (A,V16,F,60)",
            "07:25 (A,V17,T,10)",
            "07:30 (A,V18,T,7.5)",
            "07:35 (A,V19,F,75)",
            "07:40 (A,V20,F,95)",
            "07:45 (A,V21,F,95)",
            "07:50 (A,V22,F,70)",
            "07:55 (A,V23,F,80)",
            "08:00 (A,V24,T,5)",
            "08:20 (A,V25,T,15)",
            "08:25 (B,T1,O,0)",
            "08:30 (A,V26,T,20)",
            "08:35 (A,V27,T,25)",
            "08:50 (B,F1,O,0)",
            "09:00 (A,V28,F,30)",
            "09:10 (A,V1,O,0)",
            "09:15 (B,T1,O,1)",
            "09:20 (A,V27,O,0)",
            "09:25 (C,V21,O,35)",
            "09:30 (A,V19,O,0)"
    );

    public AcceptanceDtos.AcceptanceScenarioResponse runDefaultScenario() {
        ScenarioState state = new ScenarioState();
        List<AcceptanceDtos.AcceptanceEventRow> rows = new ArrayList<>();
        for (String rawEvent : DEFAULT_EVENTS) {
            ScenarioEvent event = ScenarioEvent.parse(rawEvent);
            state.advanceTo(event.time);
            String notes = state.apply(event);
            rows.add(state.snapshot(event, notes));
        }
        return new AcceptanceDtos.AcceptanceScenarioResponse(config(), rows, sampleChecks(rows));
    }

    public List<String> courseSampleRawEvents() {
        return DEFAULT_EVENTS;
    }

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

        ScenarioDtos.ScenarioDefinition scenario = scenarioDefinition();
        List<ScenarioDtos.ScenarioCheck> checks = replayChecks(sampleChecks(rows));
        return new ScenarioDtos.ReplayBundle(
                scenario,
                new ScenarioDtos.TimeSession(
                        "course-sample-session",
                        "SIMULATION",
                        scenario.startTime(),
                        scenario.startTime(),
                        scenario.startTime(),
                        scenario.stopTime(),
                        "PRECOMPUTED",
                        null
                ),
                new ScenarioDtos.SourceSummary(
                        "COURSE_SEQUENCE",
                        "课程事件序列",
                        List.of("COURSE_SEQUENCE", "SYSTEM_DERIVED"),
                        commands.size(),
                        snapshots.size()
                ),
                commands,
                snapshots,
                transitions,
                checks,
                rows
        );
    }

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
                event.rawPayload,
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

    private AcceptanceDtos.AcceptanceScenarioConfig config() {
        return new AcceptanceDtos.AcceptanceScenarioConfig(
                FAST_POWER,
                SLOW_POWER,
                1.0,
                0.7,
                0.4,
                0.8,
                2,
                3,
                QUEUE_LENGTH,
                WAITING_AREA_SIZE,
                "06:00",
                "09:30"
        );
    }

    private List<AcceptanceDtos.AcceptanceSampleCheck> sampleChecks(List<AcceptanceDtos.AcceptanceEventRow> rows) {
        return List.of(
                check(rows, "06:00", "慢充1-1", "(V1,0.00,0.00)", row -> row.slow1().get(0)),
                check(rows, "06:05", "慢充1-1", "(V1,0.83,1.00)", row -> row.slow1().get(0)),
                check(rows, "06:05", "慢充2-1", "(V2,0.00,0.00)", row -> row.slow2().get(0)),
                check(rows, "07:05", "等候区", "(V13,F,110.00)", AcceptanceDtos.AcceptanceEventRow::waitingAreaText),
                check(rows, "07:10", "等候区", "(V13,F,110.00)-(V14,F,95.00)", AcceptanceDtos.AcceptanceEventRow::waitingAreaText),
                check(rows, "07:15", "等候区", "(V13,F,110.00)-(V14,F,95.00)", AcceptanceDtos.AcceptanceEventRow::waitingAreaText)
        );
    }

    private AcceptanceDtos.AcceptanceSampleCheck check(
            List<AcceptanceDtos.AcceptanceEventRow> rows,
            String time,
            String column,
            String expected,
            ValueReader reader
    ) {
        String actual = rows.stream()
                .filter(row -> row.time().equals(time))
                .findFirst()
                .map(reader::read)
                .orElse("");
        return new AcceptanceDtos.AcceptanceSampleCheck(time, column, expected, actual, expected.equals(actual));
    }

    private interface ValueReader {
        String read(AcceptanceDtos.AcceptanceEventRow row);
    }

    private static final class ScenarioState {
        private final Map<String, ScenarioPile> piles = new LinkedHashMap<>();
        private final List<ScenarioVehicle> waitingArea = new ArrayList<>();
        private long sequence = 0;

        private ScenarioState() {
            piles.put("F1", new ScenarioPile("F1", "F", FAST_POWER));
            piles.put("F2", new ScenarioPile("F2", "F", FAST_POWER));
            piles.put("T1", new ScenarioPile("T1", "T", SLOW_POWER));
            piles.put("T2", new ScenarioPile("T2", "T", SLOW_POWER));
            piles.put("T3", new ScenarioPile("T3", "T", SLOW_POWER));
        }

        private void advanceTo(LocalDateTime time) {
            while (true) {
                Optional<Completion> next = nextCompletion(time);
                if (next.isEmpty()) {
                    break;
                }
                Completion completion = next.get();
                ScenarioVehicle vehicle = completion.pile.queue.remove(0);
                vehicle.finishAt(completion.time, completion.pile.power);
                startHeadIfNeeded(completion.pile, completion.time);
                dispatchAll(completion.time);
            }
        }

        private Optional<Completion> nextCompletion(LocalDateTime target) {
            Completion result = null;
            for (ScenarioPile pile : piles.values()) {
                if (pile.fault || pile.queue.isEmpty()) {
                    continue;
                }
                ScenarioVehicle head = pile.queue.get(0);
                LocalDateTime finishTime = head.finishTime(pile.power);
                if (finishTime != null && !finishTime.isAfter(target)) {
                    if (result == null || finishTime.isBefore(result.time)
                            || (finishTime.equals(result.time) && pile.id.compareTo(result.pile.id) < 0)) {
                        result = new Completion(pile, finishTime);
                    }
                }
            }
            return Optional.ofNullable(result);
        }

        private String apply(ScenarioEvent event) {
            if ("A".equals(event.source) && ("F".equals(event.operation) || "T".equals(event.operation))) {
                return addRequest(event);
            }
            if ("A".equals(event.source) && "O".equals(event.operation)) {
                return cancel(event.target, event.time);
            }
            if ("B".equals(event.source) && "O".equals(event.operation)) {
                return event.amount == 0.0 ? fault(event.target, event.time) : recover(event.target, event.time);
            }
            if ("C".equals(event.source) && "O".equals(event.operation)) {
                return modifyAmount(event.target, event.amount, event.time);
            }
            return "未识别事件";
        }

        private String addRequest(ScenarioEvent event) {
            if (waitingArea.size() >= WAITING_AREA_SIZE) {
                return event.target + " 未进入等候区：等候区已满";
            }
            ScenarioVehicle vehicle = new ScenarioVehicle(
                    event.target,
                    event.operation,
                    event.amount,
                    event.time,
                    ++sequence
            );
            waitingArea.add(vehicle);
            dispatchAll(event.time);
            return "";
        }

        private String cancel(String carId, LocalDateTime time) {
            for (int i = 0; i < waitingArea.size(); i++) {
                if (waitingArea.get(i).carId.equals(carId)) {
                    waitingArea.remove(i);
                    dispatchAll(time);
                    return "";
                }
            }
            for (ScenarioPile pile : piles.values()) {
                for (int i = 0; i < pile.queue.size(); i++) {
                    ScenarioVehicle vehicle = pile.queue.get(i);
                    if (vehicle.carId.equals(carId)) {
                        if (i == 0) {
                            vehicle.stopAt(time, pile.power);
                        }
                        pile.queue.remove(i);
                        startHeadIfNeeded(pile, time);
                        dispatchAll(time);
                        return "";
                    }
                }
            }
            dispatchAll(time);
            return carId + " 无可取消请求";
        }

        private String fault(String pileId, LocalDateTime time) {
            ScenarioPile pile = piles.get(pileId);
            if (pile == null) {
                return pileId + " 不存在";
            }
            if (pile.fault) {
                return pileId + " 已处于故障状态";
            }
            List<ScenarioVehicle> affected = new ArrayList<>(pile.queue);
            for (ScenarioVehicle vehicle : affected) {
                if (vehicle == pile.queue.get(0)) {
                    vehicle.stopAt(time, pile.power);
                }
                vehicle.faultAffected = true;
            }
            pile.queue.clear();
            pile.fault = true;
            waitingArea.addAll(0, affected);
            dispatchFaultAffected(pile.mode, time);
            dispatchAll(time);
            return "";
        }

        private String recover(String pileId, LocalDateTime time) {
            ScenarioPile pile = piles.get(pileId);
            if (pile == null) {
                return pileId + " 不存在";
            }
            pile.fault = false;
            redistributeUnstarted(pile.mode, time);
            dispatchAll(time);
            return "";
        }

        private String modifyAmount(String carId, double amount, LocalDateTime time) {
            for (ScenarioVehicle vehicle : waitingArea) {
                if (vehicle.carId.equals(carId)) {
                    vehicle.targetAmount = amount;
                    dispatchAll(time);
                    return "";
                }
            }
            return carId + " 不在等候区，不能修改电量";
        }

        private void redistributeUnstarted(String mode, LocalDateTime time) {
            List<ScenarioVehicle> movable = new ArrayList<>();
            for (ScenarioPile pile : piles.values()) {
                if (!pile.mode.equals(mode) || pile.fault) {
                    continue;
                }
                List<ScenarioVehicle> keep = new ArrayList<>();
                for (int i = 0; i < pile.queue.size(); i++) {
                    ScenarioVehicle vehicle = pile.queue.get(i);
                    if (i == 0 && vehicle.isCharging()) {
                        keep.add(vehicle);
                    } else {
                        vehicle.activeStart = null;
                        movable.add(vehicle);
                    }
                }
                pile.queue.clear();
                pile.queue.addAll(keep);
            }
            movable.sort(Comparator.comparingLong(vehicle -> vehicle.sequence));
            waitingArea.addAll(0, movable);
            for (ScenarioPile pile : piles.values()) {
                startHeadIfNeeded(pile, time);
            }
        }

        private void dispatchFaultAffected(String mode, LocalDateTime time) {
            boolean moved;
            do {
                moved = dispatchOne(mode, time, true);
            } while (moved);
        }

        private void dispatchAll(LocalDateTime time) {
            boolean moved;
            do {
                moved = false;
                moved |= dispatchOne("F", time, false);
                moved |= dispatchOne("T", time, false);
            } while (moved);
        }

        private boolean dispatchOne(String mode, LocalDateTime time, boolean faultOnly) {
            Optional<ScenarioVehicle> candidate = waitingArea.stream()
                    .filter(vehicle -> vehicle.mode.equals(mode))
                    .filter(vehicle -> !faultOnly || vehicle.faultAffected)
                    .min(Comparator
                            .comparing((ScenarioVehicle vehicle) -> !vehicle.faultAffected)
                            .thenComparingLong(vehicle -> vehicle.sequence));
            if (candidate.isEmpty()) {
                return false;
            }
            ScenarioVehicle vehicle = candidate.get();
            Optional<ScenarioPile> targetPile = piles.values().stream()
                    .filter(pile -> pile.mode.equals(mode))
                    .filter(pile -> !pile.fault)
                    .filter(pile -> pile.queue.size() < QUEUE_LENGTH)
                    .min(Comparator
                            .comparingDouble((ScenarioPile pile) -> expectedFinishHours(pile, vehicle, time))
                            .thenComparing(pile -> pile.id));
            if (targetPile.isEmpty()) {
                return false;
            }
            ScenarioPile pile = targetPile.get();
            waitingArea.remove(vehicle);
            vehicle.faultAffected = false;
            pile.queue.add(vehicle);
            startHeadIfNeeded(pile, time);
            return true;
        }

        private double expectedFinishHours(ScenarioPile pile, ScenarioVehicle candidate, LocalDateTime time) {
            double queuedHours = pile.queue.stream()
                    .mapToDouble(vehicle -> vehicle.remainingAmount(time, pile.power) / pile.power)
                    .sum();
            return queuedHours + candidate.remainingAmount(time, pile.power) / pile.power;
        }

        private void startHeadIfNeeded(ScenarioPile pile, LocalDateTime time) {
            if (!pile.fault && !pile.queue.isEmpty()) {
                ScenarioVehicle head = pile.queue.get(0);
                if (!head.isCharging()) {
                    head.activeStart = time;
                }
            }
        }

        private AcceptanceDtos.AcceptanceEventRow snapshot(ScenarioEvent event, String notes) {
            return new AcceptanceDtos.AcceptanceEventRow(
                    event.time.format(TIME_FORMAT),
                    event.rawPayload,
                    pileCells("F1", event.time),
                    pileCells("F2", event.time),
                    pileCells("T1", event.time),
                    pileCells("T2", event.time),
                    pileCells("T3", event.time),
                    waitingText(),
                    notes
            );
        }

        private ScenarioDtos.StationSnapshot replaySnapshot(
                long sequence,
                String time,
                long appliedCommandSequence,
                List<String> ruleNotes
        ) {
            List<ScenarioDtos.PileState> fastPiles = new ArrayList<>();
            List<ScenarioDtos.PileState> slowPiles = new ArrayList<>();
            Map<String, ScenarioDtos.VehicleState> vehicles = new LinkedHashMap<>();
            LocalDateTime snapshotTime = LocalDateTime.of(SCENARIO_DATE, LocalTime.parse(time, TIME_FORMAT));

            for (ScenarioVehicle vehicle : waitingArea) {
                vehicles.put(vehicle.carId, vehicleState(vehicle, "WAITING", "WAITING_AREA", vehicle.chargedAmount));
            }
            for (ScenarioPile pile : piles.values()) {
                ScenarioDtos.PileState pileState = pileState(pile, vehicles, snapshotTime);
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
                Map<String, ScenarioDtos.VehicleState> vehicles,
                LocalDateTime snapshotTime
        ) {
            List<String> queue = pile.queue.stream().map(vehicle -> vehicle.carId).toList();
            for (int i = 0; i < pile.queue.size(); i++) {
                ScenarioVehicle vehicle = pile.queue.get(i);
                double chargedKwh = i == 0 && !pile.fault
                        ? vehicle.chargedAmount(snapshotTime, pile.power)
                        : vehicle.chargedAmount;
                vehicles.put(vehicle.carId, vehicleState(
                        vehicle,
                        i == 0 && !pile.fault ? "CHARGING" : "PILE_QUEUE",
                        pile.id,
                        chargedKwh
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

        private ScenarioDtos.VehicleState vehicleState(
                ScenarioVehicle vehicle,
                String state,
                String position,
                double chargedKwh
        ) {
            return new ScenarioDtos.VehicleState(
                    vehicle.carId,
                    "F".equals(vehicle.mode) ? "FAST" : "SLOW",
                    state,
                    formatNumber(vehicle.targetAmount),
                    formatNumber(chargedKwh),
                    vehicle.mode + vehicle.sequence,
                    position
            );
        }

        private List<String> pileCells(String pileId, LocalDateTime time) {
            ScenarioPile pile = piles.get(pileId);
            List<String> cells = new ArrayList<>();
            if (pile.fault) {
                cells.add("故障");
                while (cells.size() < QUEUE_LENGTH) {
                    cells.add("-");
                }
                return cells;
            }
            for (int i = 0; i < QUEUE_LENGTH; i++) {
                if (i < pile.queue.size()) {
                    ScenarioVehicle vehicle = pile.queue.get(i);
                    cells.add("(%s,%s,%s)".formatted(
                            vehicle.carId,
                            formatNumber(vehicle.chargedAmount(time, pile.power)),
                            formatMoney(vehicle.fee(time, pile.power))
                    ));
                } else {
                    cells.add("-");
                }
            }
            return cells;
        }

        private String waitingText() {
            if (waitingArea.isEmpty()) {
                return "-";
            }
            return waitingArea.stream()
                    .sorted(Comparator.comparingLong(vehicle -> vehicle.sequence))
                    .map(vehicle -> "(%s,%s,%s)".formatted(
                            vehicle.carId,
                            vehicle.mode,
                            formatNumber(vehicle.targetAmount)
                    ))
                    .reduce((left, right) -> left + "-" + right)
                    .orElse("-");
        }
    }

    private record Completion(ScenarioPile pile, LocalDateTime time) {
    }

    private static final class ScenarioPile {
        private final String id;
        private final String mode;
        private final double power;
        private final List<ScenarioVehicle> queue = new ArrayList<>();
        private boolean fault;

        private ScenarioPile(String id, String mode, double power) {
            this.id = id;
            this.mode = mode;
            this.power = power;
        }
    }

    private static final class ScenarioVehicle {
        private final String carId;
        private final String mode;
        private final LocalDateTime requestTime;
        private final long sequence;
        private double targetAmount;
        private double chargedAmount;
        private BigDecimal feeAmount = BigDecimal.ZERO;
        private LocalDateTime activeStart;
        private boolean faultAffected;

        private ScenarioVehicle(String carId, String mode, double targetAmount, LocalDateTime requestTime, long sequence) {
            this.carId = carId;
            this.mode = mode;
            this.targetAmount = targetAmount;
            this.requestTime = requestTime;
            this.sequence = sequence;
        }

        private boolean isCharging() {
            return activeStart != null;
        }

        private LocalDateTime finishTime(double power) {
            if (activeStart == null) {
                return null;
            }
            long seconds = Math.round((remainingAmount(activeStart, power) / power) * 3600.0);
            return activeStart.plusSeconds(Math.max(1, seconds));
        }

        private double chargedAmount(LocalDateTime time, double power) {
            if (activeStart == null || time.isBefore(activeStart)) {
                return chargedAmount;
            }
            double activeAmount = Duration.between(activeStart, time).toSeconds() / 3600.0 * power;
            return Math.min(targetAmount, chargedAmount + activeAmount);
        }

        private double remainingAmount(LocalDateTime time, double power) {
            return Math.max(0.0, targetAmount - chargedAmount(time, power));
        }

        private BigDecimal fee(LocalDateTime time, double power) {
            if (activeStart == null || !time.isAfter(activeStart)) {
                return feeAmount.setScale(2, RoundingMode.HALF_UP);
            }
            double activeAmount = Math.max(0.0, chargedAmount(time, power) - chargedAmount);
            BigDecimal activeFee = BILLING_SERVICE.calculateFee(activeStart, time, activeAmount, power, TARIFF_RULE).totalFee();
            return feeAmount.add(activeFee).setScale(2, RoundingMode.HALF_UP);
        }

        private void stopAt(LocalDateTime time, double power) {
            if (activeStart == null || !time.isAfter(activeStart)) {
                activeStart = null;
                return;
            }
            double newChargedAmount = chargedAmount(time, power);
            feeAmount = fee(time, power);
            chargedAmount = newChargedAmount;
            activeStart = null;
        }

        private void finishAt(LocalDateTime time, double power) {
            stopAt(time, power);
            chargedAmount = targetAmount;
        }
    }

    private static final class ScenarioEvent {
        private final LocalDateTime time;
        private final String rawPayload;
        private final String source;
        private final String target;
        private final String operation;
        private final double amount;

        private ScenarioEvent(LocalDateTime time, String rawPayload, String source, String target, String operation, double amount) {
            this.time = time;
            this.rawPayload = rawPayload;
            this.source = source;
            this.target = target;
            this.operation = operation;
            this.amount = amount;
        }

        private static ScenarioEvent parse(String raw) {
            String[] parts = raw.split(" ", 2);
            LocalTime localTime = LocalTime.parse(parts[0]);
            String payload = parts[1];
            String body = payload.substring(1, payload.length() - 1);
            String[] fields = body.split(",");
            return new ScenarioEvent(
                    LocalDateTime.of(SCENARIO_DATE, localTime),
                    payload,
                    fields[0],
                    fields[1],
                    fields[2],
                    Double.parseDouble(fields[3])
            );
        }
    }

    private static String formatNumber(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static String formatMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
