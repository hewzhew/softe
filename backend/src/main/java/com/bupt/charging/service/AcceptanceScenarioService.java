package com.bupt.charging.service;

import com.bupt.charging.domain.TariffRule;
import com.bupt.charging.dto.AcceptanceDtos;
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
