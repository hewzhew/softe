package com.bupt.charging.repository;

import com.bupt.charging.domain.StationConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationConfigRepository extends JpaRepository<StationConfig, Long> {
    Optional<StationConfig> findFirstByOrderByIdDesc();
}
