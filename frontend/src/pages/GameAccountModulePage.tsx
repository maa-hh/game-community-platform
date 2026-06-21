import { FormEvent, useEffect, useMemo, useState } from "react";
import {
  CharacterDetail,
  CharacterResource,
  GameAccountProfile,
  GameCharacterEntity,
  GameItemEntity,
  GameSkinEntity,
  ItemDetail,
  ItemResource,
  OwnedCharacter,
  OwnedItem,
  OwnedSkin,
  SignInReward,
  SignInStatus,
  SkinDetail,
  SkinResource,
  gameAccountApi
} from "../api/gameAccount";
import { ActionButton } from "../components/ActionButton";
import { Notice } from "../components/Notice";
import { useAuth } from "../features/auth/AuthContext";
import { friendlyError } from "../utils/errors";

type MainTab = "overview" | "assets" | "catalog" | "signin" | "admin";
type ResourceTab = "characters" | "skins" | "items";
type AdminTab = ResourceTab | "rewards";

const rarityLabels: Record<number, string> = {
  1: "普通",
  2: "稀有",
  3: "史诗",
  4: "传说",
  5: "神话"
};

const rewardTypeLabels: Record<number, string> = {
  1: "角色",
  2: "皮肤",
  3: "道具",
  4: "金币",
  5: "钻石"
};

const itemTypeLabels: Record<number, string> = {
  1: "消耗",
  2: "功能",
  3: "体验券",
  4: "礼包"
};

function csvToList(text: string) {
  return text
    .split(/[,\n]/)
    .map((item) => item.trim())
    .filter(Boolean);
}

function listToCsv(items?: string[]) {
  return (items ?? []).join(", ");
}

function formatDate(value?: string) {
  if (!value) {
    return "暂无";
  }
  return value.replace("T", " ").slice(0, 16);
}

function rarityText(rarity?: number) {
  return rarity ? (rarityLabels[rarity] ?? `稀有度 ${rarity}`) : "未设置";
}

function itemTypeText(itemType?: number) {
  return itemType ? (itemTypeLabels[itemType] ?? `类型 ${itemType}`) : "未设置";
}

