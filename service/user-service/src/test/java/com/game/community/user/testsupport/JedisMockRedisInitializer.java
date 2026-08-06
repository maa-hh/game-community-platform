package com.game.community.user.testsupport;

import com.github.fppt.jedismock.RedisServer;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 测试环境内存 Redis（jedis-mock），实现真实 Redis 协议，非 mock RedisUtils。
 */
public class JedisMockRedisInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final int REDIS_PORT = 6370;

    private static final RedisServer REDIS_SERVER;

    static {
        try {
            REDIS_SERVER = RedisServer.newRedisServer(REDIS_PORT);
            REDIS_SERVER.start();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start jedis-mock Redis", e);
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of(
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=" + REDIS_PORT
        ).applyTo(context.getEnvironment());
    }
}
