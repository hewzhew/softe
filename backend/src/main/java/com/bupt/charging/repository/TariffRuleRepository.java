package com.bupt.charging.repository;

import com.bupt.charging.domain.TariffRule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TariffRuleRepository extends JpaRepository<TariffRule, Long> {
    Optional<TariffRule> findFirstByOrderByIdDesc();
}
