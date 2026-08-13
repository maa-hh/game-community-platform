package com.game.community.social.common;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.game.community.common.constant.social.SocialConstants;
import com.game.community.model.entity.social.SocialBlack;
import com.game.community.social.mapper.SocialBlackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import com.game.community.utils.RedisUtils;

@Component
@RequiredArgsConstructor
public class BlackRelationChecker {

    private final SocialBlackMapper blackMapper;
    private final RedisUtils redisUtils;

    /** 查询双向黑名单关系，优先使用短时缓存降低社交写操作的重复查库。 */
    public boolean hasRelation(Long leftUserId, Long rightUserId) {
        if (leftUserId == null || rightUserId == null || leftUserId.equals(rightUserId)) {
            return false;
        }
        String key = key(leftUserId, rightUserId);
        String cached = redisUtils.get(key);
        if (cached != null) {
            return "1".equals(cached);
        }
        boolean relation = blackMapper.selectCount(new LambdaQueryWrapper<SocialBlack>()
                .and(wrapper -> wrapper
                        .eq(SocialBlack::getUserId, leftUserId)
                        .eq(SocialBlack::getBlackUserId, rightUserId))
                .or(wrapper -> wrapper
                        .eq(SocialBlack::getUserId, rightUserId)
                        .eq(SocialBlack::getBlackUserId, leftUserId))) > 0;
        redisUtils.set(key, relation ? "1" : "0", SocialConstants.BLACK_RELATION_CACHE_SECONDS, TimeUnit.SECONDS);
        return relation;
    }

    /** 用户解除黑名单后删除双向关系缓存。 */
    public void evict(Long leftUserId, Long rightUserId) {
        if (leftUserId != null && rightUserId != null && !leftUserId.equals(rightUserId)) {
            redisUtils.del(key(leftUserId, rightUserId));
        }
    }

    private String key(Long leftUserId, Long rightUserId) {
        long first = Math.min(leftUserId, rightUserId);
        long second = Math.max(leftUserId, rightUserId);
        return SocialConstants.BLACK_RELATION_CACHE_KEY_PREFIX + first + ":" + second;
    }
}
