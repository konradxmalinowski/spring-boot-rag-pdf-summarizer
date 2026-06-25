package com.example.rag.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Cienka warstwa nad modelem embeddingów.
 *
 * Uwaga architektoniczna: w Spring AI to {@code VectorStore} (tutaj ChromaDB)
 * wywołuje {@link EmbeddingModel} pod spodem — zarówno przy zapisie chunków,
 * jak i przy wyszukiwaniu (embedduje pytanie). Dlatego w ścieżce RAG nie
 * generujemy embeddingów ręcznie (to byłaby zbędna, podwójna praca).
 *
 * Ta klasa udostępnia model embeddingów do celów diagnostycznych — np.
 * sprawdzenia wymiaru wektora, co pozwala wykryć niespójność po zmianie
 * modelu (OpenAI vs Ollama mają różne wymiary).
 */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private volatile int cachedDimensions = -1;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /** Wymiar przestrzeni embeddingów aktywnego modelu (np. 1536 dla text-embedding-3-small). */
    public int dimensions() {
        if (cachedDimensions < 0) {
            cachedDimensions = embeddingModel.dimensions();
        }
        return cachedDimensions;
    }
}
