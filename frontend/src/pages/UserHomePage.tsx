import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { ArticleSummary, ContentCategory, contentApi } from "../api/content";
import { ArticleStats, FollowUser, socialApi } from "../api/social";
import { UserVO, userApi } from "../api/user";
import { ActionButton } from "../components/ActionButton";
import { ArticleCard } from "../components/ArticleCard";
import { AvatarImage } from "../components/AvatarImage";
import { Notice } from "../components/Notice";
import { friendlyError } from "../utils/errors";

export function UserHomePage() {
  const { id, primaryId } = useParams();
  const navigate = useNavigate();
  const [user, setUser] = useState<UserVO | null>(null);
  const [following, setFollowing] = useState(false);
  const [blacked, setBlacked] = useState(false);
  const [relationCount, setRelationCount] = useState({ following: 0, fans: 0 });
  const [relationTab, setRelationTab] = useState<"following" | "fans">("following");
  const [relationUsers, setRelationUsers] = useState<FollowUser[]>([]);
  const [articles, setArticles] = useState<ArticleSummary[]>([]);
  const [categories, setCategories] = useState<ContentCategory[]>([]);
  const [articleStats, setArticleStats] = useState<Record<number, ArticleStats>>({});
  const [error, setError] = useState("");

  useEffect(() => {
    if (!id && !primaryId) {
      return;
    }
    void loadUser();
  }, [id, primaryId]);

  useEffect(() => {
    if (user) {
      void loadRelationList(relationTab, user.id);
    }
  }, [relationTab, user?.id]);

  async function loadUser() {
    setError("");
    try {
      const nextUser = primaryId
        ? (await userApi.getUsersByIds([Number(primaryId)]))[0]
        : await userApi.getUser(Number(id));
      setUser(nextUser);
      if (nextUser) {
        const [isFollowing, isBlacked, count] = await Promise.all([
          socialApi.checkFollowing(nextUser.id),
          socialApi.checkBlack(nextUser.id),
          socialApi.countRelation(nextUser.id)
        ]);
        setFollowing(isFollowing);
        setBlacked(isBlacked);
        setRelationCount(count);
        await loadRelationList(relationTab, nextUser.id);
        const [authorArticles, nextCategories] = await Promise.all([
          contentApi.listAuthorPublished(nextUser.id, 30),
          contentApi.listCategories().catch(() => [])
        ]);
        setCategories(nextCategories);
        setArticles(authorArticles);
        await enrichArticles(authorArticles);
      }
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function enrichArticles(nextArticles: ArticleSummary[]) {
    if (nextArticles.length === 0) {
      return;
    }
    const nextStats = await socialApi.getArticleStatsBatch([...new Set(nextArticles.map((article) => article.id))]);
    setArticleStats((current) => ({
      ...current,
      ...Object.fromEntries(nextStats.map((item) => [item.articleId, item]))
    }));
  }

  async function toggleArticleLike(articleId: number) {
    const current = articleStats[articleId];
    try {
      if (current?.liked) {
        await socialApi.unlikeArticle(articleId);
      } else {
        await socialApi.likeArticle(articleId);
      }
      const [nextStats] = await socialApi.getArticleStatsBatch([articleId]);
      if (nextStats) {
        setArticleStats((value) => ({ ...value, [articleId]: nextStats }));
      }
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function loadRelationList(tab: "following" | "fans", userId: number) {
    const result = tab === "following" ? await socialApi.listFollowing(userId) : await socialApi.listFans(userId);
    setRelationUsers(result.data ?? []);
  }

  async function toggleFollow() {
    if (!user) {
      return;
    }
    try {
      if (following) {
        await socialApi.unfollow(user.id);
      } else {
        await socialApi.follow(user.id);
      }
      await loadUser();
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  async function toggleBlack() {
    if (!user) {
      return;
    }
    try {
      if (blacked) {
        await socialApi.unblack(user.id);
      } else {
        await socialApi.black(user.id);
      }
      await loadUser();
    } catch (err) {
      setError(friendlyError(err));
    }
  }

  if (error) {
    return (
      <section className="page-view">
        <Notice type="error">{error}</Notice>
      </section>
    );
  }

  if (!user) {
    return <div className="page-loader">正在进入玩家主页...</div>;
  }

  return (
    <section className="page-view user-home">
      <div className="user-home-hero">
        <AvatarImage src={user.avatar} name={user.username} className="home-avatar" />
        <div>
          <p className="eyebrow">玩家主页</p>
          <h1>{user.username}</h1>
          <span>#{user.accountId} · {user.gameAccount || "未绑定游戏账号"}</span>
          <p>{user.signature || "这个玩家暂时没有签名。"}</p>
          <div className="home-actions">
            <ActionButton variant={following ? "soft" : "ghost"} onClick={toggleFollow}>
              {following ? "已关注" : "关注"}
            </ActionButton>
            <ActionButton variant={blacked ? "soft" : "ghost"} onClick={toggleBlack}>
              {blacked ? "移出黑名单" : "拉黑"}
            </ActionButton>
            <ActionButton variant="ghost" onClick={() => navigate("/app/users")}>返回发现</ActionButton>
          </div>
        </div>
      </div>

      <div className="profile-note-grid">
        <article>
          <span>关注</span>
          <strong>{relationCount.following}</strong>
        </article>
        <article>
          <span>粉丝</span>
          <strong>{relationCount.fans}</strong>
        </article>
        <article>
          <span>关系状态</span>
          <strong>{following ? "已关注" : "未关注"}</strong>
        </article>
      </div>

      <section className="user-home-section">
        <div className="section-title-row">
          <div>
            <p className="eyebrow">Published</p>
            <h2>TA 发布的文章</h2>
          </div>
          <span>按发布时间倒序</span>
        </div>
        <div className="content-grid">
          {articles.length === 0 && <Notice>TA 还没有已发布文章。</Notice>}
          {articles.map((article) => (
            <ArticleCard
              key={article.id}
              article={article}
              author={user}
              categoryName={categories.find((item) => item.id === article.categoryId)?.name ?? `分类 #${article.categoryId}`}
              stats={articleStats[article.id]}
              onOpen={(articleId) => navigate(`/app/modules/content/${articleId}`)}
              onAuthorOpen={() => undefined}
              onToggleLike={(articleId) => void toggleArticleLike(articleId)}
            />
          ))}
        </div>
      </section>

      <div className="content-filters">
        <button className={relationTab === "following" ? "active" : ""} type="button" onClick={() => setRelationTab("following")}>
          TA 关注的人
        </button>
        <button className={relationTab === "fans" ? "active" : ""} type="button" onClick={() => setRelationTab("fans")}>
          TA 的粉丝
        </button>
      </div>

      <div className="social-grid">
        {relationUsers.length === 0 && <Notice>这里暂时还没有关系数据。</Notice>}
        {relationUsers.map((item) => (
          <article key={item.userId} className="social-user-card">
            <AvatarImage className="avatar" src={item.avatar} name={item.username} />
            <div>
              <h3>{item.username}</h3>
              <p>{item.signature || "这个玩家还没有留下签名。"}</p>
            </div>
            <ActionButton variant="ghost" onClick={() => navigate(`/app/users/id/${item.userId}`)}>
              看主页
            </ActionButton>
          </article>
        ))}
      </div>
    </section>
  );
}
