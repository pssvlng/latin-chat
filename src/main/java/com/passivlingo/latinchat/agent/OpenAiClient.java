package com.passivlingo.latinchat.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public final class OpenAiClient {
    private static final URI RESPONSES_URI = URI.create("https://api.openai.com/v1/responses");
    private static final Logger LOG = Logger.getLogger(OpenAiClient.class.getName());
    private static final String DEFAULT_MODEL = "gpt-5.4-mini";
    private static final String WEB_SEARCH_BADGE = "\n\n---\n[Web] _Web search used for this reply._";
    private static final Pattern FRESHNESS_PATTERN = Pattern.compile(
            "\\b(recent|latest|current|today|yesterday|this week|this month|now|breaking|news|conference|event|events|nuntios|recens|recentes|hodie|hesterno|nunc|conventus|res gestae)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Map<String, OpenAiChatModel> modelCache = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

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

    public String completeWithWebSearch(String apiKey, String systemPrompt, String userPrompt, String userText) {
        try {
            String model = resolvedModelName();

            boolean freshnessRequested = needsFreshSearch(userText);
            LOG.info("Web-search chat request. freshnessRequested=" + freshnessRequested);

            JsonNode firstPayload = mapper.createObjectNode()
                    .put("model", model)
                    .put("input", userPrompt)
                    .put("instructions", systemPrompt)
                    .put("temperature", 0.2)
                    .put("max_output_tokens", 1200)
                    .set("tools", mapper.createArrayNode().add(
                            mapper.createObjectNode().put("type", "web_search_preview")
                    ));

            if (freshnessRequested) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) firstPayload).put("tool_choice", "required");
            }

            JsonNode firstResponse = sendResponsesRequest(apiKey, firstPayload);

            if (freshnessRequested && !responseIncludesWebSearchCall(firstResponse)) {
                LOG.warning("No web_search call detected on first attempt; retrying with strict tool-required instruction.");
                JsonNode strictRetryPayload = mapper.createObjectNode()
                        .put("model", model)
                        .put("input", userPrompt)
                    .put("instructions", systemPrompt + "\\n\\nIf the user asks about recent or current events, use web_search_preview before answering.")
                        .put("temperature", 0.2)
                        .put("max_output_tokens", 1200)
                        .put("tool_choice", "required")
                        .set("tools", mapper.createArrayNode().add(
                                mapper.createObjectNode().put("type", "web_search_preview")
                        ));
                firstResponse = sendResponsesRequest(apiKey, strictRetryPayload);
            }

            boolean webSearchUsed = responseIncludesWebSearchCall(firstResponse);
            LOG.info("Web-search call detected=" + webSearchUsed);

            String text = extractOutputText(firstResponse);
            if (text == null || text.isBlank()) {
                throw new IllegalStateException("OpenAI Responses API returned empty response body");
            }
            if (webSearchUsed && !text.contains("Web search used for this reply")) {
                text = text + WEB_SEARCH_BADGE;
            }
            return text;
        } catch (Exception ex) {
            throw new IllegalStateException("OpenAI web-search completion failed", ex);
        }
    }

    private JsonNode sendResponsesRequest(String apiKey, JsonNode payload) throws Exception {
        String payloadText = mapper.writeValueAsString(payload);
            HttpRequest request = HttpRequest.newBuilder(RESPONSES_URI)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payloadText, StandardCharsets.UTF_8))
                    .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI Responses API error status: " + response.statusCode());
        }
        return mapper.readTree(response.body());
    }

    private boolean responseIncludesWebSearchCall(JsonNode root) {
        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return false;
        }
        for (JsonNode item : output) {
            String type = item.path("type").asText("");
            if ("web_search_call".equals(type) || "web_search_preview_call".equals(type)) {
                return true;
            }
        }
        return false;
    }

    private OpenAiChatModel modelFor(String apiKey) {
        String modelName = resolvedModelName();
        String cacheKey = apiKey + "::" + modelName;
        if (modelCache.size() > 6 && !modelCache.containsKey(cacheKey)) {
            modelCache.clear();
        }
        return modelCache.computeIfAbsent(cacheKey, ignored -> OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(0.3)
                .build());
    }

    private String resolvedModelName() {
        String model = System.getenv("OPENAI_MODEL");
        if (model != null && !model.isBlank()) {
            return model.trim();
        }
        String runtimeModel = System.getProperty("OPENAI_MODEL");
        if (runtimeModel != null && !runtimeModel.isBlank()) {
            return runtimeModel.trim();
        }
        return DEFAULT_MODEL;
    }

    private String extractOutputText(JsonNode root) {
        try {
            String topLevel = root.path("output_text").asText("").trim();
            if (!topLevel.isBlank()) {
                return topLevel;
            }

            StringBuilder combined = new StringBuilder();
            JsonNode output = root.path("output");
            if (output.isArray()) {
                for (JsonNode item : output) {
                    JsonNode content = item.path("content");
                    if (!content.isArray()) {
                        continue;
                    }
                    for (JsonNode chunk : content) {
                        String text = chunk.path("text").asText("").trim();
                        if (!text.isBlank()) {
                            if (!combined.isEmpty()) {
                                combined.append("\n");
                            }
                            combined.append(text);
                        }
                    }
                }
            }
            return combined.toString().trim();
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean needsFreshSearch(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return FRESHNESS_PATTERN.matcher(text).find();
    }
}
