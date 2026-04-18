package io.github.levlandon.numai_plus;

class SystemPromptBuilder {
    private SystemPromptBuilder() {}

    static String build(ConfigManager configManager) {
        if (configManager == null) {
            return "";
        }

        StringBuffer buffer = new StringBuffer();
        appendBlock(buffer, configManager.getConfig().getSystemPrompt());

        String personalization = buildPersonalizationBlock(configManager);
        appendBlock(buffer, personalization);
        appendBlock(buffer, configManager.getCustomSystemPrompt());

        return buffer.toString().trim();
    }

    private static String buildPersonalizationBlock(ConfigManager configManager) {
        StringBuffer buffer = new StringBuffer();
        appendLine(buffer, "User name", configManager.getUserName());
        appendLine(buffer, "User role", configManager.getUserRole());
        appendLine(buffer, "Usage goal", configManager.getUsageGoal());
        appendLine(buffer, "Preferred response style", configManager.getResponseStyle());
        appendLine(buffer, "Preferred detail level", configManager.getResponseDetailLevel());
        appendLine(buffer, "Preferred emotionality", configManager.getResponseEmotionality());
        appendLine(buffer, "Nickname", configManager.getNickname());
        return buffer.toString().trim();
    }

    private static void appendBlock(StringBuffer buffer, String block) {
        if (block == null || block.trim().length() == 0) {
            return;
        }
        if (buffer.length() > 0) {
            buffer.append("\n\n");
        }
        buffer.append(block.trim());
    }

    private static void appendLine(StringBuffer buffer, String label, String value) {
        if (value == null || value.trim().length() == 0) {
            return;
        }
        if (buffer.length() == 0) {
            buffer.append("Personalization:\n");
        }
        buffer.append("- ").append(label).append(": ").append(value.trim()).append('\n');
    }
}
