import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { ArticleSummary, contentApi } from "../api/content";
import { ActionButton } from "../components/ActionButton";
import { Notice } from "../components/Notice";
import { friendlyError } from "../utils/errors";

function statusLabel(status: number) {
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
      return "未知";
  }
}

export function ContentMinePage() {
  const navigate = useNavigate();
  const [articles, setArticles] = useState<ArticleSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [notice, setNotice] = useState<{ type: "success" | "error" | "info"; text: string } | null>(null);

  useEffect(() => {
    void loadMine();
  }, []);

  async function loadMine() {
    setLoading(true);
    try {
      const result = await contentApi.getMyArticles();
      setArticles(result);
    } catch (err) {
      setNotice({ type: "error", text: friendlyError(err) });
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(articleId: number) {
    setBusyId(articleId);
    setNotice(null);
    try {
      await contentApi.deleteArticle(articleId);
      setArticles((current) => current.filter((item) => item.id !== articleId));
      setNotice({ type: "success", text: "文章已删除。" });
    } catch (err) {
      setNotice({ type: "error", text: friendlyError(err) });
    } finally {
      setBusyId(null);
    }
  }

  return (
    <section className="page-view">
      <div className="page-heading compact-heading">
        <p className="eyebrow">我的文章</p>
        <h1>草稿、审核中、驳回原因都集中在这里。</h1>
        <span>你可以继续编辑草稿和被驳回的内容，也可以直接删除不需要的文章。</span>
      </div>

      <div className="content-detail-actions">
        <ActionButton variant="ghost" onClick={() => navigate("/app/modules/content")}>
          返回广场
        </ActionButton>
        <ActionButton onClick={() => navigate("/app/modules/content/new")}>写新内容</ActionButton>
      </div>

      {notice && <Notice type={notice.type}>{notice.text}</Notice>}
      {loading && <Notice>正在加载你的文章...</Notice>}
      {!loading && articles.length === 0 && <Notice type="info">你还没有创建任何文章，先写一篇试试。</Notice>}

      <div className="mine-articles">
        {articles.map((article) => (
          <article key={article.id} className="mine-article-card">
            <div className="mine-article-main">
              <p className="mine-article-status">{statusLabel(article.status)}</p>
              <h3>{article.title}</h3>
              <div>{article.summary || "暂时没有摘要。"} </div>
              {article.auditMessage && article.status !== 1 && (
                <Notice type={article.status === 4 ? "warning" : "info"}>{article.auditMessage}</Notice>
              )}
            </div>
            <div className="mine-article-actions">
              <ActionButton variant="ghost" onClick={() => navigate(`/app/modules/content/${article.id}`)}>
                查看
              </ActionButton>
              <ActionButton variant="soft" onClick={() => navigate(`/app/modules/content/${article.id}/edit`)}>
                编辑
              </ActionButton>
              <ActionButton
                variant="danger"
                busy={busyId === article.id}
                onClick={() => void handleDelete(article.id)}
              >
                删除
              </ActionButton>
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}

