import { useNavigate } from "react-router-dom";
import { ActionButton } from "../components/ActionButton";
import { ModuleCard } from "../components/ModuleCard";
import { useAuth } from "../features/auth/AuthContext";

const modules = [
  {
    title: "用户系统",
    eyebrow: "Identity",
    description: "账号密码登录、手机号验证码注册、JWT 网关透传、资料维护已经接入。",
    status: "ready" as const
  },
  {
    title: "AI 助手",
    eyebrow: "AI",
    description: "Redis 多轮记忆、ES RAG 知识库和管理员调试面板已经接入。",
    status: "ready" as const
  },
  {
    title: "内容广场",
    eyebrow: "Content",
    description: "帖子、图文、审核流、推荐入口和搜索链路已经接好，社区主舞台可直接使用。",
    status: "ready" as const
  },
  {
    title: "社交关系",
    eyebrow: "Social",
    description: "关注、粉丝、互动通知、举报处理和 feed 流已经接入。",
    status: "ready" as const
  },
  {
    title: "游戏资产",
    eyebrow: "Game",
    description: "游戏账号绑定、图鉴背包、签到奖励和商城异步发货都可直接体验。",
    status: "ready" as const
  }
];

export function HomePage() {
  const navigate = useNavigate();
  const { isAuthenticated } = useAuth();

  return (
    <main className="landing">
      <section className="hero">
        <div className="hero-copy">
          <p className="eyebrow">玩家社区中枢</p>
          <h1>把账号、内容、关系和游戏身份，装进一个顺滑的社区产品。</h1>
          <p className="hero-text">
            当前先完成用户与网关链路，页面结构已经为内容、社交、商城、推荐等模块留好位置，后续像拼地图一样继续扩展。
          </p>
          <div className="hero-actions">
            <ActionButton onClick={() => navigate(isAuthenticated ? "/app/dashboard" : "/auth")}>
              {isAuthenticated ? "进入控制台" : "登录 / 注册"}
            </ActionButton>
            <ActionButton variant="ghost" onClick={() => navigate("/app/modules/content")}>
              查看产品规划
            </ActionButton>
          </div>
        </div>
        <div className="hero-orbit" aria-hidden="true">
          <div className="orbit-card orbit-card--one">JWT Gateway</div>
          <div className="orbit-card orbit-card--two">Sentinel Guard</div>
          <div className="orbit-core">PLAY</div>
        </div>
      </section>
      <section className="module-grid">
        {modules.map((module) => (
          <ModuleCard
            key={module.title}
            {...module}
            onOpen={() => navigate("/app/dashboard")}
          />
        ))}
      </section>
    </main>
  );
}
