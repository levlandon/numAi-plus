package io.github.gohoski.numai;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

class ChatRepository {
    private static final String ATTACHMENTS_DIR = "chat_attachments";
    private static final int CHAT_TITLE_MAX = 48;
    private static final int PREVIEW_MAX = 96;

    private static ChatRepository instance;

    private final Context context;
    private final ChatDatabaseHelper helper;

    private ChatRepository(Context context) {
        this.context = context.getApplicationContext();
        this.helper = new ChatDatabaseHelper(this.context);
    }

    static synchronized ChatRepository getInstance(Context context) {
        if (instance == null) {
            instance = new ChatRepository(context);
        }
        return instance;
    }

    ChatRecord createChat(String modelName, boolean reasoningEnabledDefault) {
        SQLiteDatabase db = helper.getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.putNull("project_id");
        values.putNull("title");
        values.put("created_at", now);
        values.put("updated_at", now);
        values.putNull("last_message_preview");
        if (modelName == null || modelName.length() == 0) {
            values.putNull("model_name");
        } else {
            values.put("model_name", modelName);
        }
        values.put("reasoning_enabled_default", reasoningEnabledDefault ? 1 : 0);
        long chatId = db.insert(ChatDatabaseHelper.TABLE_CHATS, null, values);
        return getChat(chatId);
    }

