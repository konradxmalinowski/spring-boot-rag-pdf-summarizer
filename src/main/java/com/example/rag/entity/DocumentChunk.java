package com.example.rag.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Metadata for a single chunk.
 * The embedding vector and full text live in ChromaDB — only the document
 * association, chunk order, and ChromaDB entry ID (needed for deletion) are stored here.
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    /** ChromaDB entry ID (UUID) — used to delete a specific chunk. */
    @Column(nullable = false)
    private String chromaId;

    /** Zero-based position of the chunk within the document (0, 1, 2, ...). */
    @Column(nullable = false)
    private int chunkIndex;

    protected DocumentChunk() {
        // required by JPA
    }

    public DocumentChunk(Document document, String chromaId, int chunkIndex) {
        this.document = document;
        this.chromaId = chromaId;
        this.chunkIndex = chunkIndex;
    }

    public Long getId() {
        return id;
    }

    public Document getDocument() {
        return document;
    }

    public String getChromaId() {
        return chromaId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }
}
