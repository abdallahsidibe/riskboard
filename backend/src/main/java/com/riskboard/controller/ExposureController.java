package com.riskboard.controller;

import com.riskboard.service.RiskLimitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/exposure")
public class ExposureController {

    private final RiskLimitService riskLimitService;

    public ExposureController(RiskLimitService riskLimitService) {
        this.riskLimitService = riskLimitService;
    }

    @GetMapping
    public ResponseEntity<Map<String, BigDecimal>> getExposureBySector() {
        return ResponseEntity.ok(riskLimitService.getExposureBySector());
    }
}
