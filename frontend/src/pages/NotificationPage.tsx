import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { NotificationMessage, notificationApi } from "../api/notification";
import { ActionButton } from "../components/ActionButton";
import { AvatarImage } from "../components/AvatarImage";
import { Notice } from "../components/Notice";
import { useNotification } from "../features/notification/NotificationContext";
import { friendlyError } from "../utils/errors";

function formatTime(value?: string) {
  if (!value) {
    return "刚刚";
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

export function NotificationPage() {
  const navigate = useNavigate();
  const { markAllAsRead, buildNotificationPath } = useNotification();
  const [messages, setMessages] = useState<NotificationMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    void loadMessages();
  }, []);

  async function loadMessages() {
    setLoading(true);
    setError(null);
    try {
      await markAllAsRead();
      const page = await notificationApi.listMessages(1, 50);
      setMessages((page.data ?? []).map((item) => ({ ...item, readStatus: 1 })));
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setLoading(false);
    }
  }

  function openMessage(message: NotificationMessage) {
    const path = buildNotificationPath(message);
    if (!path) {
      return;
    }
    navigate(path);
  }

  return (
    <section className="page-view notification-page">
      <div className="page-heading compact-heading">
        <p className="eyebrow">通知中心</p>
        <h1>互动、关注、举报反馈都会留在这里。</h1>
        <span>进入页面后会自动清空普通通知未读，关注流的新内容红点仍只显示在内容广场。</span>
      </div>

      <div className="notification-toolbar">
        <ActionButton variant="ghost" onClick={() => navigate("/app/modules/content")}>
          返回广场
        </ActionButton>
        <ActionButton variant="soft" onClick={() => void loadMessages()}>
          刷新通知
        </ActionButton>
      </div>

      {error && <Notice type="error">{error}</Notice>}
      {loading && <Notice>正在加载通知列表...</Notice>}
      {!loading && messages.length === 0 && <Notice type="info">目前还没有新的通知，去广场互动一下试试。</Notice>}

      <div className="notification-list">
        {messages.map((message) => {
          const clickable = Boolean(buildNotificationPath(message));
          return (
            <article
              key={message.id}
              className={`notification-card ${clickable ? "clickable" : ""}`}
              onClick={() => clickable && openMessage(message)}
              role={clickable ? "button" : undefined}
              tabIndex={clickable ? 0 : -1}
              onKeyDown={(event) => {
                if (clickable && (event.key === "Enter" || event.key === " ")) {
                  event.preventDefault();
                  openMessage(message);
                }
              }}
            >
              <AvatarImage src={message.actorAvatar} name={message.actorUsername} className="avatar" />
              <div className="notification-card-content">
                <div className="notification-card-header">
                  <strong>{message.previewText || "系统通知"}</strong>
                  <time>{formatTime(message.createTime)}</time>
                </div>
                {message.resultText && <p>{message.resultText}</p>}
                {!message.resultText && message.actorUsername && <p>{message.actorUsername}</p>}
              </div>
            </article>
          );
        })}
      </div>
    </section>
  );
}
