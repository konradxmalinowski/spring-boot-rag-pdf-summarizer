package com.example.rag.controller;

import com.example.rag.dto.AskResponse;
import com.example.rag.entity.Document;
import com.example.rag.exception.DocumentNotFoundException;
import com.example.rag.service.DocumentService;
import com.example.rag.service.RagService;
import com.example.rag.service.SummaryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test integracyjny warstwy REST (MockMvc). Serwisy są zamockowane —
 * sprawdzamy mapowanie HTTP, statusy i obsługę błędów, bez DB/AI/Chromy.
 */
@WebMvcTest(controllers = {DocumentController.class, ChatController.class})
class RestApiIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean DocumentService documentService;
    @MockitoBean SummaryService summaryService;
    @MockitoBean RagService ragService;

    @Test
    void uploadZwraca201() throws Exception {
        Document document = new Document("raport.pdf", 1234);
        document.markIndexed(3, "treść");
        when(documentService.uploadAndIndex(any())).thenReturn(document);

        var file = new org.springframework.mock.web.MockMultipartFile(
                "file", "raport.pdf", "application/pdf", "%PDF-1.4".getBytes());

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("raport.pdf"))
                .andExpect(jsonPath("$.status").value("INDEXED"))
                .andExpect(jsonPath("$.chunkCount").value(3));
    }

    @Test
    void summaryNieistniejacyZwraca404() throws Exception {
        when(summaryService.summarize(eq(99L)))
                .thenThrow(new DocumentNotFoundException(99L));

        mockMvc.perform(post("/api/documents/99/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deleteZwraca204() throws Exception {
        mockMvc.perform(delete("/api/documents/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void askZwracaOdpowiedz() throws Exception {
        when(ragService.ask(any()))
                .thenReturn(new AskResponse("To dokument o RAG.", List.of("fragment")));

        mockMvc.perform(post("/api/chat/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"O czym jest dokument?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("To dokument o RAG."))
                .andExpect(jsonPath("$.sources[0]").value("fragment"));
    }

    @Test
    void askWalidujePustePytanie() throws Exception {
        mockMvc.perform(post("/api/chat/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
