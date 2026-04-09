package io.github.gohoski.numai;

import java.util.List;

/**
 * Created by Gleb on 21.08.2025.
 */

class Message {
    private long messageId = -1L;
    private long chatId = -1L;
    private long timestamp = 0L;
    private Role role = Role.USER;
    private String content = "";
    private String llm;
    List<String> inputImages;
    private List<ChatAttachment> attachments;
    private boolean isError = false;
    private boolean reasoningUsed = false;

    Message(Role role, String content) {
        this.role = role;
        this.content = content;
    }
    Message(Role role, String content, String llm) {
        this.role = role;
        this.content = content;
        this.llm = llm;
    }
    Message(Role role, String content, List<String> inputImages, String llm) {
        this.role = role;
        this.content = content;
        this.llm = llm;
        this.inputImages = inputImages;
    }

    long getMessageId() {
        return messageId;
    }

    void setMessageId(long messageId) {
        this.messageId = messageId;
    }

    long getChatId() {
        return chatId;
    }

    void setChatId(long chatId) {
        this.chatId = chatId;
    }

    long getTimestamp() {
        return timestamp;
    }

    void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    String getRole() {
        return role.toString();
    }

    String getContent() {
        return content;
    }

    String getLlm() {
        return llm;
    }

    void setLlm(String llm) {
        this.llm = llm;
    }

    List<String> getInputImages() {
        if ((inputImages == null || inputImages.isEmpty()) && attachments != null && !attachments.isEmpty()) {
            java.util.ArrayList<String> restored = new java.util.ArrayList<String>();
            for (int i = 0; i < attachments.size(); i++) {
                ChatAttachment attachment = attachments.get(i);
                if (attachment == null || !attachment.isImage()) continue;
                String dataUrl = attachment.toDataUrl();
                if (dataUrl != null && dataUrl.length() != 0) {
                    restored.add(dataUrl);
                }
            }
            inputImages = restored;
        }
        return inputImages;
    }

    void setInputImages(List<String> inputImages) {
        this.inputImages = inputImages;
    }

    List<ChatAttachment> getAttachments() {
        return attachments;
    }

    void setAttachments(List<ChatAttachment> attachments) {
        this.attachments = attachments;
    }

    // update content
    public void updateContent(String additionalContent) {
        this.content += additionalContent;
    }

    //set content completely
    void setContent(String newContent) {
        this.content = newContent;
    }

    boolean isReasoningUsed() {
        return reasoningUsed;
    }

    void setReasoningUsed(boolean reasoningUsed) {
        this.reasoningUsed = reasoningUsed;
    }

    boolean isSent() {
        return role == Role.USER;
    }

    void setAsError() {
        isError=true;
    }
}
