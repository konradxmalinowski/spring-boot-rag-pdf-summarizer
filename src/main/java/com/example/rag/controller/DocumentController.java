package com.example.rag.controller;

import com.example.rag.dto.DocumentResponse;
import com.example.rag.dto.StatsResponse;
import com.example.rag.dto.SummaryResponse;
import com.example.rag.dto.UploadResponse;
import com.example.rag.entity.Document;
import com.example.rag.service.DocumentService;
import com.example.rag.service.SummaryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;
    private final SummaryService summaryService;

    public DocumentController(DocumentService documentService, SummaryService summaryService) {
        this.documentService = documentService;
        this.summaryService = summaryService;
    }

    /** Upload PDF + indeksacja. 201 Created. */
    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file) {
        Document document = documentService.uploadAndIndex(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(UploadResponse.from(document));
    }

    /** Lista wszystkich zaindeksowanych dokumentów. */
    @GetMapping
    public List<DocumentResponse> list() {
        return documentService.listDocuments().stream()
                .map(DocumentResponse::from)
                .toList();
    }

    /** Statystyki magazynu wiedzy (literalna ścieżka — przed mapowaniem /{id}). */
    @GetMapping("/stats")
    public StatsResponse stats() {
        return documentService.stats();
    }

    /** Szczegóły pojedynczego dokumentu. */
    @GetMapping("/{id}")
    public DocumentResponse get(@PathVariable Long id) {
        return DocumentResponse.from(documentService.getDocument(id));
    }

    /** Streszczenie dokumentu. */
    @PostMapping("/{id}/summary")
    public SummaryResponse summary(@PathVariable Long id) {
        return summaryService.summarize(id);
    }

    /** Ponowna indeksacja dokumentu. */
    @PostMapping("/{id}/reindex")
    public UploadResponse reindex(@PathVariable Long id) {
        return UploadResponse.from(documentService.reindex(id));
    }

    /** Usunięcie dokumentu wraz z chunkami z ChromaDB. 204 No Content. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
