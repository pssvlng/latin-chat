package com.passivlingo.latinchat.model;

import java.time.Instant;

public record Message(long id, long conversationId, String role, String content, Instant createdAt) {
}