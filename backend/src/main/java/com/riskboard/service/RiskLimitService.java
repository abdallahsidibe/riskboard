package com.riskboard.service;

import com.riskboard.dto.LimitCheckResult;
import com.riskboard.dto.RiskLimitDto;
import com.riskboard.enums.LimitType;
import com.riskboard.entity.RiskLimit;
import com.riskboard.mapper.RiskLimitMapper;
import com.riskboard.repository.RiskLimitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class RiskLimitService {

    private static final Logger log = LoggerFactory.getLogger(RiskLimitService.class);
    private static final BigDecimal THRESHOLD_FACTOR = new BigDecimal("1.5");

    private final RiskLimitRepository riskLimitRepository;
    private final RiskLimitMapper riskLimitMapper;

    public RiskLimitService(RiskLimitRepository riskLimitRepository,
                            RiskLimitMapper riskLimitMapper) {
        this.riskLimitRepository = riskLimitRepository;
        this.riskLimitMapper = riskLimitMapper;
    }

    public List<RiskLimitDto> findAll() {
        log.debug("Fetching all risk limits with computed usage");
        return riskLimitRepository.findAll().stream()
                .map(riskLimitMapper::toDto)
                .toList();
    }

    public Map<String, BigDecimal> getExposureBySector() {
        log.debug("Computing exposure aggregated by sector");
        List<Object[]> rows = riskLimitRepository.findUsedAmountGroupedBySector();
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String sector = (String) row[0];
            BigDecimal total = (BigDecimal) row[1];
            result.put(sector, total.setScale(4, RoundingMode.HALF_UP));
        }
        return result;
    }

    public LimitCheckResult checkLimit(Long counterpartyId, LimitType limitType, BigDecimal amount) {
        Optional<RiskLimit> opt = riskLimitRepository.findByCounterpartyIdAndLimitType(counterpartyId, limitType);
        if (opt.isEmpty()) {
            log.debug("No limit found for counterpartyId={} limitType={}", counterpartyId, limitType);
            return new LimitCheckResult(false, false, null, null);
        }
        RiskLimit limit = opt.get();
        BigDecimal max = limit.getMaxAmount();
        BigDecimal threshold = max.multiply(THRESHOLD_FACTOR).setScale(4, RoundingMode.HALF_UP);
        boolean within = amount.compareTo(threshold) <= 0;
        log.debug("Limit check counterpartyId={} limitType={} amount={} max={} threshold={} within={}",
                counterpartyId, limitType, amount, max, threshold, within);
        return new LimitCheckResult(true, within, max, threshold);
    }
}
