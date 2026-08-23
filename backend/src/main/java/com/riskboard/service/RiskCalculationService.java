package com.riskboard.service;

import com.riskboard.enums.AlertLevel;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure domain service responsible for risk calculations.
 * Kept stateless and free of JPA dependencies for easy unit testing.
 */
@Service
public class RiskCalculationService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ORANGE_LOWER = BigDecimal.valueOf(70);
    private static final BigDecimal ORANGE_UPPER = BigDecimal.valueOf(90);

    /**
     * Computes usageRate = (usedAmount / maxAmount) * 100.
     * Returns BigDecimal.ZERO when maxAmount is zero to avoid division by zero.
     */
    public BigDecimal computeUsageRate(BigDecimal usedAmount, BigDecimal maxAmount) {
        if (maxAmount == null || maxAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return usedAmount.multiply(HUNDRED)
                .divide(maxAmount, 4, RoundingMode.HALF_UP);
    }

    /**
     * Alert level rules (bornes exactes) :
     *  - usageRate < 70  → GREEN
     *  - 70 ≤ usageRate ≤ 90 → ORANGE
     *  - usageRate > 90  → RED
     */
    public AlertLevel computeAlertLevel(BigDecimal usageRate) {
        if (usageRate.compareTo(ORANGE_LOWER) < 0) {
            return AlertLevel.GREEN;
        } else if (usageRate.compareTo(ORANGE_UPPER) <= 0) {
            return AlertLevel.ORANGE;
        } else {
            return AlertLevel.RED;
        }
    }
}
