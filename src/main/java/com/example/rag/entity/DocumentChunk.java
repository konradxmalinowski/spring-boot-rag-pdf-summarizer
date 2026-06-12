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
 * Metadane pojedynczego chunku.
 * Wektor (embedding) i pełna treść mieszkają w ChromaDB — tu trzymamy
 * tylko powiązanie z dokumentem, kolejność i ID wpisu w Chromie (do usuwania).
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

    /** ID wpisu w ChromaDB (UUID) — pozwala precyzyjnie usunąć chunk. */
    @Column(nullable = false)
    private String chromaId;

    /** Kolejność chunku w dokumencie (0, 1, 2, ...). */
    @Column(nullable = false)
    private int chunkIndex;

    protected DocumentChunk() {
        // wymagane przez JPA
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
