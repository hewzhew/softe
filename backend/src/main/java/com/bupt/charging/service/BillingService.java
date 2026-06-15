package com.bupt.charging.service;

import com.bupt.charging.domain.Bill;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingSession;
import com.bupt.charging.domain.DetailedList;
import com.bupt.charging.domain.TariffRule;
import com.bupt.charging.dto.BillingDtos;
import com.bupt.charging.repository.BillRepository;
import com.bupt.charging.repository.DetailedListRepository;
import com.bupt.charging.repository.TariffRuleRepository;
import com.bupt.charging.support.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingService {
    private static final List<LocalTime> PRICE_BOUNDARIES = List.of(
            LocalTime.of(7, 0),
            LocalTime.of(10, 0),
            LocalTime.of(15, 0),
            LocalTime.of(18, 0),
            LocalTime.of(21, 0),
            LocalTime.of(23, 0)
    );

    private BillRepository billRepository;
    private DetailedListRepository detailedListRepository;
    private TariffRuleRepository tariffRuleRepository;

    public BillingService() {
    }

    @Autowired
    public BillingService(
            BillRepository billRepository,
            DetailedListRepository detailedListRepository,
            TariffRuleRepository tariffRuleRepository
    ) {
        this.billRepository = billRepository;
        this.detailedListRepository = detailedListRepository;
        this.tariffRuleRepository = tariffRuleRepository;
    }

    public FeeResult calculateFee(LocalDateTime start, LocalDateTime end, double amount, double power, TariffRule rule) {
        if (!end.isAfter(start)) {
            throw new BusinessException("end time must be after start time");
        }
        if (amount < 0 || power <= 0) {
            throw new BusinessException("invalid charge amount or power");
        }

        double totalSeconds = Duration.between(start, end).toSeconds();
        BigDecimal chargeFee = BigDecimal.ZERO;
        LocalDateTime cursor = start;
        while (cursor.isBefore(end)) {
            LocalDateTime next = nextPriceBoundary(cursor, end);
            double seconds = Duration.between(cursor, next).toSeconds();
            double slotAmount = amount * (seconds / totalSeconds);
            chargeFee = chargeFee.add(rule.priceAt(cursor.toLocalTime()).multiply(BigDecimal.valueOf(slotAmount)));
            cursor = next;
        }

        BigDecimal serviceFee = rule.getServicePrice().multiply(BigDecimal.valueOf(amount));
        return new FeeResult(money(chargeFee), money(serviceFee), money(chargeFee.add(serviceFee)));
    }

    @Transactional
    public BillingDtos.BillResponse createBillForSession(
            ChargingSession session,
            ChargingPile pile,
            double actualAmount,
            LocalDateTime endTime
    ) {
        requireRepositories();
        LocalDateTime startTime = session.getStartTime();
        LocalDateTime effectiveEnd = endTime.isAfter(startTime)
                ? endTime
                : startTime.plusMinutes(Math.max(1, Math.round((actualAmount / pile.getPower()) * 60.0)));
        if (Duration.between(startTime, effectiveEnd).toSeconds() <= 0) {
            effectiveEnd = startTime.plusMinutes(1);
        }
        double durationHours = Duration.between(startTime, effectiveEnd).toMinutes() / 60.0;
        TariffRule rule = tariffRuleRepository.findFirstByOrderByIdDesc().orElseGet(TariffRule::defaults);
        FeeResult fee = calculateFee(startTime, effectiveEnd, actualAmount, pile.getPower(), rule);
        Bill bill = billRepository.save(new Bill(
                session.getCarId(),
                effectiveEnd.toLocalDate(),
                effectiveEnd,
                fee.chargeFee(),
                fee.serviceFee(),
                fee.totalFee()
        ));
        DetailedList detail = detailedListRepository.save(new DetailedList(
                bill.getId(),
                session.getCarId(),
                pile.getPileId(),
                actualAmount,
                durationHours,
                startTime,
                effectiveEnd,
                fee.chargeFee(),
                fee.serviceFee(),
                fee.totalFee()
        ));
        return toBillResponse(bill, detail);
    }

    public List<BillingDtos.BillResponse> queryBills(String carId, LocalDate date) {
        requireRepositories();
        return billRepository.findByCarIdAndBillDateOrderByGeneratedAtDesc(carId, date).stream()
                .map(bill -> {
                    DetailedList detail = detailedListRepository.findByBillId(bill.getId()).stream()
                            .findFirst()
                            .orElse(null);
                    return toBillResponse(bill, detail);
                })
                .toList();
    }

    public List<BillingDtos.DetailedListResponse> queryDetails(Long billId) {
        requireRepositories();
        return detailedListRepository.findByBillId(billId).stream()
                .map(this::toDetailResponse)
                .toList();
    }

    private LocalDateTime nextPriceBoundary(LocalDateTime cursor, LocalDateTime end) {
        LocalDateTime next = end;
        for (LocalTime boundary : PRICE_BOUNDARIES) {
            LocalDateTime candidate = LocalDateTime.of(cursor.toLocalDate(), boundary);
            if (candidate.isAfter(cursor) && candidate.isBefore(next)) {
                next = candidate;
            }
        }
        LocalDateTime nextDayValleyEnd = LocalDateTime.of(cursor.toLocalDate().plusDays(1), LocalTime.of(7, 0));
        if (nextDayValleyEnd.isAfter(cursor) && nextDayValleyEnd.isBefore(next)) {
            next = nextDayValleyEnd;
        }
        return next;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BillingDtos.BillResponse toBillResponse(Bill bill, DetailedList detail) {
        return new BillingDtos.BillResponse(
                bill.getId(),
                bill.getCarId(),
                bill.getBillDate(),
                detail == null ? null : detail.getPileId(),
                detail == null ? 0.0 : detail.getChargeAmount(),
                detail == null ? 0.0 : detail.getChargeDuration(),
                detail == null ? null : detail.getStartTime(),
                detail == null ? null : detail.getEndTime(),
                bill.getTotalChargeFee(),
                bill.getTotalServiceFee(),
                bill.getTotalFee()
        );
    }

    private BillingDtos.DetailedListResponse toDetailResponse(DetailedList detail) {
        return new BillingDtos.DetailedListResponse(
                detail.getId(),
                detail.getBillId(),
                detail.getCarId(),
                detail.getPileId(),
                detail.getChargeAmount(),
                detail.getChargeDuration(),
                detail.getStartTime(),
                detail.getEndTime(),
                detail.getChargeFee(),
                detail.getServiceFee(),
                detail.getSubtotalFee()
        );
    }

    private void requireRepositories() {
        if (billRepository == null || detailedListRepository == null || tariffRuleRepository == null) {
            throw new BusinessException("billing repositories are not configured");
        }
    }

    public record FeeResult(BigDecimal chargeFee, BigDecimal serviceFee, BigDecimal totalFee) {
    }
}
