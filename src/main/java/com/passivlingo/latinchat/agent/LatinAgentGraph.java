package com.passivlingo.latinchat.agent;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public final class LatinAgentGraph {
    private static final String LATIN_SYSTEM_PROMPT = """
            Tu es adiutor eruditus et urbanus. SEMPER Latine responde, etiam si usor alia lingua scribit.
            Regulae:
            - Solum lingua Latina in responsione adhibeatur.
            - Forma responsionis sit Markdown clara et pulchra.
            - Utere capitibus, indicibus, et codicis tabulis cum opus est.
            - Si quaestio est ambigua, breviter quaere clarificationem, sed Latine.
            - Contextum ad intellegendum utere; noli totam conversationem in responsione transferre.
            """;

    private static final String LATIN_RETRY_PROMPT = """
            Responsio prior satis Latina non erat. Nunc iterum responde:
            - SOLA lingua Latina utenda est.
            - Nulla verba Anglica, Germanica, Gallica, Hispanica.
            - Forma Markdown servetur.
            """;

    private static final String FALLBACK_OUTPUT = "Ignosce, iterum conabor. Quaeso eandem quaestionem repete.";

    private final OpenAiClient client;
    private final CompiledGraph<AgentState> graph;

    public LatinAgentGraph(OpenAiClient client) {
        this.client = client;
        this.graph = compileGraph();
    }

    public String run(String apiKey, String userText, String conversationContext) throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put(AgentState.API_KEY, safe(apiKey));
        input.put(AgentState.USER_TEXT, safe(userText));
        input.put(AgentState.CONTEXT_MARKDOWN, safe(conversationContext));
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
                        String prompt = "Contextus conversationis:\n" + state.contextMarkdown() + "\n\nNuntius usoris:\n" + state.userText();
                        String output = client.complete(state.apiKey(), LATIN_SYSTEM_PROMPT, prompt);
                        return Map.of(AgentState.MODEL_OUTPUT, output);
                    }))
                    .addNode("validate_first", node_async(state ->
                            Map.of(AgentState.LATIN_VALID, LatinValidator.looksLatin(state.modelOutput()))
                    ))
                    .addNode("retry_pass", node_async(state -> {
                        String prompt = LATIN_RETRY_PROMPT + "\n\nNuntius usoris:\n" + state.userText();
                        String output = client.complete(state.apiKey(), LATIN_SYSTEM_PROMPT, prompt);
                        return Map.of(AgentState.MODEL_OUTPUT, output);
                    }))
                    .addNode("validate_retry", node_async(state ->
                            Map.of(AgentState.LATIN_VALID, LatinValidator.looksLatin(state.modelOutput()))
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
}
