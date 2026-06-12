package com.example.rag.service;

import com.example.rag.dto.AskRequest;
import com.example.rag.dto.AskResponse;
import com.example.rag.vectorstore.ChromaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    ChromaService chromaService;

    @Test
    void zwracaKomunikatGdyBrakDopasowan() {
        ChatClient chatClient = mock(ChatClient.class);
        RagService service = new RagService(chromaService, chatClient, 4);
        when(chromaService.search(anyString(), anyInt(), isNull())).thenReturn(List.of());

        AskResponse response = service.ask(new AskRequest("Cokolwiek?", null));

        assertThat(response.answer()).contains("Nie znalazłem");
        assertThat(response.sources()).isEmpty();
        // Bez kontekstu nie wołamy modelu.
        verifyNoInteractions(chatClient);
    }

    @Test
    void budujeKontekstIWolaModel() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        RagService service = new RagService(chromaService, chatClient, 4);

        when(chromaService.search(anyString(), anyInt(), isNull()))
                .thenReturn(List.of(new Document("Spring AI to framework."),
                        new Document("RAG łączy retrieval i generację.")));
        when(chatClient.prompt()
                .system(anyString())
                .user(any(Consumer.class))
                .call()
                .content()).thenReturn("To dokument o Spring AI i RAG.");

        AskResponse response = service.ask(new AskRequest("O czym jest dokument?", null));

        assertThat(response.answer()).isEqualTo("To dokument o Spring AI i RAG.");
        assertThat(response.sources()).hasSize(2);
    }
}
