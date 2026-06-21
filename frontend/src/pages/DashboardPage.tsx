import { useNavigate } from "react-router-dom";
import { ActionButton } from "../components/ActionButton";
import { ModuleCard } from "../components/ModuleCard";
import { useAuth } from "../features/auth/AuthContext";

const roadmap = [
  { title: "AI 助手", eyebrow: "AI", description: "Redis 十轮记忆、ES RAG 知识库和管理员检索调试页已经接通。", status: "ready" as const, path: "/app/modules/ai-agent" },
  { title: "内容广场", eyebrow: "Content", description: "帖子、图文发布、审核结果反馈与创作者工作台。", status: "ready" as const, path: "/app/modules/content" },
  { title: "游戏资产", eyebrow: "Game", description: "账号绑定、图鉴背包、签到奖励与商城异步发货。", status: "ready" as const, path: "/app/modules/game-account" },
  { title: "社交关系", eyebrow: "Social", description: "关注、粉丝、浏览历史、点赞文章与评论互动。", status: "ready" as const, path: "/app/modules/social" },
  { title: "商城中心", eyebrow: "Shop", description: "Redis + Lua 预扣库存、异步落库订单与支付扣款。", status: "ready" as const, path: "/app/modules/shop" }
];

export function DashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();

  return (
    <section className="page-view">
      <div className="page-heading">
        <p className="eyebrow">控制台</p>
        <h1>欢迎回来，{user?.username}</h1>
        <span>当前用户模块已经可用，后续模块会沿着这里继续接入。</span>
      </div>

      <div className="stat-row">
        <article>
          <span>账号 ID</span>
          <strong>{user?.accountId}</strong>
        </article>
        <article>
          <span>游戏账号</span>
          <strong>{user?.gameAccount || "未绑定"}</strong>
        </article>
        <article>
          <span>关注 / 粉丝</span>
          <strong>{user?.followCount ?? 0} / {user?.fansCount ?? 0}</strong>
        </article>
      </div>

      <div className="feature-strip">
        <div>
          <h2>现在连 AI 知识库也接进来了</h2>
          <p>除了资料、内容、社交、商城和游戏资产，现在还可以在同一个前端里做站内知识问答、知识文档上传、ES 检索调试和会话记忆验证。</p>
        </div>
        <ActionButton onClick={() => navigate("/app/modules/ai-agent")}>进入 AI 助手</ActionButton>
      </div>

      <div className="module-grid module-grid--compact">
        {roadmap.map((item) => (
          <ModuleCard key={item.title} {...item} onOpen={() => navigate(item.path)} />
        ))}
      </div>
    </section>
  );
}
