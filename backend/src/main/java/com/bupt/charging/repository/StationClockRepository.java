package com.bupt.charging.repository;

import com.bupt.charging.domain.StationClock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationClockRepository extends JpaRepository<StationClock, Long> {
}