    ChatRecord getChat(long chatId) {
        if (chatId <= 0L) {
            return null;
        }
        Cursor cursor = helper.getReadableDatabase().query(ChatDatabaseHelper.TABLE_CHATS, null,
                "chat_id=?", new String[]{String.valueOf(chatId)}, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                return readChat(cursor);
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    ArrayList<ChatRecord> getChats(String query) {
        ArrayList<ChatRecord> chats = new ArrayList<ChatRecord>();
        String selection = null;
        String[] args = null;
        if (query != null && query.trim().length() != 0) {
            selection = "title LIKE ? COLLATE NOCASE";
            args = new String[]{"%" + query.trim() + "%"};
        }
        Cursor cursor = helper.getReadableDatabase().query(ChatDatabaseHelper.TABLE_CHATS, null,
                selection, args, null, null, "updated_at DESC");
        try {
            while (cursor.moveToNext()) {
                chats.add(readChat(cursor));
            }
        } finally {
            cursor.close();
        }
        return chats;
    }

    ArrayList<Message> getMessages(long chatId) {
        ArrayList<Message> messages = new ArrayList<Message>();
        if (chatId <= 0L) {
            return messages;
        }
        Cursor cursor = helper.getReadableDatabase().query(ChatDatabaseHelper.TABLE_MESSAGES, null,
                "chat_id=?", new String[]{String.valueOf(chatId)}, null, null, "timestamp ASC, message_id ASC");
        try {
            while (cursor.moveToNext()) {
                messages.add(readMessage(cursor));
            }
        } finally {
            cursor.close();
        }
        return messages;
    }

    Message addUserMessage(long chatId, String content, List<String> imageDataUrls, List<String> attachmentNames,
                           String modelName, boolean reasoningDefault) {
        ArrayList<ChatAttachment> attachments = persistComposeAttachments(imageDataUrls, attachmentNames);
        Message message = insertMessage(chatId, Role.USER, content, attachments, System.currentTimeMillis(), null, false);
        if (imageDataUrls != null) {
            message.setInputImages(new ArrayList<String>(imageDataUrls));
        }
        ensureChatTitle(chatId, content, attachments);
        updateChatSummary(chatId, content, attachments, modelName, reasoningDefault);
        return message;
    }

    Message addAssistantPlaceholder(long chatId, String modelName, boolean reasoningUsed) {
        Message message = insertMessage(chatId, Role.ASSISTANT, "", null, System.currentTimeMillis(), modelName, reasoningUsed);
        updateChatSummary(chatId, "", null, modelName, reasoningUsed);
        return message;
    }

    Message addAssistantMessage(long chatId, String content, String modelName, boolean reasoningUsed) {
        Message message = insertMessage(chatId, Role.ASSISTANT, content, null, System.currentTimeMillis(), modelName, reasoningUsed);
        updateChatSummary(chatId, content, null, modelName, reasoningUsed);
        return message;
    }

    void updateAssistantMessage(Message message) {
        if (message == null || message.getMessageId() <= 0L) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("content", safe(message.getContent()));
        if (message.getLlm() == null || message.getLlm().length() == 0) {
            values.putNull("model_used");
        } else {
            values.put("model_used", message.getLlm());
        }
        values.put("reasoning_used", message.isReasoningUsed() ? 1 : 0);
        helper.getWritableDatabase().update(ChatDatabaseHelper.TABLE_MESSAGES, values, "message_id=?",
                new String[]{String.valueOf(message.getMessageId())});
        updateChatSummary(message.getChatId(), message.getContent(), message.getAttachments(), message.getLlm(), message.isReasoningUsed());
    }

    void deleteMessage(long messageId) {
        if (messageId <= 0L) {
            return;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        deleteAttachmentFilesForSelection(db, "message_id=?", new String[]{String.valueOf(messageId)});
        db.delete(ChatDatabaseHelper.TABLE_MESSAGES, "message_id=?", new String[]{String.valueOf(messageId)});
    }

    void deleteMessagesFrom(long chatId, long messageIdInclusive) {
        if (chatId <= 0L || messageIdInclusive <= 0L) {
            return;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        String selection = "chat_id=? AND message_id>=?";
        String[] args = new String[]{String.valueOf(chatId), String.valueOf(messageIdInclusive)};
        deleteAttachmentFilesForSelection(db, selection, args);
        db.delete(ChatDatabaseHelper.TABLE_MESSAGES, selection, args);
        refreshChatSummary(chatId);
    }

    void renameChat(long chatId, String title) {
        if (chatId <= 0L) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("updated_at", System.currentTimeMillis());
        helper.getWritableDatabase().update(ChatDatabaseHelper.TABLE_CHATS, values, "chat_id=?",
                new String[]{String.valueOf(chatId)});
    }

    void deleteChat(long chatId) {
        if (chatId <= 0L) {
            return;
        }
        SQLiteDatabase db = helper.getWritableDatabase();
        deleteAttachmentFilesForSelection(db, "chat_id=?", new String[]{String.valueOf(chatId)});
        db.delete(ChatDatabaseHelper.TABLE_CHATS, "chat_id=?", new String[]{String.valueOf(chatId)});
    }

    void updateChatDefaults(long chatId, String modelName, boolean reasoningEnabledDefault) {
        if (chatId <= 0L) {
            return;
        }
        ContentValues values = new ContentValues();
        if (modelName == null || modelName.length() == 0) {
            values.putNull("model_name");
        } else {
            values.put("model_name", modelName);
        }
        values.put("reasoning_enabled_default", reasoningEnabledDefault ? 1 : 0);
        helper.getWritableDatabase().update(ChatDatabaseHelper.TABLE_CHATS, values, "chat_id=?",
                new String[]{String.valueOf(chatId)});
    }

    private Message insertMessage(long chatId, Role role, String content, List<ChatAttachment> attachments,
                                  long timestamp, String modelUsed, boolean reasoningUsed) {
        ContentValues values = new ContentValues();
        values.put("chat_id", chatId);
        values.put("role", role.toString());
        values.put("content", safe(content));
        if (attachments == null || attachments.isEmpty()) {
            values.putNull("attachments_json");
        } else {
            values.put("attachments_json", serializeAttachments(attachments));
        }
        values.put("timestamp", timestamp);
        if (modelUsed == null || modelUsed.length() == 0) {
            values.putNull("model_used");
        } else {
            values.put("model_used", modelUsed);
        }
        values.put("reasoning_used", reasoningUsed ? 1 : 0);
        long messageId = helper.getWritableDatabase().insert(ChatDatabaseHelper.TABLE_MESSAGES, null, values);

        Message message = new Message(role, safe(content), modelUsed);
        message.setMessageId(messageId);
        message.setChatId(chatId);
        message.setTimestamp(timestamp);
        message.setAttachments(attachments);
        message.setReasoningUsed(reasoningUsed);
        return message;
    }

    private void ensureChatTitle(long chatId, String content, List<ChatAttachment> attachments) {
        ChatRecord chat = getChat(chatId);
        if (chat == null || (chat.getTitle() != null && chat.getTitle().trim().length() != 0)) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("title", buildChatTitle(content, attachments));
        helper.getWritableDatabase().update(ChatDatabaseHelper.TABLE_CHATS, values, "chat_id=?",
                new String[]{String.valueOf(chatId)});
    }

    private void updateChatSummary(long chatId, String content, List<ChatAttachment> attachments,
                                   String modelName, boolean reasoningEnabledDefault) {
        if (chatId <= 0L) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("updated_at", System.currentTimeMillis());
        String preview = buildPreview(content, attachments);
        if (preview.length() != 0) {
            values.put("last_message_preview", preview);
        }
        if (modelName == null || modelName.length() == 0) {
            values.putNull("model_name");
        } else {
            values.put("model_name", modelName);
        }
        values.put("reasoning_enabled_default", reasoningEnabledDefault ? 1 : 0);
        helper.getWritableDatabase().update(ChatDatabaseHelper.TABLE_CHATS, values, "chat_id=?",
                new String[]{String.valueOf(chatId)});
    }

    private void refreshChatSummary(long chatId) {
        Cursor cursor = helper.getReadableDatabase().query(ChatDatabaseHelper.TABLE_MESSAGES, null,
                "chat_id=?", new String[]{String.valueOf(chatId)}, null, null, "timestamp DESC, message_id DESC", "1");
        try {
            if (cursor.moveToFirst()) {
                Message latest = readMessage(cursor);
                updateChatSummary(chatId, latest.getContent(), latest.getAttachments(), latest.getLlm(), latest.isReasoningUsed());
                return;
            }
        } finally {
            cursor.close();
        }
        ContentValues values = new ContentValues();
        values.put("updated_at", System.currentTimeMillis());
        values.putNull("last_message_preview");
        helper.getWritableDatabase().update(ChatDatabaseHelper.TABLE_CHATS, values, "chat_id=?",
                new String[]{String.valueOf(chatId)});
    }

    private ChatRecord readChat(Cursor cursor) {
        ChatRecord chat = new ChatRecord();
        chat.setChatId(cursor.getLong(cursor.getColumnIndex("chat_id")));
        int projectColumn = cursor.getColumnIndex("project_id");
        if (!cursor.isNull(projectColumn)) {
            chat.setProjectId(Long.valueOf(cursor.getLong(projectColumn)));
        }
        chat.setTitle(cursor.getString(cursor.getColumnIndex("title")));
        chat.setCreatedAt(cursor.getLong(cursor.getColumnIndex("created_at")));
        chat.setUpdatedAt(cursor.getLong(cursor.getColumnIndex("updated_at")));
        chat.setLastMessagePreview(cursor.getString(cursor.getColumnIndex("last_message_preview")));
        chat.setModelName(cursor.getString(cursor.getColumnIndex("model_name")));
        chat.setReasoningEnabledDefault(cursor.getInt(cursor.getColumnIndex("reasoning_enabled_default")) == 1);
        return chat;
    }

    private Message readMessage(Cursor cursor) {
        Message message = new Message(
                Role.fromValue(cursor.getString(cursor.getColumnIndex("role"))),
                safe(cursor.getString(cursor.getColumnIndex("content"))),
                cursor.getString(cursor.getColumnIndex("model_used"))
        );
        message.setMessageId(cursor.getLong(cursor.getColumnIndex("message_id")));
        message.setChatId(cursor.getLong(cursor.getColumnIndex("chat_id")));
        message.setTimestamp(cursor.getLong(cursor.getColumnIndex("timestamp")));
        message.setAttachments(parseAttachments(cursor.getString(cursor.getColumnIndex("attachments_json"))));
        message.setReasoningUsed(cursor.getInt(cursor.getColumnIndex("reasoning_used")) == 1);
        return message;
    }

    private ArrayList<ChatAttachment> persistComposeAttachments(List<String> imageDataUrls, List<String> attachmentNames) {
        ArrayList<ChatAttachment> attachments = new ArrayList<ChatAttachment>();
        if (imageDataUrls == null) {
            return attachments;
        }
        for (int i = 0; i < imageDataUrls.size(); i++) {
            String dataUrl = imageDataUrls.get(i);
            if (dataUrl == null || dataUrl.length() == 0) {
                continue;
            }
            String name = attachmentNames != null && i < attachmentNames.size() ? attachmentNames.get(i) : "image_" + (i + 1) + ".jpg";
            ChatAttachment attachment = persistDataUrlAttachment(dataUrl, name);
            if (attachment != null) {
                attachments.add(attachment);
            }
        }
        return attachments;
    }

    private ChatAttachment persistDataUrlAttachment(String dataUrl, String name) {
        int separator = dataUrl.indexOf(',');
        String metadata = separator != -1 ? dataUrl.substring(0, separator) : "";
        String encoded = separator != -1 ? dataUrl.substring(separator + 1) : dataUrl;
        String mimeType = "image/jpeg";
        if (metadata.startsWith("data:")) {
            int end = metadata.indexOf(';');
            if (end > 5) {
                mimeType = metadata.substring(5, end);
            }
        }
        try {
            byte[] bytes = Base64.decode(encoded);
            File dir = new File(context.getFilesDir(), ATTACHMENTS_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = buildUniqueAttachmentFile(dir, guessExtension(mimeType, name));
            FileOutputStream outputStream = new FileOutputStream(file);
            try {
                outputStream.write(bytes);
            } finally {
                outputStream.close();
            }
            return new ChatAttachment("image", name, mimeType, file.getAbsolutePath(), null);
        } catch (Exception ignored) {
            return new ChatAttachment("image", name, mimeType, null, dataUrl);
        }
    }

    private File buildUniqueAttachmentFile(File dir, String extension) {
        long seed = System.currentTimeMillis();
        File file = new File(dir, "msg_" + seed + extension);
        int suffix = 1;
        while (file.exists()) {
            file = new File(dir, "msg_" + seed + "_" + suffix + extension);
            suffix++;
        }
        return file;
    }

    private String guessExtension(String mimeType, String name) {
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot != -1) {
                return name.substring(dot);
            }
        }
        if ("image/png".equals(mimeType)) {
            return ".png";
        }
        if ("image/webp".equals(mimeType)) {
            return ".webp";
        }
        return ".jpg";
    }

    private void deleteAttachmentFilesForSelection(SQLiteDatabase db, String selection, String[] args) {
        Cursor cursor = db.query(ChatDatabaseHelper.TABLE_MESSAGES, new String[]{"attachments_json"}, selection, args, null, null, null);
        try {
            while (cursor.moveToNext()) {
                ArrayList<ChatAttachment> attachments = parseAttachments(cursor.getString(0));
                for (int i = 0; i < attachments.size(); i++) {
                    ChatAttachment attachment = attachments.get(i);
                    if (attachment == null || attachment.getUri() == null || attachment.getUri().length() == 0) {
                        continue;
                    }
                    File file = new File(attachment.getUri());
                    if (file.exists()) {
                        file.delete();
                    }
                }
            }
        } finally {
            cursor.close();
        }
    }

    private String serializeAttachments(List<ChatAttachment> attachments) {
        JSONArray array = new JSONArray();
        for (int i = 0; i < attachments.size(); i++) {
            ChatAttachment attachment = attachments.get(i);
            if (attachment == null) continue;
            JSONObject item = new JSONObject();
            item.put("type", attachment.getType());
            item.put("name", attachment.getName());
            item.put("mime_type", attachment.getMimeType());
            item.put("uri", attachment.getUri());
            item.put("data", attachment.getData());
            array.add(item);
        }
        return array.toString();
    }

    private ArrayList<ChatAttachment> parseAttachments(String raw) {
        ArrayList<ChatAttachment> attachments = new ArrayList<ChatAttachment>();
        if (raw == null || raw.length() == 0) {
            return attachments;
        }
        try {
            JSONArray array = JSON.getArray(raw);
            for (int i = 0; i < array.size(); i++) {
                JSONObject item = array.getObject(i);
                String type = item.getString("type");
                String name = item.getString("name");
                String mimeType = item.getString("mime_type");
                String uri = readNullable(item, "uri");
                String data = readNullable(item, "data");
                attachments.add(new ChatAttachment(type, name, mimeType, uri, data));
            }
        } catch (Exception ignored) {
        }
        return attachments;
    }

    private String readNullable(JSONObject item, String key) {
        try {
            return item.getString(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String buildChatTitle(String content, List<ChatAttachment> attachments) {
        String firstLine = safe(content);
        int newLine = firstLine.indexOf('\n');
        if (newLine != -1) {
            firstLine = firstLine.substring(0, newLine);
        }
        firstLine = firstLine.trim();
        if (firstLine.length() == 0 && attachments != null && !attachments.isEmpty()) {
            firstLine = attachments.get(0).getName();
        }
        if (firstLine.length() == 0) {
            firstLine = "New chat";
        }
        return truncate(firstLine, CHAT_TITLE_MAX);
    }

    private String buildPreview(String content, List<ChatAttachment> attachments) {
        String preview = safe(content).replace('\n', ' ').trim();
        if (preview.length() == 0 && attachments != null && !attachments.isEmpty()) {
            ChatAttachment attachment = attachments.get(0);
            preview = attachment != null && attachment.getName() != null && attachment.getName().length() != 0
                    ? attachment.getName()
                    : "Attachment";
        }
        return truncate(preview, PREVIEW_MAX);
    }

    private String truncate(String text, int max) {
        String normalized = safe(text).trim();
        if (normalized.length() <= max) {
            return normalized;
        }
        if (max <= 3) {
            return normalized.substring(0, max);
        }
        return normalized.substring(0, max - 3) + "...";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
