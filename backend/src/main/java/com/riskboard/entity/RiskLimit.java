package com.riskboard.entity;

import com.riskboard.enums.LimitType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "risk_limits",
        uniqueConstraints = @UniqueConstraint(columnNames = {"counterparty_id", "limit_type"}))
public class RiskLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "counterparty_id", nullable = false)
    private Counterparty counterparty;

    @Enumerated(EnumType.STRING)
    @Column(name = "limit_type", nullable = false)
    private LimitType limitType;

    @Column(name = "max_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "used_amount", nullable = false, precision = 20, scale = 4)
    private BigDecimal usedAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
}
