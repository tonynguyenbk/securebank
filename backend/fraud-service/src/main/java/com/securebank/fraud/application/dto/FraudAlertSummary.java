package com.securebank.fraud.application.dto;

import com.securebank.fraud.domain.FraudAlert;
import com.securebank.fraud.domain.FraudAlertStatus;
import com.securebank.fraud.domain.RiskLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FraudAlertSummary(
        UUID id,
        UUID transactionId,
        String transactionReference,
        UUID customerId,
        String customerName,
        UUID sourceAccountId,
        String sourceAccountNumber,
        BigDecimal amount,
        String currency,
        int riskScore,
        RiskLevel riskLevel,
        FraudAlertStatus status,
        Instant createdAt
) {
    public static FraudAlertSummary from(FraudAlert a) {
        return new FraudAlertSummary(a.getId(), a.getTransactionId(), a.getTransactionReference(), a.getCustomerId(),
                a.getCustomerName(), a.getSourceAccountId(), a.getSourceAccountNumber(), a.getAmount(),
                a.getCurrency(), a.getRiskScore(), a.getRiskLevel(), a.getStatus(), a.getCreatedAt());
    }
}
