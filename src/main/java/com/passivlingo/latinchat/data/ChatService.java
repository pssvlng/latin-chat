package com.passivlingo.latinchat.data;

import com.passivlingo.latinchat.model.Conversation;
import com.passivlingo.latinchat.model.ConversationWebTab;
import com.passivlingo.latinchat.model.Message;

import java.sql.SQLException;
import java.util.List;

public final class ChatService {
    private final ChatRepository repository;

    public ChatService(ChatRepository repository) {
        this.repository = repository;
    }

    public List<Conversation> listConversations() throws SQLException {
        return repository.listConversations();
    }

    public long createConversation(String title) throws SQLException {
        return repository.createConversation(title);
    }

    public void deleteConversation(long conversationId) throws SQLException {
        repository.deleteConversation(conversationId);
    }

    public List<Message> listMessages(long conversationId) throws SQLException {
        return repository.listMessages(conversationId);
    }

    public void addUserMessage(long conversationId, String content) throws SQLException {
        repository.addMessage(conversationId, "user", content);
    }

    public void addAssistantMessage(long conversationId, String content) throws SQLException {
        repository.addMessage(conversationId, "assistant", content);
    }

    public List<ConversationWebTab> listWebTabs(long conversationId) throws SQLException {
        return repository.listWebTabs(conversationId);
    }

    public long addWebTab(long conversationId, String url) throws SQLException {
        return repository.addWebTab(conversationId, url);
    }

    public void deleteWebTab(long webTabId) throws SQLException {
        repository.deleteWebTab(webTabId);
    }
}