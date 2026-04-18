package io.github.gohoski.numai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONArray;
import cc.nnproject.json.JSONObject;

/**
 * Created by Gleb on 21.08.2025.
 * бля как же мне лень эту залупу кодить
 */
class ApiService {
    private final ApiClient apiClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConfigManager config;
    private Context ctx;

    ApiService(Context context) {
        this.apiClient = new ApiClient(context);
        this.config = ConfigManager.getInstance(context);
        ctx = context;
    }

    void chatCompletion(final List<Message> msg, final boolean thinking, final ApiCallback<ApiResult> callback) {
        chatCompletion(msg, thinking, null, callback);
    }

    void chatCompletion(final List<Message> msg, final boolean thinking, final Boolean streamOverride, final ApiCallback<ApiResult> callback) {
        System.out.println("chatCompletion...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String providerType = config.getProviderType();
                    final String providerUrl = config.getConfig().getBaseUrl();
                    boolean isLocalOpenAiCompatibleProvider = isLocalOpenAiCompatibleProvider(providerType, providerUrl);
                    final String streamingMode = config.getProviderStreamingMode(providerUrl);
                    final String streamSupport = config.getProviderStreamSupport(providerUrl);
                    final boolean preferStreaming = resolveStreamingPreference(streamingMode, streamSupport, streamOverride);
                    final boolean allowStreamingFallback = preferStreaming && !Boolean.FALSE.equals(streamOverride);
                    ApiRequest request = new ApiRequest(isLocalOpenAiCompatibleProvider ? "/chat/completions" : "/chat/completions?include_think=true", "POST");
                    JSONArray messages = new JSONArray();
                    String systemStr = SystemPromptBuilder.build(config);
                    if (systemStr.length() != 0) {
                        JSONObject system = new JSONObject();
                        system.put("role", "system");
                        system.put("content", systemStr);
                        messages.add(system);
                    }
                    boolean hasImg = false;
                    //convert all messages to json
                    for (Message message : msg) {
                        JSONObject messageJson = new JSONObject();
                        messageJson.put("role", message.getRole());
                        List<String> inputImages = message.getInputImages();
                        if (inputImages == null || inputImages.isEmpty()) {
                            messageJson.put("content", message.getContent());
                        } else {
                            hasImg = true;
                            JSONArray content = new JSONArray();
                            JSONObject inputText = new JSONObject();
                            inputText.put("type", "text");
                            inputText.put("text", message.getContent());
                            content.add(inputText);
                            for (String image: inputImages) {
                                JSONObject input = new JSONObject();
                                input.put("type", "image_url");
                                JSONObject imageUrl = new JSONObject();
                                imageUrl.put("url", image);
                                input.put("image_url", imageUrl);
                                content.add(input);
                            }
                            messageJson.put("content", content);
                        }
                        messages.add(messageJson);
                    }
                    JSONObject body = new JSONObject();
                    final String model = thinking ? config.getConfig().getThinkingModel() : config.getConfig().getChatModel();
                    body.put("model", model);
                    body.put("messages", messages);
                    body.put("stream", preferStreaming);
                    if (thinking && !isLocalOpenAiCompatibleProvider) {
                        //apparently the OpenAI Chat Completions API format still doesn't have proper reasoning support.
                        // I would've used Responses API if possible, but very few platforms support it.
                        // Flags are different on each API so this needs to be constantly changed in the future...
                        switch (config.getConfig().getBaseUrl()) {
                            case "https://openrouter.ai/api/v1":
                                JSONObject reasoning = new JSONObject();
                                reasoning.put("enabled", true);
                                body.put("reasoning", reasoning);
                                break;
                            case "https://api.together.xyz/v1":
                                JSONObject kw = new JSONObject();
                                kw.put("thinking", true);
                                body.put("chat_template_kwargs", kw);
                                break;
                            case "https://dashscope.aliyuncs.com/compatible-mode/v1":
                            case "https://dashscope-intl.aliyuncs.com/compatible-mode/v1":
                                body.put("enable_thinking", true); break;
                            default:
                                body.put("reasoning_effort", "high");
                        }
                    }
                    request.setBody(body.toString());

                    ApiResponse response = apiClient.execute(request);
                    if (response.isSuccessful()) {
                        if (preferStreaming) {
                            ApiResult streamingResult = prepareStreamingResult(model, response.getBody(), thinking, providerUrl);
                            if (streamingResult != null) {
                                deliverSuccess(callback, streamingResult);
                                return;
                            }
                            if (allowStreamingFallback) {
                                config.setProviderStreamSupport(providerUrl, ConfigManager.STREAM_SUPPORT_FALSE);
                                chatCompletion(msg, thinking, Boolean.FALSE, callback);
                                return;
                            }
                            deliverError(callback, new ApiError(ctx.getString(R.string.streaming_unavailable_message)));
                            return;
                        }
                        String responseBody = apiClient.readInputStreamToString(response.getBody());
                        deliverSuccess(callback, new ApiResult(model, buildSyntheticStream(responseBody, thinking), false));
                    } else {
                        String errorBody = "no body";
                        try {
                            errorBody = apiClient.readInputStreamToString(response.getBody());
                        } catch(IOException ignored) {}
                        if (preferStreaming && allowStreamingFallback && shouldFallbackStreamingError(response.getStatusCode(), errorBody)) {
                            config.setProviderStreamSupport(providerUrl, ConfigManager.STREAM_SUPPORT_FALSE);
                            chatCompletion(msg, thinking, Boolean.FALSE, callback);
                            return;
                        }
                        deliverError(callback, new ApiError(ctx.getString(hasImg ? R.string.fail_send_vision : R.string.fail_send, response.getStatusCode() + " " + errorBody)));
                    }
                } catch (ApiError e) {
                    deliverError(callback, e);
                } catch (Exception e) {
                    deliverError(callback, new ApiError(ctx.getString(R.string.request_failed) + ": " + e.getMessage()));
                }
            }
        }).start();
    }

    private boolean resolveStreamingPreference(String mode, String streamSupport, Boolean streamOverride) {
        if (streamOverride != null) {
            return streamOverride.booleanValue();
        }
        if (ConfigManager.STREAMING_MODE_OFF.equals(mode)) {
            return false;
        }
        if (ConfigManager.STREAMING_MODE_ON.equals(mode)) {
            return true;
        }
        return !ConfigManager.STREAM_SUPPORT_FALSE.equals(streamSupport);
    }

    private boolean isLocalOpenAiCompatibleProvider(String providerType, String providerUrl) {
        if ("LM Studio".equals(providerType)
                || "Ollama".equals(providerType)
                || "LocalAI".equals(providerType)
                || "llama.cpp server".equals(providerType)) {
            return true;
        }
        String normalizedUrl = providerUrl != null ? providerUrl.trim().toLowerCase(Locale.US) : "";
        if (normalizedUrl.length() == 0) {
            return false;
        }
        try {
            URI uri = URI.create(normalizedUrl);
            String host = uri.getHost();
            if (host == null) {
                return false;
            }
            return "localhost".equals(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host)
                    || host.startsWith("10.")
                    || host.startsWith("192.168.")
                    || host.startsWith("172.16.")
                    || host.startsWith("172.17.")
                    || host.startsWith("172.18.")
                    || host.startsWith("172.19.")
                    || host.startsWith("172.2")
                    || host.startsWith("172.30.")
                    || host.startsWith("172.31.");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean shouldFallbackStreamingError(int statusCode, String errorBody) {
        String normalized = errorBody != null ? errorBody.toLowerCase() : "";
        if (statusCode == 404 || statusCode == 405 || statusCode == 415 || statusCode == 422) {
            return true;
        }
        return normalized.indexOf("stream") != -1
                || normalized.indexOf("sse") != -1
                || normalized.indexOf("event stream") != -1
                || normalized.indexOf("unsupported") != -1
                || normalized.indexOf("not support") != -1
                || normalized.indexOf("malformed") != -1
                || normalized.indexOf("unexpected endpoint") != -1;
    }

    private ApiResult prepareStreamingResult(String model, InputStream body, boolean thinking, String providerUrl) throws IOException {
        if (body == null) {
            return null;
        }
        PushbackInputStream stream = new PushbackInputStream(body, 4096);
        byte[] probe = new byte[512];
        int read = stream.read(probe);
        if (read <= 0) {
            return new ApiResult(model, stream, true);
        }
        stream.unread(probe, 0, read);
        String prefix = new String(probe, 0, read, "UTF-8").trim();
        if (prefix.startsWith("data:")) {
            return new ApiResult(model, stream, true);
        }
        String responseBody = apiClient.readInputStreamToString(stream);
        if (responseBody == null || responseBody.trim().length() == 0) {
            return null;
        }
        config.setProviderStreamSupport(providerUrl, ConfigManager.STREAM_SUPPORT_FALSE);
        return new ApiResult(model, buildSyntheticStream(responseBody, thinking), false);
    }

    private InputStream buildSyntheticStream(String responseBody, boolean thinking) {
        String content = extractMessageContent(responseBody);
        String reasoning = thinking ? extractReasoningContent(responseBody) : "";
        JSONObject delta = new JSONObject();
        if (reasoning != null && reasoning.length() != 0) {
            delta.put("reasoning", reasoning);
        }
        if (content != null && content.length() != 0) {
            delta.put("content", content);
        }
        JSONObject choice = new JSONObject();
        choice.put("delta", delta);
        JSONArray choices = new JSONArray();
        choices.add(choice);
        JSONObject payload = new JSONObject();
        payload.put("choices", choices);
        String synthetic = "data: " + payload.toString() + "\n\n" + "data: [DONE]\n\n";
        try {
            return new ByteArrayInputStream(synthetic.getBytes("UTF-8"));
        } catch (Exception ignored) {
            return new ByteArrayInputStream(synthetic.getBytes());
        }
    }

    private String extractMessageContent(String responseBody) {
        if (responseBody == null) {
            return "";
        }
        try {
            JSONObject response = JSON.getObject(responseBody);
            JSONArray choices = response.getArray("choices");
            if (choices == null || choices.size() == 0) {
                return extractDirectResponseContent(response);
            }
            JSONObject choice = choices.getObject(0);
            JSONObject message = null;
            try {
                message = choice.getObject("message");
            } catch (Exception ignored) {
            }
            if (message == null) {
                try {
                    message = choice.getObject("delta");
                } catch (Exception ignored) {
                }
            }
            String content = extractContentValue(message, "content");
            if (content == null || content.length() == 0) {
                content = extractContentValue(choice, "text");
            }
            if (content == null || content.length() == 0) {
                content = extractContentValue(choice, "response");
            }
            if (content != null) {
                return content;
            }
            return extractDirectResponseContent(response);
        } catch (Exception ignored) {
            return looksLikeJsonStructure(responseBody) ? "" : responseBody;
        }
    }

    private String extractDirectResponseContent(JSONObject response) {
        if (response == null) {
            return "";
        }
        String content = extractContentValue(response, "content");
        if (content == null || content.length() == 0) {
            content = extractContentValue(response, "text");
        }
        if (content == null || content.length() == 0) {
            content = extractContentValue(response, "message");
        }
        if (content == null || content.length() == 0) {
            content = extractContentValue(response, "response");
        }
        if (content == null || content.length() == 0) {
            content = extractContentValue(response, "output_text");
        }
        return content != null ? content : "";
    }

    private String extractReasoningContent(String responseBody) {
        try {
            JSONObject response = JSON.getObject(responseBody);
            JSONArray choices = response.getArray("choices");
            if (choices == null || choices.size() == 0) {
                return "";
            }
            JSONObject choice = choices.getObject(0);
            JSONObject message = null;
            try {
                message = choice.getObject("message");
            } catch (Exception ignored) {
            }
            String reasoning = extractContentValue(message, "reasoning");
            if (reasoning == null || reasoning.length() == 0) {
                reasoning = extractContentValue(message, "reasoning_content");
            }
            if ((reasoning == null || reasoning.length() == 0) && message != null) {
                reasoning = extractContentValue(message, "thinking");
            }
            if (reasoning == null || reasoning.length() == 0) {
                reasoning = extractContentValue(choice, "reasoning");
            }
            if (reasoning == null || reasoning.length() == 0) {
                reasoning = extractContentValue(choice, "reasoning_content");
            }
            return reasoning != null ? reasoning : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractContentValue(JSONObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return null;
        }
        try {
            String value = object.getString(key);
            if (value != null) {
                return value;
            }
        } catch (Exception ignored) {
        }
        try {
            JSONArray array = object.getArray(key);
            StringBuffer buffer = new StringBuffer();
            for (int i = 0; i < array.size(); i++) {
                try {
                    JSONObject part = array.getObject(i);
                    String text = null;
                    try {
                        text = part.getString("text");
                    } catch (Exception ignored) {
                    }
                    if (text == null || text.length() == 0) {
                        try {
                            text = part.getString("content");
                        } catch (Exception ignored) {
                        }
                    }
                    if (text != null && text.length() != 0) {
                        buffer.append(text);
                    }
                } catch (Exception ignored) {
                    try {
                        String value = array.getString(i);
                        if (value != null) {
                            buffer.append(value);
                        }
                    } catch (Exception ignoredAgain) {
                    }
                }
            }
            return buffer.length() != 0 ? buffer.toString() : null;
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean looksLikeJsonStructure(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    void getModels(final ApiCallback<ArrayList<String>> callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ApiRequest request = new ApiRequest("/models", "GET");
                    String response = apiClient.executeAsString(request);
                    ArrayList<String> models = parseModelsResponse(response);
                    if (models.isEmpty()) {
                        deliverError(callback, new ApiError(ctx.getString(R.string.no_models_available)));
                    } else {
                        deliverSuccess(callback, models);
                    }
                } catch (ApiError e) {
                    deliverError(callback, e);
                } catch (Exception e) {
                    deliverError(callback, new ApiError(ctx.getString(R.string.unexpected_models_format)));
                }
            }
        }).start();
    }

    private ArrayList<String> parseModelsResponse(String response) throws ApiError {
        ArrayList<String> models = new ArrayList<String>();
        if (response == null || response.trim().length() == 0) {
            return models;
        }
        try {
            JSONObject resp = JSON.getObject(response);
            if (resp.has("error")) {
                try {
                    deliverModelError(resp.getObject("error"));
                } catch (Exception ignored) {
                    throw new ApiError(ctx.getString(R.string.request_failed));
                }
            }
            JSONArray json = resp.has("data") ? resp.getArray("data") : null;
            if (json != null) {
                appendModelsFromArray(models, json);
                return models;
            }
        } catch (ApiError e) {
            throw e;
        } catch (Exception ignored) {
        }
        try {
            JSONArray json = JSON.getArray(response);
            appendModelsFromArray(models, json);
            return models;
        } catch (Exception ignored) {
        }
        return models;
    }

    private void appendModelsFromArray(ArrayList<String> models, JSONArray json) {
        if (json == null) {
            return;
        }
        for (int i = 0; i < json.size(); i++) {
            try {
                JSONObject model = json.getObject(i);
                String id = model.getString("id");
                if (id == null || id.length() == 0) {
                    continue;
                }
                if (model.has("endpoints")) {
                    if (model.getArray("endpoints").has("/v1/chat/completions")) {
                        models.add(id);
                    }
                } else {
                    models.add(id);
                }
            } catch (Exception ignored) {
                try {
                    String value = json.getString(i);
                    if (value != null && value.length() != 0) {
                        models.add(value);
                    }
                } catch (Exception ignoredAgain) {
                }
            }
        }
    }

    private void deliverModelError(JSONObject errorObject) throws ApiError {
        try {
            throw new ApiError(errorObject.getString("message"));
        } catch (Exception ignored) {
            throw new ApiError(ctx.getString(R.string.request_failed));
        }
    }

    private <T> void deliverSuccess(final ApiCallback<T> callback, final T result) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(result);
            }
        });
    }

    private <T> void deliverError(final ApiCallback<T> callback, final ApiError error) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                error.printStackTrace();
                callback.onError(error);
            }
        });
    }
}
