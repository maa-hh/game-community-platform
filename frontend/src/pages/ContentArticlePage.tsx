import { useEffect, useRef, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import { ArticleDetail, ContentCategory, contentApi } from "../api/content";
import { ArticleStats, CommentVO, ReplyVO, ReportTargetType, socialApi } from "../api/social";
import { UserVO, userApi } from "../api/user";
import { ActionButton } from "../components/ActionButton";
import { AvatarImage } from "../components/AvatarImage";
import { Notice } from "../components/Notice";
import { useAuth } from "../features/auth/AuthContext";
import { friendlyError } from "../utils/errors";

function statusLabel(status?: number) {
  switch (status) {
    case 0:
      return "草稿";
    case 1:
      return "已发布";
    case 2:
      return "审核中";
    case 3:
      return "已下架";
    case 4:
      return "已驳回";
    default:
      return "未知状态";
  }
}

function formatTime(value?: string) {
  if (!value) {
    return "未记录";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("zh-CN", {
    year: "numeric",
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

export function ContentArticlePage() {
  const { articleId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user: currentUser } = useAuth();
  const [article, setArticle] = useState<ArticleDetail | null>(null);
  const [author, setAuthor] = useState<UserVO | null>(null);
  const [stats, setStats] = useState<ArticleStats | null>(null);
  const [comments, setComments] = useState<CommentVO[]>([]);
  const [categories, setCategories] = useState<ContentCategory[]>([]);
  const [repliesByComment, setRepliesByComment] = useState<Record<number, ReplyVO[]>>({});
  const [replyInputs, setReplyInputs] = useState<Record<string, string>>({});
  const [expandedComments, setExpandedComments] = useState<Record<number, boolean>>({});
  const [following, setFollowing] = useState(false);
  const [blacked, setBlacked] = useState(false);
  const [commentText, setCommentText] = useState("");
  const [activeImageIndex, setActiveImageIndex] = useState(0);
  const [reportTarget, setReportTarget] = useState<{ targetType: number; targetId: number; title: string } | null>(null);
  const [reportReason, setReportReason] = useState("");
  const [reportSubmitting, setReportSubmitting] = useState(false);
  const [reportSuccess, setReportSuccess] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const highlightedTargetRef = useRef<string | null>(null);
  const targetCommentId = parseId(searchParams.get("commentId"));
  const targetReplyId = parseId(searchParams.get("replyId"));

  useEffect(() => {
    setActiveImageIndex(0);
    highlightedTargetRef.current = null;
    void loadArticle();
  }, [articleId]);

  useEffect(() => {
    const targetId = targetReplyId ? `reply-${targetReplyId}` : targetCommentId ? `comment-${targetCommentId}` : null;
    if (!targetId || highlightedTargetRef.current === targetId) {
      return;
    }
    const element = document.getElementById(targetId);
    if (!element) {
      return;
    }
    highlightedTargetRef.current = targetId;
    element.scrollIntoView({ behavior: "smooth", block: "center" });
    element.classList.add("anchor-highlight");
    const timer = window.setTimeout(() => {
      element.classList.remove("anchor-highlight");
    }, 2400);
    return () => window.clearTimeout(timer);
  }, [comments, repliesByComment, targetCommentId, targetReplyId]);

  async function loadArticle() {
    if (!articleId) {
      setError("文章 ID 不存在。");
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const detail = await contentApi.getArticleDetail(Number(articleId));
      await socialApi.viewArticle(Number(articleId));
      if (!detail.content) {
        const content = await contentApi.getArticleContent(Number(articleId));
        detail.content = content?.content ?? "";
        detail.contentParagraphs = content?.contentParagraphs ?? detail.contentParagraphs;
        detail.imageUrls = content?.imageUrls ?? detail.imageUrls ?? [];
      }
      setArticle(detail);
      contentApi.listCategories().then(setCategories).catch(() => setCategories([]));
      const users = await userApi.getUsersByIds([detail.userId]);
      const nextAuthor = users[0] ?? null;
      setAuthor(nextAuthor);
      await Promise.all([
        loadSocial(Number(articleId)),
        nextAuthor ? refreshRelation(nextAuthor.id) : Promise.resolve()
      ]);
      await ensureAnchorTargets(detail.id);
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setLoading(false);
    }
  }

  async function refreshRelation(authorId: number) {
    const [isFollowing, isBlacked] = await Promise.all([
      socialApi.checkFollowing(authorId),
      socialApi.checkBlack(authorId)
    ]);
    setFollowing(isFollowing);
    setBlacked(isBlacked);
  }

  async function loadSocial(id: number) {
    const [statsResult, commentsResult] = await Promise.all([
      socialApi.getArticleStats(id),
      socialApi.listComments(id)
    ]);
    setStats(statsResult);
    setComments(commentsResult.data ?? []);
  }

  async function ensureAnchorTargets(currentArticleId: number) {
    if (!targetCommentId) {
      return;
    }
    try {
      const comment = await socialApi.getCommentDetail(targetCommentId);
      if (comment.articleId !== currentArticleId) {
        return;
      }
      setComments((current) => current.some((item) => item.id === comment.id) ? current : [comment, ...current]);
      setExpandedComments((current) => ({ ...current, [targetCommentId]: true }));
      let nextReplies = (await socialApi.listReplies(targetCommentId, 1, 100)).data ?? [];
      if (targetReplyId && !nextReplies.some((item) => item.id === targetReplyId)) {
        const reply = await socialApi.getReplyDetail(targetReplyId);
        if (reply.commentId === targetCommentId) {
          nextReplies = [reply, ...nextReplies];
        }
      }
      setRepliesByComment((current) => ({
        ...current,
        [targetCommentId]: dedupeReplies([...(current[targetCommentId] ?? []), ...nextReplies])
      }));
    } catch {
      // 忽略锚点补拉失败，页面主内容仍可正常阅读。
    }
  }

  async function toggleArticleLike() {
    if (!article) {
      return;
    }
    try {
      if (stats?.liked) {
        await socialApi.unlikeArticle(article.id);
      } else {
        await socialApi.likeArticle(article.id);
      }
      setStats(await socialApi.getArticleStats(article.id));
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function toggleCommentLike(comment: CommentVO) {
    try {
      if (comment.liked) {
        await socialApi.unlikeComment(comment.id);
      } else {
        await socialApi.likeComment(comment.id);
      }
      setComments((current) => current.map((item) => item.id === comment.id
        ? { ...item, liked: !item.liked, likeCount: Math.max(0, item.likeCount + (item.liked ? -1 : 1)) }
        : item));
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function loadReplies(commentId: number) {
    try {
      const result = await socialApi.listReplies(commentId);
      setRepliesByComment((current) => ({ ...current, [commentId]: result.data ?? [] }));
      setExpandedComments((current) => ({ ...current, [commentId]: true }));
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function toggleReplyLike(reply: ReplyVO) {
    try {
      if (reply.liked) {
        await socialApi.unlikeReply(reply.id);
      } else {
        await socialApi.likeReply(reply.id);
      }
      setRepliesByComment((current) => ({
        ...current,
        [reply.commentId]: (current[reply.commentId] ?? []).map((item) => item.id === reply.id
          ? { ...item, liked: !item.liked, likeCount: Math.max(0, item.likeCount + (item.liked ? -1 : 1)) }
          : item)
      }));
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function submitReply(commentId: number, replyToUserId?: number) {
    const inputKey = replyToUserId ? `${commentId}:${replyToUserId}` : String(commentId);
    const content = replyInputs[inputKey]?.trim();
    if (!content) {
      return;
    }
    try {
      await socialApi.addReply(commentId, content, replyToUserId);
      setReplyInputs((current) => ({ ...current, [inputKey]: "" }));
      await Promise.all([loadReplies(commentId), article ? loadSocial(article.id) : Promise.resolve()]);
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function submitComment() {
    if (!article || !commentText.trim()) {
      return;
    }
    try {
      await socialApi.addComment(article.id, commentText.trim());
      setCommentText("");
      await loadSocial(article.id);
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function toggleFollow() {
    if (!author) {
      return;
    }
    try {
      if (following) {
        await socialApi.unfollow(author.id);
      } else {
        await socialApi.follow(author.id);
      }
      await refreshRelation(author.id);
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function toggleBlack() {
    if (!author) {
      return;
    }
    try {
      if (blacked) {
        await socialApi.unblack(author.id);
      } else {
        await socialApi.black(author.id);
      }
      await refreshRelation(author.id);
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  function openReportModal(targetType: number, targetId: number, title: string) {
    setError(null);
    setReportSuccess(null);
    setReportReason("");
    setReportTarget({ targetType, targetId, title });
  }

  function closeReportModal() {
    if (reportSubmitting) {
      return;
    }
    setReportTarget(null);
    setReportReason("");
  }

  async function submitReport() {
    if (!reportTarget) {
      return;
    }
    const reason = reportReason.trim();
    if (!reason) {
      setError("请输入举报理由。");
      return;
    }
    setReportSubmitting(true);
    try {
      await socialApi.createReport({
        targetType: reportTarget.targetType,
        targetId: reportTarget.targetId,
        reason
      });
      setError(null);
      setReportSuccess("举报已提交，管理员会在审核中心处理。");
      setReportTarget(null);
      setReportReason("");
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setReportSubmitting(false);
    }
  }

  const coverImages = normalizeImages(article?.imageUrls ?? [], article?.coverUrl);
  const activeCoverImage = coverImages[activeImageIndex] ?? coverImages[0];
  const categoryName = article ? categories.find((item) => item.id === article.categoryId)?.name : undefined;
  const canEditArticle = Boolean(article && currentUser && article.userId === currentUser.id);
  const blockedInteraction = Boolean(author && currentUser && author.id !== currentUser.id && blacked);
  const contentParagraphs = article ? resolveParagraphs(article.contentParagraphs, article.content) : [];

  return (
    <section className="page-view content-detail">
      <div className="page-heading compact-heading">
        <p className="eyebrow">文章详情</p>
        <h1>{article?.title ?? "正在加载内容..."}</h1>
        <span>这里直接展示文章骨架数据和 Mongo 正文。如果你是作者，也可以从这里继续编辑。</span>
      </div>

      <div className="content-detail-actions">
        <ActionButton variant="ghost" onClick={() => navigate("/app/modules/content")}>
          返回广场
        </ActionButton>
        {canEditArticle && article && (
          <ActionButton onClick={() => navigate(`/app/modules/content/${article.id}/edit`)}>
            继续编辑
          </ActionButton>
        )}
      </div>

      {loading && <Notice>正在加载文章详情...</Notice>}
      {error && <Notice type="error">{error}</Notice>}
      {reportSuccess && <Notice type="info">{reportSuccess}</Notice>}

      {article && !loading && (
        <>
          <div className="content-detail-meta">
            <article>
              <span>状态</span>
              <strong>{statusLabel(article.status)}</strong>
            </article>
            <article>
              <span>发布时间</span>
              <strong>{formatTime(article.publishedTime ?? article.updateTime)}</strong>
            </article>
            <article>
              <span>分类</span>
              <strong>{categoryName ?? `分类 #${article.categoryId}`}</strong>
            </article>
          </div>

          {author && (
            <article className="article-author-card">
              <button className="article-author-main" type="button" onClick={() => navigate(`/app/users/${author.accountId}`)}>
                <AvatarImage src={author.avatar} name={author.username} className="avatar" />
                <span>
                  <strong>{author.username}</strong>
                  <em>#{author.accountId} · {author.signature || "这个作者还没有签名。"}</em>
                </span>
              </button>
              <div className="article-author-actions">
                <ActionButton variant={following ? "soft" : "ghost"} onClick={toggleFollow}>
                  {following ? "已关注" : "关注作者"}
                </ActionButton>
                <ActionButton variant={blacked ? "soft" : "ghost"} onClick={toggleBlack}>
                  {blacked ? "移出黑名单" : "拉黑"}
                </ActionButton>
                <ActionButton variant="ghost" onClick={() => openReportModal(ReportTargetType.USER, author.id, `用户：${author.username}`)}>
                  举报用户
                </ActionButton>
              </div>
            </article>
          )}

          {article.auditMessage && article.status !== 1 && (
            <Notice type={article.status === 4 ? "warning" : "info"}>{article.auditMessage}</Notice>
          )}

          {coverImages.length > 0 && (
            <div className="content-cover-carousel">
              <div className="content-detail-cover">
                <img src={activeCoverImage} alt={`${article.title} 封面 ${activeImageIndex + 1}`} />
              </div>
              {coverImages.length > 1 && (
                <div className="cover-carousel-controls">
                  <button
                    type="button"
                    onClick={() => setActiveImageIndex((current) => (current - 1 + coverImages.length) % coverImages.length)}
                  >
                    上一张
                  </button>
                  <span>{activeImageIndex + 1} / {coverImages.length}</span>
                  <button
                    type="button"
                    onClick={() => setActiveImageIndex((current) => (current + 1) % coverImages.length)}
                  >
                    下一张
                  </button>
                </div>
              )}
              {coverImages.length > 1 && (
                <div className="cover-carousel-dots">
                  {coverImages.map((image, index) => (
                    <button
                      key={image}
                      type="button"
                      className={index === activeImageIndex ? "active" : ""}
                      onClick={() => setActiveImageIndex(index)}
                      aria-label={`查看第 ${index + 1} 张封面`}
                    />
                  ))}
                </div>
              )}
            </div>
          )}

          <article className="content-detail-body">
            {article.summary && (
              <div className="content-detail-summary">
                <strong>摘要</strong>
                <p>{article.summary}</p>
              </div>
            )}
            <div className="content-detail-text">
              {contentParagraphs.map((paragraph, index) => (
                <p key={`${index}-${paragraph.slice(0, 16)}`}>{paragraph}</p>
              ))}
            </div>
          </article>

          <section className="social-panel">
              <div className="social-stats">
              <span>浏览 {stats?.viewCount ?? 0}</span>
              <span>评论 {stats?.commentCount ?? comments.length}</span>
              <span>点赞 {stats?.likeCount ?? 0}</span>
              <ActionButton variant={stats?.liked ? "soft" : "ghost"} onClick={toggleArticleLike} disabled={blockedInteraction}>
                {stats?.liked ? "已点赞" : "点赞"}
              </ActionButton>
              <ActionButton variant="ghost" onClick={() => openReportModal(ReportTargetType.ARTICLE, article.id, `文章：${article.title}`)}>
                举报文章
              </ActionButton>
            </div>
            {blockedInteraction && <Notice type="info">你已拉黑该作者，不能对 TA 的内容继续点赞、评论或回复。</Notice>}

            <label>
              写一条评论
              <textarea
                value={commentText}
                onChange={(event) => setCommentText(event.target.value)}
                placeholder={blockedInteraction ? "黑名单关系下不能互动" : "把你的看法留在这里..."}
                disabled={blockedInteraction}
              />
            </label>
            <ActionButton onClick={submitComment} disabled={blockedInteraction || !commentText.trim()}>
              发布评论
            </ActionButton>

            <div className="comment-list">
              {comments.length === 0 && <Notice>还没有评论，来抢一楼。</Notice>}
              {comments.map((comment) => (
                <article key={comment.id} id={`comment-${comment.id}`} className="comment-card">
                  <div className="comment-author">
                    <AvatarImage src={comment.avatar} name={comment.username} className="avatar" />
                    <button type="button" onClick={() => navigate(`/app/users/id/${comment.userId}`)}>
                      {comment.username}
                    </button>
                    <time>{formatTime(comment.createTime)}</time>
                  </div>
                  <p>{comment.content}</p>
                  <div className="comment-actions">
                    <button
                      type="button"
                      className={comment.liked ? "active" : ""}
                      disabled={blockedInteraction}
                      onClick={() => void toggleCommentLike(comment)}
                    >
                      {comment.liked ? "♥ 已赞" : "♡ 点赞"} · {comment.likeCount}
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        if (expandedComments[comment.id]) {
                          setExpandedComments((current) => ({ ...current, [comment.id]: false }));
                        } else {
                          void loadReplies(comment.id);
                        }
                      }}
                    >
                      {expandedComments[comment.id] ? "收起回复" : `查看回复 · ${comment.replyCount}`}
                    </button>
                    <button type="button" onClick={() => openReportModal(ReportTargetType.COMMENT, comment.id, `评论：${comment.username}`)}>
                      举报
                    </button>
                  </div>
                  <div className="reply-editor compact">
                    <input
                      value={replyInputs[String(comment.id)] ?? ""}
                      onChange={(event) => setReplyInputs((current) => ({ ...current, [String(comment.id)]: event.target.value }))}
                      placeholder={`回复 ${comment.username}`}
                      disabled={blockedInteraction}
                    />
                    <button type="button" onClick={() => void submitReply(comment.id)} disabled={blockedInteraction || !replyInputs[String(comment.id)]?.trim()}>
                      回复
                    </button>
                  </div>
                  {expandedComments[comment.id] && (
                    <div className="reply-list">
                      {(repliesByComment[comment.id] ?? []).length === 0 && <span className="reply-empty">还没有回复。</span>}
                      {(repliesByComment[comment.id] ?? []).map((reply) => {
                        const inputKey = `${comment.id}:${reply.userId}`;
                        return (
                          <article key={reply.id} id={`reply-${reply.id}`} className="reply-card">
                            <div className="comment-author">
                              <AvatarImage src={reply.avatar} name={reply.username} className="avatar" />
                              <button type="button" onClick={() => navigate(`/app/users/id/${reply.userId}`)}>
                                {reply.username}
                              </button>
                              {reply.replyToUsername && <span>回复 {reply.replyToUsername}</span>}
                              <time>{formatTime(reply.createTime)}</time>
                            </div>
                            <p>{reply.content}</p>
                            <div className="comment-actions">
                              <button
                                type="button"
                                className={reply.liked ? "active" : ""}
                                disabled={blockedInteraction}
                                onClick={() => void toggleReplyLike(reply)}
                              >
                                {reply.liked ? "♥ 已赞" : "♡ 点赞"} · {reply.likeCount}
                              </button>
                              <button
                                type="button"
                                onClick={() => setReplyInputs((current) => ({
                                  ...current,
                                  [inputKey]: current[inputKey] ?? `@${reply.username} `
                                }))}
                              >
                                回复
                              </button>
                              <button type="button" onClick={() => openReportModal(ReportTargetType.REPLY, reply.id, `回复：${reply.username}`)}>
                                举报
                              </button>
                            </div>
                            {replyInputs[inputKey] !== undefined && (
                              <div className="reply-editor compact">
                                <input
                                  value={replyInputs[inputKey] ?? ""}
                                  onChange={(event) => setReplyInputs((current) => ({ ...current, [inputKey]: event.target.value }))}
                                  placeholder={`回复 ${reply.username}`}
                                  disabled={blockedInteraction}
                                />
                                <button type="button" onClick={() => void submitReply(comment.id, reply.userId)} disabled={blockedInteraction || !replyInputs[inputKey]?.trim()}>
                                  发送
                                </button>
                              </div>
                            )}
                          </article>
                        );
                      })}
                    </div>
                  )}
                </article>
              ))}
            </div>
          </section>
        </>
      )}

      {reportTarget && (
        <div className="report-modal-backdrop" role="presentation" onClick={closeReportModal}>
          <section className="report-modal" role="dialog" aria-modal="true" aria-label="提交举报" onClick={(event) => event.stopPropagation()}>
            <p className="eyebrow">提交举报</p>
            <h2>{reportTarget.title}</h2>
            <label>
              举报理由
              <textarea
                value={reportReason}
                onChange={(event) => setReportReason(event.target.value)}
                maxLength={200}
                placeholder="请说明违规原因，例如广告、辱骂、违法内容或恶意骚扰。"
                autoFocus
              />
            </label>
            <span className="report-modal-count">{reportReason.trim().length}/200</span>
            <div className="report-modal-actions">
              <ActionButton variant="ghost" onClick={closeReportModal} disabled={reportSubmitting}>
                取消
              </ActionButton>
              <ActionButton onClick={() => void submitReport()} disabled={reportSubmitting || !reportReason.trim()}>
                {reportSubmitting ? "提交中..." : "提交举报"}
              </ActionButton>
            </div>
          </section>
        </div>
      )}
    </section>
  );
}

function normalizeImages(images: string[], coverUrl?: string) {
  const result: string[] = [];
  for (const image of images) {
    if (image && !result.includes(image)) {
      result.push(image);
    }
  }
  if (coverUrl && !result.includes(coverUrl)) {
    result.unshift(coverUrl);
  }
  return result;
}

function dedupeReplies(replies: ReplyVO[]) {
  const map = new Map<number, ReplyVO>();
  for (const reply of replies) {
    map.set(reply.id, reply);
  }
  return [...map.values()].sort((left, right) => {
    const leftTime = left.createTime ? new Date(left.createTime).getTime() : 0;
    const rightTime = right.createTime ? new Date(right.createTime).getTime() : 0;
    return leftTime - rightTime;
  });
}

function parseId(value: string | null) {
  if (!value) {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
}

function resolveParagraphs(contentParagraphs?: Record<string, string>, content?: string) {
  const values = contentParagraphs
    ? Object.entries(contentParagraphs)
      .sort(([left], [right]) => left.localeCompare(right, "zh-CN", { numeric: true }))
      .map(([, value]) => value)
      .filter(Boolean)
    : [];
  if (values.length > 0) {
    return values;
  }
  return content?.split(/\n{2,}/).map((item) => item.trim()).filter(Boolean) ?? [];
}
