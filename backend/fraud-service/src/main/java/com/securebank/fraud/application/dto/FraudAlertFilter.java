package com.securebank.fraud.application.dto;

import com.securebank.fraud.domain.FraudAlertStatus;
import com.securebank.fraud.domain.RiskLevel;

import java.util.UUID;

/** Optional list filters; null means "any". */
public record FraudAlertFilter(FraudAlertStatus status, RiskLevel riskLevel, UUID customerId) {
}
