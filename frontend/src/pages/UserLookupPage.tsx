import { CSSProperties, FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { UserSimpleVO, userApi } from "../api/user";
import { ActionButton } from "../components/ActionButton";
import { AvatarImage } from "../components/AvatarImage";
import { Notice } from "../components/Notice";
import { friendlyError } from "../utils/errors";

type SearchMode = "id" | "name";

export function UserLookupPage() {
  const [mode, setMode] = useState<SearchMode>("name");
  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState<UserSimpleVO[]>([]);
  const [total, setTotal] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  async function handleSearch(event: FormEvent) {
    event.preventDefault();
    if (!keyword.trim()) {
      setError(mode === "id" ? "请输入账号 ID。" : "请输入昵称前缀。");
      return;
    }
    setBusy(true);
    setError("");
    setResults([]);
    try {
      if (mode === "id") {
        const user = await userApi.getSimpleUser(Number(keyword));
        setResults([user]);
        setTotal(1);
      } else {
        const page = await userApi.searchUsers({ username: keyword.trim(), size: 18 });
        setResults(page.records);
        setTotal(page.total);
      }
    } catch (err) {
      setError(friendlyError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="page-view page-view--feed">
      <div className="page-heading compact-heading">
        <p className="eyebrow">发现玩家</p>
        <h1>像刷笔记一样找到玩家。</h1>
        <span>支持账号 ID 精确查询，也支持昵称前缀搜索；点击卡片会进入用户主页，后续可接关注、私信和社交动态。</span>
      </div>

      <form className="lookup-card lookup-card--sticky" onSubmit={handleSearch}>
        <div className="segmented">
          <button type="button" className={mode === "name" ? "active" : ""} onClick={() => setMode("name")}>昵称前缀</button>
          <button type="button" className={mode === "id" ? "active" : ""} onClick={() => setMode("id")}>账号 ID</button>
        </div>
        <label>
          {mode === "id" ? "账号 ID" : "昵称前缀"}
          <input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder={mode === "id" ? "例如：6" : "例如：gateway"}
          />
        </label>
        <ActionButton type="submit" busy={busy}>搜索</ActionButton>
      </form>

      {error && <Notice type="error">{error}</Notice>}
      {!error && total > 0 && <p className="feed-count">找到 {total} 位玩家</p>}

      <div className="user-masonry">
        {results.map((user, index) => (
          <button
            className="user-note-card"
            key={user.id}
            style={{ "--card-shift": `${index % 4}` } as CSSProperties}
            onClick={() => navigate(`/app/users/${user.accountId}`)}
          >
            <AvatarImage src={user.avatar} name={user.username} className="note-avatar" />
            <div className="note-body">
              <strong>{user.username}</strong>
              <span>#{user.accountId}</span>
              <p>{user.signature || "这个玩家还没写签名，但主页已经给他留好了。"}</p>
              <em>{user.gameAccount || "未绑定游戏账号"}</em>
            </div>
          </button>
        ))}
      </div>
    </section>
  );
}
