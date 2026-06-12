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
 * Jedyny punkt kontaktu aplikacji z ChromaDB (główny vector store).
 *
 * Korzysta ze Spring-owego {@link VectorStore}, który sam liczy embeddingi
 * (przez skonfigurowany EmbeddingModel) zarówno przy zapisie, jak i przy
 * wyszukiwaniu. Tutaj dodajemy logikę specyficzną dla naszej domeny:
 * metadane (documentId, filename, chunkIndex) oraz mapowanie wyjątków.
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
     * Zapisuje chunki dokumentu do ChromaDB. Embeddingi liczone są automatycznie.
     *
     * @return lista wygenerowanych ID wpisów w Chromie (do późniejszego usunięcia)
     */
    public List<String> addChunks(Long documentId, String filename, List<String> chunkTexts) {
        List<Document> documents = new ArrayList<>(chunkTexts.size());
        for (int i = 0; i < chunkTexts.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(META_DOCUMENT_ID, documentId);
            metadata.put(META_FILENAME, filename);
            metadata.put(META_CHUNK_INDEX, i);
            documents.add(new Document(chunkTexts.get(i), metadata));
        }
        try {
            vectorStore.add(documents);
        } catch (Exception e) {
            throw new AiServiceException("Nie udało się zapisać chunków do ChromaDB", e);
        }
        return documents.stream().map(Document::getId).toList();
    }

    /**
     * Similarity search. Jeśli podano documentId, zawęża wyszukiwanie do
     * jednego dokumentu (filtr po metadanych).
     */
    public List<Document> search(String query, int topK, Long documentId) {
        SearchRequest.Builder request = SearchRequest.builder()
                .query(query)
                .topK(topK);
        if (documentId != null) {
            request.filterExpression(META_DOCUMENT_ID + " == " + documentId);
        }
        try {
            List<Document> results = vectorStore.similaritySearch(request.build());
            return results != null ? results : List.of();
        } catch (Exception e) {
            throw new AiServiceException("Wyszukiwanie w ChromaDB nie powiodło się", e);
        }
    }

    /** Usuwa wskazane wpisy z ChromaDB po ich ID. */
    public void deleteByIds(List<String> chromaIds) {
        if (chromaIds == null || chromaIds.isEmpty()) {
            return;
        }
        try {
            vectorStore.delete(chromaIds);
        } catch (Exception e) {
            throw new AiServiceException("Nie udało się usunąć wpisów z ChromaDB", e);
        }
    }
}
