package com.riskboard.service;

import com.riskboard.dto.CsvRowDto;
import com.riskboard.dto.ImportSummary;
import com.riskboard.entity.Counterparty;
import com.riskboard.entity.RiskLimit;
import com.riskboard.enums.LimitType;
import com.riskboard.mapper.CsvRowMapper;
import com.riskboard.repository.CounterpartyRepository;
import com.riskboard.repository.RiskLimitRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private final CounterpartyRepository counterpartyRepository;
    private final RiskLimitRepository riskLimitRepository;
    private final CsvRowMapper csvRowMapper;

    public CsvImportService(CounterpartyRepository counterpartyRepository,
                            RiskLimitRepository riskLimitRepository,
                            CsvRowMapper csvRowMapper) {
        this.counterpartyRepository = counterpartyRepository;
        this.riskLimitRepository    = riskLimitRepository;
        this.csvRowMapper           = csvRowMapper;
    }

    @Transactional
    public ImportSummary importCsv(MultipartFile file) {
        List<ImportSummary.ImportError> errors = new ArrayList<>();
        int successCount = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                int lineNumber = (int) record.getRecordNumber() + 1;
                try {
                    upsert(parseRow(record));
                    successCount++;
                    log.debug("Line {} processed successfully", lineNumber);
                } catch (Exception e) {
                    log.warn("Line {} skipped: {}", lineNumber, e.getMessage());
                    errors.add(new ImportSummary.ImportError(lineNumber, e.getMessage()));
                }
            }

        } catch (Exception e) {
            log.error("Failed to parse CSV file", e);
            errors.add(new ImportSummary.ImportError(0, "Unable to parse file: " + e.getMessage()));
        }

        log.info("CSV import complete: {} success, {} errors", successCount, errors.size());
        return new ImportSummary(successCount, errors.size(), errors);
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private CsvRowDto parseRow(CSVRecord record) {
        String name          = requireColumn(record, "name");
        String ricosCode     = requireColumn(record, "ricosCode");
        String country       = requireColumn(record, "country");
        String sector        = requireColumn(record, "sector");
        String limitTypeRaw  = requireColumn(record, "limitType");
        String maxAmountRaw  = requireColumn(record, "maxAmount");
        String usedAmountRaw = requireColumn(record, "usedAmount");
        String currency      = requireColumn(record, "currency");

        LimitType limitType;
        try {
            limitType = LimitType.valueOf(limitTypeRaw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown limitType: " + limitTypeRaw);
        }

        BigDecimal maxAmount;
        BigDecimal usedAmount;
        try {
            maxAmount = new BigDecimal(maxAmountRaw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid maxAmount: " + maxAmountRaw);
        }
        try {
            usedAmount = new BigDecimal(usedAmountRaw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid usedAmount: " + usedAmountRaw);
        }

        if (maxAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("maxAmount must be >= 0, got: " + maxAmount);
        }
        if (usedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("usedAmount must be >= 0, got: " + usedAmount);
        }

        return new CsvRowDto(name, ricosCode, country, sector,
                             limitType, maxAmount, usedAmount, currency);
    }

    // ── Upsert via MapStruct ──────────────────────────────────────────────────

    private void upsert(CsvRowDto dto) {
        Counterparty counterparty = counterpartyRepository
                .findByRicosCode(dto.ricosCode())
                .map(existing -> {
                    csvRowMapper.updateCounterparty(existing, dto);
                    return existing;
                })
                .orElseGet(() -> csvRowMapper.toCounterparty(dto));

        counterparty = counterpartyRepository.save(counterparty);

        RiskLimit riskLimit = riskLimitRepository
                .findByCounterpartyRicosCodeAndLimitType(dto.ricosCode(), dto.limitType())
                .orElseGet(RiskLimit::new);

        csvRowMapper.updateRiskLimit(riskLimit, dto);
        riskLimit.setCounterparty(counterparty);
        riskLimitRepository.save(riskLimit);
    }

    private String requireColumn(CSVRecord record, String column) {
        if (!record.isMapped(column)) {
            throw new IllegalArgumentException("Missing column: " + column);
        }
        String value = record.get(column);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Empty value for column: " + column);
        }
        return value;
    }
}
