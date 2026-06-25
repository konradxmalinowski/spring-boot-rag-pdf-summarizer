package com.example.rag.service;

import com.example.rag.exception.PdfProcessingException;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Odpowiada za odczyt treści PDF i podział na chunki.
 * Nie dotyka bazy ani AI — to czysta transformacja tekstu.
 */
@Service
public class PdfProcessingService {

    /** Wynik przetworzenia PDF: pełny tekst + gotowe chunki. */
    public record PdfContent(String fullText, List<String> chunks) {
    }

    /** Odczyt PDF z surowych bajtów + chunking. */
    public PdfContent process(byte[] pdfBytes) {
        List<Document> pages = readPages(pdfBytes);

        String fullText = pages.stream()
                .map(Document::getText)
                .reduce("", (a, b) -> a.isBlank() ? b : a + "\n\n" + b);

        if (fullText == null || fullText.isBlank()) {
            throw new PdfProcessingException(
                    "PDF nie zawiera tekstu (możliwe, że to skan/obraz)", null);
        }

        List<String> chunks = chunk(fullText);
        return new PdfContent(fullText, chunks);
    }

    /** Dzieli gotowy tekst na chunki — używane też przy reindeksacji. */
    public List<String> chunk(String text) {
        List<Document> split = new TokenTextSplitter().split(List.of(new Document(text)));
        return split.stream().map(Document::getText).toList();
    }

    private List<Document> readPages(byte[] pdfBytes) {
        try {
            PagePdfDocumentReader reader = new PagePdfDocumentReader(new ByteArrayResource(pdfBytes));
            return reader.read();
        } catch (Exception e) {
            throw new PdfProcessingException("Nie udało się odczytać pliku PDF", e);
        }
    }
}
