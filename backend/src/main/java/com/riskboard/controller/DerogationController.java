package com.riskboard.controller;

import com.riskboard.dto.CreateDerogationRequest;
import com.riskboard.dto.DerogationRequestDto;
import com.riskboard.enums.DerogationStatus;
import com.riskboard.service.DerogationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Dérogations", description = "Soumission et validation des demandes de dérogation")
@RestController
@RequestMapping("/api/derogations")
public class DerogationController {

    private final DerogationService derogationService;

    public DerogationController(DerogationService derogationService) {
        this.derogationService = derogationService;
    }

    @Operation(summary = "Liste les dérogations (filtre optionnel par statut : PENDING, APPROVED, REJECTED)")
    @GetMapping
    public ResponseEntity<List<DerogationRequestDto>> getDerogations(
            @RequestParam(required = false) DerogationStatus status) {
        if (status != null) {
            return ResponseEntity.ok(derogationService.findByStatus(status));
        }
        return ResponseEntity.ok(derogationService.findAll());
    }

    @Operation(summary = "Soumettre une nouvelle demande de dérogation")
    @PostMapping
    public ResponseEntity<DerogationRequestDto> create(@Valid @RequestBody CreateDerogationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(derogationService.create(request));
    }

    @Operation(summary = "Valider une demande → statut APPROVED")
    @PatchMapping("/{id}/approve")
    public ResponseEntity<DerogationRequestDto> approve(@PathVariable Long id) {
        return ResponseEntity.ok(derogationService.approve(id));
    }

    @Operation(summary = "Rejeter une demande → statut REJECTED")
    @PatchMapping("/{id}/reject")
    public ResponseEntity<DerogationRequestDto> reject(@PathVariable Long id) {
        return ResponseEntity.ok(derogationService.reject(id));
    }
}
