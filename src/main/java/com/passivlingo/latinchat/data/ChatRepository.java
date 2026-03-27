package com.passivlingo.latinchat.data;

import com.passivlingo.latinchat.model.Conversation;
import com.passivlingo.latinchat.model.ConversationWebTab;
import com.passivlingo.latinchat.model.Message;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ChatRepository {
    private final DatabaseManager db;

    public ChatRepository(DatabaseManager db) {
        this.db = db;
    }

    public synchronized List<Conversation> listConversations() throws SQLException {
        List<Conversation> items = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement("""
            SELECT id, title, include_web_search, created_at, updated_at
                FROM conversations
                ORDER BY updated_at DESC
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new Conversation(
                            rs.getLong("id"),
                            rs.getString("title"),
                            Instant.parse(rs.getString("created_at")),
                            Instant.parse(rs.getString("updated_at")),
                            rs.getInt("include_web_search") == 1));
                }
            }
        }
        return items;
    }

    public synchronized long createConversation(String title, boolean includeWebSearch) throws SQLException {
        Instant now = Instant.now();
        try (PreparedStatement ps = db.connection().prepareStatement("""
                INSERT INTO conversations(title, include_web_search, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                """, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, title);
            ps.setInt(2, includeWebSearch ? 1 : 0);
            ps.setString(3, now.toString());
            ps.setString(4, now.toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to create conversation");
    }

    public synchronized void deleteConversation(long conversationId) throws SQLException {
        try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM conversations WHERE id = ?")) {
            ps.setLong(1, conversationId);
            ps.executeUpdate();
        }
    }

    public synchronized void deleteAllConversations() throws SQLException {
        try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM conversations")) {
            ps.executeUpdate();
        }
    }

    public synchronized List<Message> listMessages(long conversationId) throws SQLException {
        List<Message> items = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement("""
                SELECT id, conversation_id, role, content, created_at
                FROM messages
                WHERE conversation_id = ?
                ORDER BY created_at ASC, id ASC
                """)) {
            ps.setLong(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new Message(
                            rs.getLong("id"),
                            rs.getLong("conversation_id"),
                            rs.getString("role"),
                            rs.getString("content"),
                            Instant.parse(rs.getString("created_at"))));
                }
            }
        }
        return items;
    }

    public synchronized long addMessage(long conversationId, String role, String content) throws SQLException {
        Instant now = Instant.now();
        try (PreparedStatement ps = db.connection().prepareStatement("""
                INSERT INTO messages(conversation_id, role, content, created_at)
                VALUES (?, ?, ?, ?)
                """, PreparedStatement.RETURN_GENERATED_KEYS);
             PreparedStatement updateConversation = db.connection().prepareStatement("""
                UPDATE conversations SET updated_at = ? WHERE id = ?
                """)) {
            ps.setLong(1, conversationId);
            ps.setString(2, role);
            ps.setString(3, content);
            ps.setString(4, now.toString());
            ps.executeUpdate();

            updateConversation.setString(1, now.toString());
            updateConversation.setLong(2, conversationId);
            updateConversation.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to insert message");
    }

    public synchronized List<ConversationWebTab> listWebTabs(long conversationId) throws SQLException {
        List<ConversationWebTab> items = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement("""
                SELECT id, conversation_id, url, created_at
                FROM web_tabs
                WHERE conversation_id = ?
                ORDER BY created_at ASC, id ASC
                """)) {
            ps.setLong(1, conversationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new ConversationWebTab(
                            rs.getLong("id"),
                            rs.getLong("conversation_id"),
                            rs.getString("url"),
                            Instant.parse(rs.getString("created_at"))));
                }
            }
        }
        return items;
    }

    public synchronized long addWebTab(long conversationId, String url) throws SQLException {
        Instant now = Instant.now();
        try (PreparedStatement ps = db.connection().prepareStatement("""
                INSERT INTO web_tabs(conversation_id, url, created_at)
                VALUES (?, ?, ?)
                """, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, conversationId);
            ps.setString(2, url);
            ps.setString(3, now.toString());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to persist web tab");
    }

    public synchronized void deleteWebTab(long webTabId) throws SQLException {
        try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM web_tabs WHERE id = ?")) {
            ps.setLong(1, webTabId);
            ps.executeUpdate();
        }
    }
}