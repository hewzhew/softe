package com.bupt.charging.repository;

import com.bupt.charging.domain.StationClock;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StationClockRepository extends JpaRepository<StationClock, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select clock from StationClock clock where clock.id = :id")
    Optional<StationClock> findByIdForUpdate(@Param("id") Long id);
}
