package com.example.rag.entity;

/**
 * Processing status of a document in the RAG pipeline.
 */
public enum DocumentStatus {
    /** File saved, not yet indexed. */
    UPLOADED,
    /** PDF reading / chunking / embedding in progress. */
    PROCESSING,
    /** Chunks and embeddings are in ChromaDB — document is ready for queries. */
    INDEXED,
    /** Processing failed (details in the errorMessage field). */
    FAILED
}
