package com.riskboard.dto;

import java.util.List;

public record ImportSummary(
        int successCount,
        int errorCount,
        List<ImportError> errors
) {
    public record ImportError(int lineNumber, String message) {}
}
