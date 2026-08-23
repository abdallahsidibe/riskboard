package com.riskboard.dto;

import com.riskboard.enums.AlertLevel;
import com.riskboard.enums.LimitType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record RiskLimitDto(
        Long id,
        Long counterpartyId,
        String counterpartyName,
        String ricosCode,
        String country,
        String sector,
        LimitType limitType,
        BigDecimal maxAmount,
        BigDecimal usedAmount,
        String currency,
        BigDecimal usageRate,
        AlertLevel alertLevel,
        LocalDateTime lastUpdated
) {}
