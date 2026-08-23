package com.riskboard.dto;

import com.riskboard.enums.DerogationStatus;
import com.riskboard.enums.LimitType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DerogationRequestDto(
        Long id,
        Long counterpartyId,
        String counterpartyName,
        String ricosCode,
        String requestedBy,
        BigDecimal amount,
        String reason,
        DerogationStatus status,
        LimitType limitType,
        LocalDateTime createdAt
) {}
