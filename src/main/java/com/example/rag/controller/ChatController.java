package com.example.rag.controller;

import com.example.rag.dto.AskRequest;
import com.example.rag.dto.AskResponse;
import com.example.rag.service.RagService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    /** RAG search: zadaj pytanie do zaindeksowanych dokumentów. */
    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return ragService.ask(request);
    }
}
