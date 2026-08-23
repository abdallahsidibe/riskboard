package com.riskboard.service;

import com.riskboard.dto.ImportSummary;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("CsvImportService — parsing and resilience")
class CsvImportServiceTest {

    @Autowired
    private CsvImportService csvImportService;

    @Test
    @DisplayName("Valid CSV: all 11 lines imported successfully")
    void import_valid11Lines() throws Exception {
        // Given
        byte[] content = getClass().getClassLoader()
                .getResourceAsStream("sample-risklimits.csv")
                .readAllBytes();
        MockMultipartFile file = new MockMultipartFile("file", "sample.csv",
                "text/csv", content);

        // When
        ImportSummary summary = csvImportService.importCsv(file);

        // Then
        assertThat(summary.successCount()).isEqualTo(11);
        assertThat(summary.errorCount()).isZero();
        assertThat(summary.errors()).isEmpty();
    }

    @Test
    @DisplayName("CSV with one bad line: it is reported but does not block the others")
    void import_oneBadLine_doesNotBlockImport() {
        // Given — line 3 has an invalid limitType ("INVALID")
        String csvContent = """
                name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency
                BNP PARIBAS,RICOS48213,FR,Banking,CREDIT,50000000,32000000,EUR
                BAD LINE,RICOS99999,FR,Banking,INVALID,50000000,32000000,EUR
                GOLDMAN SACHS,RICOS36180,US,Banking,LIQUIDITY,15000000,4200000,USD
                """;

        MockMultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        // When
        ImportSummary summary = csvImportService.importCsv(file);

        // Then — 2 successes, 1 error reported
        assertThat(summary.successCount()).isEqualTo(2);
        assertThat(summary.errorCount()).isEqualTo(1);
        assertThat(summary.errors()).hasSize(1);
        assertThat(summary.errors().get(0).lineNumber()).isEqualTo(3);
        assertThat(summary.errors().get(0).message()).contains("INVALID");
    }

    @Test
    @DisplayName("Empty CSV (header only): 0 successes, 0 errors")
    void import_emptyFile_headerOnly() {
        String csvContent = "name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency\n";

        MockMultipartFile file = new MockMultipartFile("file", "empty.csv",
                "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        ImportSummary summary = csvImportService.importCsv(file);

        assertThat(summary.successCount()).isZero();
        assertThat(summary.errorCount()).isZero();
    }

    @Test
    @DisplayName("CSV with missing required column value: line is reported as error")
    void import_missingColumnValue_reportedAsError() {
        String csvContent = """
                name,ricosCode,country,sector,limitType,maxAmount,usedAmount,currency
                ,RICOS48213,FR,Banking,CREDIT,50000000,32000000,EUR
                """;

        MockMultipartFile file = new MockMultipartFile("file", "test.csv",
                "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        ImportSummary summary = csvImportService.importCsv(file);

        assertThat(summary.errorCount()).isEqualTo(1);
        assertThat(summary.errors().get(0).message()).contains("name");
    }

    @Test
    @DisplayName("Upsert: re-importing same ricosCode updates the record, no duplicate")
    void import_upsert_doesNotCreateDuplicate() throws Exception {
        byte[] content = getClass().getClassLoader()
                .getResourceAsStream("sample-risklimits.csv")
                .readAllBytes();
        MockMultipartFile file = new MockMultipartFile("file", "sample.csv", "text/csv", content);

        // When — import twice
        csvImportService.importCsv(file);
        ImportSummary second = csvImportService.importCsv(file);

        // Then — second import succeeds without errors (upsert, no unique constraint violation)
        assertThat(second.successCount()).isEqualTo(11);
        assertThat(second.errorCount()).isZero();
    }
}
