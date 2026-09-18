package com.game.community.social.client;

import com.game.community.common.exception.BusinessException;
import com.game.community.feign.ContentFeignClient;
import com.game.community.feign.UserFeignClient;
import com.game.community.model.base.Result;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.user.UserCardInternalVO;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
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

    /**
     * 读取单篇文章，并在本地服务内统一经过熔断、重试和并发隔离边界。
     */
    @CircuitBreaker(name = "socialRemote")
    @Retry(name = "socialRemoteRetry")
    @Bulkhead(name = "socialRemote", type = Bulkhead.Type.SEMAPHORE)
    public ArticleListVO getArticle(Long articleId) {
        if (articleId == null) {
            return null;
        }
        List<ArticleListVO> articles = listArticlesByIds(Collections.singletonList(articleId));
        if (articles == null || articles.isEmpty()) {
            return null;
        }
        return articles.get(0);
    }

    @CircuitBreaker(name = "socialRemote")
    @Retry(name = "socialRemoteRetry")
    @Bulkhead(name = "socialRemote", type = Bulkhead.Type.SEMAPHORE)
    public ArticleListVO getArticleByPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return null;
        }
        return unwrap(contentFeignClient.getArticleByPublicId(publicId), "文章服务暂不可用");
    }

    @CircuitBreaker(name = "socialRemote")
    @Retry(name = "socialRemoteRetry")
    @Bulkhead(name = "socialRemote", type = Bulkhead.Type.SEMAPHORE)
    public List<ArticleListVO> listArticlesByPublicIds(List<String> publicIds) {
        if (publicIds == null || publicIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ArticleListVO> articles = unwrap(
                contentFeignClient.listArticlesByPublicIds(publicIds), "文章服务暂不可用");
        return articles == null ? Collections.emptyList() : articles;
    }

    @CircuitBreaker(name = "socialRemote")
    @Retry(name = "socialRemoteRetry")
    @Bulkhead(name = "socialRemote", type = Bulkhead.Type.SEMAPHORE)
    public List<ArticleListVO> listArticlesByIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ArticleListVO> articles = unwrap(contentFeignClient.listArticlesByIds(articleIds), "文章服务暂不可用");
        return articles == null ? Collections.emptyList() : articles;
    }

    @CircuitBreaker(name = "socialRemote")
    @Retry(name = "socialRemoteRetry")
    @Bulkhead(name = "socialRemote", type = Bulkhead.Type.SEMAPHORE)
    public List<ArticleListVO> listPublishedByAuthor(Long authorId, int size) {
        if (authorId == null || size < 1) {
            return Collections.emptyList();
        }
        List<ArticleListVO> articles = unwrap(contentFeignClient.listPublishedByAuthor(authorId, size), "文章服务暂不可用");
        return articles == null ? Collections.emptyList() : articles;
    }

    @CircuitBreaker(name = "socialRemote")
    @Retry(name = "socialRemoteRetry")
    @Bulkhead(name = "socialRemote", type = Bulkhead.Type.SEMAPHORE)
    public List<ArticleListVO> listPublishedByAuthorsBefore(List<Long> authorIds, LocalDateTime before,
                                                             Long beforeArticleId, int size) {
        if (authorIds == null || authorIds.isEmpty()) {
            return Collections.emptyList();
        }
        String beforeText = before == null ? null : before.toString();
        List<ArticleListVO> articles = unwrap(contentFeignClient.listPublishedByAuthors(
                authorIds, beforeText, beforeArticleId, size), "文章服务暂不可用");
        return articles == null ? Collections.emptyList() : articles;
    }

    @CircuitBreaker(name = "socialRemote")
    @Retry(name = "socialRemoteRetry")
    @Bulkhead(name = "socialRemote", type = Bulkhead.Type.SEMAPHORE)
    public List<UserCardInternalVO> listUsersByIds(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<UserCardInternalVO> users = unwrap(userFeignClient.getUsersByUserIds(userIds), "用户服务暂不可用");
        return users == null ? Collections.emptyList() : users;
    }

    @CircuitBreaker(name = "socialRemote")
    @Retry(name = "socialRemoteRetry")
    @Bulkhead(name = "socialRemote", type = Bulkhead.Type.SEMAPHORE)
    public UserCardInternalVO getUserByAccountId(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return unwrap(userFeignClient.getUserByAccountId(accountId), "用户服务暂不可用");
    }

    /**
     * 解析文章作者内部 userId；这是跨服务边界，不能因为同类内部调用而绕过韧性组件。
     */
    @CircuitBreaker(name = "socialRemote")
    @Retry(name = "socialRemoteRetry")
    @Bulkhead(name = "socialRemote", type = Bulkhead.Type.SEMAPHORE)
    public Long articleAuthorUserId(ArticleListVO article) {
        if (article == null || article.getAuthorAccountId() == null) {
            return null;
        }
        UserCardInternalVO author = getUserByAccountId(article.getAuthorAccountId());
        return author == null ? null : author.getUserId();
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
