package io.github.gohoski.numai;

class ChatRecord {
    private long chatId;
    private Long projectId;
    private String title;
    private long createdAt;
    private long updatedAt;
    private String lastMessagePreview;
    private String modelName;
    private boolean reasoningEnabledDefault;

    long getChatId() {
        return chatId;
    }

    void setChatId(long chatId) {
        this.chatId = chatId;
    }

    Long getProjectId() {
        return projectId;
    }

    void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    String getTitle() {
        return title;
    }

    void setTitle(String title) {
        this.title = title;
    }

    long getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    long getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    String getLastMessagePreview() {
        return lastMessagePreview;
    }

    void setLastMessagePreview(String lastMessagePreview) {
        this.lastMessagePreview = lastMessagePreview;
    }

    String getModelName() {
        return modelName;
    }

    void setModelName(String modelName) {
        this.modelName = modelName;
    }

    boolean isReasoningEnabledDefault() {
        return reasoningEnabledDefault;
    }

    void setReasoningEnabledDefault(boolean reasoningEnabledDefault) {
        this.reasoningEnabledDefault = reasoningEnabledDefault;
    }
}
