package com.bupt.charging.strategy;

import com.bupt.charging.domain.ChargingRequest;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TimeOrderFaultStrategy {
    public List<ChargingRequest> order(List<ChargingRequest> requests) {
        return requests.stream()
                .sorted(Comparator
                        .comparing(ChargingRequest::getRequestTime)
                        .thenComparingLong(ChargingRequest::getQueueSequence))
                .toList();
    }
}
