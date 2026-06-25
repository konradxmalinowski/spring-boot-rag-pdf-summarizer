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

import java.nio.file.Paths;
import java.util.List;

/**
 * Orchestrates the document lifecycle: upload -> processing -> indexing,
 * as well as listing, deletion, re-indexing, and statistics.
 *
 * Maintains two consistent representations: metadata in PostgreSQL (this service)
 * and chunks + embeddings in ChromaDB (via ChromaService).
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

    @Transactional
    public Document uploadAndIndex(MultipartFile file) {
        validate(file);

        byte[] bytes = readBytes(file);
        String safeName = Paths.get(file.getOriginalFilename()).getFileName().toString();
        Document document = documentRepository.save(
                new Document(safeName, bytes.length));

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

    /** Re-indexes from stored extracted text (no file re-upload required). */
    @Transactional
    public Document reindex(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        String text = document.getExtractedText();
        if (text == null || text.isBlank()) {
            throw new InvalidFileException(
                    "Document has no stored text — please re-upload the file");
        }

        removeChunksFromStores(document);

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

    /** Deletes the document along with all its chunks from ChromaDB and the database. */
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

    // --- helpers ---

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
            throw new InvalidFileException("File is empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new InvalidFileException(
                    "File is too large (max " + maxFileSizeBytes + " bytes)");
        }
        String contentType = file.getContentType();
        boolean pdfByType = allowedContentType.equalsIgnoreCase(contentType);
        boolean pdfByName = file.getOriginalFilename() != null
                && file.getOriginalFilename().toLowerCase().endsWith(".pdf");
        if (!pdfByType && !pdfByName) {
            throw new InvalidFileException("Only PDF files are accepted");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new InvalidFileException("Failed to read the uploaded file");
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
