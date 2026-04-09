package io.github.gohoski.numai;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;
import android.text.style.UnderlineSpan;

class MarkdownFormatter {
    private MarkdownFormatter() {}

    static CharSequence format(Context context, String markdown) {
        if (markdown == null || markdown.length() == 0) {
            return "";
        }

        SpannableStringBuilder builder = new SpannableStringBuilder();
        int cursor = 0;
        while (cursor < markdown.length()) {
            int fenceStart = markdown.indexOf("```", cursor);
            if (fenceStart == -1) {
                appendTextBlock(context, builder, markdown.substring(cursor));
                break;
            }
            appendTextBlock(context, builder, markdown.substring(cursor, fenceStart));
            int fenceEnd = markdown.indexOf("```", fenceStart + 3);
            if (fenceEnd == -1) {
                appendTextBlock(context, builder, markdown.substring(fenceStart));
                break;
            }
            appendCodeBlock(context, builder, markdown.substring(fenceStart + 3, fenceEnd));
            cursor = fenceEnd + 3;
        }
        return builder;
    }

    private static void appendTextBlock(Context context, SpannableStringBuilder builder, String block) {
        if (block == null || block.length() == 0) {
            return;
        }
        String[] lines = block.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            appendTextLine(context, builder, lines[i]);
            if (i < lines.length - 1) {
                builder.append('\n');
            }
        }
    }

    private static void appendTextLine(Context context, SpannableStringBuilder builder, String line) {
        String trimmed = line.trim();
        if (trimmed.length() == 0) {
            return;
        }

        int headingLevel = getHeadingLevel(trimmed);
        if (headingLevel > 0) {
            int start = builder.length();
            appendInline(context, builder, trimmed.substring(headingLevel + 1));
            applyHeadingSpan(builder, start, builder.length(), headingLevel);
            return;
        }

        if (trimmed.startsWith("> ")) {
            int prefixStart = builder.length();
            builder.append("| ");
            int contentStart = builder.length();
            appendInline(context, builder, trimmed.substring(2));
            applyQuoteSpan(context, builder, prefixStart, builder.length(), contentStart);
            return;
        }

        if (isHorizontalRule(trimmed)) {
            appendHorizontalRule(builder);
            return;
        }

        String tableLine = renderTableLine(trimmed);
        if (tableLine != null) {
            int start = builder.length();
            builder.append(tableLine);
            builder.setSpan(new TypefaceSpan("monospace"), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new RelativeSizeSpan(0.95f), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return;
        }

        String prefix = "";
        String content = line;
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            prefix = "- ";
            content = trimmed.substring(2);
        } else {
            int orderedIndex = indexAfterOrderedPrefix(trimmed);
            if (orderedIndex > 0) {
                prefix = trimmed.substring(0, orderedIndex);
                content = trimmed.substring(orderedIndex);
            }
        }

        builder.append(prefix);
        appendInline(context, builder, content.trim());
    }

    private static int getHeadingLevel(String line) {
        int level = 0;
        while (level < line.length() && level < 6 && line.charAt(level) == '#') {
            level++;
        }
        if (level > 0 && level < line.length() && line.charAt(level) == ' ') {
            return level;
        }
        return 0;
    }

    private static int indexAfterOrderedPrefix(String line) {
        int index = 0;
        while (index < line.length() && Character.isDigit(line.charAt(index))) {
            index++;
        }
        if (index > 0 && index + 1 < line.length() && line.charAt(index) == '.' && line.charAt(index + 1) == ' ') {
            return index + 2;
        }
        return -1;
    }

    private static void appendInline(Context context, SpannableStringBuilder builder, String text) {
        int i = 0;
        while (i < text.length()) {
            if (text.startsWith("~~", i)) {
                int end = text.indexOf("~~", i + 2);
                if (end != -1) {
                    int start = builder.length();
                    appendInline(context, builder, text.substring(i + 2, end));
                    builder.setSpan(new StrikethroughSpan(), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }
            if (text.startsWith("[", i)) {
                int labelEnd = text.indexOf("](", i + 1);
                if (labelEnd != -1) {
                    int urlEnd = text.indexOf(')', labelEnd + 2);
                    if (urlEnd != -1) {
                        int start = builder.length();
                        builder.append(text.substring(i + 1, labelEnd));
                        applyLinkSpan(context, builder, start, builder.length(), text.substring(labelEnd + 2, urlEnd));
                        i = urlEnd + 1;
                        continue;
                    }
                }
            }
            if (text.startsWith("**", i)) {
                int end = text.indexOf("**", i + 2);
                if (end != -1) {
                    int start = builder.length();
                    appendInline(context, builder, text.substring(i + 2, end));
                    builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }
            if (text.startsWith("__", i)) {
                int end = text.indexOf("__", i + 2);
                if (end != -1) {
                    int start = builder.length();
                    appendInline(context, builder, text.substring(i + 2, end));
                    builder.setSpan(new StyleSpan(Typeface.BOLD), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }
            char ch = text.charAt(i);
            if (ch == '*' || ch == '_') {
                int end = text.indexOf(ch, i + 1);
                if (end != -1) {
                    int start = builder.length();
                    appendInline(context, builder, text.substring(i + 1, end));
                    builder.setSpan(new StyleSpan(Typeface.ITALIC), start, builder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }
            if (ch == '`') {
                int end = text.indexOf('`', i + 1);
                if (end != -1) {
                    int start = builder.length();
                    builder.append(text.substring(i + 1, end));
                    applyCodeSpan(context, builder, start, builder.length(), false);
                    i = end + 1;
                    continue;
                }
            }
            builder.append(ch);
            i++;
        }
    }

    private static void appendCodeBlock(Context context, SpannableStringBuilder builder, String rawBlock) {
        String block = rawBlock;
        if (block.startsWith("\n")) {
            block = block.substring(1);
        }
        int firstBreak = block.indexOf('\n');
        if (firstBreak > 0) {
            String firstLine = block.substring(0, firstBreak).trim();
            if (firstLine.length() > 0 && firstLine.indexOf(' ') == -1 && firstLine.indexOf('\t') == -1) {
                block = block.substring(firstBreak + 1);
            }
        }
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }
        int start = builder.length();
        builder.append(block.replace('\r', '\n'));
        int end = builder.length();
        applyCodeSpan(context, builder, start, end, true);
        if (builder.length() > 0 && builder.charAt(builder.length() - 1) != '\n') {
            builder.append('\n');
        }
    }

    private static void applyCodeSpan(Context context, SpannableStringBuilder builder, int start, int end, boolean block) {
        if (start >= end) {
            return;
        }
        builder.setSpan(new TypefaceSpan("monospace"), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new BackgroundColorSpan(context.getResources().getColor(R.color.surfaceAlt)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (block) {
            builder.setSpan(new RelativeSizeSpan(0.92f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void applyHeadingSpan(SpannableStringBuilder builder, int start, int end, int level) {
        if (start >= end) {
            return;
        }
        builder.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        float size;
        switch (level) {
            case 1:
                size = 1.28f;
                break;
            case 2:
                size = 1.2f;
                break;
            case 3:
                size = 1.12f;
                break;
            default:
                size = 1.04f;
                break;
        }
        builder.setSpan(new RelativeSizeSpan(size), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void applyQuoteSpan(Context context, SpannableStringBuilder builder, int start, int end, int contentStart) {
        if (start >= end) {
            return;
        }
        builder.setSpan(new BackgroundColorSpan(context.getResources().getColor(R.color.surfaceAlt)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new RelativeSizeSpan(0.98f), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (contentStart < end) {
            builder.setSpan(new StyleSpan(Typeface.ITALIC), contentStart, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void applyLinkSpan(Context context, SpannableStringBuilder builder, int start, int end, String url) {
        if (start >= end) {
            return;
        }
        builder.setSpan(new URLSpan(url), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new UnderlineSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.colorPrimary)), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static boolean isHorizontalRule(String trimmed) {
        if (trimmed.length() < 3) {
            return false;
        }
        char ch = trimmed.charAt(0);
        if (ch != '-' && ch != '*' && ch != '_') {
            return false;
        }
        for (int i = 1; i < trimmed.length(); i++) {
            if (trimmed.charAt(i) != ch) {
                return false;
            }
        }
        return true;
    }

    private static void appendHorizontalRule(SpannableStringBuilder builder) {
        builder.append("------------");
    }

    private static String renderTableLine(String trimmed) {
        if (trimmed.indexOf('|') == -1) {
            return null;
        }
        String normalized = trimmed;
        if (normalized.startsWith("|")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("|")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String[] cells = normalized.split("\\|", -1);
        if (cells.length < 2) {
            return null;
        }

        boolean separatorLine = true;
        for (int i = 0; i < cells.length; i++) {
            String cell = cells[i].trim();
            if (cell.length() == 0) {
                continue;
            }
            for (int j = 0; j < cell.length(); j++) {
                char ch = cell.charAt(j);
                if (ch != '-' && ch != ':') {
                    separatorLine = false;
                    break;
                }
            }
            if (!separatorLine) {
                break;
            }
        }
        if (separatorLine) {
            return null;
        }

        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                buffer.append(" | ");
            }
            buffer.append(cells[i].trim());
        }
        return buffer.toString();
    }
}
