package com.riskboard.mapper;

import com.riskboard.dto.CounterpartyDto;
import com.riskboard.entity.Counterparty;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CounterpartyMapper {

    CounterpartyDto toDto(Counterparty counterparty);
}
