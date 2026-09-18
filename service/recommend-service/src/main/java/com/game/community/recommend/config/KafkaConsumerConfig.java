package com.game.community.recommend.config;

import com.game.community.model.message.ArticleBehaviorMessage;
import com.game.community.model.message.DanmakuEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;

/**
 * 行为事件消费失败时有限重试，最终进入 <topic>.DLT，避免坏消息阻塞整个分区。
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${recommend.kafka.danmaku-concurrency:1}")
    private int danmakuConcurrency;

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L));
    }

    /** 为文章行为事件提供明确类型的反序列化工厂，避免 JsonDeserializer 属性与 setter 重复配置。 */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ArticleBehaviorMessage>
            articleBehaviorKafkaListenerContainerFactory(
                    KafkaProperties kafkaProperties,
                    CommonErrorHandler kafkaErrorHandler) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.remove(JsonDeserializer.TRUSTED_PACKAGES);
        properties.remove(JsonDeserializer.VALUE_DEFAULT_TYPE);
        properties.remove(JsonDeserializer.USE_TYPE_INFO_HEADERS);

        JsonDeserializer<ArticleBehaviorMessage> deserializer =
                new JsonDeserializer<>(ArticleBehaviorMessage.class);
        deserializer.addTrustedPackages("com.game.community.model.message");
        deserializer.setUseTypeHeaders(false);

        ConsumerFactory<String, ArticleBehaviorMessage> consumerFactory =
                new DefaultKafkaConsumerFactory<>(properties, new StringDeserializer(), deserializer);
        ConcurrentKafkaListenerContainerFactory<String, ArticleBehaviorMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(resolveConcurrency(kafkaProperties, 3));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    /** 为 DanmakuEvent 提供独立反序列化工厂，避免全局行为事件默认类型误解析弹幕消息。 */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DanmakuEvent> danmakuKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties,
            CommonErrorHandler kafkaErrorHandler) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.remove(JsonDeserializer.TRUSTED_PACKAGES);
        properties.remove(JsonDeserializer.VALUE_DEFAULT_TYPE);
        properties.remove(JsonDeserializer.USE_TYPE_INFO_HEADERS);
        JsonDeserializer<DanmakuEvent> deserializer = new JsonDeserializer<>(DanmakuEvent.class);
        deserializer.addTrustedPackages("com.game.community.model.message");
        deserializer.setUseTypeHeaders(false);
        ConsumerFactory<String, DanmakuEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(
                properties, new StringDeserializer(), deserializer);
        ConcurrentKafkaListenerContainerFactory<String, DanmakuEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(Math.max(1, danmakuConcurrency));
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }

    /** 优先使用 application.yml 的统一并发配置，避免代码硬编码覆盖部署参数。 */
    private int resolveConcurrency(KafkaProperties kafkaProperties, int defaultValue) {
        Integer configured = kafkaProperties.getListener().getConcurrency();
        return configured == null || configured < 1 ? defaultValue : configured;
    }
}
