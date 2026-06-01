package com.bupt.charging.repository;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingRequest;
import com.bupt.charging.domain.RequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargingRequestRepository extends JpaRepository<ChargingRequest, Long> {
    List<ChargingRequest> findByModeAndStatusOrderByRequestTimeAsc(ChargeMode mode, RequestStatus status);

    List<ChargingRequest> findByAssignedPileIdAndStatusOrderByPileQueuePositionAsc(String pileId, RequestStatus status);

    List<ChargingRequest> findByAssignedPileIdAndStatusInOrderByPileQueuePositionAsc(
            String pileId, Collection<RequestStatus> statuses);

    Optional<ChargingRequest> findFirstByCarIdOrderByRequestTimeDesc(String carId);

    Optional<ChargingRequest> findFirstByCarIdAndStatusInOrderByRequestTimeDesc(
            String carId, Collection<RequestStatus> statuses);

    long countByMode(ChargeMode mode);
}
