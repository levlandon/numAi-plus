package io.github.gohoski.numai;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Use this class to change preferences
 *
 * Handles loading/saving user's settings and validates configuration format.
 */
class ConfigManager {
    private static final String PREFS_NAME = "numAi",
        KEY_BASE_URL = "baseUrl",
        KEY_API_KEY = "apiKey",
        KEY_CHAT_MODEL = "chatModel",
        KEY_THINKING_MODEL = "thinkingModel",
        KEY_SHRINK_THINK = "shrinkThink",
        KEY_SYSTEM_PROMPT = "systemPrompt",
        KEY_UPDATE_DELAY = "updateDelay",
        KEY_SHOW_ALL_MODELS = "showAllModels",
        KEY_SELECTED_MODELS = "selectedModels",
        KEY_CACHED_MODELS = "cachedModels",
        KEY_CACHED_MODELS_URL = "cachedModelsUrl";

    private static ConfigManager instance;
    private final SharedPreferences preferences;
    private Config config;

    private ConfigManager(Context appContext) {
        preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        config = loadConfig();
    }

    // Get singleton instance
    static synchronized ConfigManager getInstance(Context context) {
        if (instance == null)
            instance = new ConfigManager(context.getApplicationContext());
        return instance;
    }

    static ConfigManager getInstance() {
        if (instance == null)
            throw new IllegalStateException("ConfigManager not initialized; call getInstance(Context) first");
        return instance;
    }

    // Load configuration from SharedPreferences
    private Config loadConfig() {
        return new Config(preferences.getString(KEY_BASE_URL, "https://api.voidai.app/v1"),
            preferences.getString(KEY_API_KEY, ""),
            preferences.getString(KEY_CHAT_MODEL, ""),
            preferences.getString(KEY_THINKING_MODEL, ""),
            preferences.getBoolean(KEY_SHRINK_THINK, false),
            preferences.getString(KEY_SYSTEM_PROMPT, ""),
            preferences.getInt(KEY_UPDATE_DELAY, 250));
    }

    // Save configuration to SharedPreferences
    private void saveConfig() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_BASE_URL, config.getBaseUrl());
        editor.putString(KEY_API_KEY, config.getApiKey());
        editor.putString(KEY_CHAT_MODEL, config.getChatModel());
        editor.putString(KEY_THINKING_MODEL, config.getThinkingModel());
        editor.putBoolean(KEY_SHRINK_THINK, config.getShrinkThink());
        editor.putString(KEY_SYSTEM_PROMPT, config.getSystemPrompt());
        editor.putInt(KEY_UPDATE_DELAY, config.getUpdateDelay());
        editor.commit(); //apply() doesn't exist in Android 1.0
    }

    Config getConfig() {
        return config;
    }

    void setConfig(Config config) {
        this.config = config;
        saveConfig();
    }

    void updateBaseUrl(String baseUrl) {
        config.setBaseUrl(baseUrl);
        saveConfig();
    }

    void updateApiKey(String apiKey) {
        config.setApiKey(apiKey);
        saveConfig();
    }

    void updateChatModel(String model) {
        config.setChatModel(model);
        saveConfig();
    }
    void updateThinkingModel(String model) {
        config.setThinkingModel(model);
        saveConfig();
    }

    void updateSystemPrompt(String systemPrompt) {
        config.setSystemPrompt(systemPrompt);
        saveConfig();
    }

    boolean getShowAllModels() {
        return preferences.getBoolean(KEY_SHOW_ALL_MODELS, true);
    }

    void setShowAllModels(boolean showAllModels) {
        preferences.edit().putBoolean(KEY_SHOW_ALL_MODELS, showAllModels).commit();
    }

    java.util.ArrayList<String> getSelectedChatModels() {
        return parseStoredModels(preferences.getString(KEY_SELECTED_MODELS, ""));
    }

    void setSelectedChatModels(java.util.List<String> models) {
        preferences.edit().putString(KEY_SELECTED_MODELS, joinModels(models)).commit();
    }

    java.util.ArrayList<String> getCachedModels() {
        String cachedBaseUrl = preferences.getString(KEY_CACHED_MODELS_URL, "");
        if (cachedBaseUrl == null || !cachedBaseUrl.equals(config.getBaseUrl())) {
            return new java.util.ArrayList<String>();
        }
        return parseStoredModels(preferences.getString(KEY_CACHED_MODELS, ""));
    }

    void setCachedModels(java.util.List<String> models) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_CACHED_MODELS_URL, config.getBaseUrl());
        editor.putString(KEY_CACHED_MODELS, joinModels(models));
        editor.commit();
    }

    private java.util.ArrayList<String> parseStoredModels(String raw) {
        java.util.ArrayList<String> models = new java.util.ArrayList<String>();
        if (raw == null || raw.length() == 0) {
            return models;
        }
        String[] parts = raw.split("\n");
        for (int i = 0; i < parts.length; i++) {
            if (parts[i] != null && parts[i].trim().length() != 0) {
                models.add(parts[i]);
            }
        }
        return models;
    }

    private String joinModels(java.util.List<String> models) {
        StringBuffer buffer = new StringBuffer();
        if (models != null) {
            for (int i = 0; i < models.size(); i++) {
                String model = models.get(i);
                if (model == null || model.trim().length() == 0) continue;
                if (buffer.length() > 0) {
                    buffer.append('\n');
                }
                buffer.append(model);
            }
        }
        return buffer.toString();
    }

    java.util.ArrayList<String> getVisibleChatModels(java.util.List<String> allModels) {
        java.util.ArrayList<String> visibleModels = new java.util.ArrayList<String>();
        if (allModels == null || allModels.isEmpty()) {
            return visibleModels;
        }

        if (getShowAllModels()) {
            visibleModels.addAll(allModels);
            return visibleModels;
        }

        java.util.ArrayList<String> selectedModels = getSelectedChatModels();
        if (selectedModels.isEmpty()) {
            visibleModels.addAll(allModels);
            return visibleModels;
        }

        for (int i = 0; i < allModels.size(); i++) {
            String model = allModels.get(i);
            if (selectedModels.contains(model)) {
                visibleModels.add(model);
            }
        }
        if (visibleModels.isEmpty()) {
            visibleModels.addAll(allModels);
        }
        return visibleModels;
    }

    public boolean isConfigValid() {
        return config.isValid();
    }
}
