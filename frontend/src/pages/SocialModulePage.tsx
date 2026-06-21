import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArticleSummary } from "../api/content";
import { BrowseHistory, FollowUser, socialApi } from "../api/social";
import { ActionButton } from "../components/ActionButton";
import { AvatarImage } from "../components/AvatarImage";
import { Notice } from "../components/Notice";
import { friendlyError } from "../utils/errors";

type SocialTab = "following" | "fans" | "black" | "history" | "likes";

const tabs: Array<{ id: SocialTab; label: string }> = [
  { id: "following", label: "我关注的" },
  { id: "fans", label: "关注我的" },
  { id: "black", label: "黑名单" },
  { id: "history", label: "浏览历史" },
  { id: "likes", label: "点赞文章" }
];

export function SocialModulePage() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<SocialTab>("following");
  const [users, setUsers] = useState<FollowUser[]>([]);
  const [history, setHistory] = useState<BrowseHistory[]>([]);
  const [likes, setLikes] = useState<ArticleSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    void loadTab(activeTab);
  }, [activeTab]);

  async function loadTab(tab: SocialTab) {
    setLoading(true);
    setError(null);
    try {
      if (tab === "following") {
        const result = await socialApi.listFollowing();
        setUsers(result.data ?? []);
      } else if (tab === "fans") {
        const result = await socialApi.listFans();
        setUsers(result.data ?? []);
      } else if (tab === "black") {
        const result = await socialApi.listBlack();
        setUsers(result.data ?? []);
      } else if (tab === "history") {
        const result = await socialApi.listBrowseHistory();
        setHistory(result.data ?? []);
      } else {
        const result = await socialApi.listLikedArticles();
        setLikes(result.data ?? []);
      }
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setLoading(false);
    }
  }

  async function removeBlack(userId: number) {
    try {
      await socialApi.unblack(userId);
      await loadTab("black");
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function followUser(userId: number) {
    try {
      await socialApi.follow(userId);
      await loadTab(activeTab);
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function unfollowUser(userId: number) {
    try {
      await socialApi.unfollow(userId);
      await loadTab(activeTab);
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function blackUser(userId: number) {
    try {
      await socialApi.black(userId);
      await loadTab(activeTab);
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  return (
    <section className="page-view social-page">
      <div className="feature-strip social-hero">
        <div>
          <p className="eyebrow">Social Graph</p>
          <h1>社交关系</h1>
          <span>关注、粉丝、浏览历史和点赞文章已经接入 social-service，关系数据走 MySQL，评论正文走 MongoDB。</span>
        </div>
        <ActionButton onClick={() => navigate("/app/modules/content")}>去内容广场互动</ActionButton>
      </div>

      <div className="content-filters">
        {tabs.map((tab) => (
          <button key={tab.id} className={activeTab === tab.id ? "active" : ""} onClick={() => setActiveTab(tab.id)}>
            {tab.label}
          </button>
        ))}
      </div>

      {loading && <Notice>正在拉取社交数据...</Notice>}
      {error && <Notice type="error">{error}</Notice>}

      {!loading && !error && (activeTab === "following" || activeTab === "fans" || activeTab === "black") && (
        <div className="social-grid">
          {users.length === 0 && <Notice>这里暂时还没有数据，去内容广场看几篇文章、关注几个玩家就会热闹起来。</Notice>}
          {users.map((user) => (
            <article key={user.userId} className="social-user-card">
              <AvatarImage className="avatar" src={user.avatar} name={user.username} />
              <div>
                <h3>{user.username}</h3>
                <p>{user.signature || "这个玩家还没有留下签名。"}</p>
              </div>
              {activeTab === "black" ? (
                <ActionButton variant="soft" onClick={() => removeBlack(user.userId)}>
                  移出黑名单
                </ActionButton>
              ) : (
                <div className="social-card-actions">
                  <ActionButton variant="ghost" onClick={() => navigate(`/app/users/id/${user.userId}`)}>
                    看主页
                  </ActionButton>
                  {activeTab === "following" ? (
                    <ActionButton variant="soft" onClick={() => unfollowUser(user.userId)}>
                      取关
                    </ActionButton>
                  ) : (
                    <ActionButton variant="soft" onClick={() => followUser(user.userId)}>
                      关注
                    </ActionButton>
                  )}
                  <ActionButton variant="ghost" onClick={() => blackUser(user.userId)}>
                    拉黑
                  </ActionButton>
                </div>
              )}
            </article>
          ))}
        </div>
      )}

      {!loading && !error && activeTab === "history" && (
        <div className="mine-articles">
          {history.length === 0 && <Notice>还没有浏览记录。</Notice>}
          {history.map((item) => (
            <ArticleRow key={`${item.articleId}-${item.browseTime}`} article={item.article} fallbackId={item.articleId} onOpen={navigate} />
          ))}
        </div>
      )}

      {!loading && !error && activeTab === "likes" && (
        <div className="mine-articles">
          {likes.length === 0 && <Notice>还没有点赞过文章。</Notice>}
          {likes.map((article) => (
            <ArticleRow key={article.id} article={article} fallbackId={article.id} onOpen={navigate} />
          ))}
        </div>
      )}
    </section>
  );
}

function ArticleRow({
  article,
  fallbackId,
  onOpen
}: {
  article?: ArticleSummary;
  fallbackId: number;
  onOpen: (path: string) => void;
}) {
  return (
    <article className="mine-article-card social-article-row">
      <div className="mine-article-main">
        <span className="mine-article-status">文章 #{fallbackId}</span>
        <h3>{article?.title ?? "文章信息暂不可用"}</h3>
        <div>{article?.summary ?? "依赖内容服务返回文章骨架数据。"}</div>
      </div>
      <ActionButton variant="ghost" onClick={() => onOpen(`/app/modules/content/${fallbackId}`)}>
        查看
      </ActionButton>
    </article>
  );
}
