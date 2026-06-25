package com.example.rag.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Metadata for an uploaded PDF document.
 * Content and embeddings are NOT stored here — that is ChromaDB's responsibility.
 * Only the data needed for listing, status tracking, and re-indexing is kept here.
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    /** Number of chunks stored in ChromaDB for this document. */
    @Column(nullable = false)
    private int chunkCount;

    /** Populated when status = FAILED. */
    @Column(length = 1000)
    private String errorMessage;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    /**
     * Raw PDF text — retained so the document can be summarised and re-indexed
     * without requiring the user to re-upload the file.
     */
    @Column(columnDefinition = "text")
    private String extractedText;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DocumentChunk> chunks = new ArrayList<>();

    protected Document() {
        // required by JPA
    }

    public Document(String filename, long fileSizeBytes) {
        this.filename = filename;
        this.fileSizeBytes = fileSizeBytes;
        this.status = DocumentStatus.UPLOADED;
        this.chunkCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /** Marks the document as successfully indexed. */
    public void markIndexed(int chunkCount, String extractedText) {
        this.status = DocumentStatus.INDEXED;
        this.chunkCount = chunkCount;
        this.extractedText = extractedText;
        this.errorMessage = null;
        touch();
    }

    public void markProcessing() {
        this.status = DocumentStatus.PROCESSING;
        touch();
    }

    public void markFailed(String errorMessage) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = errorMessage;
        touch();
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public List<DocumentChunk> getChunks() {
        return chunks;
    }
}
