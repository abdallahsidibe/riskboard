package com.riskboard.mapper;

import com.riskboard.dto.DerogationRequestDto;
import com.riskboard.entity.DerogationRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DerogationRequestMapper {

    @Mapping(target = "counterpartyId",   source = "counterparty.id")
    @Mapping(target = "counterpartyName", source = "counterparty.name")
    @Mapping(target = "ricosCode",        source = "counterparty.ricosCode")
    DerogationRequestDto toDto(DerogationRequest derogationRequest);
}
