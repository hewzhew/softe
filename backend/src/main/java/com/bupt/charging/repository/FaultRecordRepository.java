package com.bupt.charging.repository;

import com.bupt.charging.domain.FaultRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaultRecordRepository extends JpaRepository<FaultRecord, Long> {
    Optional<FaultRecord> findFirstByPileIdAndStatusOrderByFaultTimeDesc(String pileId, String status);
}
