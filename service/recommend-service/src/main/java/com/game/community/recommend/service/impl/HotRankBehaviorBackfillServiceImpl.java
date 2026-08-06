package com.game.community.recommend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.recommend.mapper.ArticleBehaviorEventBackfillMapper;
import com.game.community.recommend.mapper.ArticleBehaviorEventMapper;
import com.game.community.recommend.service.HotRankBehaviorBackfillService;
import com.game.community.recommend.util.HotRankPeriodUtils;
import com.game.community.utils.RedisUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class HotRankBehaviorBackfillServiceImpl implements HotRankBehaviorBackfillService {

    private static final DateTimeFormatter FALLBACK_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final ArticleBehaviorEventBackfillMapper backfillMapper;
    private final ArticleBehaviorEventMapper articleBehaviorEventMapper;
    private final RedisUtils redisUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long backfillFromSocial(boolean force) {
        String token = UUID.randomUUID().toString();
        Boolean locked = redisUtils.setIfAbsent("recommend:lock:behavior-backfill", token, 1_800L);
        if (!Boolean.TRUE.equals(locked)) {
            log.warn("历史行为回填正在其他实例执行，跳过本次任务");
            return 0L;
        }
        try {
            long existing = backfillMapper.countAll();
            if (existing > 0 && !force) {
                log.info("行为事件表已有 {} 条，跳过历史回填（force=false）", existing);
                return 0L;
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

            long total = likes + favorites + comments + replies + views + reposts + shares + commentLikes + replyLikes;
            log.info("历史行为回填完成: like={}, favorite={}, comment={}, reply={}, view={}, repost={}, shareStat={}, commentLike={}, replyLike={}, total={}",
                    likes, favorites, comments, replies, views, reposts, shares, commentLikes, replyLikes, total);
            return total;
        } finally {
            redisUtils.unlock("recommend:lock:behavior-backfill", token);
        }
    }
}
