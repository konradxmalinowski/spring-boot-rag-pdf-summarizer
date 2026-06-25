package com.example.rag.rag;

/**
 * Prompt templates used in the RAG and summarisation paths.
 * Kept in one place for easy tuning.
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    /** System prompt for RAG: the model must answer ONLY from the provided context. */
    public static final String RAG_SYSTEM = """
            You are an assistant that answers questions based solely on the provided context.
            Rules:
            - Answer exclusively from the CONTEXT section below.
            - If the answer is not found in the context, state clearly that you could not find it in the document.
            - Do not fabricate facts. Be concise and answer in the language of the question.
            """;

    /** User message template: context + question. */
    public static final String RAG_USER = """
            CONTEXT:
            {context}

            QUESTION:
            {question}
            """;

    /** System prompt for document summarisation. */
    public static final String SUMMARY_SYSTEM = """
            You are a document summarisation expert.
            You MUST respond with valid JSON only — no prose, no markdown fences.
            Fields: shortSummary (2-3 sentences), detailedSummary (one paragraph),
            keyPoints (array of 3-7 strings).
            """;

    public static final String SUMMARY_USER = """
            DOCUMENT CONTENT:
            {content}
            """;
}
