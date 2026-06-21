import { ArticleSummary } from "../api/content";
import { ArticleStats } from "../api/social";
import { UserVO } from "../api/user";
import { AvatarImage } from "./AvatarImage";

type ArticleCardProps = {
  article: ArticleSummary;
  author?: UserVO;
  categoryName?: string;
  stats?: ArticleStats;
  onOpen: (articleId: number) => void;
  onAuthorOpen: (userId: number) => void;
  onToggleLike?: (articleId: number) => void;
};

function formatTime(value?: string) {
  if (!value) {
    return "等待发布时间";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("zh-CN", {
    month: "numeric",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function resolveArticleTime(article: ArticleSummary) {
  return article.publishedTime ?? article.updateTime ?? article.createTime;
}

export function ArticleCard({ article, author, categoryName, stats, onOpen, onAuthorOpen, onToggleLike }: ArticleCardProps) {
  return (
    <article className="content-card" onClick={() => onOpen(article.id)}>
      <div className="content-card-cover">
        {article.coverUrl ? <img src={article.coverUrl} alt={article.title} /> : <span>NO COVER</span>}
      </div>
      <div className="content-card-body">
        <div className="content-card-author">
          <button
            type="button"
            onClick={(event) => {
              event.stopPropagation();
              onAuthorOpen(article.userId);
            }}
          >
            <AvatarImage src={author?.avatar} name={author?.username ?? "玩家"} className="avatar" />
            <span>{author?.username ?? `玩家 ${article.userId}`}</span>
          </button>
          <button
            type="button"
            className={`content-like ${stats?.liked ? "liked" : ""}`}
            onClick={(event) => {
              event.stopPropagation();
              onToggleLike?.(article.id);
            }}
            aria-label={stats?.liked ? "取消点赞" : "点赞"}
          >
            <span>{stats?.liked ? "♥" : "♡"}</span>
            <strong>{stats?.likeCount ?? 0}</strong>
          </button>
        </div>
        <p className="content-card-meta">
          <strong>{categoryName ?? "未分类"}</strong>
          <span>{formatTime(resolveArticleTime(article))}</span>
        </p>
        <h3>{article.title}</h3>
        <div>{article.summary || "这篇内容还没写摘要，点击查看正文。"}</div>
      </div>
    </article>
  );
}
