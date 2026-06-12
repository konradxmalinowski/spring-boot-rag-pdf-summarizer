package com.example.rag.service;

import com.example.rag.dto.SummaryResponse;
import com.example.rag.entity.Document;
import com.example.rag.exception.AiServiceException;
import com.example.rag.exception.DocumentNotFoundException;
import com.example.rag.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @Mock DocumentRepository documentRepository;

    @Test
    void brakDokumentuRzucaNotFound() {
        ChatClient chatClient = mock(ChatClient.class);
        SummaryService service = new SummaryService(documentRepository, chatClient);
        when(documentRepository.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summarize(1L))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    void brakTresciRzucaAiServiceException() {
        ChatClient chatClient = mock(ChatClient.class);
        SummaryService service = new SummaryService(documentRepository, chatClient);
        when(documentRepository.findById(any()))
                .thenReturn(Optional.of(new Document("pusty.pdf", 10)));

        assertThatThrownBy(() -> service.summarize(1L))
                .isInstanceOf(AiServiceException.class);
    }

    @Test
    void generujeStreszczenie() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        SummaryService service = new SummaryService(documentRepository, chatClient);

        Document document = new Document("doc.pdf", 100);
        document.markIndexed(1, "Treść dokumentu do streszczenia.");
        when(documentRepository.findById(any())).thenReturn(Optional.of(document));

        SummaryResponse expected = new SummaryResponse(
                "krótko", "szczegółowo", List.of("punkt 1", "punkt 2"));
        when(chatClient.prompt()
                .system(anyString())
                .user(any(Consumer.class))
                .call()
                .entity(eq(SummaryResponse.class))).thenReturn(expected);

        SummaryResponse result = service.summarize(1L);

        assertThat(result.shortSummary()).isEqualTo("krótko");
        assertThat(result.keyPoints()).hasSize(2);
    }
}
