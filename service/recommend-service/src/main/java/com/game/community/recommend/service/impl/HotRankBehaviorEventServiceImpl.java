package com.game.community.recommend.service.impl;

import com.game.community.common.constant.RecommendConstants;
import com.game.community.model.dto.recommend.ArticleRankScoreAgg;
import com.game.community.model.entity.recommend.ArticleBehaviorEvent;
import com.game.community.model.enums.recommend.HotRankBoardType;
import com.game.community.model.message.ArticleBehaviorMessage;
import com.game.community.recommend.mapper.ArticleBehaviorEventMapper;
import com.game.community.recommend.service.HotRankBehaviorEventService;
import com.game.community.recommend.util.HotRankPeriodUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HotRankBehaviorEventServiceImpl implements HotRankBehaviorEventService {

    private final ArticleBehaviorEventMapper articleBehaviorEventMapper;

    @Override
    public boolean saveEvent(ArticleBehaviorMessage message, double scoreDelta) {
        if (message == null || message.getArticleId() == null) {
            return false;
        }
        if (message.getEventId() == null || message.getEventId().isBlank()) {
            throw new IllegalArgumentException("行为事件缺少 eventId");
        }
        long eventTimeMs = HotRankPeriodUtils.resolveEventTimeMs(message);
        LocalDateTime eventTime = HotRankPeriodUtils.toLocalDateTime(eventTimeMs);
        ArticleBehaviorEvent row = new ArticleBehaviorEvent();
        row.setEventId(message.getEventId());
        row.setArticleId(message.getArticleId());
        row.setLikeDelta(defaultLong(message.getLikeDelta()));
        row.setCommentDelta(defaultLong(message.getCommentDelta()));
        row.setViewDelta(defaultLong(message.getViewDelta()));
        row.setFavoriteDelta(defaultLong(message.getFavoriteDelta()));
        row.setShareDelta(defaultLong(message.getShareDelta()));
        row.setCommentLikeDelta(defaultLong(message.getCommentLikeDelta()));
        row.setReplyLikeDelta(defaultLong(message.getReplyLikeDelta()));
        row.setScoreDelta(scoreDelta);
        row.setEventTime(eventTime);
        row.setEventTimeMs(eventTimeMs);
        return articleBehaviorEventMapper.insertIgnore(row) > 0;
    }

    @Override
    public List<ArticleRankScoreAgg> aggregate(HotRankBoardType boardType, String periodKey, Long categoryId) {
        LocalDateTime[] range = resolveTimeRange(boardType, periodKey);
        if (categoryId == null) {
            return articleBehaviorEventMapper.aggregateAllScope(range[0], range[1], RecommendConstants.HOT_RANK_SIZE);
        }
        return articleBehaviorEventMapper.aggregateCategoryScope(range[0], range[1], categoryId, RecommendConstants.HOT_RANK_SIZE);
    }

    private LocalDateTime[] resolveTimeRange(HotRankBoardType boardType, String periodKey) {
        return switch (boardType) {
            case DAILY -> {
                LocalDate date = HotRankPeriodUtils.parseDailyPeriodKey(periodKey);
                yield new LocalDateTime[]{date.atStartOfDay(), date.plusDays(1).atStartOfDay()};
            }
            case WEEKLY -> {
                LocalDate weekStart = HotRankPeriodUtils.parseWeeklyPeriodKey(periodKey);
                yield new LocalDateTime[]{weekStart.atStartOfDay(), weekStart.plusWeeks(1).atStartOfDay()};
            }
            case TOTAL -> {
                // 总榜：截止选定日 24:00 前的累计热度（periodKey 为 YYYY-MM-DD）
                LocalDate cutoff = HotRankPeriodUtils.parseDailyPeriodKey(periodKey);
                yield new LocalDateTime[]{
                        LocalDateTime.of(1970, 1, 1, 0, 0),
                        cutoff.plusDays(1).atStartOfDay()
                };
            }
        };
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
