package com.example.rag.vectorstore;

import com.example.rag.exception.AiServiceException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single point of contact between the application and ChromaDB (the primary vector store).
 *
 * Delegates to Spring AI's {@link VectorStore}, which computes embeddings automatically
 * (via the configured EmbeddingModel) for both writes and searches. This class adds
 * domain-specific logic: metadata fields (documentId, filename, chunkIndex) and
 * exception mapping.
 */
@Service
public class ChromaService {

    public static final String META_DOCUMENT_ID = "documentId";
    public static final String META_FILENAME = "filename";
    public static final String META_CHUNK_INDEX = "chunkIndex";

    private final VectorStore vectorStore;

    public ChromaService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Saves document chunks to ChromaDB. Embeddings are computed automatically.
     *
     * @return list of generated Chroma entry IDs (needed for later deletion)
     */
    public List<String> addChunks(Long documentId, String filename, List<String> chunkTexts) {
        List<Document> documents = new ArrayList<>(chunkTexts.size());
        for (int i = 0; i < chunkTexts.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(META_DOCUMENT_ID, String.valueOf(documentId));
            metadata.put(META_FILENAME, filename);
            metadata.put(META_CHUNK_INDEX, i);
            documents.add(new Document(chunkTexts.get(i), metadata));
        }
        try {
            vectorStore.add(documents);
        } catch (Exception e) {
            throw new AiServiceException("Failed to save chunks to ChromaDB", e);
        }
        return documents.stream().map(Document::getId).toList();
    }

    /**
     * Similarity search. When documentId is provided, narrows the search to
     * a single document via metadata filter.
     */
    public List<Document> search(String query, int topK, Long documentId) {
        SearchRequest.Builder request = SearchRequest.builder()
                .query(query)
                .topK(topK);
        if (documentId != null) {
            request.filterExpression(META_DOCUMENT_ID + " == '" + documentId + "'");
        }
        try {
            List<Document> results = vectorStore.similaritySearch(request.build());
            return results != null ? results : List.of();
        } catch (Exception e) {
            throw new AiServiceException("ChromaDB similarity search failed", e);
        }
    }

    /** Deletes the specified entries from ChromaDB by their IDs. */
    public void deleteByIds(List<String> chromaIds) {
        if (chromaIds == null || chromaIds.isEmpty()) {
            return;
        }
        try {
            vectorStore.delete(chromaIds);
        } catch (Exception e) {
            throw new AiServiceException("Failed to delete entries from ChromaDB", e);
        }
    }
}
