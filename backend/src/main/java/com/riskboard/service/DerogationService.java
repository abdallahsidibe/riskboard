package com.riskboard.service;

import com.riskboard.dto.CreateDerogationRequest;
import com.riskboard.dto.DerogationRequestDto;
import com.riskboard.entity.Counterparty;
import com.riskboard.entity.DerogationRequest;
import com.riskboard.enums.DerogationStatus;
import com.riskboard.entity.RiskLimit;
import com.riskboard.exception.BusinessRuleException;
import com.riskboard.exception.ResourceNotFoundException;
import com.riskboard.mapper.DerogationRequestMapper;
import com.riskboard.repository.CounterpartyRepository;
import com.riskboard.repository.DerogationRequestRepository;
import com.riskboard.repository.RiskLimitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class DerogationService {

    private static final Logger log = LoggerFactory.getLogger(DerogationService.class);
    private static final BigDecimal THRESHOLD_FACTOR = new BigDecimal("1.5");

    private final DerogationRequestRepository derogationRepository;
    private final CounterpartyRepository counterpartyRepository;
    private final RiskLimitRepository riskLimitRepository;
    private final DerogationRequestMapper derogationMapper;

    public DerogationService(DerogationRequestRepository derogationRepository,
                             CounterpartyRepository counterpartyRepository,
                             RiskLimitRepository riskLimitRepository,
                             DerogationRequestMapper derogationMapper) {
        this.derogationRepository = derogationRepository;
        this.counterpartyRepository = counterpartyRepository;
        this.riskLimitRepository = riskLimitRepository;
        this.derogationMapper = derogationMapper;
    }

    public List<DerogationRequestDto> findByStatus(DerogationStatus status) {
        return derogationRepository.findByStatus(status).stream()
                .map(derogationMapper::toDto)
                .toList();
    }

    public List<DerogationRequestDto> findAll() {
        return derogationRepository.findAll().stream()
                .map(derogationMapper::toDto)
                .toList();
    }

    @Transactional
    public DerogationRequestDto create(CreateDerogationRequest request) {
        Counterparty counterparty = counterpartyRepository.findById(request.counterpartyId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Counterparty not found with id: " + request.counterpartyId()));

        Optional<RiskLimit> limitOpt = riskLimitRepository
                .findByCounterpartyIdAndLimitType(request.counterpartyId(), request.limitType());

        if (limitOpt.isEmpty()) {
            throw new BusinessRuleException(
                    "No risk limit exists for counterparty id=" + request.counterpartyId()
                            + " and type=" + request.limitType());
        }

        RiskLimit limit = limitOpt.get();
        BigDecimal maxThreshold = limit.getMaxAmount()
                .multiply(THRESHOLD_FACTOR)
                .setScale(4, RoundingMode.HALF_UP);

        if (request.amount().compareTo(maxThreshold) > 0) {
            throw new BusinessRuleException(
                    "Requested amount " + request.amount()
                            + " exceeds 150% of max limit (" + maxThreshold + ")");
        }

        DerogationRequest entity = DerogationRequest.builder()
                .counterparty(counterparty)
                .limitType(request.limitType())
                .requestedBy(request.requestedBy())
                .amount(request.amount())
                .reason(request.reason())
                .status(DerogationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        DerogationRequest saved = derogationRepository.save(entity);
        log.info("Derogation created id={} counterparty={} amount={}", saved.getId(),
                counterparty.getRicosCode(), request.amount());
        return derogationMapper.toDto(saved);
    }

    @Transactional
    public DerogationRequestDto approve(Long id) {
        return updateStatus(id, DerogationStatus.APPROVED);
    }

    @Transactional
    public DerogationRequestDto reject(Long id) {
        return updateStatus(id, DerogationStatus.REJECTED);
    }

    private DerogationRequestDto updateStatus(Long id, DerogationStatus newStatus) {
        DerogationRequest entity = derogationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Derogation not found with id: " + id));
        entity.setStatus(newStatus);
        DerogationRequest saved = derogationRepository.save(entity);
        log.info("Derogation id={} status updated to {}", id, newStatus);
        return derogationMapper.toDto(saved);
    }
}
