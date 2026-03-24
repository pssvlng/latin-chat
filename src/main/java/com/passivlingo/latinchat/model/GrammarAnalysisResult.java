package com.passivlingo.latinchat.model;

import java.util.ArrayList;
import java.util.List;

public record GrammarAnalysisResult(
        String status,
        String errorMessage,
        List<SentencePair> sentencePairs,
        List<WordAnalysis> words
) {
    public static GrammarAnalysisResult error(String message) {
        return new GrammarAnalysisResult("error", safe(message), List.of(), List.of());
    }

    public boolean isSuccess() {
        return "ok".equalsIgnoreCase(safe(status));
    }

    public GrammarAnalysisResult normalized() {
        List<SentencePair> normalizedPairs = sentencePairs == null ? List.of() : sentencePairs.stream()
                .map(SentencePair::normalized)
                .toList();
        List<WordAnalysis> normalizedWords = words == null ? List.of() : words.stream()
                .map(WordAnalysis::normalized)
                .toList();
        return new GrammarAnalysisResult(
                isSuccess() ? "ok" : "error",
                safe(errorMessage),
                normalizedPairs,
                normalizedWords
        );
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public record SentencePair(
            String latin,
            String english,
            String latinHighlighted,
            String englishHighlighted
    ) {
        public SentencePair normalized() {
            return new SentencePair(
                    safe(latin),
                    safe(english),
                    safe(latinHighlighted),
                    safe(englishHighlighted)
            );
        }
    }

    public record WordAnalysis(
            String selectedWord,
            String lemma,
            String partOfSpeech,
            String morphology,
            String baseForms,
            String contextTranslation,
            List<String> notes,
            TableData detailedTable,
            List<String> alternatives
    ) {
        public WordAnalysis normalized() {
            List<String> normalizedNotes = notes == null ? List.of() : notes.stream()
                    .map(GrammarAnalysisResult::safe)
                    .filter(value -> !value.isBlank())
                    .toList();
            List<String> normalizedAlternatives = alternatives == null ? List.of() : alternatives.stream()
                    .map(GrammarAnalysisResult::safe)
                    .filter(value -> !value.isBlank())
                    .toList();
            return new WordAnalysis(
                    safe(selectedWord),
                    safe(lemma),
                    safe(partOfSpeech),
                    safe(morphology),
                    safe(baseForms),
                    safe(contextTranslation),
                    normalizedNotes,
                    detailedTable == null ? null : detailedTable.normalized(),
                    normalizedAlternatives
            );
        }

        public TableData fallbackTable() {
            List<String> headers = List.of("Field", "Value");
            List<List<String>> rows = new ArrayList<>();
            rows.add(List.of("Word", safe(selectedWord)));
            rows.add(List.of("Lemma", safe(lemma)));
            rows.add(List.of("Part of speech", safe(partOfSpeech)));
            rows.add(List.of("Morphology", safe(morphology)));
            rows.add(List.of("Base forms", safe(baseForms)));
            rows.add(List.of("Context translation", safe(contextTranslation)));
            return new TableData("Detailed Analysis", headers, rows).normalized();
        }
    }

    public record TableData(
            String title,
            List<String> headers,
            List<List<String>> rows
    ) {
        public TableData normalized() {
            List<String> normalizedHeaders = headers == null ? List.of() : headers.stream()
                    .map(GrammarAnalysisResult::safe)
                    .toList();
            List<List<String>> normalizedRows = rows == null ? List.of() : rows.stream()
                    .map(row -> row == null ? List.<String>of() : row.stream().map(GrammarAnalysisResult::safe).toList())
                    .toList();
            return new TableData(safe(title), normalizedHeaders, normalizedRows);
        }
    }
}
