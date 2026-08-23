package com.riskboard.mapper;

import com.riskboard.dto.RiskLimitDto;
import com.riskboard.entity.RiskLimit;
import com.riskboard.service.RiskCalculationService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class RiskLimitMapper {

    @Autowired
    protected RiskCalculationService riskCalculationService;

    @Mapping(target = "counterpartyId",   source = "counterparty.id")
    @Mapping(target = "counterpartyName", source = "counterparty.name")
    @Mapping(target = "ricosCode",        source = "counterparty.ricosCode")
    @Mapping(target = "country",          source = "counterparty.country")
    @Mapping(target = "sector",           source = "counterparty.sector")
    @Mapping(target = "usageRate",
            expression = "java(riskCalculationService.computeUsageRate(riskLimit.getUsedAmount(), riskLimit.getMaxAmount()))")
    @Mapping(target = "alertLevel",
            expression = "java(riskCalculationService.computeAlertLevel(riskCalculationService.computeUsageRate(riskLimit.getUsedAmount(), riskLimit.getMaxAmount())))")
    public abstract RiskLimitDto toDto(RiskLimit riskLimit);
}
