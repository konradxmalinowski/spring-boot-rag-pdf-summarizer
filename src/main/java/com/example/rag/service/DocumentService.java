package com.example.rag.service;

import com.example.rag.dto.StatsResponse;
import com.example.rag.embedding.EmbeddingService;
import com.example.rag.entity.Document;
import com.example.rag.entity.DocumentChunk;
import com.example.rag.exception.DocumentNotFoundException;
import com.example.rag.exception.InvalidFileException;
import com.example.rag.repository.DocumentChunkRepository;
import com.example.rag.repository.DocumentRepository;
import com.example.rag.vectorstore.ChromaService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Orkiestruje cykl życia dokumentu: upload -> przetworzenie -> indeksacja,
 * a także listowanie, usuwanie, reindeksację i statystyki.
 *
 * Trzyma dwie spójne reprezentacje: metadane w PostgreSQL (ten serwis)
 * oraz chunki + embeddingi w ChromaDB (przez ChromaService).
 */
@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final PdfProcessingService pdfProcessingService;
    private final ChromaService chromaService;
    private final EmbeddingService embeddingService;

    private final long maxFileSizeBytes;
    private final String allowedContentType;

    public DocumentService(DocumentRepository documentRepository,
                           DocumentChunkRepository chunkRepository,
                           PdfProcessingService pdfProcessingService,
                           ChromaService chromaService,
                           EmbeddingService embeddingService,
                           @Value("${app.upload.max-file-size-bytes:20971520}") long maxFileSizeBytes,
                           @Value("${app.upload.allowed-content-type:application/pdf}") String allowedContentType) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.pdfProcessingService = pdfProcessingService;
        this.chromaService = chromaService;
        this.embeddingService = embeddingService;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.allowedContentType = allowedContentType;
    }

    /** Upload + pełna indeksacja PDF-a. */
    @Transactional
    public Document uploadAndIndex(MultipartFile file) {
        validate(file);

        byte[] bytes = readBytes(file);
        Document document = documentRepository.save(
                new Document(file.getOriginalFilename(), bytes.length));

        try {
            document.markProcessing();
            documentRepository.save(document);
            index(document, bytes);
        } catch (RuntimeException e) {
            document.markFailed(truncate(e.getMessage()));
            documentRepository.save(document);
            throw e;
        }
        return document;
    }

    /** Ponowna indeksacja z zapisanego tekstu (bez ponownego uploadu pliku). */
    @Transactional
    public Document reindex(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        String text = document.getExtractedText();
        if (text == null || text.isBlank()) {
            throw new InvalidFileException(
                    "Dokument nie ma zapisanej treści — wgraj plik ponownie");
        }

        // Usuń stare chunki z Chromy i z bazy.
        removeChunksFromStores(document);

        // Przelicz i zapisz na nowo.
        document.markProcessing();
        documentRepository.save(document);
        List<String> chunks = pdfProcessingService.chunk(text);
        persistChunks(document, chunks);
        document.markIndexed(chunks.size(), text);
        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public List<Document> listDocuments() {
        return documentRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Document getDocument(Long id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    /** Usuwa dokument wraz ze wszystkimi chunkami z ChromaDB i z bazy. */
    @Transactional
    public void deleteDocument(Long id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        removeChunksFromStores(document);
        documentRepository.delete(document);
    }

    @Transactional(readOnly = true)
    public StatsResponse stats() {
        return new StatsResponse(
                documentRepository.count(),
                chunkRepository.count(),
                embeddingService.dimensions());
    }

    // --- pomocnicze ---

    private void index(Document document, byte[] bytes) {
        PdfProcessingService.PdfContent content = pdfProcessingService.process(bytes);
        persistChunks(document, content.chunks());
        document.markIndexed(content.chunks().size(), content.fullText());
        documentRepository.save(document);
    }

    private void persistChunks(Document document, List<String> chunks) {
        List<String> chromaIds = chromaService.addChunks(
                document.getId(), document.getFilename(), chunks);
        for (int i = 0; i < chromaIds.size(); i++) {
            chunkRepository.save(new DocumentChunk(document, chromaIds.get(i), i));
        }
    }

    private void removeChunksFromStores(Document document) {
        List<DocumentChunk> existing = chunkRepository.findByDocumentId(document.getId());
        List<String> chromaIds = existing.stream().map(DocumentChunk::getChromaId).toList();
        chromaService.deleteByIds(chromaIds);
        chunkRepository.deleteAll(existing);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Plik jest pusty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidFileException(
                    "Plik jest za duży (max " + maxFileSizeBytes + " bajtów)");
        }
        String contentType = file.getContentType();
        boolean pdfByType = allowedContentType.equalsIgnoreCase(contentType);
        boolean pdfByName = file.getOriginalFilename() != null
                && file.getOriginalFilename().toLowerCase().endsWith(".pdf");
        if (!pdfByType && !pdfByName) {
            throw new InvalidFileException("Dozwolone są wyłącznie pliki PDF");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new InvalidFileException("Nie udało się odczytać przesłanego pliku");
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "Nieznany błąd";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
