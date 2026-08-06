package com.game.community.utils.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 模拟审核：按关键词打分，便于本地联调。
 * <ul>
 *   <li>含「违规|色情|赌博|违禁|头像驳回」→ 1 分拒绝</li>
 *   <li>含「人工|待审」→ 5 分人工复核</li>
 *   <li>其余 → 9 分通过</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "audit", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockAuditClient implements AuditClient {

    private static final Pattern REJECT = Pattern.compile("违规|色情|赌博|违禁|头像驳回");
    private static final Pattern HUMAN = Pattern.compile("人工|待审");

    @Override
    public AuditResult auditText(String text) {
        return score(text, "文本");
    }

    @Override
    public AuditResult auditImage(byte[] imageBytes, String mimeType) {
        // 图片字节本身无法带关键词；默认通过。联调拒绝请用文件名含「违规」再走 URL 审核。
        return AuditResult.of(9, "图片模拟审核通过");
    }

    @Override
    public AuditResult auditImageUrl(String imageUrl) {
        return score(imageUrl, "图片URL");
    }

    private AuditResult score(String content, String scene) {
        String value = content == null ? "" : content;
        AuditResult result;
        if (REJECT.matcher(value).find()) {
            result = AuditResult.of(1, scene + "模拟审核未通过");
        } else if (HUMAN.matcher(value.toLowerCase(Locale.ROOT)).find()
                || HUMAN.matcher(value).find()) {
            result = AuditResult.of(5, scene + "需人工复核（模拟）");
        } else {
            result = AuditResult.of(9, scene + "模拟审核通过");
        }
        result.setDurationMs(5L);
        log.info("Mock{}审核: score={}, reason={}, sample={}",
                scene, result.getScore(), result.getReason(), abbreviate(value));
        return result;
    }

    private String abbreviate(String value) {
        if (!StringUtils.hasText(value) || value.length() <= 64) {
            return value;
        }
        return value.substring(0, 64) + "...";
    }
}
