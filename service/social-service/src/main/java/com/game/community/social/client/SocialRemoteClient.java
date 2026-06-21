package com.game.community.social.client;

import com.game.community.common.exception.BusinessException;
import com.game.community.feign.ContentFeignClient;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.entity.article.Article;
import com.game.community.model.vo.user.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SocialRemoteClient {

    private final ContentFeignClient contentFeignClient;

    private final UserFeignClient userFeignClient;

    public Article getArticle(Long articleId) {
        List<Article> articles = listArticlesByIds(Collections.singletonList(articleId));
        if (articles == null || articles.isEmpty()) {
            return null;
        }
        return articles.get(0);
    }

    public List<Article> listArticlesByIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Article> articles = unwrap(contentFeignClient.listArticlesByIds(articleIds), "文章服务暂不可用");
        return articles == null ? Collections.emptyList() : articles;
    }

    public List<Article> listPublishedByAuthor(Long authorId, int size) {
        List<Article> articles = unwrap(contentFeignClient.listPublishedByAuthor(authorId, size), "文章服务暂不可用");
        return articles == null ? Collections.emptyList() : articles;
    }

    public List<Article> listPublishedByAuthorsBefore(List<Long> authorIds, LocalDateTime before, int size) {
        if (authorIds == null || authorIds.isEmpty()) {
            return Collections.emptyList();
        }
        String beforeText = before == null ? null : before.toString();
        List<Article> articles = unwrap(contentFeignClient.listPublishedByAuthors(authorIds, beforeText, size), "文章服务暂不可用");
        return articles == null ? Collections.emptyList() : articles;
    }

    public List<UserVO> listUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserVO> users = unwrap(userFeignClient.getUsersByIds(userIds), "用户服务暂不可用");
        return users == null ? Collections.emptyList() : users;
    }

    private <T> T unwrap(Result<T> result, String defaultMessage) {
        if (result == null) {
            return null;
        }
        if (result.getCode() == null || result.getCode() != 200) {
            throw new BusinessException(result.getMessage() == null ? defaultMessage : result.getMessage());
        }
        return result.getData();
    }
}
