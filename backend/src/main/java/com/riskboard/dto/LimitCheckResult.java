package com.riskboard.dto;

import java.math.BigDecimal;

public record LimitCheckResult(
        boolean limitExists,
        boolean amountWithinBounds,
        BigDecimal maxAmount,
        BigDecimal threshold150Percent
) {}
