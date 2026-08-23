package com.riskboard.mapper;

import com.riskboard.dto.CsvRowDto;
import com.riskboard.entity.Counterparty;
import com.riskboard.entity.RiskLimit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring", imports = LocalDateTime.class)
public interface CsvRowMapper {

    /**
     * Creates a new Counterparty from a CSV row.
     * 'id' is ignored — JPA assigns it on persist.
     */
    @Mapping(target = "id", ignore = true)
    Counterparty toCounterparty(CsvRowDto dto);

    /**
     * Updates an existing Counterparty in place (upsert pattern).
     * 'id' is preserved — only data fields are overwritten.
     */
    @Mapping(target = "id", ignore = true)
    void updateCounterparty(@MappingTarget Counterparty counterparty, CsvRowDto dto);

    /**
     * Updates an existing RiskLimit in place (upsert pattern).
     * 'id' and 'counterparty' are preserved — only data fields are overwritten.
     * 'lastUpdated' is set to now().
     */
    @Mapping(target = "id",           ignore = true)
    @Mapping(target = "counterparty", ignore = true)
    @Mapping(target = "lastUpdated",  expression = "java(LocalDateTime.now())")
    void updateRiskLimit(@MappingTarget RiskLimit riskLimit, CsvRowDto dto);
}
