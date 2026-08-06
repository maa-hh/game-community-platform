package com.game.community.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.community.utils.RedisUtils;
import com.game.community.utils.session.UserSessionRedisReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 网关 Redis 工具注册（gateway 包扫描范围外）。
 */
@Configuration
public class GatewayRedisConfig {

    @Bean
    public RedisUtils redisUtils(StringRedisTemplate stringRedisTemplate) {
        return new RedisUtils(stringRedisTemplate);
    }

    @Bean
    public UserSessionRedisReader userSessionRedisReader(RedisUtils redisUtils, ObjectMapper objectMapper) {
        return new UserSessionRedisReader(redisUtils, objectMapper);
    }
}
