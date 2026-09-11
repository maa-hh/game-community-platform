package com.game.community.ai.moderation;

import com.game.community.ai.config.ModerationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AhoCorasickSensitiveWordMatcherTest {

    /** 验证 AC 自动机可以命中普通屏蔽词。 */
    @Test
    void findsSensitiveWord() throws IOException {
        AhoCorasickSensitiveWordMatcher matcher = matcher();

        assertEquals("网络赌博", matcher.find("这里提供网络赌博平台").get(0));
    }

    /** 验证规范化可以拦截用空格和全角字符拆分的规避写法。 */
    @Test
    void normalizesEvasionCharacters() throws IOException {
        AhoCorasickSensitiveWordMatcher matcher = matcher();

        assertTrue(matcher.find("游 戏 外 挂 代 理").contains("游戏外挂代理"));
        assertTrue(matcher.find("办证，刻章").contains("办证刻章"));
    }

    /** 验证正常社区内容不会误命中高置信词库。 */
    @Test
    void allowsNormalCommunityText() throws IOException {
        assertTrue(matcher().find("分享一下新版本的职业搭配和副本打法").isEmpty());
    }

    /** 使用生产词库创建测试匹配器。 */
    private AhoCorasickSensitiveWordMatcher matcher() throws IOException {
        return new AhoCorasickSensitiveWordMatcher(new ModerationProperties(), new DefaultResourceLoader());
    }
}
