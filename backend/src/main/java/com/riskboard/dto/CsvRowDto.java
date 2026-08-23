package com.riskboard.dto;

import com.riskboard.enums.LimitType;

import java.math.BigDecimal;

/**
 * Intermediate DTO representing one parsed and validated CSV row.
 * Produced by CsvImportService, consumed by CsvRowMapper.
 */
public record CsvRowDto(
        String name,
        String ricosCode,
        String country,
        String sector,
        LimitType limitType,
        BigDecimal maxAmount,
        BigDecimal usedAmount,
        String currency
) {}
