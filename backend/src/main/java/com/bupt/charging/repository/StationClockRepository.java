package com.bupt.charging.repository;

import com.bupt.charging.domain.StationClock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationClockRepository extends JpaRepository<StationClock, Long> {
    Optional<StationClock> findFirstByOrderByIdAsc();
}
