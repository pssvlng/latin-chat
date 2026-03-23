package com.passivlingo.latinchat.agent;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class OpenAiClient {
    private final Map<String, OpenAiChatModel> modelCache = new ConcurrentHashMap<>();

    public String complete(String apiKey, String systemPrompt, String userPrompt) {
        OpenAiChatModel model = modelFor(apiKey);

        List<ChatMessage> messages = List.of(
                SystemMessage.from(systemPrompt),
                UserMessage.from(userPrompt)
        );

        String text = model.chat(messages).aiMessage().text();
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("OpenAI API returned empty response body");
        }
        return text;
    }

    private OpenAiChatModel modelFor(String apiKey) {
        return modelCache.computeIfAbsent(apiKey, key -> OpenAiChatModel.builder()
                .apiKey(key)
                .modelName("gpt-4o-mini")
                .temperature(0.3)
                .build());
    }
}
