package com.game.community.content.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.game.community.model.base.PageResult;
import com.game.community.model.dto.article.ArticleDTO;
import com.game.community.model.entity.article.Article;
import com.game.community.model.vo.article.ArticleDetailVO;

import java.util.List;
/**
 * 文章服务接口
 */
public interface ArticleService extends IService<Article> {

    /**
     * 获取更新的文章（首页刷新）
     *
     * @param categoryId 分类ID（可选）
     * @param size 每页大小
     * @return 文章列表
     */
    List<Article> getLatestArticles(Long categoryId, Integer size);

    /**
     * 获取更多的文章（加载更多）
     *
     * @param categoryId 分类ID（可选）
     * @param lastId 上一页最后一条文章的ID（必填）
     * @param size 每页大小
     * @return 文章列表
     */
    List<Article> getMoreArticles(Long categoryId, Long lastId, Integer size);

    /**
     * 创建或更新文章（带内容Map）
     * - 草稿：直接保存，返回
     * - 非草稿：先审核，审核通过后保存
     * - 保存时将content转为freemarker HTML存到MinIO
     *
     * @param articleDTO 文章DTO（包含content Map）
     * @param userId 作者ID
     * @return 文章ID
     */
    Long saveArticle(ArticleDTO articleDTO, Long userId);

    /**
     * 删除文章
     *
     * @param id 文章ID
     */
    void deleteArticle(Long id);

    /**
     * 获取文章详情（合并MySQL + MongoDB数据）
     *
     * @param id 文章ID
     * @return 文章详情VO
     */
    ArticleDetailVO getArticleDetail(Long id);

    /**
     * 分页查询文章列表
     *
     * @param page 当前页
     * @param size 每页大小
     * @param categoryId 分类ID（可选）
     * @param status 文章状态（可选）
     * @return 分页结果
     */
    Page<Article> getArticlePage(Integer page, Integer size, Long categoryId, Integer status);

    /**
     * 获取用户的文章列表
     *
     * @param userId 用户ID
     * @return 文章列表
     */
    List<Article> getUserArticles(Long userId);

    /**
     * 根据关注列表拉取帖子（拉模式）
     *
     * @param userId 当前用户ID
     * @param page 当前页
     * @param size 每页大小
     * @return 分页结果
     */
    Page<Article> getFollowArticles(Long userId, Integer page, Integer size);

    /**
     * 更新文章状态
     *
     * @param id 文章ID
     * @param status 状态
     */
    void updateArticleStatus(Long id, Integer status);

    /**
     * 获取所有已发布文章列表
     *
     * @return 文章列表
     */
    List<Article> listPublishedArticles();

    /**
     * 分页获取已发布文章列表
     *
     * @param page 页码
     * @param size 每页数量
     * @return 文章分页列表
     */
    PageResult<Article> listPublishedArticlesPage(Integer page, Integer size);

    /**
     * 分页查询文章列表（管理员接口）
     *
     * @param page        页码
     * @param size        每页数量
     * @param keyword     关键字（标题/摘要）
     * @param categoryId  分类ID
     * @param status      文章状态（0-草稿，1-已发布，2-待审核，3-已下架）
     * @param userId      作者ID
     * @return 文章分页列表
     */
    Page<Article> getArticlePageAdmin(Integer page, Integer size, String keyword, Long categoryId, Integer status, Long authorId);

    /**
     * 删除文章（管理员接口）
     *
     * @param articleId 文章ID
     */
    void deleteArticleAdmin(Long articleId);

    /**
     * 根据用户ID列表查询已发布文章
     *
     * @param userIds 用户ID列表
     * @param status  状态
     * @param limit   数量限制
     * @return 文章列表
     */
    List<Article> listByUserIdsAndStatus(List<Long> userIds, Integer status, int limit);

    List<Article> listPublishedByAuthor(Long authorId, int limit);

    List<Article> listPublishedByAuthorsBefore(List<Long> authorIds, java.time.LocalDateTime before, int limit);

    /**
     * 统计文章总数
     *
     * @return 文章总数
     */
    Long countArticle();
}
