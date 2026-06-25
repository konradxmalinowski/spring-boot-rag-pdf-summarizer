package com.example.rag.service;

import com.example.rag.exception.PdfProcessingException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Responsible for reading PDF content and splitting it into chunks.
 * Has no database or AI dependencies — pure text transformation.
 */
@Service
public class PdfProcessingService {

    /** Result of processing a PDF: full text plus ready-to-index chunks. */
    public record PdfContent(String fullText, List<String> chunks) {
    }

    /** Reads a PDF from raw bytes and splits it into chunks. */
    public PdfContent process(byte[] pdfBytes) {
        List<Document> pages = readPages(pdfBytes);

        String fullText = pages.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a.isBlank() ? b : a + "\n\n" + b);

        if (fullText == null || fullText.isBlank()) {
            throw new PdfProcessingException(
                    "PDF contains no extractable text (possibly a scanned image)", null);
        }

        List<String> chunks = chunk(fullText);
        return new PdfContent(fullText, chunks);
    }

    /** Splits text into chunks — also used during re-indexing. */
    public List<String> chunk(String text) {
        List<Document> split = new TokenTextSplitter().split(List.of(new Document(text)));
        return split.stream().map(Document::getText).toList();
    }

    private List<Document> readPages(byte[] pdfBytes) {
        try {
            PagePdfDocumentReader reader = new PagePdfDocumentReader(new ByteArrayResource(pdfBytes));
            return reader.read();
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to read the PDF file", e);
        }
    }
}
