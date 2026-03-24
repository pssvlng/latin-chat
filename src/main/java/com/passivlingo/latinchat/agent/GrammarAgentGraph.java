package com.passivlingo.latinchat.agent;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.passivlingo.latinchat.model.GrammarAnalysisResult;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

public final class GrammarAgentGraph {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String FALLBACK_MESSAGE = "Partial analysis generated because the model response was not fully structured.";

    private static final String GRAMMAR_SYSTEM_PROMPT = """
            You are a strict Latin grammar analyst.
            Return ONLY valid JSON (no markdown fences, no extra text).

            Required JSON shape:
            {
              "status": "ok",
              "errorMessage": "",
              "sentencePairs": [
                {
                  "latin": "...",
                  "english": "...",
                  "latinHighlighted": "... with <mark>...</mark> around selected words ...",
                  "englishHighlighted": "... with <mark>...</mark> where context translation maps ..."
                }
              ],
              "words": [
                {
                  "selectedWord": "...",
                  "lemma": "...",
                  "partOfSpeech": "...",
                  "morphology": "...",
                  "baseForms": "for verbs include principal parts and conjugation class when possible",
                  "contextTranslation": "context-sensitive English translation",
                  "notes": ["..."],
                  "detailedTable": {
                    "title": "...",
                    "headers": ["..."],
                    "rows": [["...", "..."]]
                  },
                  "alternatives": ["..."]
                }
              ]
            }

            Rules:
            - Use the selected text and transcript context to identify the correct sentence context.
            - Provide side-by-side translation context in sentencePairs.
            - sentencePairs must contain at least one item and each item must have non-empty latin and english text.
            - Do not highlight words in sentencePairs. latin and english should be plain sentence text.
            - For single-word mode, detailedTable must be present with meaningful paradigm information.
            - Keep alternatives concise.
            - If analysis fails, return:
              {"status":"error","errorMessage":"...","sentencePairs":[],"words":[]}
            """;

    private static final String RETRY_PROMPT = """
            Your previous response did not satisfy strict JSON requirements.
            Return ONLY JSON that matches the exact schema.
            Do not include markdown, comments, or surrounding text.
            """;

    private static final double CONTEXT_COVERAGE_THRESHOLD = 0.6;

    private final OpenAiClient client;
    private final CompiledGraph<GrammarAgentState> graph;

    public GrammarAgentGraph(OpenAiClient client) {
        this.client = client;
        this.graph = compileGraph();
    }

    public GrammarAnalysisResult run(String apiKey, String selectedText, String transcriptText, boolean singleWord) throws Exception {
        Map<String, Object> input = new HashMap<>();
        input.put(GrammarAgentState.API_KEY, safe(apiKey));
        input.put(GrammarAgentState.SELECTED_TEXT, safe(selectedText));
        input.put(GrammarAgentState.TRANSCRIPT_TEXT, safe(transcriptText));
        input.put(GrammarAgentState.SINGLE_WORD, singleWord);
        input.put(GrammarAgentState.RAW_OUTPUT, "");
        input.put(GrammarAgentState.PARSE_ERROR, "");
        input.put(GrammarAgentState.VALID, false);
        input.put(GrammarAgentState.RESULT_JSON, fallbackJson(synthesizeFallbackResult(null)));

        Optional<GrammarAgentState> finalState = graph.invoke(input);
        if (finalState.isEmpty()) {
            return synthesizeFallbackResult(null);
        }
        return parseResultJson(finalState.get().resultJson(), finalState.get());
    }

    public String translateSelection(String apiKey, String selectedText) {
        String text = safe(selectedText).trim();
        if (text.isBlank()) {
            return "";
        }

        String translation = client.complete(
                apiKey,
                "You are a translation assistant. Translate the provided Latin text into clear English. Return only the English translation text.",
                "Translate this selected Latin text into English:\n" + text
        );
        return cleanupTranslation(translation);
    }

    private CompiledGraph<GrammarAgentState> compileGraph() {
        try {
            StateGraph<GrammarAgentState> stateGraph = new StateGraph<>(GrammarAgentState::new)
                    .addNode("first_pass", node_async(state -> {
                        String output = client.complete(state.apiKey(), GRAMMAR_SYSTEM_PROMPT, grammarUserPrompt(state));
                        return Map.of(GrammarAgentState.RAW_OUTPUT, output);
                    }))
                    .addNode("parse_first", node_async(this::parseNode))
                    .addNode("retry_pass", node_async(state -> {
                        String output = client.complete(
                                state.apiKey(),
                                GRAMMAR_SYSTEM_PROMPT,
                                RETRY_PROMPT + "\n\n" + grammarUserPrompt(state)
                        );
                        return Map.of(GrammarAgentState.RAW_OUTPUT, output);
                    }))
                    .addNode("parse_retry", node_async(this::parseNode))
                    .addNode("fallback", node_async(state -> Map.of(
                            GrammarAgentState.VALID, true,
                            GrammarAgentState.RESULT_JSON, fallbackJson(synthesizeFallbackResult(state))
                    )))
                    .addEdge(StateGraph.START, "first_pass")
                    .addEdge("first_pass", "parse_first")
                    .addConditionalEdges(
                            "parse_first",
                            edge_async(state -> state.valid() ? "done" : "retry"),
                            Map.of("done", StateGraph.END, "retry", "retry_pass")
                    )
                    .addEdge("retry_pass", "parse_retry")
                    .addConditionalEdges(
                            "parse_retry",
                            edge_async(state -> state.valid() ? "done" : "fallback"),
                            Map.of("done", StateGraph.END, "fallback", "fallback")
                    )
                    .addEdge("fallback", StateGraph.END);

            return stateGraph.compile();
        } catch (GraphStateException e) {
            throw new IllegalStateException("Failed to build grammar agent graph", e);
        }
    }

