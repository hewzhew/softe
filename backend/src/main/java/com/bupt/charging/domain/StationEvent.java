package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class StationEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime eventTime;

    @Column(nullable = false)
    private LocalDateTime receivedTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StationEventSourceType sourceType;

    @Column(nullable = false)
    private String sourceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StationEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventCommitState commitState;

    @Column(nullable = false)
    private String targetId;

    private String ownerName;

    private double carCapacity;

    @Enumerated(EnumType.STRING)
    private ChargeMode mode;

    private double amount;

    @Column(nullable = false)
    private long sequence;

    @Column(nullable = false)
    private boolean applied;

    private LocalDateTime appliedAt;

    @Column(length = 1024)
    private String rawText;

    protected StationEvent() {
    }

    public StationEvent(
            LocalDateTime eventTime,
            LocalDateTime receivedTime,
            StationEventSourceType sourceType,
            String sourceName,
            StationEventType eventType,
            EventCommitState commitState,
            String targetId,
            String ownerName,
            double carCapacity,
            ChargeMode mode,
            double amount,
            long sequence,
            String rawText
    ) {
        this.eventTime = eventTime;
        this.receivedTime = receivedTime;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.eventType = eventType;
        this.commitState = commitState;
        this.targetId = targetId;
        this.ownerName = ownerName;
        this.carCapacity = carCapacity;
        this.mode = mode;
        this.amount = amount;
        this.sequence = sequence;
        this.rawText = rawText;
    }

    public void markApplied(LocalDateTime appliedAt) {
        this.applied = true;
        this.appliedAt = appliedAt;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public LocalDateTime getReceivedTime() {
        return receivedTime;
    }

    public StationEventSourceType getSourceType() {
        return sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public StationEventType getEventType() {
        return eventType;
    }

    public EventCommitState getCommitState() {
        return commitState;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public double getCarCapacity() {
        return carCapacity;
    }

    public ChargeMode getMode() {
        return mode;
    }

    public double getAmount() {
        return amount;
    }

    public long getSequence() {
        return sequence;
    }

    public boolean isApplied() {
        return applied;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public String getRawText() {
        return rawText;
    }
}
