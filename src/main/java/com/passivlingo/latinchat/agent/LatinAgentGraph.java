package com.passivlingo.latinchat.agent;

import com.passivlingo.latinchat.model.LanguageConfig;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public final class LatinAgentGraph {
    private static final String FALLBACK_OUTPUT = "Ignosce, iterum conabor. Quaeso eandem quaestionem repete.";

    private final OpenAiClient client;
    private final CompiledGraph<AgentState> graph;

    public LatinAgentGraph(OpenAiClient client) {
        this.client = client;
        this.graph = compileGraph();
    }

    public String run(String apiKey, String userText, String conversationContext) throws Exception {
        return run(apiKey, userText, conversationContext, false, "la");
    }

    public String run(String apiKey, String userText, String conversationContext, boolean includeWebSearch) throws Exception {
        return run(apiKey, userText, conversationContext, includeWebSearch, "la");
    }

    public String run(String apiKey,
                      String userText,
                      String conversationContext,
                      boolean includeWebSearch,
                      String languageCode) throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put(AgentState.API_KEY, safe(apiKey));
        input.put(AgentState.USER_TEXT, safe(userText));
        input.put(AgentState.CONTEXT_MARKDOWN, safe(conversationContext));
        input.put(AgentState.INCLUDE_WEB_SEARCH, includeWebSearch);
        input.put(AgentState.LANGUAGE_CODE, safe(languageCode));
        input.put(AgentState.MODEL_OUTPUT, "");
        input.put(AgentState.LATIN_VALID, false);

        Optional<AgentState> finalState = graph.invoke(input);
        if (finalState.isEmpty()) {
            return FALLBACK_OUTPUT;
        }

        String output = finalState.get().modelOutput();
        return (output == null || output.isBlank()) ? FALLBACK_OUTPUT : output;
    }

    private CompiledGraph<AgentState> compileGraph() {
        try {
            StateGraph<AgentState> stateGraph = new StateGraph<>(AgentState::new)
                    .addNode("first_pass", node_async(state -> {
                    String systemPrompt = buildSystemPrompt(state.languageCode());
                        String prompt = "Contextus conversationis:\n" + state.contextMarkdown() + "\n\nNuntius usoris:\n" + state.userText();
                        String output = state.includeWebSearch()
                        ? client.completeWithWebSearch(state.apiKey(), systemPrompt, prompt, state.userText())
                        : client.complete(state.apiKey(), systemPrompt, prompt);
                        return Map.of(AgentState.MODEL_OUTPUT, output);
                    }))
                    .addNode("validate_first", node_async(state ->
                        Map.of(AgentState.LATIN_VALID, looksAcceptable(state.modelOutput(), state.languageCode()))
                    ))
                    .addNode("retry_pass", node_async(state -> {
                    String systemPrompt = buildSystemPrompt(state.languageCode());
                    String prompt = buildRetryPrompt(state.languageCode()) + "\n\nNuntius usoris:\n" + state.userText();
                        String output = state.includeWebSearch()
                        ? client.completeWithWebSearch(state.apiKey(), systemPrompt, prompt, state.userText())
                        : client.complete(state.apiKey(), systemPrompt, prompt);
                        return Map.of(AgentState.MODEL_OUTPUT, output);
                    }))
                    .addNode("validate_retry", node_async(state ->
                        Map.of(AgentState.LATIN_VALID, looksAcceptable(state.modelOutput(), state.languageCode()))
                    ))
                    .addNode("fallback_response", node_async(state ->
                            Map.of(AgentState.MODEL_OUTPUT, FALLBACK_OUTPUT, AgentState.LATIN_VALID, false)
                    ))
                    .addEdge(StateGraph.START, "first_pass")
                    .addEdge("first_pass", "validate_first")
                    .addConditionalEdges(
                            "validate_first",
                            edge_async(state -> state.latinValid() ? "done" : "retry"),
                            Map.of("done", StateGraph.END, "retry", "retry_pass")
                    )
                    .addEdge("retry_pass", "validate_retry")
                    .addConditionalEdges(
                            "validate_retry",
                            edge_async(state -> state.latinValid() ? "done" : "fallback"),
                            Map.of("done", StateGraph.END, "fallback", "fallback_response")
                    )
                    .addEdge("fallback_response", StateGraph.END);

            return stateGraph.compile();
        } catch (GraphStateException e) {
            throw new IllegalStateException("Failed to build Latin agent graph", e);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static boolean looksAcceptable(String output, String languageCode) {
        if (output == null || output.isBlank()) {
            return false;
        }
        LanguageConfig language = LanguageConfig.fromCode(languageCode).orElse(LanguageConfig.defaultLanguage());
        if ("la".equals(language.baseCode())) {
            return LatinValidator.looksLatin(output);
        }
        return true;
    }

    private static String buildSystemPrompt(String languageCode) {
        LanguageConfig language = LanguageConfig.fromCode(languageCode).orElse(LanguageConfig.defaultLanguage());
        String target = language.displayName();
        String baseTarget = language.transcribed()
                ? language.displayName().replace(" Transcribed", "")
                : target;

        if (language.transcribed()) {
            return """
                    You are a knowledgeable and friendly assistant.
                    Always respond in %s, but output must be romanized/transcribed into the Latin alphabet.
                    Requirements:
                    - Keep the content in %s. Do not translate the response into English.
                    - Use only left-to-right Latin letters and normal punctuation.
                    - Do not output native-script characters.
                    - Use natural spelling/transliteration conventions for %s.
                    - Format responses in clean Markdown with headings/lists/code blocks when useful.
                    - Use conversation context for continuity and avoid repeating the whole history.
                    """.formatted(target, baseTarget, baseTarget);
        }

        return """
                You are a knowledgeable and friendly assistant.
                Always respond in %s.
                Requirements:
                - Keep all response text in %s.
                - Format responses in clean Markdown with headings/lists/code blocks when useful.
                - If user intent is ambiguous, ask a brief clarification in %s.
                - Use conversation context for continuity and avoid repeating the whole history.
                """.formatted(target, target, target);
    }

    private static String buildRetryPrompt(String languageCode) {
        LanguageConfig language = LanguageConfig.fromCode(languageCode).orElse(LanguageConfig.defaultLanguage());
        String target = language.displayName().toLowerCase(Locale.ROOT);
        String baseTarget = language.transcribed()
                ? language.displayName().replace(" Transcribed", "").toLowerCase(Locale.ROOT)
                : target;

        if (language.transcribed()) {
            return """
                    The previous answer did not follow the format. Reply again:
                    - Use %s only.
                    - Keep the content in %s (not English translation).
                    - Keep output romanized in Latin alphabet.
                    - Do not include native-script characters.
                    - Keep Markdown formatting.
                    """.formatted(target, baseTarget);
        }

        return """
                The previous answer did not follow the required language. Reply again:
                - Use only %s.
                - Keep Markdown formatting.
                """.formatted(target);
    }
}
