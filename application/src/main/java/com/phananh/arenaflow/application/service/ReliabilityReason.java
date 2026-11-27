package com.phananh.arenaflow.application.service;

/** Lý do thay đổi điểm uy tín (đội hoặc cá nhân). */
public enum ReliabilityReason {
    MATCH_CANCELLED_AFTER_CONFIRMED,
    MATCH_REQUEST_WITHDRAWN_AFTER_ACCEPTED,
    MATCH_COMPLETED,
    FIVE_STAR_RATING,
    REPORT_RECEIVED,
    REPORT_REJECTED_REFUND,
    MERCENARY_COMPLETED,
    MERCENARY_WITHDRAWN_AFTER_APPROVED
}
