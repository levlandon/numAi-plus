package io.github.gohoski.numai;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Locale;

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
        KEY_CACHED_MODELS_URL = "cachedModelsUrl",
        KEY_ACTIVE_CHAT_ID = "activeChatId",
        KEY_APP_LANGUAGE = "appLanguage",
        KEY_NICKNAME = "nickname",
        KEY_AVATAR_PATH = "avatarPath",
        KEY_USER_NAME = "user_name",
        KEY_USER_ROLE = "user_role",
        KEY_USAGE_GOAL = "usage_goal",
        KEY_RESPONSE_STYLE = "response_style",
        KEY_RESPONSE_DETAIL_LEVEL = "response_detail_level",
        KEY_RESPONSE_EMOTIONALITY = "response_emotionality",
        KEY_CUSTOM_SYSTEM_PROMPT = "custom_system_prompt",
        KEY_PROVIDER_TYPE = "provider_type",
        KEY_PROVIDER_URL = "provider_url",
        KEY_PROVIDER_STATUS = "provider_status",
        KEY_PROVIDER_LAST_CHECK = "provider_last_check",
        KEY_PROVIDER_URL_PREFIX = "provider_url_scoped",
        KEY_PROVIDER_CHAT_MODEL_PREFIX = "provider_chat_model",
        KEY_PROVIDER_THINKING_MODEL_PREFIX = "provider_thinking_model",
        KEY_PROVIDER_SHOW_ALL_MODELS_PREFIX = "provider_show_all_models",
        KEY_PROVIDER_SELECTED_MODELS_PREFIX = "provider_selected_models",
        KEY_PROVIDER_CACHED_MODELS_PREFIX = "provider_cached_models",
        KEY_PROVIDER_STREAMING_MODE_PREFIX = "provider_streaming_mode",
        KEY_PROVIDER_STREAM_SUPPORTED_PREFIX = "provider_stream_supported";

    static final String PROVIDER_STATUS_UNKNOWN = "UNKNOWN";
    static final String PROVIDER_STATUS_OK = "OK";
    static final String PROVIDER_STATUS_FAILED = "FAILED";
    static final String STREAMING_MODE_AUTO = "AUTO";
    static final String STREAMING_MODE_ON = "ON";
    static final String STREAMING_MODE_OFF = "OFF";
    static final String STREAM_SUPPORT_UNKNOWN = "UNKNOWN";
    static final String STREAM_SUPPORT_TRUE = "TRUE";
    static final String STREAM_SUPPORT_FALSE = "FALSE";

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
        String storedProviderType = preferences.getString(KEY_PROVIDER_TYPE, "");
        String baseUrl = normalizeProviderUrl(storedProviderType, preferences.getString(KEY_BASE_URL, "https://api.voidai.app/v1"));
        String chatModel = preferences.getString(KEY_CHAT_MODEL, "");
        if (chatModel == null || chatModel.length() == 0) {
            chatModel = getStoredProviderChatModel(baseUrl);
        }
        String thinkingModel = preferences.getString(KEY_THINKING_MODEL, "");
        if (thinkingModel == null || thinkingModel.length() == 0) {
            thinkingModel = getStoredProviderThinkingModel(baseUrl);
        }
        return new Config(baseUrl,
            preferences.getString(KEY_API_KEY, ""),
            chatModel,
            thinkingModel,
            preferences.getBoolean(KEY_SHRINK_THINK, false),
            preferences.getString(KEY_SYSTEM_PROMPT, ""),
            preferences.getInt(KEY_UPDATE_DELAY, 250));
    }

    // Save configuration to SharedPreferences
    private void saveConfig() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_BASE_URL, config.getBaseUrl());
        editor.putString(KEY_PROVIDER_URL, config.getBaseUrl());
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
        setProviderChatModel(config.getBaseUrl(), config.getChatModel());
        setProviderThinkingModel(config.getBaseUrl(), config.getThinkingModel());
        saveConfig();
    }

    void updateBaseUrl(String baseUrl) {
        String normalized = normalizeProviderUrl(getProviderType(), baseUrl);
        config.setBaseUrl(normalized);
        preferences.edit().putString(KEY_PROVIDER_URL, normalized != null ? normalized : "").commit();
        saveConfig();
    }

    void updateApiKey(String apiKey) {
        config.setApiKey(apiKey);
        saveConfig();
    }

    void updateChatModel(String model) {
        config.setChatModel(model);
        setProviderChatModel(config.getBaseUrl(), model);
        saveConfig();
    }
    void updateThinkingModel(String model) {
        config.setThinkingModel(model);
        setProviderThinkingModel(config.getBaseUrl(), model);
        saveConfig();
    }

    void updateSystemPrompt(String systemPrompt) {
        config.setSystemPrompt(systemPrompt);
        saveConfig();
    }

    boolean getShowAllModels() {
        return getShowAllModels(config.getBaseUrl());
    }

    void setShowAllModels(boolean showAllModels) {
        setShowAllModels(config.getBaseUrl(), showAllModels);
    }

    java.util.ArrayList<String> getSelectedChatModels() {
        return getSelectedChatModels(config.getBaseUrl());
    }

    void setSelectedChatModels(java.util.List<String> models) {
        setSelectedChatModels(config.getBaseUrl(), models);
    }

    java.util.ArrayList<String> getCachedModels() {
        return getCachedModels(config.getBaseUrl());
    }

    void setCachedModels(java.util.List<String> models) {
        setCachedModels(config.getBaseUrl(), models);
    }

    String getStreamingMode() {
        return getProviderStreamingMode(config.getBaseUrl());
    }

    void setStreamingMode(String mode) {
        setProviderStreamingMode(config.getBaseUrl(), mode);
    }

    String getStreamSupport() {
        return getProviderStreamSupport(config.getBaseUrl());
    }

    void setStreamSupport(String value) {
        setProviderStreamSupport(config.getBaseUrl(), value);
    }

    boolean getShowAllModels(String providerUrl) {
        String scopedKey = scopedKey(KEY_PROVIDER_SHOW_ALL_MODELS_PREFIX, providerUrl);
        if (preferences.contains(scopedKey)) {
            return preferences.getBoolean(scopedKey, true);
        }
        return preferences.getBoolean(KEY_SHOW_ALL_MODELS, true);
    }

    void setShowAllModels(String providerUrl, boolean showAllModels) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(scopedKey(KEY_PROVIDER_SHOW_ALL_MODELS_PREFIX, providerUrl), showAllModels);
        if (sameProvider(providerUrl, config.getBaseUrl())) {
            editor.putBoolean(KEY_SHOW_ALL_MODELS, showAllModels);
        }
        editor.commit();
    }

    java.util.ArrayList<String> getSelectedChatModels(String providerUrl) {
        String scopedKey = scopedKey(KEY_PROVIDER_SELECTED_MODELS_PREFIX, providerUrl);
        if (preferences.contains(scopedKey)) {
            return parseStoredModels(preferences.getString(scopedKey, ""));
        }
        return parseStoredModels(preferences.getString(KEY_SELECTED_MODELS, ""));
    }

    void setSelectedChatModels(String providerUrl, java.util.List<String> models) {
        String joined = joinModels(models);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(scopedKey(KEY_PROVIDER_SELECTED_MODELS_PREFIX, providerUrl), joined);
        if (sameProvider(providerUrl, config.getBaseUrl())) {
            editor.putString(KEY_SELECTED_MODELS, joined);
        }
        editor.commit();
    }

    java.util.ArrayList<String> getCachedModels(String providerUrl) {
        String scopedKey = scopedKey(KEY_PROVIDER_CACHED_MODELS_PREFIX, providerUrl);
        if (preferences.contains(scopedKey)) {
            return parseStoredModels(preferences.getString(scopedKey, ""));
        }
        String cachedBaseUrl = preferences.getString(KEY_CACHED_MODELS_URL, "");
        if (sameProvider(providerUrl, cachedBaseUrl)) {
            return parseStoredModels(preferences.getString(KEY_CACHED_MODELS, ""));
        }
        return new java.util.ArrayList<String>();
    }

    void setCachedModels(String providerUrl, java.util.List<String> models) {
        String joined = joinModels(models);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(scopedKey(KEY_PROVIDER_CACHED_MODELS_PREFIX, providerUrl), joined);
        if (sameProvider(providerUrl, config.getBaseUrl())) {
            editor.putString(KEY_CACHED_MODELS_URL, providerUrl);
            editor.putString(KEY_CACHED_MODELS, joined);
        }
        editor.commit();
    }

    String getProviderStreamingMode(String providerUrl) {
        String scoped = preferences.getString(scopedKey(KEY_PROVIDER_STREAMING_MODE_PREFIX, providerUrl), null);
        if (STREAMING_MODE_ON.equals(scoped) || STREAMING_MODE_OFF.equals(scoped) || STREAMING_MODE_AUTO.equals(scoped)) {
            return scoped;
        }
        return STREAMING_MODE_AUTO;
    }

    void setProviderStreamingMode(String providerUrl, String mode) {
        String safeMode = STREAMING_MODE_AUTO;
        if (STREAMING_MODE_ON.equals(mode) || STREAMING_MODE_OFF.equals(mode)) {
            safeMode = mode;
        }
        preferences.edit().putString(scopedKey(KEY_PROVIDER_STREAMING_MODE_PREFIX, providerUrl), safeMode).commit();
    }

    String getProviderStreamSupport(String providerUrl) {
        String scoped = preferences.getString(scopedKey(KEY_PROVIDER_STREAM_SUPPORTED_PREFIX, providerUrl), null);
        if (STREAM_SUPPORT_TRUE.equals(scoped) || STREAM_SUPPORT_FALSE.equals(scoped)) {
            return scoped;
        }
        return STREAM_SUPPORT_UNKNOWN;
    }

    void setProviderStreamSupport(String providerUrl, String value) {
        String safeValue = STREAM_SUPPORT_UNKNOWN;
        if (STREAM_SUPPORT_TRUE.equals(value) || STREAM_SUPPORT_FALSE.equals(value)) {
            safeValue = value;
        }
        preferences.edit().putString(scopedKey(KEY_PROVIDER_STREAM_SUPPORTED_PREFIX, providerUrl), safeValue).commit();
    }

    String getProviderChatModel(String providerUrl) {
        String model = getStoredProviderChatModel(providerUrl);
        if ((model == null || model.length() == 0) && sameProvider(providerUrl, config.getBaseUrl())) {
            return config.getChatModel();
        }
        return model;
    }

    void setProviderChatModel(String providerUrl, String model) {
        putNullableString(scopedKey(KEY_PROVIDER_CHAT_MODEL_PREFIX, providerUrl), model);
    }

    String getProviderThinkingModel(String providerUrl) {
        String model = getStoredProviderThinkingModel(providerUrl);
        if ((model == null || model.length() == 0) && sameProvider(providerUrl, config.getBaseUrl())) {
            return config.getThinkingModel();
        }
        return model;
    }

    void setProviderThinkingModel(String providerUrl, String model) {
        putNullableString(scopedKey(KEY_PROVIDER_THINKING_MODEL_PREFIX, providerUrl), model);
    }

    long getActiveChatId() {
        return preferences.getLong(KEY_ACTIVE_CHAT_ID, -1L);
    }

    void setActiveChatId(long chatId) {
        preferences.edit().putLong(KEY_ACTIVE_CHAT_ID, chatId).commit();
    }

    String getAppLanguage() {
        return preferences.getString(KEY_APP_LANGUAGE, "");
    }

    void setAppLanguage(String language) {
        preferences.edit().putString(KEY_APP_LANGUAGE, language != null ? language : "").commit();
    }

    String getNickname() {
        return preferences.getString(KEY_NICKNAME, "");
    }

    void setNickname(String nickname) {
        preferences.edit().putString(KEY_NICKNAME, nickname != null ? nickname : "").commit();
    }

    String getAvatarPath() {
        return preferences.getString(KEY_AVATAR_PATH, "");
    }

    void setAvatarPath(String avatarPath) {
        preferences.edit().putString(KEY_AVATAR_PATH, avatarPath != null ? avatarPath : "").commit();
    }

    String getUserName() {
        return preferences.getString(KEY_USER_NAME, "");
    }

    void setUserName(String value) {
        preferences.edit().putString(KEY_USER_NAME, value != null ? value : "").commit();
    }

    String getUserRole() {
        return preferences.getString(KEY_USER_ROLE, "");
    }

    void setUserRole(String value) {
        preferences.edit().putString(KEY_USER_ROLE, value != null ? value : "").commit();
    }

    String getUsageGoal() {
        return preferences.getString(KEY_USAGE_GOAL, "");
    }

    void setUsageGoal(String value) {
        preferences.edit().putString(KEY_USAGE_GOAL, value != null ? value : "").commit();
    }

    String getResponseStyle() {
        return preferences.getString(KEY_RESPONSE_STYLE, "");
    }

    void setResponseStyle(String value) {
        preferences.edit().putString(KEY_RESPONSE_STYLE, value != null ? value : "").commit();
    }

    String getResponseDetailLevel() {
        return preferences.getString(KEY_RESPONSE_DETAIL_LEVEL, "");
    }

    void setResponseDetailLevel(String value) {
        preferences.edit().putString(KEY_RESPONSE_DETAIL_LEVEL, value != null ? value : "").commit();
    }

    String getResponseEmotionality() {
        return preferences.getString(KEY_RESPONSE_EMOTIONALITY, "");
    }

    void setResponseEmotionality(String value) {
        preferences.edit().putString(KEY_RESPONSE_EMOTIONALITY, value != null ? value : "").commit();
    }

    String getCustomSystemPrompt() {
        return preferences.getString(KEY_CUSTOM_SYSTEM_PROMPT, "");
    }

    void setCustomSystemPrompt(String value) {
        preferences.edit().putString(KEY_CUSTOM_SYSTEM_PROMPT, value != null ? value : "").commit();
    }

    String getProviderType() {
        return preferences.getString(KEY_PROVIDER_TYPE, "");
    }

    void setProviderType(String value) {
        preferences.edit().putString(KEY_PROVIDER_TYPE, value != null ? value : "").commit();
    }

    String getProviderUrl() {
        String stored = preferences.getString(KEY_PROVIDER_URL, null);
        if (stored != null) {
            return stored;
        }
        return config.getBaseUrl();
    }

    void setProviderUrl(String value) {
        String normalized = normalizeProviderUrl(getProviderType(), value);
        preferences.edit().putString(KEY_PROVIDER_URL, normalized != null ? normalized : "").commit();
    }

    String getProviderUrlForType(String providerType, String fallbackUrl) {
        String scoped = preferences.getString(scopedKey(KEY_PROVIDER_URL_PREFIX, providerType), null);
        if (scoped != null && scoped.length() != 0) {
            return normalizeProviderUrl(providerType, scoped);
        }
        if (providerType != null && providerType.equals(getProviderType())) {
            String current = getProviderUrl();
            if (current != null && current.length() != 0) {
                return normalizeProviderUrl(providerType, current);
            }
        }
        return normalizeProviderUrl(providerType, fallbackUrl);
    }

    void setProviderUrlForType(String providerType, String value) {
        String safeValue = normalizeProviderUrl(providerType, value);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(scopedKey(KEY_PROVIDER_URL_PREFIX, providerType), safeValue);
        if (providerType != null && providerType.equals(getProviderType())) {
            editor.putString(KEY_PROVIDER_URL, safeValue);
        }
        editor.commit();
    }

    String getProviderStatus() {
        return preferences.getString(KEY_PROVIDER_STATUS, PROVIDER_STATUS_UNKNOWN);
    }

    void setProviderStatus(String status) {
        preferences.edit().putString(KEY_PROVIDER_STATUS, status != null ? status : PROVIDER_STATUS_UNKNOWN).commit();
    }

    long getProviderLastCheckTimestamp() {
        return preferences.getLong(KEY_PROVIDER_LAST_CHECK, 0L);
    }

    void setProviderLastCheckTimestamp(long timestamp) {
        preferences.edit().putLong(KEY_PROVIDER_LAST_CHECK, timestamp).commit();
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

    private void putNullableString(String key, String value) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(key, value != null ? value : "");
        editor.commit();
    }

    private String getStoredProviderChatModel(String providerUrl) {
        String scopedKey = scopedKey(KEY_PROVIDER_CHAT_MODEL_PREFIX, providerUrl);
        if (preferences.contains(scopedKey)) {
            return preferences.getString(scopedKey, "");
        }
        if (sameProvider(providerUrl, preferences.getString(KEY_BASE_URL, ""))) {
            return preferences.getString(KEY_CHAT_MODEL, "");
        }
        return "";
    }

    private String getStoredProviderThinkingModel(String providerUrl) {
        String scopedKey = scopedKey(KEY_PROVIDER_THINKING_MODEL_PREFIX, providerUrl);
        if (preferences.contains(scopedKey)) {
            return preferences.getString(scopedKey, "");
        }
        if (sameProvider(providerUrl, preferences.getString(KEY_BASE_URL, ""))) {
            return preferences.getString(KEY_THINKING_MODEL, "");
        }
        return "";
    }

    private String scopedKey(String prefix, String providerUrl) {
        return prefix + "_" + normalizeProviderKey(providerUrl);
    }

    private boolean sameProvider(String left, String right) {
        return normalizeProviderKey(left).equals(normalizeProviderKey(right));
    }

    private String normalizeProviderKey(String providerUrl) {
        String normalized = providerUrl != null ? providerUrl.trim().toLowerCase(Locale.US) : "";
        if (normalized.length() == 0) {
            normalized = "default";
        }
        StringBuffer buffer = new StringBuffer(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                buffer.append(ch);
            } else {
                buffer.append('_');
            }
        }
        return buffer.toString();
    }

    private String normalizeProviderUrl(String providerType, String providerUrl) {
        String normalized = providerUrl != null ? providerUrl.trim() : "";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!"LM Studio".equals(providerType) && !"Ollama".equals(providerType)) {
            return normalized;
        }
        String lower = normalized.toLowerCase(Locale.US);
        if (lower.endsWith("/chat/completions")) {
            normalized = normalized.substring(0, normalized.length() - "/chat/completions".length());
            lower = normalized.toLowerCase(Locale.US);
        }
        if (lower.endsWith("/models")) {
            normalized = normalized.substring(0, normalized.length() - "/models".length());
            lower = normalized.toLowerCase(Locale.US);
        }
        if (!lower.endsWith("/v1")) {
            normalized = normalized + "/v1";
        }
        return normalized;
    }
}
