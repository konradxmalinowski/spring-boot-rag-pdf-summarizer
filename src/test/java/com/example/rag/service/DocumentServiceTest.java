package com.example.rag.service;

import com.example.rag.embedding.EmbeddingService;
import com.example.rag.entity.Document;
import com.example.rag.entity.DocumentStatus;
import com.example.rag.exception.DocumentNotFoundException;
import com.example.rag.exception.InvalidFileException;
import com.example.rag.repository.DocumentChunkRepository;
import com.example.rag.repository.DocumentRepository;
import com.example.rag.vectorstore.ChromaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock DocumentRepository documentRepository;
    @Mock DocumentChunkRepository chunkRepository;
    @Mock PdfProcessingService pdfProcessingService;
    @Mock ChromaService chromaService;
    @Mock EmbeddingService embeddingService;

    DocumentService service;

    @BeforeEach
    void setUp() {
        service = new DocumentService(documentRepository, chunkRepository,
                pdfProcessingService, chromaService, embeddingService,
                20_971_520L, "application/pdf");
    }

    @Test
    void odrzucaPustyPlik() {
        MockMultipartFile empty = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.uploadAndIndex(empty))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("pusty");
    }

    @Test
    void odrzucaNiepoprawnyTyp() {
        MockMultipartFile txt = new MockMultipartFile(
                "file", "notatka.txt", "text/plain", "hello".getBytes());

        assertThatThrownBy(() -> service.uploadAndIndex(txt))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("PDF");
    }

    @Test
    void indeksujePoprawnyPdf() {
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "raport.pdf", "application/pdf", "%PDF-1.4 fake".getBytes());

        when(documentRepository.save(any(Document.class))).thenAnswer(i -> i.getArgument(0));
        when(pdfProcessingService.process(any()))
                .thenReturn(new PdfProcessingService.PdfContent(
                        "Pełna treść dokumentu", List.of("chunk A", "chunk B")));
        when(chromaService.addChunks(any(), anyString(), any()))
                .thenReturn(List.of("id-1", "id-2"));

        Document result = service.uploadAndIndex(pdf);

        assertThat(result.getStatus()).isEqualTo(DocumentStatus.INDEXED);
        assertThat(result.getChunkCount()).isEqualTo(2);
        verify(chromaService).addChunks(any(), anyString(), any());
        verify(chunkRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void usuwanieNieistniejacegoRzucaNotFound() {
        when(documentRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteDocument(99L))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void statystykiZwracajaLiczby() {
        when(documentRepository.count()).thenReturn(3L);
        when(chunkRepository.count()).thenReturn(42L);
        lenient().when(embeddingService.dimensions()).thenReturn(1536);

        var stats = service.stats();

        assertThat(stats.documentCount()).isEqualTo(3L);
        assertThat(stats.chunkCount()).isEqualTo(42L);
        assertThat(stats.embeddingDimensions()).isEqualTo(1536);
    }
}
