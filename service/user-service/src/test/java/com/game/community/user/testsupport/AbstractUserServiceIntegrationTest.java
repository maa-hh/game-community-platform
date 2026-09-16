package com.game.community.user.testsupport;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.user.mapper.UserAccountMapper;
import com.game.community.user.mapper.UserAuthMapper;
import com.game.community.user.mapper.UserMapper;
import com.game.community.user.service.UserFieldAuditTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Set;

/**
 * 用户服务集成测试基类：H2 + jedis-mock Redis，不使用 Redis mock。
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = JedisMockRedisInitializer.class)
public abstract class AbstractUserServiceIntegrationTest {

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @Autowired
    protected UserMapper userMapper;

    @Autowired
    protected UserAccountMapper userAccountMapper;

    @Autowired
    protected UserAuthMapper userAuthMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    protected UserFieldAuditTaskService userFieldAuditTaskService;

    @MockitoBean
    protected KafkaTemplate<String, NotificationEventMessage> kafkaTemplate;

    @BeforeEach
    void cleanData() {
        jdbcTemplate.execute("DELETE FROM t_user_operation_log");
        jdbcTemplate.execute("DELETE FROM t_user_auth");
        jdbcTemplate.execute("DELETE FROM t_user_account");
        jdbcTemplate.execute("DELETE FROM t_user");
        flushRedis();
    }

    protected void flushRedis() {
        Set<String> keys = stringRedisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }
}
