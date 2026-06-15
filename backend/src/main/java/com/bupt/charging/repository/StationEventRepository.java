package com.bupt.charging.repository;

import com.bupt.charging.domain.EventCommitState;
import com.bupt.charging.domain.StationEvent;
import com.bupt.charging.domain.StationEventSourceType;
import com.bupt.charging.domain.StationEventType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StationEventRepository extends JpaRepository<StationEvent, Long> {
    List<StationEvent> findAllByOrderByEventTimeAscSequenceAsc();

    List<StationEvent> findByAppliedFalseAndEventTimeLessThanEqualOrderByEventTimeAscSequenceAsc(
            LocalDateTime eventTime);

    Optional<StationEvent> findFirstByAppliedFalseAndCommitStateAndEventTimeLessThanEqualOrderByEventTimeAscSequenceAsc(
            EventCommitState commitState,
            LocalDateTime eventTime);

    Optional<StationEvent> findFirstByAppliedFalseAndCommitStateOrderByEventTimeAscSequenceAsc(
            EventCommitState commitState);

    boolean existsBySourceType(StationEventSourceType sourceType);

    boolean existsByAppliedFalseAndCommitStateAndEventTypeAndTargetIdAndEventTimeGreaterThanEqual(
            EventCommitState commitState,
            StationEventType eventType,
            String targetId,
            LocalDateTime eventTime);

    boolean existsByAppliedFalseAndCommitStateAndEventTypeAndTargetIdIn(
            EventCommitState commitState,
            StationEventType eventType,
            Collection<String> targetIds);
}
