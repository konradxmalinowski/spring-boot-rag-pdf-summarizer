package com.example.rag.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds a single {@link ChatClient} from the auto-configured builder.
 *
 * Services depend on the ChatClient abstraction rather than a concrete model.
 * Switching from OpenAI to Ollama is done via profile/configuration without
 * touching the code (see application-ollama.yml).
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
