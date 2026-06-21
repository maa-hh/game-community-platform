import { FormEvent, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { userApi } from "../api/user";
import { ActionButton } from "../components/ActionButton";
import { Notice } from "../components/Notice";
import { useAuth } from "../features/auth/AuthContext";
import { friendlyError } from "../utils/errors";

type AuthMode = "login" | "register";

export function AuthPage() {
  const [mode, setMode] = useState<AuthMode>("login");
  const [accountId, setAccountId] = useState("");
  const [password, setPassword] = useState("");
  const [username, setUsername] = useState("");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [notice, setNotice] = useState<{ type: "info" | "success" | "warning" | "error"; text: string } | null>(null);
  const [busy, setBusy] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const { applyLogin } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = (location.state as { from?: string } | null)?.from ?? "/app/dashboard";

  async function handleSendCode() {
    if (!phone) {
      setNotice({ type: "warning", text: "先填手机号，我再帮你发验证码。" });
      return;
    }
    try {
      const sentCode = await userApi.sendCode(phone);
      setCode(sentCode);
      setCountdown(45);
      const timer = window.setInterval(() => {
        setCountdown((current) => {
          if (current <= 1) {
            window.clearInterval(timer);
            return 0;
          }
          return current - 1;
        });
      }, 1000);
      setNotice({ type: "success", text: `验证码已发送。开发阶段已自动填入：${sentCode}` });
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    }
  }

  async function handleLogin(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setNotice(null);
    try {
      const login = await userApi.loginByAccount({ accountId: Number(accountId), password });
      await applyLogin(login);
      navigate(redirectTo, { replace: true });
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusy(false);
    }
  }

  async function handleRegister(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setNotice(null);
    try {
      const newAccountId = await userApi.registerByPhone({ username, password, phone, code });
      setAccountId(String(newAccountId));
      setMode("login");
      setNotice({ type: "success", text: `注册成功，你的账号 ID 是 ${newAccountId}。登录后去“游戏资产”模块绑定游戏账号。` });
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-panel">
        <button className="mini-brand" onClick={() => navigate("/")}>回到首页</button>
        <h1>{mode === "login" ? "登录" : "注册"}</h1>
        <div className="auth-switch">
          <button className={mode === "login" ? "active" : ""} onClick={() => setMode("login")}>账号登录</button>
          <button className={mode === "register" ? "active" : ""} onClick={() => setMode("register")}>手机号注册</button>
        </div>

        {notice && <Notice type={notice.type}>{notice.text}</Notice>}

        {mode === "login" ? (
          <form className="form-stack" onSubmit={handleLogin}>
            <label>
              账号 ID
              <input value={accountId} onChange={(event) => setAccountId(event.target.value)} placeholder="注册后获得的数字 ID" />
            </label>
            <label>
              密码
              <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="请输入密码" />
            </label>
            <ActionButton type="submit" busy={busy}>进入社区</ActionButton>
          </form>
        ) : (
          <form className="form-stack" onSubmit={handleRegister}>
            <label>
              昵称
              <input value={username} onChange={(event) => setUsername(event.target.value)} placeholder="例如：峡谷夜巡者" />
            </label>
            <label>
              密码
              <input type="password" value={password} onChange={(event) => setPassword(event.target.value)} placeholder="6-32 位密码" />
            </label>
            <label>
              手机号
              <input value={phone} onChange={(event) => setPhone(event.target.value)} placeholder="中国大陆手机号" />
            </label>
            <div className="inline-field">
              <label>
                验证码
                <input value={code} onChange={(event) => setCode(event.target.value)} placeholder="6 位验证码" />
              </label>
              <ActionButton type="button" variant="soft" disabled={countdown > 0} onClick={handleSendCode}>
                {countdown > 0 ? `${countdown}s` : "发送验证码"}
              </ActionButton>
            </div>
            <ActionButton type="submit" busy={busy}>创建账号</ActionButton>
          </form>
        )}
      </section>
    </main>
  );
}
