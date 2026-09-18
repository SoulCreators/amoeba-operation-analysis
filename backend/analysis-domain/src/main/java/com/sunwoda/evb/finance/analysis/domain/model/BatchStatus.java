package com.sunwoda.evb.finance.analysis.domain.model;

public enum BatchStatus {
    DRAFT,
    CHECKING,
    READY_FOR_CALCULATION,
    CALCULATING,
    CALCULATED,
    CONFIRMED,
    PUBLISHED,
    FAILED
}
