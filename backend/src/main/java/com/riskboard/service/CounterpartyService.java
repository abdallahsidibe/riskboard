package com.riskboard.service;

import com.riskboard.dto.CounterpartyDto;
import com.riskboard.mapper.CounterpartyMapper;
import com.riskboard.repository.CounterpartyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CounterpartyService {

    private final CounterpartyRepository counterpartyRepository;
    private final CounterpartyMapper counterpartyMapper;

    public CounterpartyService(CounterpartyRepository counterpartyRepository,
                               CounterpartyMapper counterpartyMapper) {
        this.counterpartyRepository = counterpartyRepository;
        this.counterpartyMapper = counterpartyMapper;
    }

    public List<CounterpartyDto> findAll() {
        return counterpartyRepository.findAll().stream()
                .map(counterpartyMapper::toDto)
                .toList();
    }
}
