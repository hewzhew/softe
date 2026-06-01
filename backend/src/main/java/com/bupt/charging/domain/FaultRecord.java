package com.bupt.charging.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;

@Entity
public class FaultRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String pileId;

    @Column(nullable = false)
    private String strategy;

    @Column(nullable = false)
    private LocalDateTime faultTime;

    private LocalDateTime recoveredAt;

    @Column(nullable = false)
    private String status;

    private String resultSummary;

    protected FaultRecord() {
    }

    public FaultRecord(String pileId, String strategy, LocalDateTime faultTime) {
        this.pileId = pileId;
        this.strategy = strategy;
        this.faultTime = faultTime;
        this.status = "OPEN";
    }

    public void close(LocalDateTime recoveredAt, String resultSummary) {
        this.recoveredAt = recoveredAt;
        this.resultSummary = resultSummary;
        this.status = "CLOSED";
    }

    public void updateResult(String resultSummary) {
        this.resultSummary = resultSummary;
    }

    public Long getId() {
        return id;
    }

    public String getPileId() {
        return pileId;
    }

    public String getStrategy() {
        return strategy;
    }

    public LocalDateTime getFaultTime() {
        return faultTime;
    }

    public LocalDateTime getRecoveredAt() {
        return recoveredAt;
    }

    public String getStatus() {
        return status;
    }

    public String getResultSummary() {
        return resultSummary;
    }
}
