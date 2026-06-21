import { ChangeEvent, FormEvent, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { userApi } from "../api/user";
import { ActionButton } from "../components/ActionButton";
import { AvatarImage } from "../components/AvatarImage";
import { Notice } from "../components/Notice";
import { useAuth } from "../features/auth/AuthContext";
import { tokenStore } from "../api/client";
import { friendlyError } from "../utils/errors";
import { compressAvatar } from "../utils/image";

export function ProfilePage() {
  const { user, refreshMe } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [signature, setSignature] = useState("");
  const [phone, setPhone] = useState("");
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [avatarPreview, setAvatarPreview] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [avatarBusy, setAvatarBusy] = useState(false);
  const [passwordBusy, setPasswordBusy] = useState(false);
  const [notice, setNotice] = useState<{ type: "success" | "error" | "info"; text: string } | null>(null);
  const [passwordNotice, setPasswordNotice] = useState<{ type: "success" | "error" | "info"; text: string } | null>(null);
  const auditing = user?.auditStatus === 1;

  useEffect(() => {
    if (!user) {
      return;
    }
    setUsername(user.username ?? "");
    setSignature(user.signature ?? "");
    setPhone(user.phone ?? "");
    setAvatarPreview(user.pendingAvatarUrl ?? user.avatar ?? null);
  }, [user]);

  useEffect(() => {
    if (!auditing) {
      return;
    }
    const timer = window.setInterval(() => {
      void refreshMe();
    }, 4000);
    return () => window.clearInterval(timer);
  }, [auditing, refreshMe]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const nextUsername = username.trim();
    const nextSignature = signature.trim();
    if (nextUsername.length < 2) {
      setNotice({ type: "error", text: "昵称至少需要 2 个字符。" });
      return;
    }
    setBusy(true);
    setNotice(null);
    try {
      await userApi.updateInfo({
        version: user?.version ?? 0,
        username: nextUsername,
        signature: nextSignature,
        phone: phone.trim()
      });
      await refreshMe();
      setNotice({ type: "success", text: "资料已提交审核，昵称和签名会在审核通过后一起生效。" });
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusy(false);
    }
  }

  async function handleAvatarChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    setAvatarBusy(true);
    setNotice({ type: "info", text: "正在压缩头像并上传审核..." });
    try {
      const compressed = await compressAvatar(file);
      const url = await userApi.uploadAvatar(compressed);
      setAvatarPreview(url);
      await refreshMe();
      setNotice({ type: "info", text: "头像已上传并进入审核，页面会在审核完成后自动刷新。" });
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setAvatarBusy(false);
    }
  }

  async function handleChangePassword(event: FormEvent) {
    event.preventDefault();
    if (!oldPassword || !newPassword || !confirmPassword) {
      setPasswordNotice({ type: "error", text: "请完整填写原密码、新密码和确认密码。" });
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordNotice({ type: "error", text: "两次输入的新密码不一致。" });
      return;
    }
    setPasswordBusy(true);
    setPasswordNotice(null);
    try {
      await userApi.changePassword({ oldPassword, newPassword });
      tokenStore.clear();
      setOldPassword("");
      setNewPassword("");
      setConfirmPassword("");
      navigate("/login", {
        replace: true,
        state: { from: "/app/profile" }
      });
    } catch (error) {
      setPasswordNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setPasswordBusy(false);
    }
  }

  return (
    <section className="page-view">
      <div className="page-heading">
        <p className="eyebrow">我的资料</p>
        <h1>让别人一眼知道你是谁。</h1>
        <span>资料修改会经过后端 AI 审核，头像也会走多模态审核链路。</span>
      </div>

      <div className="profile-layout">
        <aside className="profile-card">
          <AvatarImage src={avatarPreview} name={username} alt="用户头像" className="avatar avatar--large" />
          <strong>{username || "未命名玩家"}</strong>
          <span>ID {user?.accountId}</span>
          <label className="upload-zone">
            {auditing ? "审核中" : avatarBusy ? "头像处理中..." : "更换头像"}
            <input type="file" accept="image/*" onChange={handleAvatarChange} disabled={avatarBusy || auditing} />
          </label>
          <p className="upload-hint">支持 JPG/PNG/WEBP，原图不超过 2MB，上传前会压缩为 WebP。</p>
        </aside>

        <div className="form-stack">
          <form className="form-card" onSubmit={handleSubmit}>
            {notice && <Notice type={notice.type}>{notice.text}</Notice>}
            {auditing && <Notice type="info">资料或头像正在审核中，暂时不能再次修改。</Notice>}
            <label>
              昵称
              <input value={username} onChange={(event) => setUsername(event.target.value)} maxLength={32} />
              <small>{username.length}/32</small>
            </label>
            <label>
              个性签名
              <textarea value={signature} onChange={(event) => setSignature(event.target.value)} maxLength={120} />
              <small>{signature.length}/120</small>
            </label>
            <label>
              手机号
              <input value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="留空则不修改手机号" />
            </label>
            <div className="profile-note-grid">
              <article>
                <strong>游戏账号绑定已独立出来</strong>
                <p>为了保证商城发货、签到、背包和换绑逻辑一致，游戏账号现在不再和用户资料一起提交审核。</p>
              </article>
              <ActionButton type="button" variant="ghost" onClick={() => navigate("/app/modules/game-account")}>
                去管理游戏账号
              </ActionButton>
            </div>
            <ActionButton type="submit" busy={busy} disabled={auditing}>保存资料</ActionButton>
          </form>

          <form className="form-card" onSubmit={handleChangePassword}>
            <p className="eyebrow">账号安全</p>
            <h2>修改密码后将立即下线。</h2>
            <span>为了保护账号安全，修改成功后当前登录态和 refresh cookie 会立刻失效，你需要使用新密码重新登录。</span>
            {passwordNotice && <Notice type={passwordNotice.type}>{passwordNotice.text}</Notice>}
            <label>
              原密码
              <input type="password" value={oldPassword} onChange={(event) => setOldPassword(event.target.value)} />
            </label>
            <label>
              新密码
              <input type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} />
            </label>
            <label>
              确认新密码
              <input
                type="password"
                value={confirmPassword}
                onChange={(event) => setConfirmPassword(event.target.value)}
              />
            </label>
            <ActionButton type="submit" busy={passwordBusy}>更新密码</ActionButton>
          </form>
        </div>
      </div>
    </section>
  );
}
