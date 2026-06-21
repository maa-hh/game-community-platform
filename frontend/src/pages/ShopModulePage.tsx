import { useEffect, useState } from "react";
import { gameAccountApi, GameAccountProfile } from "../api/gameAccount";
import { shopApi, ShopCoupon, ShopCurrency, ShopItem, ShopOrder } from "../api/shop";
import { ActionButton } from "../components/ActionButton";
import { Notice } from "../components/Notice";
import { friendlyError } from "../utils/errors";

const payTypeLabels: Record<number, string> = {
  0: "金币",
  1: "钻石"
};

function couponLabel(coupon: ShopCoupon) {
  const threshold = coupon.minAmount > 0 ? `满${coupon.minAmount}` : "无门槛";
  if (coupon.discountType === 1) {
    return `${coupon.couponName} · ${threshold}减 ${coupon.discountValue}`;
  }
  return `${coupon.couponName} · ${threshold}享 ${coupon.discountValue}折扣点`;
}

function couponAppliesToItem(coupon: ShopCoupon, item: ShopItem) {
  if (item.price < (coupon.minAmount ?? 0)) {
    return false;
  }
  if (coupon.scopeItemId !== undefined && coupon.scopeItemId !== null) {
    return coupon.scopeItemId === item.id;
  }
  if (coupon.scopeProductType !== undefined && coupon.scopeProductType !== null) {
    return coupon.scopeProductType === item.productType;
  }
  if (coupon.scopeType === -1) {
    return true;
  }
  return coupon.scopeType === item.productType;
}

