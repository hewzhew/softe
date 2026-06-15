package com.bupt.charging.strategy;

public record PileQueueLoad(int occupiedPositions, double expectedWaitHours) {
    public static PileQueueLoad empty() {
        return new PileQueueLoad(0, 0.0);
    }
}
