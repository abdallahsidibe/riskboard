package com.riskboard.repository;

import com.riskboard.enums.LimitType;
import com.riskboard.entity.RiskLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface RiskLimitRepository extends JpaRepository<RiskLimit, Long> {

    Optional<RiskLimit> findByCounterpartyIdAndLimitType(Long counterpartyId, LimitType limitType);

    Optional<RiskLimit> findByCounterpartyRicosCodeAndLimitType(String ricosCode, LimitType limitType);

    @Query("SELECT r.counterparty.sector, SUM(r.usedAmount) FROM RiskLimit r GROUP BY r.counterparty.sector")
    List<Object[]> findUsedAmountGroupedBySector();
}
