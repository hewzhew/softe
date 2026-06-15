package com.bupt.charging.domain;

public enum StationEventType {
    ChargeRequestSubmitted,
    ChargeRequestCancelled,
    RequestedAmountChanged,
    PileFaulted,
    PileRecovered,
    ChargingCompleted,
    BillGenerated
}
