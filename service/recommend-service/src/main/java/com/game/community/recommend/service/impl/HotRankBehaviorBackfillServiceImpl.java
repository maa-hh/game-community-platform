package com.game.community.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.RecommendConstants;
import com.game.community.recommend.mapper.ArticleBehaviorEventBackfillMapper;
import com.game.community.recommend.mapper.ArticleBehaviorEventMapper;
import com.game.community.recommend.lock.RecommendLockService;
import com.game.community.recommend.service.HotRankBehaviorBackfillService;
import com.game.community.recommend.util.HotRankPeriodUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotRankBehaviorBackfillServiceImpl implements HotRankBehaviorBackfillService {

    private static final DateTimeFormatter FALLBACK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final ArticleBehaviorEventBackfillMapper backfillMapper;
    private final ArticleBehaviorEventMapper articleBehaviorEventMapper;
    private final RecommendLockService recommendLockService;

    @Value("${recommend.lock.rank-ttl-seconds:600}")
    private long rankLockTtlSeconds;

    @Value("${recommend.lock.backfill-ttl-seconds:1800}")
    private long backfillLockTtlSeconds;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long backfillFromSocial(boolean force) {
        return withMaintenanceLocks(() -> {
            long existing = backfillMapper.countAll();
            if (existing > 0 && !force) {
                int danmakuOnly = backfillDanmaku(false);
                log.info("行为事件表已有 {} 条，跳过社交历史回填，补入 {} 条历史弹幕（force=false，非破坏性）",
                        existing, danmakuOnly);
                return danmakuOnly;
            }
            if (force && existing > 0) {
                log.warn("强制回填：清空 t_article_behavior_event（{} 条）", existing);
                articleBehaviorEventMapper.delete(new LambdaQueryWrapper<>());
            }

            LocalDateTime fallback = LocalDate.now(HotRankPeriodUtils.SHANGHAI).minusDays(1).atTime(12, 0);
            String fallbackTime = fallback.format(FALLBACK_TIME);
            long fallbackTimeMs = fallback.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();

            int likes = backfillMapper.backfillLikes(fallbackTime);
            int favorites = backfillMapper.backfillFavorites(fallbackTime);
            int comments = backfillMapper.backfillComments(fallbackTime);
            int replies = backfillMapper.backfillReplies(fallbackTime);
            int views = backfillMapper.backfillViews(fallbackTime);
            int reposts = backfillMapper.backfillReposts(fallbackTime);
            int shares = backfillMapper.backfillShareCounts(fallbackTime, fallbackTimeMs);
            int commentLikes = backfillMapper.backfillCommentLikes(fallbackTime);
            int replyLikes = backfillMapper.backfillReplyLikes(fallbackTime);
            int danmaku = backfillDanmaku(false);

            long total = likes + favorites + comments + replies + views + reposts + shares + commentLikes + replyLikes + danmaku;
            log.info("历史行为回填完成: like={}, favorite={}, comment={}, reply={}, view={}, repost={}, shareStat={}, commentLike={}, replyLike={}, danmaku={}, total={}",
                    likes, favorites, comments, replies, views, reposts, shares, commentLikes, replyLikes, danmaku, total);
            return total;
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long refreshDanmakuFromSocial() {
        return withMaintenanceLocks(() -> backfillDanmaku(true));
    }

    /**
     * 补入当前可见弹幕；普通启动和增量任务只 INSERT IGNORE，显式刷新任务才删除旧弹幕事件。
     */
    private int backfillDanmaku(boolean replaceExisting) {
        LocalDateTime fallback = LocalDate.now(HotRankPeriodUtils.SHANGHAI).minusDays(1).atTime(12, 0);
        if (replaceExisting) {
            backfillMapper.deleteDanmakuEvents();
        }
        return backfillMapper.backfillDanmaku(fallback.format(FALLBACK_TIME));
    }

    /** 先暂停实时投影，再执行历史表回填，防止重建与 Kafka 增量互相覆盖。 */
    private long withMaintenanceLocks(LongSupplier action) {
        AtomicLong result = new AtomicLong();
        boolean executed = recommendLockService.tryExecute(
                RecommendConstants.RANK_MAINTENANCE_LOCK_KEY,
                rankLockTtlSeconds,
                () -> {
                    boolean locked = recommendLockService.tryExecute(
                            RecommendConstants.BEHAVIOR_BACKFILL_LOCK_KEY,
                            backfillLockTtlSeconds,
                            () -> result.set(action.getAsLong()));
                    if (!locked) {
                        log.warn("历史行为回填正在其他实例执行，跳过本次任务");
                    }
                });
        if (!executed) {
            log.warn("热榜维护正在其他实例执行，跳过历史行为回填");
        }
        return result.get();
    }
}
