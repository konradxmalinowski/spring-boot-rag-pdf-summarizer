package com.example.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Buduje pojedynczy {@link ChatClient} z auto-skonfigurowanego buildera.
 *
 * Dzięki temu serwisy zależą od ChatClient (abstrakcja), a nie od konkretnego
 * modelu. Zmiana OpenAI -> Ollama odbywa się przez profil/konfigurację, bez
 * dotykania kodu (zob. application-ollama.yml).
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
