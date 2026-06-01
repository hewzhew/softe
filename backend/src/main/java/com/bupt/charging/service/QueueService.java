package com.bupt.charging.service;

import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.dto.QueueDtos;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QueueService {
    private final ChargingRequestRepository requestRepository;
    private final ChargingPileRepository pileRepository;

    public QueueService(ChargingRequestRepository requestRepository, ChargingPileRepository pileRepository) {
        this.requestRepository = requestRepository;
        this.pileRepository = pileRepository;
    }

    public QueueDtos.QueueStateResponse queryQueueState() {
        List<QueueDtos.QueueItemResponse> waiting = new ArrayList<>();
        requestRepository.findAll().stream()
                .filter(request -> request.getStatus() == RequestStatus.WAITING_AREA)
                .sorted((a, b) -> a.getRequestTime().compareTo(b.getRequestTime()))
                .forEach(request -> waiting.add(toWaitingItem(request)));

        List<QueueDtos.QueueItemResponse> pileQueues = new ArrayList<>();
        for (ChargingPile pile : pileRepository.findAll()) {
            List<ChargingRequest> queue = requestRepository
                    .findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc(
                            pile.getPileId(), RequestStatus.PILE_QUEUE);
            double wait = 0.0;
            for (ChargingRequest request : queue) {
                pileQueues.add(toPileItem(request, wait));
                wait += request.getRequestAmount() / pile.getPower();
            }
        }
        return new QueueDtos.QueueStateResponse(waiting, pileQueues);
    }

    private QueueDtos.QueueItemResponse toWaitingItem(ChargingRequest request) {
        return new QueueDtos.QueueItemResponse(
                request.getCarId(),
                request.getCarCapacity(),
                request.getRequestAmount(),
                request.getMode(),
                request.getStatus(),
                request.getQueueNum(),
                null,
                0,
                0.0,
                request.getRequestTime()
        );
    }

    private QueueDtos.QueueItemResponse toPileItem(ChargingRequest request, double waitTime) {
        return new QueueDtos.QueueItemResponse(
                request.getCarId(),
                request.getCarCapacity(),
                request.getRequestAmount(),
                request.getMode(),
                request.getStatus(),
                request.getQueueNum(),
                request.getAssignedPileId(),
                request.getPileQueuePosition(),
                waitTime,
                request.getRequestTime()
        );
    }
}
