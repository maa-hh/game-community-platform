import { ActionButton } from "./ActionButton";

type ModuleCardProps = {
  title: string;
  eyebrow: string;
  description: string;
  status: "ready" | "planned";
  onOpen?: () => void;
};

export function ModuleCard({ title, eyebrow, description, status, onOpen }: ModuleCardProps) {
  return (
    <article className={`module-card module-card--${status}`}>
      <p>{eyebrow}</p>
      <h3>{title}</h3>
      <span>{status === "ready" ? "已接入" : "预留模块"}</span>
      <div>{description}</div>
      <ActionButton variant={status === "ready" ? "primary" : "soft"} onClick={onOpen}>
        {status === "ready" ? "进入模块" : "查看规划"}
      </ActionButton>
    </article>
  );
}
