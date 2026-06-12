package com.example.rag.dto;

import java.util.List;

/**
 * Streszczenie dokumentu generowane przez LLM.
 */
public record SummaryResponse(
        String shortSummary,
        String detailedSummary,
        List<String> keyPoints
) {
}
