import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArticleSummary, ContentCategory, contentApi } from "../api/content";
import { HotArticle, recommendApi } from "../api/recommend";
import { ArticleSearchItem, SearchHistory, SuggestDocument, searchApi } from "../api/search";
import { ArticleStats, socialApi } from "../api/social";
import { UserVO, userApi } from "../api/user";
import { ActionButton } from "../components/ActionButton";
import { ArticleCard } from "../components/ArticleCard";
import { Notice } from "../components/Notice";
import { useNotification } from "../features/notification/NotificationContext";
import { friendlyError } from "../utils/errors";

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

export function ContentHubPage() {
  const navigate = useNavigate();
  const { summary, markFeedRead } = useNotification();
  const [categories, setCategories] = useState<ContentCategory[]>([]);
  const [articles, setArticles] = useState<ArticleSummary[]>([]);
  const [authors, setAuthors] = useState<Record<number, UserVO>>({});
  const [stats, setStats] = useState<Record<number, ArticleStats>>({});
  const [mode, setMode] = useState<"discover" | "feed" | "hot" | "search">("discover");
  const [selectedCategory, setSelectedCategory] = useState<number | null>(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [activeKeyword, setActiveKeyword] = useState("");
  const [suggestions, setSuggestions] = useState<SuggestDocument[]>([]);
  const [history, setHistory] = useState<SearchHistory[]>([]);
  const [searchPage, setSearchPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void bootstrap();
  }, []);

  useEffect(() => {
    if (!loading) {
      void reloadArticles(selectedCategory, mode);
    }
  }, [selectedCategory, mode]);

  useEffect(() => {
    if (mode === "feed" && summary.feedUnread) {
      void markFeedRead();
    }
  }, [mode, summary.feedUnread]);

  useEffect(() => {
    if (!searchTerm.trim()) {
      setSuggestions([]);
      return;
    }
    const timer = window.setTimeout(async () => {
      try {
        setSuggestions(await searchApi.suggest(searchTerm));
      } catch {
        setSuggestions([]);
      }
    }, 240);
    return () => window.clearTimeout(timer);
  }, [searchTerm]);

  async function bootstrap() {
    setLoading(true);
    setError(null);
    try {
      const [categoryData, articleData, historyData] = await Promise.all([
        contentApi.listCategories(),
        contentApi.listLatest(),
        searchApi.listHistory().catch(() => [])
      ]);
      setCategories(categoryData);
      setArticles(articleData);
      setHistory(historyData);
      await enrichArticles(articleData);
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setLoading(false);
    }
  }

  async function reloadArticles(categoryId: number | null, nextMode = mode) {
    setLoading(true);
    setError(null);
    try {
      const articleData = nextMode === "search"
        ? applySearchArticles(await searchApi.searchArticles({ keyword: activeKeyword || searchTerm, categoryId, page: 1, size: 12 }))
        : nextMode === "feed"
        ? (await socialApi.listFeed({ size: 12 })).data
        : nextMode === "hot"
          ? applyHotArticles(await recommendApi.listHot({ categoryId, size: 12 }))
          : await contentApi.listLatest(categoryId ? { categoryId } : undefined);
      setArticles(articleData);
      if (nextMode !== "hot") {
        await enrichArticles(articleData);
      }
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setLoading(false);
    }
  }

  async function handleLoadMore() {
    const lastArticle = articles[articles.length - 1];
    if (!lastArticle) {
      return;
    }
    setLoadingMore(true);
    setError(null);
    try {
      const more = mode === "search"
        ? applySearchArticles(await searchApi.searchArticles({
          keyword: activeKeyword,
          categoryId: selectedCategory,
          page: searchPage + 1,
          size: 12
        }))
        : mode === "feed"
        ? (await socialApi.listFeed({ before: resolveArticleTime(lastArticle), size: 8 })).data
        : mode === "hot"
          ? applyHotArticles(await recommendApi.listHot({ page: Math.floor(articles.length / 12) + 1, size: 12, categoryId: selectedCategory }))
          : await contentApi.listMore({
            lastId: lastArticle.id,
            categoryId: selectedCategory ?? undefined,
            size: 8
          });
      setArticles((current) => {
        const existing = new Set(current.map((item) => item.id));
        const additions = more.filter((item) => !existing.has(item.id));
        if (mode !== "hot") {
          void enrichArticles(additions);
        }
        return current.concat(additions);
      });
      if (mode === "search") {
        setSearchPage((current) => current + 1);
      }
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setLoadingMore(false);
    }
  }

  async function enrichArticles(nextArticles: ArticleSummary[]) {
    if (nextArticles.length === 0) {
      return;
    }
    const articleIds = [...new Set(nextArticles.map((article) => article.id))];
    const authorIds = [...new Set(nextArticles.map((article) => article.userId))];
    const [nextStats, nextAuthors] = await Promise.all([
      socialApi.getArticleStatsBatch(articleIds),
      userApi.getUsersByIds(authorIds)
    ]);
    setStats((current) => ({
      ...current,
      ...Object.fromEntries(nextStats.map((item) => [item.articleId, item]))
    }));
    setAuthors((current) => ({
      ...current,
      ...Object.fromEntries(nextAuthors.map((item) => [item.id, item]))
    }));
  }

  function applyHotArticles(items: HotArticle[]) {
    const nextStats: Record<number, ArticleStats> = {};
    const nextAuthors: Record<number, UserVO> = {};
    const nextArticles = items.map((item) => {
      nextStats[item.id] = {
        articleId: item.id,
        likeCount: item.likeCount,
        commentCount: item.commentCount,
        viewCount: item.viewCount,
        liked: item.liked
      };
      nextAuthors[item.userId] = {
        id: item.userId,
        accountId: item.userId,
        username: item.authorName ?? `玩家${item.userId}`,
        avatar: item.authorAvatar,
        status: 1,
        type: 0,
        auditStatus: 1,
        version: 0,
        followCount: 0,
        fansCount: 0
      };
      return {
        id: item.id,
        userId: item.userId,
        title: item.title,
        summary: item.summary,
        coverUrl: item.coverUrl,
        categoryId: item.categoryId,
        status: 1,
        publishedTime: item.publishedTime,
        createTime: item.createTime,
        updateTime: item.updateTime
      };
    });
    setStats((current) => ({ ...current, ...nextStats }));
    setAuthors((current) => ({ ...current, ...nextAuthors }));
    return nextArticles;
  }

  function applySearchArticles(items: ArticleSearchItem[]) {
    const nextAuthors: Record<number, UserVO> = {};
    const nextArticles = items.map((item) => {
      if (item.username || item.avatar) {
        nextAuthors[item.userId] = {
          id: item.userId,
          accountId: item.userId,
          username: item.username ?? `玩家${item.userId}`,
          avatar: item.avatar,
          status: 1,
          type: 0,
          auditStatus: 1,
          version: 0,
          followCount: 0,
          fansCount: 0
        };
      }
      return item;
    });
    setAuthors((current) => ({ ...current, ...nextAuthors }));
    return nextArticles;
  }

  async function executeSearch(keyword = searchTerm) {
    const normalized = keyword.trim();
    if (!normalized) {
      setMode("discover");
      setActiveKeyword("");
      setSearchPage(1);
      await reloadArticles(selectedCategory, "discover");
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = applySearchArticles(await searchApi.searchArticles({
        keyword: normalized,
        categoryId: selectedCategory,
        page: 1,
        size: 12
      }));
      setMode("search");
      setActiveKeyword(normalized);
      setSearchTerm(normalized);
      setSearchPage(1);
      setArticles(result);
      await enrichArticles(result);
      setHistory(await searchApi.listHistory().catch(() => history));
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setLoading(false);
    }
  }

  async function toggleLike(articleId: number) {
    const current = stats[articleId];
    try {
      if (current?.liked) {
        await socialApi.unlikeArticle(articleId);
      } else {
        await socialApi.likeArticle(articleId);
      }
      const [nextStats] = await socialApi.getArticleStatsBatch([articleId]);
      if (nextStats) {
        setStats((value) => ({ ...value, [articleId]: nextStats }));
      }
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  const categoryMap = useMemo(() => new Map(categories.map((item) => [item.id, item.name])), [categories]);

  function categoryNameOf(article: ArticleSummary) {
    return categoryMap.get(article.categoryId) ?? (article as ArticleSearchItem).categoryName;
  }

  return (
    <section className="page-view page-view--feed">
      <div className="page-heading compact-heading">
        <p className="eyebrow">内容广场</p>
        <h1>把发帖、审核和创作流真正接上。</h1>
        <span>这里现在直接连 `content-service`，支持分类筛选、详情查看、继续创作和我的文章管理。</span>
      </div>

      <div className="feature-strip content-strip">
        <div>
          <h2>创作入口已经接好</h2>
          <p>你可以直接进编辑器保存草稿、提交审核，已发布内容会出现在广场，驳回原因也能在我的文章里看到。</p>
        </div>
        <div className="content-strip-actions">
          <ActionButton variant="ghost" onClick={() => void reloadArticles(selectedCategory, mode)}>
            更新
          </ActionButton>
          <ActionButton variant="ghost" onClick={() => navigate("/app/modules/content/mine")}>
            我的文章
          </ActionButton>
          <ActionButton onClick={() => navigate("/app/modules/content/new")}>写一篇新内容</ActionButton>
        </div>
      </div>

      <form
        className="search-panel"
        onSubmit={(event) => {
          event.preventDefault();
          void executeSearch();
        }}
      >
        <div>
          <label>搜索内容</label>
          <div className="search-box">
            <input
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              placeholder="搜标题、摘要、正文或分类关键词"
            />
            <ActionButton>搜索</ActionButton>
          </div>
        </div>
        {(suggestions.length > 0 || history.length > 0) && (
          <div className="search-chips">
            {suggestions.map((item) => (
              <button key={`s-${item.id ?? item.suggest}`} type="button" onClick={() => void executeSearch(item.suggest)}>
                {item.suggest}
              </button>
            ))}
            {history.slice(0, 8).map((item) => (
              <button key={`h-${item.id}`} type="button" className="history" onClick={() => void executeSearch(item.keyword)}>
                {item.keyword}
              </button>
            ))}
            {history.length > 0 && (
              <button
                type="button"
                className="muted"
                onClick={async () => {
                  await searchApi.clearHistory();
                  setHistory([]);
                }}
              >
                清空历史
              </button>
            )}
          </div>
        )}
      </form>

      <div className="content-filters">
        <button
          className={mode === "search" ? "active" : ""}
          onClick={() => void executeSearch(activeKeyword || searchTerm)}
          disabled={!activeKeyword && !searchTerm.trim()}
          type="button"
        >
          搜索结果
        </button>
        <button
          className={mode === "discover" ? "active" : ""}
          onClick={() => {
            setMode("discover");
            void reloadArticles(selectedCategory, "discover");
          }}
          type="button"
        >
          发现
        </button>
        <button
          className={mode === "feed" ? "active" : ""}
          onClick={() => {
            setMode("feed");
            void reloadArticles(null, "feed");
          }}
          type="button"
        >
          关注流
          {summary.feedUnread && <span className="inline-dot" />}
        </button>
        <button
          className={mode === "hot" ? "active" : ""}
          onClick={() => {
            setMode("hot");
            void reloadArticles(selectedCategory, "hot");
          }}
          type="button"
        >
          热门榜
        </button>
        <button
          className={mode !== "feed" && selectedCategory == null ? "active" : ""}
          onClick={() => setSelectedCategory(null)}
          disabled={mode === "feed"}
          type="button"
        >
          全部
        </button>
        {categories.map((category) => (
          <button
            key={category.id}
            className={selectedCategory === category.id ? "active" : ""}
            onClick={() => setSelectedCategory(category.id)}
            disabled={mode === "feed"}
            type="button"
          >
            {category.name}
          </button>
        ))}
      </div>

      {error && <Notice type="error">{error}</Notice>}
      {loading && <Notice>正在加载内容列表...</Notice>}
      {!loading && categories.length === 0 && (
        <Notice type="warning">当前还没有启用分类，先去后台创建分类后，创作入口才能完整工作。</Notice>
      )}
      {!loading && articles.length === 0 && (
        <Notice type="info">当前分类下还没有已发布文章，你可以先写一篇内容试试。</Notice>
      )}

      <div className="content-grid">
        {articles.map((article) => (
          <ArticleCard
            key={article.id}
            article={article}
            author={authors[article.userId]}
            categoryName={categoryNameOf(article)}
            stats={stats[article.id]}
            onOpen={(articleId) => navigate(`/app/modules/content/${articleId}`)}
            onAuthorOpen={(userId) => navigate(`/app/users/id/${userId}`)}
            onToggleLike={(articleId) => void toggleLike(articleId)}
          />
        ))}
      </div>

      {!loading && articles.length > 0 && (
        <div className="content-load-more">
          <ActionButton variant="soft" busy={loadingMore} onClick={handleLoadMore}>
            {mode === "feed" ? "发现更多关注内容" : mode === "hot" ? "查看更多热门" : mode === "search" ? "查看更多搜索结果" : "发现更多"}
          </ActionButton>
        </div>
      )}
    </section>
  );
}
