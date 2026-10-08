package com.securebank.fraud.application.dto;

/**
 * Dashboard counters. {@code critical} / {@code high} count alerts still awaiting a decision (OPEN or
 * UNDER_REVIEW); {@code createdToday} uses the Asia/Ho_Chi_Minh business day.
 */
public record FraudAlertStats(long open, long underReview, long critical, long high, long createdToday) {
}
