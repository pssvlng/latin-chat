package com.passivlingo.latinchat.model;

import java.time.Instant;

public record ConversationWebTab(long id, long conversationId, String url, Instant createdAt) {
}