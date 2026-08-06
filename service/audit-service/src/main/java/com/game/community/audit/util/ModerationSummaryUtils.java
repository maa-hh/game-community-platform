package com.game.community.audit.util;

import org.springframework.util.StringUtils;

public final class ModerationSummaryUtils {

    private static final int MAX_LINES = 3;
    private static final int MAX_LEN = 512;

    private ModerationSummaryUtils() {
    }

    public static String firstLines(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").trim();
        String[] lines = normalized.split("\n");
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(trimmed);
            count++;
            if (count >= MAX_LINES) {
                break;
            }
        }
        if (builder.isEmpty()) {
            return clip(normalized);
        }
        return clip(builder.toString());
    }

    private static String clip(String value) {
        if (value.length() <= MAX_LEN) {
            return value;
        }
        return value.substring(0, MAX_LEN);
    }
}
