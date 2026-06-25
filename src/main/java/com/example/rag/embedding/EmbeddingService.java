package com.example.rag.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around the embedding model.
 *
 * Architectural note: in Spring AI the {@code VectorStore} (ChromaDB here)
 * calls {@link EmbeddingModel} internally — both when saving chunks and when
 * searching (it embeds the query). Manual embedding generation in the RAG path
 * is therefore unnecessary and would duplicate work.
 *
 * This class exposes the embedding model for diagnostic purposes — e.g.
 * checking the vector dimension, which helps detect inconsistencies after a
 * model switch (OpenAI and Ollama use different dimensions).
 */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private volatile int cachedDimensions = -1;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /** Dimension of the active model's embedding space (e.g. 1536 for text-embedding-3-small). */
    public int dimensions() {
        if (cachedDimensions < 0) {
            cachedDimensions = embeddingModel.dimensions();
        }
        return cachedDimensions;
    }
}
