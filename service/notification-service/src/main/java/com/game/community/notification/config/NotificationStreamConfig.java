package com.game.community.notification.config;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.constant.notification.NotificationCategory;
import com.game.community.model.message.NotificationEventMessage;
import com.game.community.notification.stream.NotificationAggregate;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.Suppressed;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.apache.kafka.streams.kstream.Windowed;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;

/**
 * 通知事件转发与互动聚合。互动事件按收件人和目标对象在短窗口内合并，
 * 其他通知保持实时直通，最终统一写入 ready topic。
 */
@Configuration
public class NotificationStreamConfig {

    @Value("${notification.kafka.window-seconds:2}")
    private long windowSeconds;

    @Value("${notification.kafka.window-grace-ms:500}")
    private long windowGraceMs;

    @Bean
    public KStream<String, NotificationEventMessage> notificationAggregateStream(StreamsBuilder builder) {
        JsonSerde<NotificationEventMessage> messageSerde = new JsonSerde<>(NotificationEventMessage.class);

        KStream<String, NotificationEventMessage> source = builder.stream(
                KafkaTopicConstants.NOTIFICATION_EVENT_TOPIC,
                Consumed.with(Serdes.String(), messageSerde));

        source.filter((key, value) -> value == null
                        || value.getRecipientUserId() == null
                        || value.getEventType() == null)
                .to(KafkaTopicConstants.NOTIFICATION_EVENT_INVALID_TOPIC,
                        Produced.with(Serdes.String(), messageSerde));

        KStream<String, NotificationEventMessage> valid = source
                .filter((key, value) -> value != null
                        && value.getRecipientUserId() != null
                        && value.getEventType() != null)
                .selectKey((key, value) -> String.valueOf(value.getRecipientUserId()));

        JsonSerde<NotificationAggregate> aggregateSerde = new JsonSerde<>(NotificationAggregate.class);
        valid.filter((key, value) -> !NotificationCategory.isAggregatable(value.getEventType()))
                .to(KafkaTopicConstants.NOTIFICATION_EVENT_READY_TOPIC,
                        Produced.with(Serdes.String(), messageSerde));

        KTable<Windowed<String>, NotificationAggregate> aggregates = valid
                .filter((key, value) -> NotificationCategory.isAggregatable(value.getEventType()))
                .selectKey((key, value) -> com.game.community.notification.stream.NotificationAggregateKey.build(value))
                .groupByKey(Grouped.with(Serdes.String(), messageSerde))
                .windowedBy(TimeWindows.ofSizeAndGrace(
                        Duration.ofSeconds(Math.max(1, windowSeconds)),
                        Duration.ofMillis(Math.max(0, windowGraceMs))))
                .aggregate(NotificationAggregate::empty,
                        (key, value, aggregate) -> aggregate.merge(value),
                        Materialized.with(Serdes.String(), aggregateSerde));

        aggregates.suppress(Suppressed.untilWindowCloses(
                        Suppressed.BufferConfig.maxBytes(64L * 1024 * 1024).shutDownWhenFull()))
                .toStream()
                .map((windowedKey, aggregate) -> {
                    NotificationEventMessage event = aggregate.toEvent();
                    event.setEventId("aggregate:" + windowedKey.key() + ":" + windowedKey.window().start());
                    return KeyValue.pair(windowedKey.key(), event);
                })
                .to(KafkaTopicConstants.NOTIFICATION_EVENT_READY_TOPIC,
                        Produced.with(Serdes.String(), messageSerde));

        return source;
    }
}
