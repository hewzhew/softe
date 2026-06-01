package com.bupt.charging.service;

import com.bupt.charging.domain.TariffRule;
import com.bupt.charging.support.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.springframework.stereotype.Service;

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

    public record FeeResult(BigDecimal chargeFee, BigDecimal serviceFee, BigDecimal totalFee) {
    }
}
