package com.riskboard.service;

import com.riskboard.enums.AlertLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RiskCalculationService — alert level boundaries")
class RiskCalculationServiceTest {

    private final RiskCalculationService service = new RiskCalculationService();

    // usageRate computation

    @Test
    @DisplayName("usageRate = usedAmount / maxAmount * 100")
    void computeUsageRate_nominal() {
        // Given
        BigDecimal used = new BigDecimal("32000000");
        BigDecimal max  = new BigDecimal("50000000");

        // When
        BigDecimal rate = service.computeUsageRate(used, max);

        // Then — BNP Paribas: 64 %
        assertThat(rate).isEqualByComparingTo(new BigDecimal("64.0000"));
    }

    @Test
    @DisplayName("usageRate returns 0 when maxAmount is zero (avoid division by zero)")
    void computeUsageRate_zeroMax() {
        BigDecimal rate = service.computeUsageRate(BigDecimal.TEN, BigDecimal.ZERO);
        assertThat(rate).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // Alert level — GREEN (usageRate < 70)

    @Test
    @DisplayName("usageRate < 70 → GREEN")
    void alertLevel_green_below70() {
        // Given — usageRate = 64 (BNP Paribas)
        BigDecimal usageRate = new BigDecimal("64.0000");

        // When
        AlertLevel level = service.computeAlertLevel(usageRate);

        // Then
        assertThat(level).isEqualTo(AlertLevel.GREEN);
    }

    @Test
    @DisplayName("usageRate = 0 → GREEN")
    void alertLevel_green_zero() {
        assertThat(service.computeAlertLevel(BigDecimal.ZERO)).isEqualTo(AlertLevel.GREEN);
    }

    @Test
    @DisplayName("usageRate = 69.9999 → GREEN (just below boundary)")
    void alertLevel_green_justBelowOrange() {
        assertThat(service.computeAlertLevel(new BigDecimal("69.9999"))).isEqualTo(AlertLevel.GREEN);
    }

    // Alert level — ORANGE (70 ≤ usageRate ≤ 90, bornes INCLUSES — PIÈGE)

    @Test
    @DisplayName("usageRate = 70 (lower bound) → ORANGE")
    void alertLevel_orange_lowerBoundExact() {
        // Given — boundary value 70 must be ORANGE, not GREEN
        BigDecimal usageRate = new BigDecimal("70");

        // When
        AlertLevel level = service.computeAlertLevel(usageRate);

        // Then
        assertThat(level).isEqualTo(AlertLevel.ORANGE);
    }

    @Test
    @DisplayName("usageRate = 80 → ORANGE")
    void alertLevel_orange_mid() {
        // Given — Credit Suisse Finance: 80 %
        BigDecimal usageRate = new BigDecimal("80");

        // When
        AlertLevel level = service.computeAlertLevel(usageRate);

        // Then
        assertThat(level).isEqualTo(AlertLevel.ORANGE);
    }

    @Test
    @DisplayName("usageRate = 90 (upper bound) → ORANGE")
    void alertLevel_orange_upperBoundExact() {
        // Given — boundary value 90 must be ORANGE, not RED
        BigDecimal usageRate = new BigDecimal("90");

        // When
        AlertLevel level = service.computeAlertLevel(usageRate);

        // Then
        assertThat(level).isEqualTo(AlertLevel.ORANGE);
    }

    // Alert level — RED (usageRate > 90)

    @Test
    @DisplayName("usageRate = 90.0001 → RED (just above boundary)")
    void alertLevel_red_justAboveOrange() {
        assertThat(service.computeAlertLevel(new BigDecimal("90.0001"))).isEqualTo(AlertLevel.RED);
    }

    @Test
    @DisplayName("usageRate > 90 → RED")
    void alertLevel_red_above90() {
        // Given — Deutsche Bank
        BigDecimal usageRate = new BigDecimal("92.5");

        // When
        AlertLevel level = service.computeAlertLevel(usageRate);

        // Then
        assertThat(level).isEqualTo(AlertLevel.RED);
    }

    @Test
    @DisplayName("usageRate = 100 → RED")
    void alertLevel_red_full() {
        assertThat(service.computeAlertLevel(new BigDecimal("100"))).isEqualTo(AlertLevel.RED);
    }
}
