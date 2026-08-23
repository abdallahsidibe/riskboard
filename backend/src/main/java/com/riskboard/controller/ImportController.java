package com.riskboard.controller;

import com.riskboard.dto.ImportSummary;
import com.riskboard.service.CsvImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Import CSV", description = "Import des contreparties et limites de risque via fichier CSV")
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final CsvImportService csvImportService;

    public ImportController(CsvImportService csvImportService) {
        this.csvImportService = csvImportService;
    }

    @Operation(summary = "Importer un fichier CSV (upsert contreparties + limites). Lignes invalides ignorées et rapportées.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportSummary> importCsv(@RequestParam("file") MultipartFile file) {
        log.info("Received CSV upload: name={} size={}", file.getOriginalFilename(), file.getSize());
        ImportSummary summary = csvImportService.importCsv(file);
        return ResponseEntity.ok(summary);
    }
}
