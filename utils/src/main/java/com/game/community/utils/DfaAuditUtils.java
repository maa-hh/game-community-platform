package com.game.community.utils;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 轻量本地敏感词审核，后续可替换为词库加载。
 */
@Component
public class DfaAuditUtils {

    private static final List<String> BLOCK_WORDS = List.of("赌博", "色情", "诈骗", "外挂");

    public boolean pass(String text) {
        if (!StringUtils.hasText(text)) {
            return true;
        }
        return BLOCK_WORDS.stream().noneMatch(text::contains);
    }
}
