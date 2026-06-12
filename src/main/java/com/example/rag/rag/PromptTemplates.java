package com.example.rag.rag;

/**
 * Szablony promptów używane w ścieżkach RAG i streszczania.
 * Trzymane w jednym miejscu, by łatwo je dostroić.
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    /** System prompt dla RAG: model ma odpowiadać TYLKO na podstawie kontekstu. */
    public static final String RAG_SYSTEM = """
            Jesteś asystentem odpowiadającym na pytania na podstawie dostarczonego kontekstu.
            Zasady:
            - Odpowiadaj wyłącznie na podstawie sekcji KONTEKST poniżej.
            - Jeśli w kontekście nie ma odpowiedzi, napisz wprost, że nie znalazłeś jej w dokumencie.
            - Nie zmyślaj faktów. Odpowiadaj zwięźle i w języku pytania.
            """;

    /** Szablon wiadomości użytkownika: kontekst + pytanie. */
    public static final String RAG_USER = """
            KONTEKST:
            {context}

            PYTANIE:
            {question}
            """;

    /** System prompt dla streszczania dokumentu. */
    public static final String SUMMARY_SYSTEM = """
            Jesteś ekspertem od streszczania dokumentów.
            Na podstawie treści dokumentu przygotuj:
            - shortSummary: zwięzłe streszczenie w 2-3 zdaniach,
            - detailedSummary: szczegółowe streszczenie w jednym akapicie,
            - keyPoints: listę 3-7 najważniejszych punktów.
            Pisz w języku dokumentu. Nie dodawaj informacji spoza dokumentu.
            """;

    public static final String SUMMARY_USER = """
            TREŚĆ DOKUMENTU:
            {content}
            """;
}
