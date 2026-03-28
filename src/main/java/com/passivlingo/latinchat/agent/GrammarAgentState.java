package com.passivlingo.latinchat.agent;

import java.util.Map;

public final class GrammarAgentState extends org.bsc.langgraph4j.state.AgentState {
    public static final String API_KEY = "apiKey";
    public static final String SELECTED_TEXT = "selectedText";
    public static final String TRANSCRIPT_TEXT = "transcriptText";
    public static final String SINGLE_WORD = "singleWord";
    public static final String LANGUAGE_CODE = "languageCode";
    public static final String RAW_OUTPUT = "rawOutput";
    public static final String PARSE_ERROR = "parseError";
    public static final String VALID = "valid";
    public static final String RESULT_JSON = "resultJson";

    public GrammarAgentState(Map<String, Object> data) {
        super(data);
    }

    public String apiKey() {
        return value(API_KEY, "");
    }

    public String selectedText() {
        return value(SELECTED_TEXT, "");
    }

    public String transcriptText() {
        return value(TRANSCRIPT_TEXT, "");
    }

    public boolean singleWord() {
        return value(SINGLE_WORD, Boolean.FALSE);
    }

    public String languageCode() {
        return value(LANGUAGE_CODE, "la");
    }

    public String rawOutput() {
        return value(RAW_OUTPUT, "");
    }

    public String parseError() {
        return value(PARSE_ERROR, "");
    }

    public boolean valid() {
        return value(VALID, Boolean.FALSE);
    }

    public String resultJson() {
        return value(RESULT_JSON, "");
    }
}
