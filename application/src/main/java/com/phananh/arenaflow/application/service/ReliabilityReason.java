package com.phananh.arenaflow.application.service;

/** Lý do cộng/trừ điểm uy tín. */
public enum ReliabilityReason {
    MATCH_CANCELLED_AFTER_CONFIRMED,
    MATCH_REQUEST_WITHDRAWN_AFTER_ACCEPTED,
    MATCH_COMPLETED,
    FIVE_STAR_RATING,
    REPORT_RECEIVED,
    REPORT_REJECTED_REFUND
}