function requestId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return `req-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function ShopModulePage() {
  const [items, setItems] = useState<ShopItem[]>([]);
  const [orders, setOrders] = useState<ShopOrder[]>([]);
  const [coupons, setCoupons] = useState<ShopCoupon[]>([]);
  const [currency, setCurrency] = useState<ShopCurrency | null>(null);
  const [gameProfile, setGameProfile] = useState<GameAccountProfile | null>(null);
  const [payType, setPayType] = useState(1);
  const [selectedCoupon, setSelectedCoupon] = useState<Record<number, number | "">>({});
  const [notice, setNotice] = useState<{ type: "success" | "error" | "info"; text: string } | null>(null);
  const [busyItemId, setBusyItemId] = useState<number | null>(null);
  const [busyOrderNo, setBusyOrderNo] = useState<string | null>(null);

  useEffect(() => {
    void loadShop();
  }, []);

  async function loadShop() {
    setNotice(null);
    try {
      const [itemPage, currencyData, orderPage, currentGameProfile] = await Promise.all([
        shopApi.listItems({ status: 1, size: 20 }),
        shopApi.getCurrency(),
        shopApi.listOrders({ size: 8 }),
        gameAccountApi.current().catch(() => null)
      ]);
      setItems(itemPage.data ?? []);
      setCurrency(currencyData);
      setOrders(orderPage.data ?? []);
      setGameProfile(currentGameProfile);
      const couponPage = await shopApi.listUserCoupons({ status: 0, size: 50 });
      setCoupons(couponPage.data ?? []);
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    }
  }

  async function createOrder(item: ShopItem) {
    if ((item.productType === 2 || item.productType === 3) && !gameProfile?.bound) {
      setNotice({ type: "error", text: "该商品属于游戏资产类，请先去“游戏资产”模块绑定游戏账号再下单。" });
      return;
    }
    setBusyItemId(item.id);
    setNotice({ type: "info", text: "正在提交到 Redis 预扣库存队列..." });
    try {
      const selectedUserCouponId = selectedCoupon[item.id];
      const selectedValidCoupon = coupons.find((coupon) => (
        coupon.userCouponId === selectedUserCouponId && couponAppliesToItem(coupon, item)
      ));
      const order = await shopApi.createOrder({
        itemId: item.id,
        quantity: 1,
        payType,
        requestId: requestId(),
        userCouponId: selectedValidCoupon?.userCouponId
      });
      setNotice({ type: "success", text: `订单 ${order.orderNo} 已生成，正在异步确认库存。` });
      await waitOrderReady(order.orderNo);
      await loadShop();
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusyItemId(null);
    }
  }

  async function waitOrderReady(orderNo: string) {
    for (let i = 0; i < 8; i++) {
      const order = await shopApi.getOrder(orderNo);
      if (order.status !== 1) {
        setOrders((current) => [order, ...current.filter((item) => item.orderNo !== orderNo)].slice(0, 8));
        return;
      }
      await new Promise((resolve) => window.setTimeout(resolve, 500));
    }
  }

  async function pay(orderNo: string) {
    setBusyOrderNo(orderNo);
    try {
      await shopApi.payOrder(orderNo);
      setNotice({ type: "success", text: "支付成功，库存和余额已按数据库事务更新。" });
      await loadShop();
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusyOrderNo(null);
    }
  }

  async function cancel(orderNo: string) {
    setBusyOrderNo(orderNo);
    try {
      await shopApi.cancelOrder(orderNo);
      setNotice({ type: "success", text: "订单已取消，库存和限购计数已归还。" });
      await loadShop();
    } catch (error) {
      setNotice({ type: "error", text: friendlyError(error) });
    } finally {
      setBusyOrderNo(null);
    }
  }

  return (
    <section className="page-view shop-page">
      <div className="page-heading compact-heading">
        <p className="eyebrow">商城中心</p>
        <h1>库存要快，订单要稳。</h1>
        <span>下单入口通过 Redis + Lua 做库存预扣和幂等校验，后台异步落库并扣减 MySQL 库存。</span>
      </div>

      {notice && <Notice type={notice.type}>{notice.text}</Notice>}

      <div className="shop-toolbar">
        <article>
          <span>金币</span>
          <strong>{currency?.gold ?? "-"}</strong>
        </article>
        <article>
          <span>钻石</span>
          <strong>{currency?.diamond ?? "-"}</strong>
        </article>
        <div className="shop-pay-switch">
          <button className={payType === 1 ? "active" : ""} type="button" onClick={() => setPayType(1)}>
            钻石支付
          </button>
          <button className={payType === 0 ? "active" : ""} type="button" onClick={() => setPayType(0)}>
            金币支付
          </button>
        </div>
      </div>

      {(gameProfile?.bound || items.some((item) => item.productType === 2 || item.productType === 3)) && (
        <Notice type={gameProfile?.bound ? "info" : "error"}>
          {gameProfile?.bound
            ? `当前游戏资产商品会发货到 ${gameProfile.accountNo} · ${gameProfile.name ?? "未命名账号"}。`
            : "当前商城包含游戏资产类商品，但你还没有绑定游戏账号，相关商品暂时无法下单。"}
        </Notice>
      )}

      <section className="shop-coupons">
        <div className="section-title-row">
          <div>
            <p className="eyebrow">优惠券</p>
            <h2>我的可用券</h2>
          </div>
        </div>
        {coupons.length === 0 ? (
          <Notice>当前没有可用优惠券。</Notice>
        ) : (
          <div className="coupon-strip">
            {coupons.map((coupon) => (
              <span className="coupon-chip" key={coupon.userCouponId}>
                {couponLabel(coupon)}
              </span>
            ))}
          </div>
        )}
      </section>

      <div className="shop-grid">
        {items.map((item) => (
          <article className="shop-card" key={item.id}>
            <div className="shop-cover">
              {item.icon ? <img src={item.icon} alt={item.name} /> : <span>SHOP</span>}
            </div>
            <div className="shop-card-body">
              <span className="audit-pill">{item.stock < 0 ? "不限量" : `库存 ${item.stock}`}</span>
              <h2>{item.name}</h2>
              <p>{item.description || "暂无描述"}</p>
              <div className="shop-card-meta">
                <strong>{item.price} {payTypeLabels[payType]}</strong>
                <span>{item.limitCount < 0 ? "不限购" : `每人限购 ${item.limitCount}`}</span>
              </div>
              <select
                className="coupon-select"
                value={selectedCoupon[item.id] ?? ""}
                onChange={(event) => {
                  const value = event.target.value;
                  setSelectedCoupon((current) => ({ ...current, [item.id]: value ? Number(value) : "" }));
                }}
              >
                <option value="">不使用优惠券</option>
                {coupons
                  .filter((coupon) => couponAppliesToItem(coupon, item))
                  .map((coupon) => (
                    <option value={coupon.userCouponId} key={coupon.userCouponId}>
                      {couponLabel(coupon)}
                    </option>
                  ))}
              </select>
              <ActionButton busy={busyItemId === item.id} onClick={() => void createOrder(item)}>
                立即下单
              </ActionButton>
            </div>
          </article>
        ))}
      </div>

      <section className="shop-orders">
        <div className="section-title-row">
          <div>
            <p className="eyebrow">订单</p>
            <h2>最近订单</h2>
          </div>
          <ActionButton variant="ghost" onClick={() => void loadShop()}>刷新</ActionButton>
        </div>
        {orders.length === 0 && <Notice>还没有订单，先挑一个商品试试。</Notice>}
        {orders.map((order) => (
          <article className="shop-order-row" key={order.orderNo}>
            <div>
              <strong>{order.itemName}</strong>
              <span>{order.orderNo}</span>
            </div>
            <div>
              <strong>{order.finalPrice} {payTypeLabels[order.payType]}</strong>
              <span>
                {order.statusText}
                {order.discountAmount > 0 ? ` · 已优惠 ${order.discountAmount}` : ""}
              </span>
            </div>
            {order.status === 2 && (
              <div className="shop-order-actions">
                <ActionButton busy={busyOrderNo === order.orderNo} onClick={() => void pay(order.orderNo)}>
                  支付
                </ActionButton>
                <ActionButton variant="ghost" busy={busyOrderNo === order.orderNo} onClick={() => void cancel(order.orderNo)}>
                  取消
                </ActionButton>
              </div>
            )}
          </article>
        ))}
      </section>
    </section>
  );
}
