package com.riskboard.dto;

import com.riskboard.enums.LimitType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateDerogationRequest(
        @NotNull(message = "Counterparty ID is required")
        Long counterpartyId,

        @NotNull(message = "Limit type is required")
        LimitType limitType,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.0001", message = "Amount must be strictly greater than 0")
        BigDecimal amount,

        @NotBlank(message = "Reason is required")
        @Size(min = 20, message = "Reason must be at least 20 characters")
        String reason,

        @NotBlank(message = "RequestedBy is required")
        @Size(min = 6, message = "RequestedBy must be at least 6 characters")
        String requestedBy
) {}
