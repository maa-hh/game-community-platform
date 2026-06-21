import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { ActionButton } from "../components/ActionButton";
import { AvatarImage } from "../components/AvatarImage";
import { useAuth } from "../features/auth/AuthContext";
import { useNotification } from "../features/notification/NotificationContext";
import { useSafeNavigate } from "../hooks/useSafeNavigate";

const baseNavItems = [
  { to: "/app/dashboard", label: "控制台" },
  { to: "/app/profile", label: "我的资料" },
  { to: "/app/users", label: "用户查询" },
  { to: "/app/modules/ai-agent", label: "AI 助手" },
  { to: "/app/modules/content", label: "内容广场" },
  { to: "/app/modules/game-account", label: "游戏资产" },
  { to: "/app/modules/social", label: "社交关系" },
  { to: "/app/modules/shop", label: "商城" }
];

export function AppShell() {
  const { user, logout } = useAuth();
  const { summary, toasts, dismissToast, buildNotificationPath } = useNotification();
  const navigate = useNavigate();
  const safeNavigate = useSafeNavigate();
  const navItems = user?.type === 1
    ? [...baseNavItems, { to: "/app/audit/reports", label: "审核中心" }]
    : baseNavItems;

  return (
    <div className="app-shell">
      <aside className="side-nav">
        <button className="brand-mark" onClick={() => void safeNavigate.go("/app/dashboard")}>
          <span>GC</span>
          <strong>Game Community</strong>
        </button>
        <nav>
          {navItems.map((item) => (
            <NavLink key={item.to} to={item.to}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <button className="notification-bell" type="button" onClick={() => navigate("/app/notifications")}>
          <span>通知中心</span>
          {summary.unreadNotificationCount > 0 && <strong>{summary.unreadNotificationCount > 99 ? "99+" : summary.unreadNotificationCount}</strong>}
        </button>
        <button className="side-user" type="button" onClick={() => user && navigate(`/app/users/id/${user.id}`)}>
          <AvatarImage src={user?.avatar} name={user?.username} />
          <div>
            <strong>{user?.username}</strong>
            <span>ID {user?.accountId}</span>
          </div>
        </button>
        <ActionButton
          variant="ghost"
          onClick={async () => {
            await logout();
            navigate("/");
          }}
        >
          退出登录
        </ActionButton>
      </aside>
      <main className="content-stage">
        <Outlet />
        {toasts.length > 0 && (
          <div className="notification-toast-stack">
            {toasts.map((toast) => {
              const path = buildNotificationPath(toast);
              return (
                <button
                  key={toast.toastId}
                  type="button"
                  className="notification-toast"
                  onClick={() => {
                    dismissToast(toast.toastId);
                    if (path) {
                      navigate(path);
                    } else {
                      navigate("/app/notifications");
                    }
                  }}
                >
                  <AvatarImage src={toast.actorAvatar} name={toast.actorUsername} className="avatar" />
                  <div>
                    <strong>{toast.previewText || "你有一条新通知"}</strong>
                    {toast.resultText && <span>{toast.resultText}</span>}
                  </div>
                </button>
              );
            })}
          </div>
        )}
      </main>
    </div>
  );
}
