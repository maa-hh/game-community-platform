package com.game.community.social.service;

import com.game.community.common.constant.social.SocialRateLimitConstants;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialRateLimiterTest {

    @Test
    void usesRedisAtomicCounterAndRejectsAfterLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(DefaultRedisScript.class), anyList(), anyString()))
                .thenReturn(1L, 2L);
        SocialRateLimiter limiter = new SocialRateLimiter(redis);
        ReflectionTestUtils.setField(limiter, "enabled", true);

        assertTrue(limiter.allow(7L, SocialRateLimitConstants.COMMENT_CREATE, 1,
                SocialRateLimitConstants.SHORT_WINDOW_SECONDS));
        assertFalse(limiter.allow(7L, SocialRateLimitConstants.COMMENT_CREATE, 1,
                SocialRateLimitConstants.SHORT_WINDOW_SECONDS));
    }

    @Test
    void disabledLimiterDoesNotTouchRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        SocialRateLimiter limiter = new SocialRateLimiter(redis);
        ReflectionTestUtils.setField(limiter, "enabled", false);

        assertTrue(limiter.allow(7L, SocialRateLimitConstants.COMMENT_CREATE, 1,
                SocialRateLimitConstants.SHORT_WINDOW_SECONDS));
    }
}
