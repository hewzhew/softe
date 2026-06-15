package com.bupt.charging.repository;

import com.bupt.charging.domain.ChargingSession;
import com.bupt.charging.domain.SessionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargingSessionRepository extends JpaRepository<ChargingSession, Long> {
    List<ChargingSession> findByStatusOrderByStartTimeAsc(SessionStatus status);

    Optional<ChargingSession> findFirstByCarIdAndStatusOrderByStartTimeDesc(String carId, SessionStatus status);

    Optional<ChargingSession> findFirstByPileIdAndStatusOrderByStartTimeDesc(String pileId, SessionStatus status);
}