    private Map<String, Object> parseNode(GrammarAgentState state) {
        try {
            GrammarAnalysisResult parsed = parseModelJson(state.rawOutput()).normalized();
            parsed = ensureWholeSentencePair(parsed, state);
            String parsedJson = MAPPER.writeValueAsString(parsed);
            if (!parsed.isSuccess()) {
                return Map.of(
                        GrammarAgentState.VALID, false,
                        GrammarAgentState.PARSE_ERROR, safe(parsed.errorMessage()),
                        GrammarAgentState.RESULT_JSON, parsedJson
                );
            }
            return Map.of(
                    GrammarAgentState.VALID, true,
                    GrammarAgentState.PARSE_ERROR, "",
                    GrammarAgentState.RESULT_JSON, parsedJson
            );
        } catch (Exception ex) {
            return Map.of(
                    GrammarAgentState.VALID, false,
                    GrammarAgentState.PARSE_ERROR, safe(ex.getMessage()),
                    GrammarAgentState.RESULT_JSON, fallbackJson(synthesizeFallbackResult(state))
            );
        }
    }

    private GrammarAnalysisResult ensureWholeSentencePair(GrammarAnalysisResult result, GrammarAgentState state) {
        if (result == null) {
            return synthesizeFallbackResult(state);
        }

        String selectedWord = safe(state == null ? "" : state.selectedText()).trim();
        String fullSentence = extractSentenceFromContext(state == null ? "" : state.transcriptText(), selectedWord);
        if (fullSentence.isBlank()) {
            fullSentence = selectedWord;
        }

        GrammarAnalysisResult.SentencePair current = null;
        if (result.sentencePairs() != null && !result.sentencePairs().isEmpty()) {
            current = result.sentencePairs().get(0);
        }

        String currentLatin = current == null ? "" : safe(current.latin());
        String currentEnglish = current == null ? "" : safe(current.english());

        int fullSentenceWords = wordCount(fullSentence);
        int currentLatinWords = wordCount(currentLatin);

        boolean latinLooksWordOnly = !selectedWord.isBlank()
            && currentLatin.trim().equalsIgnoreCase(selectedWord)
            && fullSentenceWords > 1;
        boolean latinMissingOrTooShort = currentLatin.isBlank() || (currentLatinWords <= 1 && fullSentenceWords > 1);
        boolean latinLikelyTruncated = fullSentenceWords >= 5
            && currentLatinWords > 0
            && ((double) currentLatinWords / (double) fullSentenceWords) < CONTEXT_COVERAGE_THRESHOLD;

        boolean replacedLatin = latinMissingOrTooShort || latinLooksWordOnly || latinLikelyTruncated;
        String latinForContext = replacedLatin ? fullSentence : currentLatin;
        boolean englishLooksWordOnly = looksWordOnlyEnglish(currentEnglish, selectedWord, latinForContext);
        String englishForContext = (replacedLatin || currentEnglish.isBlank() || englishLooksWordOnly)
            ? translateSentence(latinForContext, state == null ? "" : state.apiKey())
            : currentEnglish;

        GrammarAnalysisResult.SentencePair fixedPair = new GrammarAnalysisResult.SentencePair(
                latinForContext,
                englishForContext,
                "",
                ""
        );

        return new GrammarAnalysisResult(
                result.isSuccess() ? "ok" : "error",
                safe(result.errorMessage()),
                List.of(fixedPair),
                result.words() == null ? List.of() : result.words()
        ).normalized();
    }

    private static GrammarAnalysisResult parseModelJson(String rawOutput) throws Exception {
        String candidate = safe(rawOutput).trim();
        if (candidate.startsWith("```")) {
            int firstNewline = candidate.indexOf('\n');
            int lastFence = candidate.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                candidate = candidate.substring(firstNewline + 1, lastFence).trim();
            }
        }

