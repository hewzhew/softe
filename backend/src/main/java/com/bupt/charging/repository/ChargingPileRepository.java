package com.bupt.charging.repository;

import com.bupt.charging.domain.ChargeMode;
import com.bupt.charging.domain.ChargingPile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChargingPileRepository extends JpaRepository<ChargingPile, Long> {
    Optional<ChargingPile> findByPileId(String pileId);

    List<ChargingPile> findByModeOrderByPileIdAsc(ChargeMode mode);
}
