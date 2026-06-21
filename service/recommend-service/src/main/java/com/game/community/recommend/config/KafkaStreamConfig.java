package com.game.community.recommend.config;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.model.message.ArticleBehaviorMessage;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Grouped;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;

@Configuration
public class KafkaStreamConfig {

    @Value("${recommend.kafka.window-seconds:10}")
    private long windowSeconds;

    @Bean
    public KStream<String, ArticleBehaviorMessage> articleBehaviorStream(StreamsBuilder builder) {
        JsonSerde<ArticleBehaviorMessage> messageSerde = new JsonSerde<>(ArticleBehaviorMessage.class);
        KStream<String, ArticleBehaviorMessage> aggregatedStream = builder.stream(KafkaTopicConstants.ARTICLE_BEHAVIOR_TOPIC, Consumed.with(Serdes.String(), messageSerde))
                .filter((key, value) -> value != null && value.getArticleId() != null)
                .groupBy((key, value) -> value.getArticleId().toString(), Grouped.with(Serdes.String(), messageSerde))
                .windowedBy(TimeWindows.ofSizeAndGrace(Duration.ofSeconds(windowSeconds), Duration.ofSeconds(2)))
                .aggregate(
                        () -> new ArticleBehaviorMessage(0L, 0L, 0L, 0L),
                        (articleId, next, aggregate) -> new ArticleBehaviorMessage(
                                next.getArticleId(),
                                safeLong(aggregate.getLikeCount()) + safeLong(next.getLikeCount()),
                                safeLong(aggregate.getCommentCount()) + safeLong(next.getCommentCount()),
                                safeLong(aggregate.getViewCount()) + safeLong(next.getViewCount())
                        ),
                        Materialized.with(Serdes.String(), messageSerde)
                )
                .toStream()
                .map((windowedKey, value) -> KeyValue.pair(windowedKey.key(), value))
                .peek((articleId, value) -> {
                    if (value != null) {
                        value.setArticleId(Long.valueOf(articleId));
                    }
                });
        aggregatedStream.to(KafkaTopicConstants.ARTICLE_BEHAVIOR_AGGREGATED_TOPIC, Produced.with(Serdes.String(), messageSerde));
        return aggregatedStream;
    }

    private long safeLong(Long value) {
        return value == null ? 0L : value;
    }
}
