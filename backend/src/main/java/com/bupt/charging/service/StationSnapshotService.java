package com.bupt.charging.service;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.domain.ChargingSession;
import com.bupt.charging.domain.PileStatus;
import com.bupt.charging.domain.RequestStatus;
import com.bupt.charging.domain.SessionStatus;
import com.bupt.charging.dto.StationDtos;
import com.bupt.charging.repository.ChargingPileRepository;
import com.bupt.charging.repository.ChargingRequestRepository;
import com.bupt.charging.repository.ChargingSessionRepository;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class StationSnapshotService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChargingPileRepository pileRepository;
    private final ChargingRequestRepository requestRepository;
    private final ChargingSessionRepository sessionRepository;
    private final StationClockService stationClockService;

    public StationSnapshotService(
            ChargingPileRepository pileRepository,
            ChargingRequestRepository requestRepository,
            ChargingSessionRepository sessionRepository,
            StationClockService stationClockService
    ) {
        this.pileRepository = pileRepository;
        this.requestRepository = requestRepository;
        this.sessionRepository = sessionRepository;
        this.stationClockService = stationClockService;
    }

    public StationDtos.StationSnapshot currentSnapshot() {
        List<ChargingPile> piles = pileRepository.findAll().stream()
                .sorted(Comparator.comparing(ChargingPile::getPileId))
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

        int pileQueueCount = (int) vehicles.values().stream()
                .filter(vehicle -> !"WAITING_AREA".equals(vehicle.position()))
                .count();
        int faultPileCount = (int) piles.stream()
                .filter(pile -> pile.getStatus() == PileStatus.FAULT)
                .count();
        int activePileCount = (int) piles.stream()
                .filter(pile -> pile.getStatus() != PileStatus.FAULT)
                .filter(pile -> pile.getStatus() != PileStatus.OFFLINE)
                .count();

        return new StationDtos.StationSnapshot(
                stationClockService.currentStationTime().format(TIME_FORMAT),
                new StationDtos.StationState(waitingArea, fastPiles, slowPiles),
                vehicles,
                new StationDtos.Metrics(waitingArea.size(), pileQueueCount, faultPileCount, activePileCount),
                "LIVE",
                new StationDtos.SourceSummary(
                        "LIVE_MANUAL",
                        "当前站点",
                        List.of("LIVE_MANUAL", "SYSTEM_DERIVED"),
                        waitingArea.size() + pileQueueCount,
                        1
                )
        );
    }

    private StationDtos.PileState pileState(
            ChargingPile pile,
            Map<String, StationDtos.VehicleState> vehicles
    ) {
        List<ChargingRequest> queue = requestRepository
                .findByAssignedPileIdAndStatusInOrderByPileQueuePositionAsc(
                        pile.getPileId(),
                        List.of(RequestStatus.CHARGING, RequestStatus.PILE_QUEUE)
                );
        for (ChargingRequest request : queue) {
            vehicles.put(request.getCarId(), vehicleState(request, pile.getPileId()));
        }
        return new StationDtos.PileState(
                pile.getPileId(),
                pile.getMode().name(),
                pile.getStatus().name(),
                pile.getCurrentCarId(),
                queue.stream().map(ChargingRequest::getCarId).toList(),
                format(pile.getPower())
        );
    }

    private StationDtos.VehicleState vehicleState(ChargingRequest request, String position) {
        return new StationDtos.VehicleState(
                request.getCarId(),
                request.getMode().name(),
                request.getStatus().name(),
                format(request.getRequestAmount()),
                format(chargedAmount(request)),
                request.getQueueNum(),
                position
        );
    }

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

    private String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
