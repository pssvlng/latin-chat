package com.passivlingo.latinchat.model;

import java.time.Instant;

public record Conversation(long id, String title, Instant createdAt, Instant updatedAt) {
}
