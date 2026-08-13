package com.game.community.recommend.config;

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
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * 行为事件消费失败时有限重试，最终进入 <topic>.DLT，避免坏消息阻塞整个分区。
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 3L));
    }

    /** 为 DanmakuEvent 提供独立反序列化工厂，避免全局行为事件默认类型误解析弹幕消息。 */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DanmakuEvent> danmakuKafkaListenerContainerFactory(
            KafkaProperties kafkaProperties,
            CommonErrorHandler kafkaErrorHandler) {
        Map<String, Object> properties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        JsonDeserializer<DanmakuEvent> deserializer = new JsonDeserializer<>(DanmakuEvent.class);
        deserializer.addTrustedPackages("com.game.community.model.message");
        deserializer.setUseTypeHeaders(false);
        ConsumerFactory<String, DanmakuEvent> consumerFactory = new DefaultKafkaConsumerFactory<>(
                properties, new StringDeserializer(), deserializer);
        ConcurrentKafkaListenerContainerFactory<String, DanmakuEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        factory.setConcurrency(1);
        return factory;
    }
}