export function GameAccountModulePage() {
  const { user } = useAuth();
  const isAdmin = user?.type === 1;
  const [tab, setTab] = useState<MainTab>("overview");
  const [assetTab, setAssetTab] = useState<ResourceTab>("characters");
  const [catalogTab, setCatalogTab] = useState<ResourceTab>("characters");
  const [adminTab, setAdminTab] = useState<AdminTab>("characters");
  const [notice, setNotice] = useState<{ type: "success" | "error" | "info"; text: string } | null>(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [profile, setProfile] = useState<GameAccountProfile | null>(null);
  const [bindAccountNo, setBindAccountNo] = useState("");
  const [signStatus, setSignStatus] = useState<SignInStatus | null>(null);
  const [signRewards, setSignRewards] = useState<SignInReward[]>([]);
  const [ownedCharacters, setOwnedCharacters] = useState<OwnedCharacter[]>([]);
  const [ownedSkins, setOwnedSkins] = useState<OwnedSkin[]>([]);
  const [ownedItems, setOwnedItems] = useState<OwnedItem[]>([]);
  const [characterCatalog, setCharacterCatalog] = useState<CharacterResource[]>([]);
  const [skinCatalog, setSkinCatalog] = useState<SkinResource[]>([]);
  const [itemCatalog, setItemCatalog] = useState<ItemResource[]>([]);
  const [catalogKeyword, setCatalogKeyword] = useState("");
  const [selectedDetailTitle, setSelectedDetailTitle] = useState("选择一项查看详情");
  const [selectedDetailBody, setSelectedDetailBody] = useState("角色、皮肤和道具的长文案与图集详情都放在 Mongo 中，这里会实时读取。");
  const [adminCharacters, setAdminCharacters] = useState<GameCharacterEntity[]>([]);
  const [adminSkins, setAdminSkins] = useState<GameSkinEntity[]>([]);
  const [adminItems, setAdminItems] = useState<GameItemEntity[]>([]);
  const [adminRewards, setAdminRewards] = useState<SignInReward[]>([]);
  const [characterForm, setCharacterForm] = useState<GameCharacterEntity>({
    characterCode: "",
    name: "",
    title: "",
    icon: "",
    rarity: 1,
    elementType: 0,
    characterType: 0,
    status: 1,
    sortOrder: 0,
    version: 0
  });
  const [characterDetailForm, setCharacterDetailForm] = useState<CharacterDetail>({
    characterCode: "",
    story: "",
    background: "",
    skills: [],
    tags: []
  });
  const [skinForm, setSkinForm] = useState<GameSkinEntity>({
    skinCode: "",
    characterId: undefined,
    characterCode: "",
    name: "",
    icon: "",
    rarity: 1,
    status: 1,
    sortOrder: 0,
    version: 0
  });
  const [skinDetailForm, setSkinDetailForm] = useState<SkinDetail>({
    skinCode: "",
    description: "",
    theme: "",
    previewImages: [],
    tags: []
  });
  const [itemForm, setItemForm] = useState<GameItemEntity>({
    itemCode: "",
    name: "",
    itemType: 1,
    rarity: 1,
    icon: "",
    maxStackCount: 99,
    status: 1,
    sortOrder: 0,
    version: 0
  });
  const [itemDetailForm, setItemDetailForm] = useState<ItemDetail>({
    itemCode: "",
    description: "",
    usageTips: "",
    sources: [],
    tags: []
  });
  const [rewardForm, setRewardForm] = useState<SignInReward>({
    dayIndex: 1,
    rewardType: 4,
    businessCode: "",
    rewardCode: "gold",
    quantity: 100,
    rewardName: "金币 x100",
    status: 1
  });

  const currentOwnedCards = useMemo(() => {
    if (assetTab === "characters") {
      return ownedCharacters.map((item) => ({
        id: item.characterId,
        title: item.name,
        subtitle: `${rarityText(item.rarity)} · 等级 ${item.level}`,
        image: item.icon,
        meta: `获取于 ${formatDate(item.obtainTime)}`
      }));
    }
    if (assetTab === "skins") {
      return ownedSkins.map((item) => ({
        id: item.skinId,
        title: item.name,
        subtitle: `${rarityText(item.rarity)} · ${item.equipStatus === 1 ? "已装备" : "未装备"}`,
        image: item.icon,
        meta: `角色编码 ${item.characterCode || "未关联"}`
      }));
    }
    return ownedItems.map((item) => ({
      id: item.itemId,
      title: item.name,
      subtitle: `${itemTypeText(item.itemType)} · ${rarityText(item.rarity)}`,
      image: item.icon,
      meta: `当前数量 ${item.quantity}`
    }));
  }, [assetTab, ownedCharacters, ownedItems, ownedSkins]);

  const currentCatalogCards = useMemo(() => {
    if (catalogTab === "characters") {
      return characterCatalog.map((item) => ({
        id: item.characterId,
        code: item.characterCode,
        title: item.name,
        subtitle: `${item.title || "无称号"} · ${rarityText(item.rarity)}`,
        image: item.icon,
        meta: item.owned ? "已拥有" : "未拥有"
      }));
    }
    if (catalogTab === "skins") {
      return skinCatalog.map((item) => ({
        id: item.skinId,
        code: item.skinCode,
        title: item.name,
        subtitle: `${item.characterCode || "独立皮肤"} · ${rarityText(item.rarity)}`,
        image: item.icon,
        meta: item.owned ? "已拥有" : "未拥有"
      }));
    }
    return itemCatalog.map((item) => ({
      id: item.itemId,
      code: item.itemCode,
      title: item.name,
      subtitle: `${itemTypeText(item.itemType)} · ${rarityText(item.rarity)}`,
      image: item.icon,
      meta: item.owned ? `已拥有 ${item.quantity ?? 0}` : "未拥有"
    }));
  }, [catalogTab, characterCatalog, itemCatalog, skinCatalog]);

  useEffect(() => {
    void loadModule();
  }, []);

  useEffect(() => {
    if (catalogKeyword.trim()) {
      return;
    }
    void loadCatalog();
  }, [catalogTab]);

  useEffect(() => {
    if (!isAdmin) {
      return;
    }
    void loadAdminSection();
  }, [adminTab, isAdmin]);

  async function loadModule() {
    setLoading(true);
    try {
      const nextProfile = await gameAccountApi.current();
      const [nextRewards, characterPage, skinPage, itemPage] = await Promise.all([
        gameAccountApi.listSignInRewards(),
        gameAccountApi.listCharacterCatalog(),
        gameAccountApi.listSkinCatalog(),
        gameAccountApi.listItemCatalog()
      ]);
      setProfile(nextProfile);
      setBindAccountNo(nextProfile.accountNo ?? "");
      setSignRewards(nextRewards);
      setCharacterCatalog(characterPage.data ?? []);
      setSkinCatalog(skinPage.data ?? []);
      setItemCatalog(itemPage.data ?? []);
      if (nextProfile.bound) {
        const [nextStatus, ownedCharacterPage, ownedSkinPage, ownedItemPage] = await Promise.all([
          gameAccountApi.getSignInStatus(),
          gameAccountApi.listOwnedCharacters(),
          gameAccountApi.listOwnedSkins(),
          gameAccountApi.listOwnedItems()
        ]);
        setSignStatus(nextStatus);
        setOwnedCharacters(ownedCharacterPage.data ?? []);
        setOwnedSkins(ownedSkinPage.data ?? []);
        setOwnedItems(ownedItemPage.data ?? []);
      } else {
        setSignStatus(null);
        setOwnedCharacters([]);
        setOwnedSkins([]);
        setOwnedItems([]);
      }
      if (isAdmin) {
        await loadAdminSection();
      }
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setLoading(false);
    }
  }

  async function loadCatalog() {
    try {
      if (catalogTab === "characters") {
        const page = await gameAccountApi.listCharacterCatalog({ keyword: catalogKeyword.trim() || undefined });
        setCharacterCatalog(page.data ?? []);
        return;
      }
      if (catalogTab === "skins") {
        const page = await gameAccountApi.listSkinCatalog({ keyword: catalogKeyword.trim() || undefined });
        setSkinCatalog(page.data ?? []);
        return;
      }
      const page = await gameAccountApi.listItemCatalog({ keyword: catalogKeyword.trim() || undefined });
      setItemCatalog(page.data ?? []);
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    }
  }

  async function loadAdminSection() {
    try {
      if (adminTab === "characters") {
        const page = await gameAccountApi.pageCharacters({ size: 20 });
        setAdminCharacters(page.data ?? []);
        return;
      }
      if (adminTab === "skins") {
        const page = await gameAccountApi.pageSkins({ size: 20 });
        setAdminSkins(page.data ?? []);
        return;
      }
      if (adminTab === "items") {
        const page = await gameAccountApi.pageItems({ size: 20 });
        setAdminItems(page.data ?? []);
        return;
      }
      const rewards = await gameAccountApi.adminListSignInRewards();
      setAdminRewards(rewards);
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    }
  }

  async function handleBindSubmit(event: FormEvent) {
    event.preventDefault();
    if (!bindAccountNo.trim()) {
      setNotice({ type: "error", text: "先输入要绑定的游戏账号号。" });
      return;
    }
    setBusy(true);
    try {
      if (profile?.bound) {
        await gameAccountApi.rebind(bindAccountNo.trim());
        setNotice({ type: "success", text: "换绑完成，后续商城发货和签到都将走新的游戏账号。" });
      } else {
        await gameAccountApi.bind(bindAccountNo.trim());
        setNotice({ type: "success", text: "绑定完成，商城发货、背包和签到中心已经连到这个游戏账号。" });
      }
      await loadModule();
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusy(false);
    }
  }

  async function handleUnbind() {
    setBusy(true);
    try {
      await gameAccountApi.unbind();
      setNotice({ type: "info", text: "已解绑。未绑定状态下无法签到，也无法给游戏资产类商品发货。" });
      await loadModule();
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusy(false);
    }
  }

  async function handleSignIn() {
    setBusy(true);
    try {
      const result = await gameAccountApi.signIn();
      setNotice({
        type: "success",
        text: `签到成功：${result.rewardName || "奖励已发放"}，累计 ${result.signCount} 天，连续 ${result.consecutiveDays} 天。`
      });
      const [nextProfile, nextStatus] = await Promise.all([
        gameAccountApi.current(),
        gameAccountApi.getSignInStatus()
      ]);
      setProfile(nextProfile);
      setSignStatus(nextStatus);
      const [ownedCharacterPage, ownedSkinPage, ownedItemPage] = await Promise.all([
        gameAccountApi.listOwnedCharacters(),
        gameAccountApi.listOwnedSkins(),
        gameAccountApi.listOwnedItems()
      ]);
      setOwnedCharacters(ownedCharacterPage.data ?? []);
      setOwnedSkins(ownedSkinPage.data ?? []);
      setOwnedItems(ownedItemPage.data ?? []);
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusy(false);
    }
  }

  async function openCatalogDetail(code: string) {
    try {
      if (catalogTab === "characters") {
        const detail = await gameAccountApi.getCharacterDetail(code);
        if (!detail) {
          setSelectedDetailTitle(code);
          setSelectedDetailBody("这项角色详情还没有补充到 Mongo，当前只展示基础元数据。");
          return;
        }
        setSelectedDetailTitle(detail.characterCode);
        setSelectedDetailBody([
          detail.story ? `故事：${detail.story}` : "",
          detail.background ? `背景：${detail.background}` : "",
          detail.skills?.length ? `技能：${detail.skills.join(" / ")}` : "",
          detail.tags?.length ? `标签：${detail.tags.join(" / ")}` : ""
        ].filter(Boolean).join("\n\n") || "暂时还没有补充详情。");
        return;
      }
      if (catalogTab === "skins") {
        const detail = await gameAccountApi.getSkinDetail(code);
        if (!detail) {
          setSelectedDetailTitle(code);
          setSelectedDetailBody("这项皮肤详情还没有补充到 Mongo，当前只展示基础元数据。");
          return;
        }
        setSelectedDetailTitle(detail.skinCode);
        setSelectedDetailBody([
          detail.description ? `描述：${detail.description}` : "",
          detail.theme ? `主题：${detail.theme}` : "",
          detail.previewImages?.length ? `预览图：${detail.previewImages.join("\n")}` : "",
          detail.tags?.length ? `标签：${detail.tags.join(" / ")}` : ""
        ].filter(Boolean).join("\n\n") || "暂时还没有补充详情。");
        return;
      }
      const detail = await gameAccountApi.getItemDetail(code);
      if (!detail) {
        setSelectedDetailTitle(code);
        setSelectedDetailBody("这项道具详情还没有补充到 Mongo，当前只展示基础元数据。");
        return;
      }
      setSelectedDetailTitle(detail.itemCode);
      setSelectedDetailBody([
        detail.description ? `描述：${detail.description}` : "",
        detail.usageTips ? `使用说明：${detail.usageTips}` : "",
        detail.sources?.length ? `来源：${detail.sources.join(" / ")}` : "",
        detail.tags?.length ? `标签：${detail.tags.join(" / ")}` : ""
      ].filter(Boolean).join("\n\n") || "暂时还没有补充详情。");
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    }
  }

  async function saveCharacter(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      if (characterForm.id) {
        await gameAccountApi.updateCharacter(characterForm);
      } else {
        await gameAccountApi.createCharacter(characterForm);
      }
      await gameAccountApi.saveCharacterDetail(characterDetailForm);
      setNotice({ type: "success", text: "角色资源和详情都已保存。" });
      resetCharacterForm();
      await loadAdminSection();
      await loadCatalog();
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusy(false);
    }
  }

  async function saveSkin(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      if (skinForm.id) {
        await gameAccountApi.updateSkin(skinForm);
      } else {
        await gameAccountApi.createSkin(skinForm);
      }
      await gameAccountApi.saveSkinDetail(skinDetailForm);
      setNotice({ type: "success", text: "皮肤资源和详情都已保存。" });
      resetSkinForm();
      await loadAdminSection();
      await loadCatalog();
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusy(false);
    }
  }

  async function saveItem(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      if (itemForm.id) {
        await gameAccountApi.updateItem(itemForm);
      } else {
        await gameAccountApi.createItem(itemForm);
      }
      await gameAccountApi.saveItemDetail(itemDetailForm);
      setNotice({ type: "success", text: "道具资源和详情都已保存。" });
      resetItemForm();
      await loadAdminSection();
      await loadCatalog();
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusy(false);
    }
  }

  async function saveReward(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    try {
      if (rewardForm.id) {
        await gameAccountApi.updateSignInReward(rewardForm);
      } else {
        await gameAccountApi.createSignInReward(rewardForm);
      }
      setNotice({ type: "success", text: "签到奖励已保存。" });
      resetRewardForm();
      await loadAdminSection();
      const rewards = await gameAccountApi.listSignInRewards();
      setSignRewards(rewards);
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusy(false);
    }
  }

  function resetCharacterForm() {
    setCharacterForm({
      characterCode: "",
      name: "",
      title: "",
      icon: "",
      rarity: 1,
      elementType: 0,
      characterType: 0,
      status: 1,
      sortOrder: 0,
      version: 0
    });
    setCharacterDetailForm({ characterCode: "", story: "", background: "", skills: [], tags: [] });
  }

  function resetSkinForm() {
    setSkinForm({
      skinCode: "",
      characterId: undefined,
      characterCode: "",
      name: "",
      icon: "",
      rarity: 1,
      status: 1,
      sortOrder: 0,
      version: 0
    });
    setSkinDetailForm({ skinCode: "", description: "", theme: "", previewImages: [], tags: [] });
  }

  function resetItemForm() {
    setItemForm({
      itemCode: "",
      name: "",
      itemType: 1,
      rarity: 1,
      icon: "",
      maxStackCount: 99,
      status: 1,
      sortOrder: 0,
      version: 0
    });
    setItemDetailForm({ itemCode: "", description: "", usageTips: "", sources: [], tags: [] });
  }

  function resetRewardForm() {
    setRewardForm({
      dayIndex: 1,
      rewardType: 4,
      businessCode: "",
      rewardCode: "gold",
      quantity: 100,
      rewardName: "金币 x100",
      status: 1
    });
  }

  if (loading) {
    return (
      <section className="page-view">
        <div className="page-heading">
          <p className="eyebrow">游戏账号</p>
          <h1>正在接入你的图鉴与背包…</h1>
          <span>我正在同时拉取绑定关系、签到状态、资源库和管理员配置。</span>
        </div>
      </section>
    );
  }

  return (
    <section className="page-view game-account-page">
      <div className="page-heading compact-heading">
        <p className="eyebrow">Game Account</p>
        <h1>绑定、图鉴、签到和发货都在这里收口。</h1>
        <span>一个社区用户绑定一个游戏账号，允许换绑；商城支付成功后会通过 Kafka 幂等发货。</span>
      </div>

      {notice && <Notice type={notice.type}>{notice.text}</Notice>}

      <div className="game-tab-row">
        {[
          { id: "overview", label: "绑定总览" },
          { id: "assets", label: "我的资产" },
          { id: "catalog", label: "资源图鉴" },
          { id: "signin", label: "签到中心" },
          ...(isAdmin ? [{ id: "admin", label: "资源管理" }] : [])
        ].map((item) => (
          <button
            key={item.id}
            type="button"
            className={tab === item.id ? "active" : ""}
            onClick={() => setTab(item.id as MainTab)}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tab === "overview" && (
        <div className="game-overview-grid">
          <article className="game-summary-card">
            <span>绑定状态</span>
            <strong>{profile?.bound ? "已绑定" : "未绑定"}</strong>
            <p>{profile?.bound ? `当前发货账号 ${profile.accountNo}` : "未绑定时无法给游戏资产类商品发货，也无法签到。"}</p>
          </article>
          <article className="game-summary-card">
            <span>金币 / 钻石</span>
            <strong>{profile?.gold ?? 0} / {profile?.diamond ?? 0}</strong>
            <p>签到奖励和商城异步发货会直接落到这个账号资产里。</p>
          </article>
          <article className="game-summary-card">
            <span>段位</span>
            <strong>{profile?.currentSeasonRank || "暂无"}</strong>
            <p>历史最高 {profile?.historySeasonRank || "暂无"}，最近更新时间 {formatDate(profile?.updateTime)}</p>
          </article>
          <form className="form-card" onSubmit={handleBindSubmit}>
            <p className="eyebrow">绑定管理</p>
            <h2>{profile?.bound ? "换绑游戏账号" : "绑定你的游戏账号"}</h2>
            <span>输入对外展示的账号号，例如 `GA10001`。换绑后不会保留历史绑定记录。</span>
            <label>
              游戏账号号
              <input value={bindAccountNo} onChange={(event) => setBindAccountNo(event.target.value)} placeholder="GA10001" />
            </label>
            <div className="inline-actions">
              <ActionButton type="submit" busy={busy}>{profile?.bound ? "确认换绑" : "立即绑定"}</ActionButton>
              {profile?.bound && (
                <ActionButton type="button" variant="ghost" busy={busy} onClick={() => void handleUnbind()}>
                  解绑
                </ActionButton>
              )}
            </div>
          </form>
        </div>
      )}

      {tab === "assets" && (
        <div className="game-section-stack">
          <div className="game-subtab-row">
            {["characters", "skins", "items"].map((item) => (
              <button key={item} type="button" className={assetTab === item ? "active" : ""} onClick={() => setAssetTab(item as ResourceTab)}>
                {item === "characters" ? "角色" : item === "skins" ? "皮肤" : "道具"}
              </button>
            ))}
          </div>
          {!profile?.bound && <Notice type="info">先绑定游戏账号，背包与图鉴才有归属对象。</Notice>}
          <div className="game-card-grid">
            {currentOwnedCards.map((card) => (
              <article className="game-card" key={card.id}>
                <div className="game-card-cover">
                  {card.image ? <img src={card.image} alt={card.title} /> : <span>{card.title.slice(0, 2)}</span>}
                </div>
                <div className="game-card-body">
                  <strong>{card.title}</strong>
                  <span>{card.subtitle}</span>
                  <p>{card.meta}</p>
                </div>
              </article>
            ))}
            {currentOwnedCards.length === 0 && <Notice>当前分类下还没有资产，可以先去签到或者在商城下单。</Notice>}
          </div>
        </div>
      )}

      {tab === "catalog" && (
        <div className="game-catalog-layout">
          <div className="game-section-stack">
            <div className="game-subtab-row">
              {["characters", "skins", "items"].map((item) => (
                <button key={item} type="button" className={catalogTab === item ? "active" : ""} onClick={() => setCatalogTab(item as ResourceTab)}>
                  {item === "characters" ? "角色图鉴" : item === "skins" ? "皮肤图鉴" : "道具图鉴"}
                </button>
              ))}
            </div>
            <div className="catalog-search-row">
              <input value={catalogKeyword} onChange={(event) => setCatalogKeyword(event.target.value)} placeholder="按编码、名称或称号搜索" />
              <ActionButton variant="soft" onClick={() => void loadCatalog()}>搜索</ActionButton>
            </div>
            <div className="game-card-grid">
              {currentCatalogCards.map((card) => (
                <article className="game-card game-card--clickable" key={card.id} onClick={() => void openCatalogDetail(card.code)}>
                  <div className="game-card-cover">
                    {card.image ? <img src={card.image} alt={card.title} /> : <span>{card.title.slice(0, 2)}</span>}
                  </div>
                  <div className="game-card-body">
                    <strong>{card.title}</strong>
                    <span>{card.subtitle}</span>
                    <p>{card.meta}</p>
                  </div>
                </article>
              ))}
              {currentCatalogCards.length === 0 && <Notice>当前条件下没有找到资源，可以换个关键词再试。</Notice>}
            </div>
          </div>
          <aside className="game-detail-panel">
            <p className="eyebrow">资源详情</p>
            <h2>{selectedDetailTitle}</h2>
            <pre>{selectedDetailBody}</pre>
          </aside>
        </div>
      )}

      {tab === "signin" && (
        <div className="game-section-stack">
          <div className="game-overview-grid">
            <article className="game-summary-card">
              <span>当月</span>
              <strong>{signStatus?.yearMonth ?? "暂无"}</strong>
              <p>已签到 {signStatus?.signCount ?? 0} 天，连续 {signStatus?.consecutiveDays ?? 0} 天。</p>
            </article>
            <article className="game-summary-card">
              <span>今日状态</span>
              <strong>{signStatus?.signedToday ? "已签到" : "未签到"}</strong>
              <p>{signStatus?.signedToday ? `最近签到时间 ${formatDate(signStatus?.lastSignInDate)}` : "点击右侧按钮即可领取今日奖励。"}</p>
            </article>
            <div className="form-card">
              <p className="eyebrow">每日奖励</p>
              <h2>签到立即发放</h2>
              <span>奖励在同一个本地事务里发放到资产表，不额外走远程链路。</span>
              <ActionButton busy={busy} disabled={!profile?.bound || signStatus?.signedToday} onClick={() => void handleSignIn()}>
                {signStatus?.signedToday ? "今天已签到" : "立即签到"}
              </ActionButton>
            </div>
          </div>
          <div className="signin-grid">
            {signRewards.map((reward) => {
              const signed = signStatus?.signedDays?.includes(reward.dayIndex);
              return (
                <article className={`signin-card ${signed ? "signed" : ""}`} key={reward.id ?? reward.dayIndex}>
                  <span>Day {reward.dayIndex}</span>
                  <strong>{reward.rewardName || `${rewardTypeLabels[reward.rewardType]} x${reward.quantity}`}</strong>
                  <p>{rewardTypeLabels[reward.rewardType]} · 编码 {reward.rewardCode || "-"}</p>
                </article>
              );
            })}
          </div>
        </div>
      )}

      {tab === "admin" && isAdmin && (
        <div className="game-section-stack">
          <div className="game-subtab-row">
            {["characters", "skins", "items", "rewards"].map((item) => (
              <button
                key={item}
                type="button"
                className={adminTab === item ? "active" : ""}
                onClick={() => setAdminTab(item as AdminTab)}
              >
                {item === "characters" ? "角色管理" : item === "skins" ? "皮肤管理" : item === "items" ? "道具管理" : "签到奖励"}
              </button>
            ))}
          </div>

          {adminTab === "characters" && (
            <div className="admin-grid">
              <div className="admin-list">
                {adminCharacters.map((item) => (
                  <button
                    className="admin-row"
                    type="button"
                    key={item.id}
                    onClick={async () => {
                      setCharacterForm(item);
                      const detail = await gameAccountApi.getCharacterDetail(item.characterCode).catch(() => null);
                      setCharacterDetailForm(detail ?? {
                        characterCode: item.characterCode,
                        story: "",
                        background: "",
                        skills: [],
                        tags: []
                      });
                    }}
                  >
                    <strong>{item.name}</strong>
                    <span>{item.characterCode} · {rarityText(item.rarity)} · {item.status === 1 ? "启用" : "禁用"}</span>
                  </button>
                ))}
              </div>
              <form className="form-card" onSubmit={saveCharacter}>
                <h2>角色资源</h2>
                <label>角色编码<input value={characterForm.characterCode} onChange={(event) => {
                  const value = event.target.value;
                  setCharacterForm((current) => ({ ...current, characterCode: value }));
                  setCharacterDetailForm((current) => ({ ...current, characterCode: value }));
                }} /></label>
                <label>角色名称<input value={characterForm.name} onChange={(event) => setCharacterForm((current) => ({ ...current, name: event.target.value }))} /></label>
                <label>称号<input value={characterForm.title ?? ""} onChange={(event) => setCharacterForm((current) => ({ ...current, title: event.target.value }))} /></label>
                <label>图标<input value={characterForm.icon ?? ""} onChange={(event) => setCharacterForm((current) => ({ ...current, icon: event.target.value }))} /></label>
                <div className="inline-field">
                  <label>稀有度<input type="number" value={characterForm.rarity ?? 1} onChange={(event) => setCharacterForm((current) => ({ ...current, rarity: Number(event.target.value) }))} /></label>
                  <label>元素<input type="number" value={characterForm.elementType ?? 0} onChange={(event) => setCharacterForm((current) => ({ ...current, elementType: Number(event.target.value) }))} /></label>
                  <label>定位<input type="number" value={characterForm.characterType ?? 0} onChange={(event) => setCharacterForm((current) => ({ ...current, characterType: Number(event.target.value) }))} /></label>
                </div>
                <label>故事<textarea value={characterDetailForm.story ?? ""} onChange={(event) => setCharacterDetailForm((current) => ({ ...current, story: event.target.value }))} /></label>
                <label>背景<textarea value={characterDetailForm.background ?? ""} onChange={(event) => setCharacterDetailForm((current) => ({ ...current, background: event.target.value }))} /></label>
                <label>技能列表<input value={listToCsv(characterDetailForm.skills)} onChange={(event) => setCharacterDetailForm((current) => ({ ...current, skills: csvToList(event.target.value) }))} placeholder="技能一, 技能二" /></label>
                <label>标签<input value={listToCsv(characterDetailForm.tags)} onChange={(event) => setCharacterDetailForm((current) => ({ ...current, tags: csvToList(event.target.value) }))} placeholder="先锋, 风属性" /></label>
                <div className="inline-actions">
                  <ActionButton type="submit" busy={busy}>保存角色</ActionButton>
                  <ActionButton type="button" variant="ghost" onClick={resetCharacterForm}>新建</ActionButton>
                  {characterForm.id && <ActionButton type="button" variant="danger" onClick={async () => {
                    await gameAccountApi.disableCharacter(characterForm.id!);
                    resetCharacterForm();
                    await loadAdminSection();
                    await loadCatalog();
                  }}>停用</ActionButton>}
                </div>
              </form>
            </div>
          )}

          {adminTab === "skins" && (
            <div className="admin-grid">
              <div className="admin-list">
                {adminSkins.map((item) => (
                  <button
                    className="admin-row"
                    type="button"
                    key={item.id}
                    onClick={async () => {
                      setSkinForm(item);
                      const detail = await gameAccountApi.getSkinDetail(item.skinCode).catch(() => null);
                      setSkinDetailForm(detail ?? {
                        skinCode: item.skinCode,
                        description: "",
                        theme: "",
                        previewImages: [],
                        tags: []
                      });
                    }}
                  >
                    <strong>{item.name}</strong>
                    <span>{item.skinCode} · 角色 {item.characterCode || "-"} · {item.status === 1 ? "启用" : "禁用"}</span>
                  </button>
                ))}
              </div>
              <form className="form-card" onSubmit={saveSkin}>
                <h2>皮肤资源</h2>
                <label>皮肤编码<input value={skinForm.skinCode} onChange={(event) => {
                  const value = event.target.value;
                  setSkinForm((current) => ({ ...current, skinCode: value }));
                  setSkinDetailForm((current) => ({ ...current, skinCode: value }));
                }} /></label>
                <label>皮肤名称<input value={skinForm.name} onChange={(event) => setSkinForm((current) => ({ ...current, name: event.target.value }))} /></label>
                <div className="inline-field">
                  <label>角色 ID<input type="number" value={skinForm.characterId ?? ""} onChange={(event) => setSkinForm((current) => ({ ...current, characterId: Number(event.target.value) || undefined }))} /></label>
                  <label>角色编码<input value={skinForm.characterCode ?? ""} onChange={(event) => setSkinForm((current) => ({ ...current, characterCode: event.target.value }))} /></label>
                </div>
                <label>图标<input value={skinForm.icon ?? ""} onChange={(event) => setSkinForm((current) => ({ ...current, icon: event.target.value }))} /></label>
                <div className="inline-field">
                  <label>稀有度<input type="number" value={skinForm.rarity ?? 1} onChange={(event) => setSkinForm((current) => ({ ...current, rarity: Number(event.target.value) }))} /></label>
                  <label>排序<input type="number" value={skinForm.sortOrder ?? 0} onChange={(event) => setSkinForm((current) => ({ ...current, sortOrder: Number(event.target.value) }))} /></label>
                </div>
                <label>描述<textarea value={skinDetailForm.description ?? ""} onChange={(event) => setSkinDetailForm((current) => ({ ...current, description: event.target.value }))} /></label>
                <label>主题<input value={skinDetailForm.theme ?? ""} onChange={(event) => setSkinDetailForm((current) => ({ ...current, theme: event.target.value }))} /></label>
                <label>预览图<input value={listToCsv(skinDetailForm.previewImages)} onChange={(event) => setSkinDetailForm((current) => ({ ...current, previewImages: csvToList(event.target.value) }))} placeholder="https://a.png, https://b.png" /></label>
                <label>标签<input value={listToCsv(skinDetailForm.tags)} onChange={(event) => setSkinDetailForm((current) => ({ ...current, tags: csvToList(event.target.value) }))} /></label>
                <div className="inline-actions">
                  <ActionButton type="submit" busy={busy}>保存皮肤</ActionButton>
                  <ActionButton type="button" variant="ghost" onClick={resetSkinForm}>新建</ActionButton>
                  {skinForm.id && <ActionButton type="button" variant="danger" onClick={async () => {
                    await gameAccountApi.disableSkin(skinForm.id!);
                    resetSkinForm();
                    await loadAdminSection();
                    await loadCatalog();
                  }}>停用</ActionButton>}
                </div>
              </form>
            </div>
          )}

          {adminTab === "items" && (
            <div className="admin-grid">
              <div className="admin-list">
                {adminItems.map((item) => (
                  <button
                    className="admin-row"
                    type="button"
                    key={item.id}
                    onClick={async () => {
                      setItemForm(item);
                      const detail = await gameAccountApi.getItemDetail(item.itemCode).catch(() => null);
                      setItemDetailForm(detail ?? {
                        itemCode: item.itemCode,
                        description: "",
                        usageTips: "",
                        sources: [],
                        tags: []
                      });
                    }}
                  >
                    <strong>{item.name}</strong>
                    <span>{item.itemCode} · {itemTypeText(item.itemType)} · {item.status === 1 ? "启用" : "禁用"}</span>
                  </button>
                ))}
              </div>
              <form className="form-card" onSubmit={saveItem}>
                <h2>道具资源</h2>
                <label>道具编码<input value={itemForm.itemCode} onChange={(event) => {
                  const value = event.target.value;
                  setItemForm((current) => ({ ...current, itemCode: value }));
                  setItemDetailForm((current) => ({ ...current, itemCode: value }));
                }} /></label>
                <label>道具名称<input value={itemForm.name} onChange={(event) => setItemForm((current) => ({ ...current, name: event.target.value }))} /></label>
                <label>图标<input value={itemForm.icon ?? ""} onChange={(event) => setItemForm((current) => ({ ...current, icon: event.target.value }))} /></label>
                <div className="inline-field">
                  <label>道具类型<input type="number" value={itemForm.itemType ?? 1} onChange={(event) => setItemForm((current) => ({ ...current, itemType: Number(event.target.value) }))} /></label>
                  <label>稀有度<input type="number" value={itemForm.rarity ?? 1} onChange={(event) => setItemForm((current) => ({ ...current, rarity: Number(event.target.value) }))} /></label>
                  <label>最大堆叠<input type="number" value={itemForm.maxStackCount ?? 99} onChange={(event) => setItemForm((current) => ({ ...current, maxStackCount: Number(event.target.value) }))} /></label>
                </div>
                <label>描述<textarea value={itemDetailForm.description ?? ""} onChange={(event) => setItemDetailForm((current) => ({ ...current, description: event.target.value }))} /></label>
                <label>使用说明<textarea value={itemDetailForm.usageTips ?? ""} onChange={(event) => setItemDetailForm((current) => ({ ...current, usageTips: event.target.value }))} /></label>
                <label>来源<input value={listToCsv(itemDetailForm.sources)} onChange={(event) => setItemDetailForm((current) => ({ ...current, sources: csvToList(event.target.value) }))} /></label>
                <label>标签<input value={listToCsv(itemDetailForm.tags)} onChange={(event) => setItemDetailForm((current) => ({ ...current, tags: csvToList(event.target.value) }))} /></label>
                <div className="inline-actions">
                  <ActionButton type="submit" busy={busy}>保存道具</ActionButton>
                  <ActionButton type="button" variant="ghost" onClick={resetItemForm}>新建</ActionButton>
                  {itemForm.id && <ActionButton type="button" variant="danger" onClick={async () => {
                    await gameAccountApi.disableItem(itemForm.id!);
                    resetItemForm();
                    await loadAdminSection();
                    await loadCatalog();
                  }}>停用</ActionButton>}
                </div>
              </form>
            </div>
          )}

          {adminTab === "rewards" && (
            <div className="admin-grid admin-grid--rewards">
              <div className="admin-list">
                {adminRewards.map((item) => (
                  <button className="admin-row" type="button" key={item.id ?? item.dayIndex} onClick={() => setRewardForm(item)}>
                    <strong>Day {item.dayIndex} · {item.rewardName || rewardTypeLabels[item.rewardType]}</strong>
                    <span>{rewardTypeLabels[item.rewardType]} · {item.rewardCode || "-"} · x{item.quantity}</span>
                  </button>
                ))}
              </div>
              <form className="form-card" onSubmit={saveReward}>
                <h2>签到奖励</h2>
                <div className="inline-field">
                  <label>天数<input type="number" value={rewardForm.dayIndex} onChange={(event) => setRewardForm((current) => ({ ...current, dayIndex: Number(event.target.value) }))} /></label>
                  <label>奖励类型<input type="number" value={rewardForm.rewardType} onChange={(event) => setRewardForm((current) => ({ ...current, rewardType: Number(event.target.value) }))} /></label>
                  <label>数量<input type="number" value={rewardForm.quantity} onChange={(event) => setRewardForm((current) => ({ ...current, quantity: Number(event.target.value) }))} /></label>
                </div>
                <label>展示名称<input value={rewardForm.rewardName ?? ""} onChange={(event) => setRewardForm((current) => ({ ...current, rewardName: event.target.value }))} /></label>
                <label>业务编码<input value={rewardForm.businessCode ?? ""} onChange={(event) => setRewardForm((current) => ({ ...current, businessCode: event.target.value }))} /></label>
                <label>奖励编码<input value={rewardForm.rewardCode ?? ""} onChange={(event) => setRewardForm((current) => ({ ...current, rewardCode: event.target.value }))} /></label>
                <div className="inline-actions">
                  <ActionButton type="submit" busy={busy}>保存奖励</ActionButton>
                  <ActionButton type="button" variant="ghost" onClick={resetRewardForm}>新建</ActionButton>
                  {rewardForm.id && <ActionButton type="button" variant="danger" onClick={async () => {
                    await gameAccountApi.deleteSignInReward(rewardForm.id!);
                    resetRewardForm();
                    await loadAdminSection();
                    const rewards = await gameAccountApi.listSignInRewards();
                    setSignRewards(rewards);
                  }}>删除</ActionButton>}
                </div>
              </form>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
