package com.game.community.content.service;

import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.entity.article.Article;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ArticleAuthorEnricher {

    private final UserFeignClient userFeignClient;

    public void enrich(List<Article> articles, List<ArticleListVO> records) {
        if (articles == null || records == null || articles.isEmpty() || records.isEmpty()) {
            return;
        }
        List<Long> userIds = articles.stream()
                .map(Article::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return;
        }
        try {
            Result<List<UserCardInternalVO>> result = userFeignClient.getUsersByUserIds(userIds);
            if (result == null || result.getCode() == null || result.getCode() != 200 || result.getData() == null) {
                return;
            }
            Map<Long, UserCardInternalVO> userMap = result.getData().stream()
                    .filter(item -> item.getUserId() != null)
                    .collect(Collectors.toMap(UserCardInternalVO::getUserId, item -> item, (a, b) -> a));
            int size = Math.min(articles.size(), records.size());
            for (int i = 0; i < size; i++) {
                UserCardInternalVO author = userMap.get(articles.get(i).getUserId());
                if (author != null) {
                    records.get(i).setAuthorAccountId(author.getAccountId());
                }
            }
        } catch (Exception e) {
            log.warn("批量填充作者 accountId 失败", e);
        }
    }
}
