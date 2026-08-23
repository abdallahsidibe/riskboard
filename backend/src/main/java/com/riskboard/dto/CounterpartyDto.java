package com.riskboard.dto;

public record CounterpartyDto(
        Long id,
        String name,
        String ricosCode,
        String country,
        String sector
) {}
