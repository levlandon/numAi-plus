package io.github.levlandon.numai_plus;

import java.util.Locale;

import cc.nnproject.json.JSON;
import cc.nnproject.json.JSONObject;

final class TtsTextSanitizer {
    private TtsTextSanitizer() {}

    static String sanitizeFinalAssistantText(String source) {
        return sanitizeForPlayback(source, null);
    }

    static String sanitizeForPlayback(String preferredSource, String fallbackSource) {
        String sanitized = sanitizeSingleSource(preferredSource);
        if (sanitized.length() != 0) {
            return sanitized;
        }
        return sanitizeSingleSource(fallbackSource);
    }

    static boolean hasSpeakableText(String preferredSource, String fallbackSource) {
        return sanitizeForPlayback(preferredSource, fallbackSource).length() != 0;
    }

    private static String sanitizeSingleSource(String source) {
        if (source == null) {
            return "";
        }
        String extracted = extractPlainAssistantText(source);
        String normalized = extracted
                .replaceAll("(?is)<think>.*?</think>", " ")
                .replace("<think>", " ")
                .replace("</think>", " ")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        if (normalized.length() == 0) {
            return "";
        }
        normalized = normalizeMarkdown(normalized);
        String[] lines = normalized.split("\n", -1);
        int start = 0;
        while (start < lines.length && shouldSkipLeadingLine(lines[start], start < lines.length - 1)) {
            start++;
        }
        StringBuffer out = new StringBuffer(normalized.length());
        for (int i = start; i < lines.length; i++) {
            String line = lines[i] != null ? lines[i].trim() : "";
            if (line.length() == 0) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                continue;
            }
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
            out.append(line);
        }
        String result = stripAssistantPrefix(out.toString().trim());
        result = stripEmoji(result);
        return result.replaceAll("[ \\t\\x0B\\f]+", " ").trim();
    }

    private static String extractPlainAssistantText(String source) {
        String trimmed = source != null ? source.trim() : "";
        if (trimmed.length() == 0) {
            return "";
        }
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            try {
                String text = extractJsonSpeakableText(JSON.getObject(trimmed));
                if (text != null && text.trim().length() != 0) {
                    return text;
                }
            } catch (Exception ignored) {
                try {
                    String text = extractJsonArraySpeakableText(JSON.getArray(trimmed));
                    if (text != null && text.trim().length() != 0) {
                        return text;
                    }
                } catch (Exception ignoredAgain) {
                }
            }
        }
        return source;
    }

    private static String extractJsonSpeakableText(JSONObject object) {
        if (object == null) {
            return null;
        }
        String[] directKeys = new String[] {"output_text", "content", "text", "response"};
        for (int i = 0; i < directKeys.length; i++) {
            String value = tryGetJsonString(object, directKeys[i]);
            if (value != null && value.trim().length() != 0 && !looksLikeJsonStructure(value)) {
                return value;
            }
        }
        try {
            String fromChoices = extractJsonArraySpeakableText(object.getArray("choices"));
            if (fromChoices != null && fromChoices.trim().length() != 0) {
                return fromChoices;
            }
        } catch (Exception ignored) {
        }
        try {
            String fromMessage = extractJsonSpeakableText(object.getObject("message"));
            if (fromMessage != null && fromMessage.trim().length() != 0) {
                return fromMessage;
            }
        } catch (Exception ignored) {
        }
        try {
            String fromResponse = extractJsonSpeakableText(object.getObject("response"));
            if (fromResponse != null && fromResponse.trim().length() != 0) {
                return fromResponse;
            }
        } catch (Exception ignored) {
        }
        try {
            String fromContent = extractJsonArraySpeakableText(object.getArray("content"));
            if (fromContent != null && fromContent.trim().length() != 0) {
                return fromContent;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static String extractJsonArraySpeakableText(cc.nnproject.json.JSONArray array) {
        if (array == null || array.size() == 0) {
            return null;
        }
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < array.size(); i++) {
            try {
                String text = extractJsonSpeakableText(array.getObject(i));
                if (text != null && text.trim().length() != 0) {
                    if (buffer.length() > 0) {
                        buffer.append('\n');
                    }
                    buffer.append(text);
                }
                continue;
            } catch (Exception ignored) {
            }
            try {
                String text = array.getString(i);
                if (text != null && text.trim().length() != 0 && !looksLikeJsonStructure(text)) {
                    if (buffer.length() > 0) {
                        buffer.append('\n');
                    }
                    buffer.append(text);
                }
            } catch (Exception ignored) {
            }
        }
        return buffer.length() == 0 ? null : buffer.toString();
    }

    private static String tryGetJsonString(JSONObject object, String key) {
        if (object == null || key == null || !object.has(key)) {
            return null;
        }
        try {
            return object.getString(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean looksLikeJsonStructure(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim();
        return (trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }

    private static String normalizeMarkdown(String source) {
        String[] lines = source.split("\n", -1);
        StringBuffer out = new StringBuffer(source.length());
        boolean inCodeBlock = false;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i] != null ? lines[i].trim() : "";
            if (trimmed.startsWith("```")) {
                inCodeBlock = !inCodeBlock;
                continue;
            }
            if (inCodeBlock) {
                continue;
            }
            String normalizedLine = normalizeMarkdownLine(lines[i]);
            if (normalizedLine.length() == 0) {
                if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                    out.append('\n');
                }
                continue;
            }
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') {
                out.append('\n');
            }
            out.append(normalizedLine);
        }
        return out.toString();
    }

    private static String normalizeMarkdownLine(String line) {
        String trimmed = line != null ? line.trim() : "";
        if (trimmed.length() == 0) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.startsWith("assistant:")) {
            trimmed = trimmed.substring("assistant:".length()).trim();
        } else if (lower.startsWith("assistant ")) {
            trimmed = trimmed.substring("assistant ".length()).trim();
        }
        int headingLevel = 0;
        while (headingLevel < trimmed.length() && headingLevel < 6 && trimmed.charAt(headingLevel) == '#') {
            headingLevel++;
        }
        if (headingLevel > 0 && headingLevel < trimmed.length() && trimmed.charAt(headingLevel) == ' ') {
            trimmed = trimmed.substring(headingLevel + 1).trim();
        }
        if (trimmed.startsWith("> ")) {
            trimmed = trimmed.substring(2).trim();
        }
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            trimmed = trimmed.substring(2).trim();
        } else {
            int orderedIndex = 0;
            while (orderedIndex < trimmed.length() && Character.isDigit(trimmed.charAt(orderedIndex))) {
                orderedIndex++;
            }
            if (orderedIndex > 0 && orderedIndex + 1 < trimmed.length()
                    && trimmed.charAt(orderedIndex) == '.'
                    && trimmed.charAt(orderedIndex + 1) == ' ') {
                trimmed = trimmed.substring(orderedIndex + 2).trim();
            }
        }
        return stripInlineMarkdown(trimmed);
    }

    private static String stripInlineMarkdown(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        StringBuffer out = new StringBuffer(text.length());
        for (int i = 0; i < text.length();) {
            if (text.startsWith("~~", i) || text.startsWith("**", i) || text.startsWith("__", i)) {
                i += 2;
                continue;
            }
            char ch = text.charAt(i);
            if (ch == '*' || ch == '_' || ch == '`') {
                i++;
                continue;
            }
            if (ch == '[') {
                int labelEnd = text.indexOf("](", i + 1);
                if (labelEnd != -1) {
                    int urlEnd = text.indexOf(')', labelEnd + 2);
                    if (urlEnd != -1) {
                        out.append(text.substring(i + 1, labelEnd));
                        i = urlEnd + 1;
                        continue;
                    }
                }
            }
            if (ch == '|') {
                out.append(' ');
                i++;
                continue;
            }
            int codePoint = text.codePointAt(i);
            out.append(Character.toChars(codePoint));
            i += Character.charCount(codePoint);
        }
        return out.toString()
                .replace("------------", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replaceAll("https?://\\S+", " ")
                .replaceAll("(?i)\\b(role|assistant|system|debug|metadata|layout|contentdescription)\\s*[:=]\\s*", " ")
                .trim();
    }

    private static boolean shouldSkipLeadingLine(String line, boolean hasMoreContent) {
        if (line == null) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.length() == 0) {
            return hasMoreContent;
        }
        if (!hasMoreContent) {
            return false;
        }
        if (isLeadingMetadataLine(trimmed)) {
            return true;
        }
        return isDecorativeLeadingLine(trimmed);
    }

    private static boolean isLeadingMetadataLine(String line) {
        String lower = line.toLowerCase(Locale.US);
        return lower.startsWith("model:")
                || lower.startsWith("provider:")
                || lower.startsWith("nickname:")
                || lower.startsWith("role:")
                || lower.startsWith("assistant:")
                || lower.startsWith("assistant ")
                || lower.startsWith("reasoning:")
                || lower.startsWith("thinking:")
                || lower.startsWith("markdown:")
                || lower.startsWith("content:")
                || lower.startsWith("response:");
    }

    private static boolean isDecorativeLeadingLine(String line) {
        String withoutEmoji = stripEmoji(line);
        String compact = withoutEmoji.replaceAll("[\\p{Punct}\\s]+", "");
        return compact.length() == 0;
    }

    private static String stripAssistantPrefix(String text) {
        if (text == null || text.length() == 0) {
            return "";
        }
        String trimmed = text.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.startsWith("assistant:")) {
            trimmed = trimmed.substring("assistant:".length()).trim();
        } else if (lower.startsWith("assistant ")) {
            trimmed = trimmed.substring("assistant ".length()).trim();
        }
        return trimmed;
    }

    private static String stripEmoji(String source) {
        if (source == null || source.length() == 0) {
            return "";
        }
        StringBuffer buffer = new StringBuffer(source.length());
        int length = source.length();
        for (int i = 0; i < length;) {
            int codePoint = source.codePointAt(i);
            i += Character.charCount(codePoint);
            if (isEmojiCodePoint(codePoint)) {
                continue;
            }
            buffer.append(Character.toChars(codePoint));
        }
        return buffer.toString();
    }

    private static boolean isEmojiCodePoint(int codePoint) {
        if (codePoint == 0x200D || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)) {
            return true;
        }
        if (codePoint >= 0x1F1E6 && codePoint <= 0x1F1FF) {
            return true;
        }
        if (codePoint >= 0x1F3FB && codePoint <= 0x1F3FF) {
            return true;
        }
        int type = Character.getType(codePoint);
        return type == Character.OTHER_SYMBOL;
    }
}
