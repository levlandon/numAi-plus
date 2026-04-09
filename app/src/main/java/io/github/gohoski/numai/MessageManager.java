package io.github.gohoski.numai;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Gleb on 21.08.2025.
 * get and add the messages
 */

class MessageManager {
    private static MessageManager instance;
    private List<Message> messages;
    private long currentChatId = -1L;

    private MessageManager() {
        messages = new ArrayList<>();
    }

    static synchronized MessageManager getInstance() {
        if (instance == null) {
            instance = new MessageManager();
        }
        return instance;
    }

    void addMessage(Message message) {
        messages.add(message);
    }

    List<Message> getMessages() {
        return messages;
    }

    void setMessages(List<Message> messages) {
        this.messages.clear();
        if (messages != null) {
            this.messages.addAll(messages);
        }
    }

    void clearMessages() {
        messages.clear();
    }

    long getCurrentChatId() {
        return currentChatId;
    }

    void setCurrentChatId(long currentChatId) {
        this.currentChatId = currentChatId;
    }
}
