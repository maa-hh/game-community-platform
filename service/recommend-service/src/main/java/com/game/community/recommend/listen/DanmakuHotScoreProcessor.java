package com.game.community.recommend.listen;

import com.game.community.common.constant.KafkaTopicConstants;
import com.game.community.common.constant.RecommendConstants;
import com.game.community.feign.ContentFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.message.ArticleBehaviorMessage;
import com.game.community.model.message.DanmakuEvent;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.recommend.service.HotRankService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

/** 将可靠弹幕事实转换为统一文章行为事件，推荐服务只维护统一热度口径。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DanmakuHotScoreProcessor {

    private final ContentFeignClient contentFeignClient;
    private final HotRankService hotRankService;

    /** 消费弹幕可靠事实；独立消费组保证不影响 danmaku-service 的持久化消费。 */
    @KafkaListener(
            topics = KafkaTopicConstants.DANMAKU_TOPIC,
            groupId = "${recommend.kafka.danmaku-consumer-group:" + RecommendConstants.DANMAKU_CONSUMER_GROUP + "}",
            containerFactory = "danmakuKafkaListenerContainerFactory")
    public void handle(DanmakuEvent event) {
        if (event == null || event.getEventId() == null || event.getEventId().isBlank()
                || event.getVideoPublicId() == null || event.getVideoPublicId().isBlank()) {
            return;
        }
        if (!Integer.valueOf(1).equals(event.getStatus())) {
            return;
        }
        Long articleId = event.getArticleId();
        if (articleId == null || articleId <= 0) {
            articleId = resolveArticleId(event.getVideoPublicId());
        }
        if (articleId == null) {
            throw new IllegalStateException("无法将弹幕映射到文章，等待 Kafka 重试: videoPublicId="
                    + event.getVideoPublicId());
        }

        ArticleBehaviorMessage behavior = new ArticleBehaviorMessage();
        behavior.setEventId("danmaku:" + event.getEventId());
        behavior.setArticleId(articleId);
        behavior.setDanmakuDelta(1L);
        behavior.setEventTimeMs(event.getEventTime() == null
                ? System.currentTimeMillis()
                : event.getEventTime().atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli());
        hotRankService.applyBehaviorDelta(behavior);
    }

    /** 按视频帖子 publicId 解析内部文章 ID，仅作为历史/旧消息兼容边界。 */
    private Long resolveArticleId(String videoPublicId) {
        Result<ArticleListVO> result = contentFeignClient.getArticleByPublicId(videoPublicId);
        ArticleListVO article = result == null ? null : result.getData();
        return article == null ? null : article.getId();
    }
}
