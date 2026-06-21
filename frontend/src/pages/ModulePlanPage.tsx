import { useParams } from "react-router-dom";
import { Notice } from "../components/Notice";

const moduleCopy: Record<string, { title: string; steps: string[] }> = {
  content: {
    title: "内容广场",
    steps: ["内容广场已经接入", "文章编辑器已可用", "审核反馈已展示", "后续继续补评论与推荐流"]
  },
  social: {
    title: "社交关系",
    steps: ["关注/取关", "粉丝列表", "私信入口", "组队邀请与通知"]
  },
  shop: {
    title: "商城中心",
    steps: ["商品列表", "订单流程", "库存与权益", "活动运营位"]
  }
};

export function ModulePlanPage() {
  const { moduleId = "content" } = useParams();
  const module = moduleCopy[moduleId] ?? {
    title: "未来模块",
    steps: ["确认 PRD", "设计接口", "补页面状态", "接入网关与统一异常"]
  };

  return (
    <section className="page-view">
      <div className="page-heading">
        <p className="eyebrow">预留规划</p>
        <h1>{module.title}</h1>
        <span>这里先占好产品位置，避免后续功能长出来时破坏整体结构。</span>
      </div>
      <Notice>{moduleId === "content" ? "内容模块已经迁到真实路由，请从侧边栏的“内容广场”进入。" : "该模块尚未接入后端，当前页面用于产品导航、权限体系和视觉结构预演。"}</Notice>
      <div className="plan-board">
        {module.steps.map((step, index) => (
          <article key={step}>
            <span>0{index + 1}</span>
            <h3>{step}</h3>
            <p>沿用当前 API 客户端、按钮防抖、路由守卫和网关异常感知能力。</p>
          </article>
        ))}
      </div>
    </section>
  );
}
