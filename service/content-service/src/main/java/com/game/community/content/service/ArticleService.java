package com.game.community.content.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.game.community.model.base.PageResult;
import com.game.community.model.base.Result;
import com.game.community.model.dto.article.ArticleDTO;
import com.game.community.model.entity.article.Article;
import com.game.community.model.vo.article.ArticleContentVO;
import com.game.community.model.vo.article.ArticleDetailVO;
import com.game.community.model.vo.article.ArticleListVO;
import com.game.community.model.vo.article.ArticleProgressVO;

import java.util.List;
import java.util.Map;

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
     * 获取文章详情（对外：仅已发布文章可读）
     */
    ArticleDetailVO getArticleDetail(Long id);

    /**
     * 作者编辑：读取自己的文章（任意状态）
     */
    ArticleDetailVO getArticleDetailForOwner(Long id, Long userId);

    /**
     * 内部调用（Feign）：不做可见性门禁
     */
    ArticleDetailVO getArticleDetailInternal(Long id);

    /**
     * 校验当前访问者可读该正文（仅已发布）
     */
    void assertContentReadable(Long articleId);

    /**
     * 作者提交审核（草稿 → PENDING），禁止直接置为已发布
     */
    void submitForAudit(Long id, Long userId);

    /**
     * 下架：停上传、取消审核任务、移入草稿，保留已上传文件
     */
    void unpublishByAuthor(Long id, Long userId);

    /**
     * 作者侧上传/审核进度
     */
    ArticleProgressVO getArticleProgress(Long id, Long userId);

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
     * 分页获取用户文章（个人页 Tab：published / draft / unpublished）
     */
    Page<Article> getUserArticlesPage(Long userId, Integer page, Integer size, String tab);

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

    List<Article> listPublishedByAuthorsBefore(List<Long> authorIds, java.time.LocalDateTime before, Long beforeArticleId, int limit);

    /**
     * 统计文章总数
     *
     * @return 文章总数
     */
    Long countArticle();

    // ==================== HTTP API（Controller / Feign 原样转发）====================

    Result<String> saveArticleForCurrentUser(ArticleDTO dto);

    Result<String> updateArticleForCurrentUser(String publicId, ArticleDTO dto);

    Result<Void> deleteArticleForCurrentUser(String publicId);

    Result<ArticleDetailVO> queryArticleDetail(String publicId);

    Result<ArticleDetailVO> queryArticleDetailForOwner(String publicId);

    Result<ArticleContentVO> queryArticleContent(String publicId);

    PageResult<ArticleListVO> queryArticlePage(Integer page, Integer size, Long categoryId, Integer status);

    PageResult<ArticleListVO> queryMyArticlesPage(Integer page, Integer size, String tab);

    PageResult<ArticleListVO> queryFollowArticles(Integer page, Integer size);

    Result<List<ArticleListVO>> queryLatestArticles(Long categoryId, Integer size);

    Result<List<ArticleListVO>> queryMoreArticles(Long categoryId, String lastPublicId, Integer size);

    Result<ArticleProgressVO> queryArticleProgress(String publicId);

    Result<Void> submitPublish(String publicId);

    Result<Void> submitUnpublish(String publicId);

    Result<List<ArticleListVO>> queryPublishedList();

    Result<PageResult<ArticleListVO>> queryPublishedPage(Integer page, Integer size);

    Result<List<ArticleListVO>> queryByIds(List<Long> ids);

    Result<List<ArticleListVO>> queryByPublicIds(List<String> publicIds);

    Long resolvePublicId(String publicId);

    String getPublicId(Long articleId);

    Article getByPublicId(String publicId);

    Result<Map<Long, List<Long>>> queryCategoryIdsByArticleIds(List<Long> articleIds);

    Result<List<ArticleListVO>> queryPublishedByAuthor(Long authorId, Integer size);

    Result<List<ArticleListVO>> queryPublishedByAccountId(Long accountId, Integer size);

    Result<List<ArticleListVO>> queryPublishedByAuthors(List<Long> authorIds, String before, Long beforeArticleId, Integer size);

    PageResult<ArticleListVO> queryArticlePageAdmin(Integer page, Integer size, String keyword,
                                                    Long categoryId, Integer status, Long authorId);

    Result<Void> deleteArticleAdminOp(Long articleId);

    Result<Void> updateArticleStatusAdmin(Long articleId, Integer status);

    Result<Long> countArticles();

    Result<ArticleDetailVO> queryArticleDetailInternal(Long id);

    Result<Void> updateArticleStatusForFeign(Long articleId, Integer status);

    Result<PageResult<ArticleListVO>> queryPublishedPageForFeign(Integer page, Integer size);
}
