package com.riskboard.controller;

import com.riskboard.dto.LimitCheckResult;
import com.riskboard.dto.RiskLimitDto;
import com.riskboard.enums.LimitType;
import com.riskboard.service.RiskLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "Limites de risque", description = "Consultation et vérification des limites de risque")
@RestController
@RequestMapping("/api/risklimits")
public class RiskLimitController {

    private final RiskLimitService riskLimitService;

    public RiskLimitController(RiskLimitService riskLimitService) {
        this.riskLimitService = riskLimitService;
    }

    @Operation(summary = "Liste toutes les limites de risque avec leur niveau d'alerte calculé")
    @GetMapping
    public ResponseEntity<List<RiskLimitDto>> getAll() {
        return ResponseEntity.ok(riskLimitService.findAll());
    }

    @Operation(summary = "Vérifie si un montant respecte le seuil de 150 % de la limite max")
    @GetMapping("/check")
    public ResponseEntity<LimitCheckResult> checkLimit(
            @RequestParam Long counterpartyId,
            @RequestParam LimitType limitType,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(riskLimitService.checkLimit(counterpartyId, limitType, amount));
    }
}
