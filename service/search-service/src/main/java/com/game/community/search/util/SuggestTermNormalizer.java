package com.game.community.search.util;

import com.game.community.common.constant.search.SearchConstants;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

public final class SuggestTermNormalizer {

    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");
    private static final Pattern INVALID_ONLY = Pattern.compile("^[\\s\\p{Punct}0-9]+$");

    private SuggestTermNormalizer() {
    }

    public static String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        text = fullWidthToHalfWidth(text);
        text = MULTI_SPACE.matcher(text).replaceAll(" ");
        if (text.length() < SearchConstants.TERM_MIN_LEN) {
            return null;
        }
        if (text.length() > SearchConstants.TERM_MAX_LEN) {
            text = text.substring(0, SearchConstants.TERM_MAX_LEN);
        }
        if (INVALID_ONLY.matcher(text).matches()) {
            return null;
        }
        return text;
    }

    private static String fullWidthToHalfWidth(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (char ch : text.toCharArray()) {
            if (ch >= 0xFF01 && ch <= 0xFF5E) {
                sb.append((char) (ch - 0xFEE0));
            } else if (ch == 0x3000) {
                sb.append(' ');
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }
}