        int firstBrace = candidate.indexOf('{');
        int lastBrace = candidate.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            candidate = candidate.substring(firstBrace, lastBrace + 1);
        }

        return MAPPER.readValue(candidate, GrammarAnalysisResult.class);
    }

    private GrammarAnalysisResult parseResultJson(String resultJson, GrammarAgentState state) {
        if (resultJson == null || resultJson.isBlank()) {
            return synthesizeFallbackResult(state);
        }
        try {
            GrammarAnalysisResult parsed = MAPPER.readValue(resultJson, GrammarAnalysisResult.class).normalized();
            return ensureWholeSentencePair(parsed, state);
        } catch (Exception ex) {
            return synthesizeFallbackResult(state);
        }
    }

    private String fallbackJson(GrammarAnalysisResult result) {
        try {
            return MAPPER.writeValueAsString(result == null ? synthesizeFallbackResult(null) : result);
        } catch (Exception ex) {
            return "{\"status\":\"ok\",\"errorMessage\":\"\",\"sentencePairs\":[{\"latin\":\"\",\"english\":\"\",\"latinHighlighted\":\"\",\"englishHighlighted\":\"\"}],\"words\":[]}";
        }
    }

    private GrammarAnalysisResult synthesizeFallbackResult(GrammarAgentState state) {
        String selected = state == null ? "" : safe(state.selectedText());
        String context = state == null ? "" : safe(state.transcriptText());

        String latinSentence = extractSentenceFromContext(context, selected);
        if (latinSentence.isBlank()) {
            latinSentence = selected;
        }

        String englishSentence = translateSentence(latinSentence, state == null ? "" : state.apiKey());

        GrammarAnalysisResult.SentencePair pair = new GrammarAnalysisResult.SentencePair(
                latinSentence,
                englishSentence,
                "",
                ""
        );

        GrammarAnalysisResult.WordAnalysis word = new GrammarAnalysisResult.WordAnalysis(
                selected,
                "",
                "",
                "",
                "",
                "",
                List.of(FALLBACK_MESSAGE),
                null,
                List.of()
        );

        return new GrammarAnalysisResult(
                "ok",
                "",
                List.of(pair),
                selected.isBlank() ? List.of() : List.of(word)
        ).normalized();
    }

    private String translateSentence(String latinSentence, String apiKey) {
        String sentence = safe(latinSentence).trim();
        if (sentence.isBlank() || apiKey == null || apiKey.isBlank()) {
            return "";
        }
        try {
            String translation = client.complete(
                    apiKey,
                    "You translate Latin into idiomatic English. Return only the English translation sentence.",
                    "Translate this full Latin sentence into English: " + sentence
            );
            String cleaned = cleanupTranslation(translation);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        } catch (Exception ex) {
            // fall through to retry prompt
        }

        try {
            String retry = client.complete(
                    apiKey,
                    "Translate exactly one full Latin sentence into one full English sentence. Output only English sentence text.",
                    sentence
            );
            return cleanupTranslation(retry);
        } catch (Exception ex) {
            return "";
        }
    }

    private static boolean looksWordOnlyEnglish(String english, String selectedWord, String latinSentence) {
        String value = safe(english).trim();
        if (value.isBlank()) {
            return true;
        }

        int latinWords = wordCount(latinSentence);
        int englishWords = wordCount(value);
        if (latinWords > 1 && englishWords <= 1) {
            return true;
        }

        String selected = safe(selectedWord).trim();
        if (!selected.isBlank() && value.equalsIgnoreCase(selected)) {
            return true;
        }

        if (latinWords >= 6 && englishWords <= 2) {
            return true;
        }

        return false;
    }

    private static String cleanupTranslation(String value) {
        String cleaned = safe(value).trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        return cleaned;
    }

    private static int wordCount(String text) {
        String value = safe(text).trim();
        if (value.isBlank()) {
            return 0;
        }
        return value.split("\\s+").length;
    }

    private static String extractSentenceFromContext(String context, String selectedWord) {
        String text = safe(context);
        if (text.isBlank()) {
            return "";
        }

        int markerIdx = text.indexOf("Sentence containing selected word:");
        if (markerIdx >= 0) {
            String tail = text.substring(markerIdx + "Sentence containing selected word:".length()).trim();
            int end = tail.indexOf("\n\n");
            if (end > 0) {
                tail = tail.substring(0, end).trim();
            }
            if (!tail.isBlank()) {
                return tail;
            }
        }

        String word = safe(selectedWord).trim().toLowerCase();
        if (word.isBlank()) {
            return text;
        }

        String lower = text.toLowerCase();
        int idx = lower.indexOf(word);
        if (idx < 0) {
            return text;
        }

        int start = idx;
        while (start > 0) {
            char ch = text.charAt(start - 1);
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                break;
            }
            start--;
        }

        int end = idx + word.length();
        while (end < text.length()) {
            char ch = text.charAt(end);
            if (ch == '.' || ch == '!' || ch == '?' || ch == '\n') {
                end++;
                break;
            }
            end++;
        }

        return text.substring(Math.max(0, start), Math.min(end, text.length())).trim();
    }

    private static String grammarUserPrompt(GrammarAgentState state) {
        return """
                Selected text:
                %s

                Single-word mode: %s

                Transcript context:
                %s
                """.formatted(
                safe(state.selectedText()),
                state.singleWord() ? "yes" : "no",
                safe(state.transcriptText())
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
