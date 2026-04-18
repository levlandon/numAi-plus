package io.github.levlandon.numai_plus;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

class ChatDatabaseHelper extends SQLiteOpenHelper {
    static final String DATABASE_NAME = "numai_storage.db";
    static final int DATABASE_VERSION = 1;

    static final String TABLE_PROJECTS = "projects";
    static final String TABLE_CHATS = "chats";
    static final String TABLE_MESSAGES = "messages";

    ChatDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_PROJECTS + " (" +
                "project_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "system_prompt TEXT NULL," +
                "instructions TEXT NULL" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_CHATS + " (" +
                "chat_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "project_id INTEGER NULL REFERENCES projects(project_id) ON DELETE SET NULL," +
                "title TEXT NULL," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "last_message_preview TEXT NULL," +
                "model_name TEXT NULL," +
                "reasoning_enabled_default INTEGER NOT NULL DEFAULT 0" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_MESSAGES + " (" +
                "message_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "chat_id INTEGER NOT NULL REFERENCES chats(chat_id) ON DELETE CASCADE," +
                "role TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "attachments_json TEXT NULL," +
                "timestamp INTEGER NOT NULL," +
                "model_used TEXT NULL," +
                "reasoning_used INTEGER NOT NULL DEFAULT 0" +
                ")");

        db.execSQL("CREATE INDEX idx_chats_updated_at ON " + TABLE_CHATS + "(updated_at DESC)");
        db.execSQL("CREATE INDEX idx_chats_title ON " + TABLE_CHATS + "(title COLLATE NOCASE)");
        db.execSQL("CREATE INDEX idx_messages_chat_time ON " + TABLE_MESSAGES + "(chat_id, timestamp ASC)");
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.execSQL("PRAGMA foreign_keys=ON");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_MESSAGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_CHATS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_PROJECTS);
        onCreate(db);
    }
}
