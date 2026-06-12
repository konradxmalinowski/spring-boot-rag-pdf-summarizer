package com.example.rag.dto;

import java.util.List;

/**
 * Odpowiedź RAG: wygenerowana odpowiedź modelu plus źródła (fragmenty),
 * z których model korzystał — przydatne do weryfikacji.
 */
public record AskResponse(
        String answer,
        List<String> sources
) {
}
