package com.passivlingo.latinchat.agent;

import java.util.Map;

public final class AgentState extends org.bsc.langgraph4j.state.AgentState {
    public static final String API_KEY = "apiKey";
    public static final String USER_TEXT = "userText";
    public static final String CONTEXT_MARKDOWN = "contextMarkdown";
    public static final String INCLUDE_WEB_SEARCH = "includeWebSearch";
    public static final String LANGUAGE_CODE = "languageCode";
    public static final String MODEL_OUTPUT = "modelOutput";
    public static final String LATIN_VALID = "latinValid";

    public AgentState(Map<String, Object> data) {
        super(data);
    }

    public String apiKey() {
        return value(API_KEY, "");
    }

    public String userText() {
        return value(USER_TEXT, "");
    }

    public String contextMarkdown() {
        return value(CONTEXT_MARKDOWN, "");
    }

    public boolean includeWebSearch() {
        return value(INCLUDE_WEB_SEARCH, Boolean.FALSE);
    }

    public String languageCode() {
        return value(LANGUAGE_CODE, "la");
    }

    public String modelOutput() {
        return value(MODEL_OUTPUT, "");
    }

    public boolean latinValid() {
        return value(LATIN_VALID, Boolean.FALSE);
    }
}
