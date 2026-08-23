package com.riskboard.service;

import com.riskboard.entity.*;
import com.riskboard.enums.LimitType;
import com.riskboard.repository.CounterpartyRepository;
import com.riskboard.repository.RiskLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("RiskLimitService — exposure aggregation by sector")
class RiskLimitServiceIntegrationTest {

    @Autowired
    private RiskLimitService riskLimitService;

    @Autowired
    private CounterpartyRepository counterpartyRepository;

    @Autowired
    private RiskLimitRepository riskLimitRepository;

    @BeforeEach
    void setUp() {
        // Reference dataset matching the 11-line CSV
        createLimit("BNP PARIBAS",            "RICOS48213", "FR", "Banking",     LimitType.CREDIT,    "50000000", "32000000", "EUR");
        createLimit("DEUTSCHE BANK AG",        "RICOS72905", "DE", "Banking",     LimitType.MARKET,    "20000000", "18500000", "EUR");
        createLimit("GOLDMAN SACHS",           "RICOS36180", "US", "Banking",     LimitType.LIQUIDITY, "15000000",  "4200000", "USD");
        createLimit("SOCIETE GENERALE",        "RICOS91427", "FR", "Banking",     LimitType.CREDIT,    "30000000", "29500000", "EUR");
        createLimit("TOTALENERGIES SE",        "RICOS05364", "FR", "Energy",      LimitType.CREDIT,    "25000000", "15000000", "EUR");
        createLimit("SHELL PLC",               "RICOS68792", "UK", "Energy",      LimitType.MARKET,    "18000000", "17200000", "GBP");
        createLimit("AIRBUS SE",               "RICOS23951", "FR", "Industrials", LimitType.CREDIT,    "12000000",  "5400000", "EUR");
        createLimit("MICROSOFT CORP",          "RICOS87046", "US", "Technology",  LimitType.LIQUIDITY, "40000000", "10000000", "USD");
        createLimit("APPLE INC",               "RICOS14638", "US", "Technology",  LimitType.CREDIT,    "35000000", "24000000", "USD");
        createLimit("UNICREDIT SPA",           "RICOS59217", "IT", "Banking",     LimitType.MARKET,    "10000000",  "9500000", "EUR");
        createLimit("CREDIT SUISSE FINANCE",   "RICOS30874", "CH", "Banking",     LimitType.CREDIT,    "20000000", "16000000", "CHF");
    }

    @Test
    @DisplayName("Banking sector: sum of usedAmount across all Banking limits")
    void exposure_banking() {
        // Given — Banking usedAmounts
        Map<String, BigDecimal> exposure = riskLimitService.getExposureBySector();

        assertThat(exposure).containsKey("Banking");
        assertThat(exposure.get("Banking")).isEqualByComparingTo(new BigDecimal("109700000"));
    }

    @Test
    @DisplayName("Energy sector: 15M + 17.2M = 32.2M")
    void exposure_energy() {
        Map<String, BigDecimal> exposure = riskLimitService.getExposureBySector();

        assertThat(exposure).containsKey("Energy");
        assertThat(exposure.get("Energy")).isEqualByComparingTo(new BigDecimal("32200000"));
    }

    @Test
    @DisplayName("Industrials sector: 5.4M")
    void exposure_industrials() {
        Map<String, BigDecimal> exposure = riskLimitService.getExposureBySector();

        assertThat(exposure).containsKey("Industrials");
        assertThat(exposure.get("Industrials")).isEqualByComparingTo(new BigDecimal("5400000"));
    }

    @Test
    @DisplayName("Technology sector: 10M + 24M = 34M")
    void exposure_technology() {
        Map<String, BigDecimal> exposure = riskLimitService.getExposureBySector();

        assertThat(exposure).containsKey("Technology");
        assertThat(exposure.get("Technology")).isEqualByComparingTo(new BigDecimal("34000000"));
    }

    @Test
    @DisplayName("All four sectors are present in the result")
    void exposure_allSectorsPresent() {
        Map<String, BigDecimal> exposure = riskLimitService.getExposureBySector();

        assertThat(exposure).containsKeys("Banking", "Energy", "Industrials", "Technology");
    }

    // Helper

    private void createLimit(String name, String ricosCode, String country, String sector,
                             LimitType limitType, String maxAmount, String usedAmount, String currency) {
        Counterparty cp = Counterparty.builder()
                .name(name).ricosCode(ricosCode).country(country).sector(sector)
                .build();
        cp = counterpartyRepository.save(cp);

        RiskLimit rl = RiskLimit.builder()
                .counterparty(cp)
                .limitType(limitType)
                .maxAmount(new BigDecimal(maxAmount))
                .usedAmount(new BigDecimal(usedAmount))
                .currency(currency)
                .lastUpdated(LocalDateTime.now())
                .build();
        riskLimitRepository.save(rl);
    }
}
