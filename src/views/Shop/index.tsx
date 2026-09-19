import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  Button,
  Empty,
  Input,
  Pagination,
  Select,
  Segmented,
  Space,
  Spin,
  Tabs,
  Tag,
  App,
} from 'antd';
import { GiftOutlined, ShoppingOutlined } from '@ant-design/icons';

import {
  equipCosmeticApi,
  fetchCosmeticBackpackApi,
  unequipCosmeticApi,
  consumeCosmeticApi,
} from '@/service/cosmetic';
import {
  exchangeShopItemApi,
  fetchShopCurrencyApi,
  fetchShopItemsApi,
  SHOP_ORDER_STATUS,
  type IShopItem,
} from '@/service/shop';
import { isAvatarFrameCosmeticCode } from '@/constants/avatarFrameCatalog';
import { isProfileBgCosmeticCode } from '@/constants/profileBgCatalog';
import type { CosmeticSlot, IUserCosmeticItem } from '@/types/cosmetic';
import { formatApiError } from '@/utils/apiError';
import { notifyCosmeticUpdated } from '@/utils/cosmeticRefresh';
import { getPageDataCache, setPageDataCache } from '@/hooks/pageDataCache';
import { useRequireLogin } from '@/hooks/useRequireLogin';

import { useAppSelector } from '@/store';

import CosmeticShopCard from './parts/CosmeticShopCard';
import {
  SHOP_CATEGORY_OPTIONS,
  filterByShopCategory,
  type ShopCategory,
} from './utils/shopCategories';
import {
  SHOP_OWNED_FILTER_OPTIONS,
  SHOP_PRICE_SORT_OPTIONS,
  applyShopItemFilters,
  type ShopItemFilterState,
  type ShopOwnedFilter,
  type ShopPriceSort,
} from './utils/shopItemFilters';

import './style.less';

const SLOT_LABEL: Record<CosmeticSlot, string> = {
  AVATAR_FRAME: '头像框',
  COMMENT_CARD: '评论卡片',
  COMMENT_FONT: '评论字体',
  POST_CARD: '帖子卡片',
  PROFILE_BG: '主页背景',
};

type ShopTabKey = 'store' | 'backpack';

interface BackpackCache {
  items: IUserCosmeticItem[];
  total: number;
}

const SHOP_TABS = [
  { key: 'store', label: '兑换', icon: <ShoppingOutlined /> },
  { key: 'backpack', label: '背包', icon: <GiftOutlined /> },
];

function buildBackpackCacheKey(
  accountId: string,
  options: {
    category: ShopCategory;
    effectMode?: 'EQUIP' | 'CONSUMABLE';
    equipped?: boolean;
    state?: 'ACTIVE' | 'EXPIRED';
    keyword: string;
    page: number;
    pageSize: number;
  },
) {
  return `shop:backpack:${accountId}:${JSON.stringify(options)}`;
}

function ShopPage() {
  const { message } = App.useApp();
  const { user } = useAppSelector((state) => state.auth);
  const { isLoggedIn, openAuth } = useRequireLogin();
  const accountId = String(user?.accountId ?? 'anonymous');
  const pointsCacheKey = `shop:points:${accountId}`;
  const storeCacheKey = `shop:store:${accountId}`;
  const [tab, setTab] = useState<ShopTabKey>('store');
  const [storeCategory, setStoreCategory] = useState<ShopCategory>('all');
  const [backpackCategory, setBackpackCategory] = useState<ShopCategory>('all');
  const [backpackEffectMode, setBackpackEffectMode] = useState<
    'EQUIP' | 'CONSUMABLE' | undefined
  >();
  const [backpackEquipped, setBackpackEquipped] = useState<
    boolean | undefined
  >();
  const [backpackState, setBackpackState] = useState<
    'ACTIVE' | 'EXPIRED' | undefined
  >();
  const [backpackKeyword, setBackpackKeyword] = useState('');
  const [backpackPage, setBackpackPage] = useState(1);
  const [backpackPageSize, setBackpackPageSize] = useState(20);
  const backpackCacheKey = buildBackpackCacheKey(accountId, {
    category: backpackCategory,
    effectMode: backpackEffectMode,
    equipped: backpackEquipped,
    state: backpackState,
    keyword: backpackKeyword,
    page: backpackPage,
    pageSize: backpackPageSize,
  });
  const initialPointsCache = getPageDataCache<number>(pointsCacheKey);
  const initialStoreCache = getPageDataCache<IShopItem[]>(storeCacheKey);
  const initialBackpackCache =
    getPageDataCache<BackpackCache>(backpackCacheKey);
  const [backpackTotal, setBackpackTotal] = useState(
    initialBackpackCache?.total ?? 0,
  );
  const [ownedFilter, setOwnedFilter] = useState<ShopOwnedFilter>('all');
  const [priceSort, setPriceSort] = useState<ShopPriceSort>('default');
  const [points, setPoints] = useState(initialPointsCache ?? 0);
  const [items, setItems] = useState<IShopItem[]>(initialStoreCache ?? []);
  const [backpack, setBackpack] = useState<IUserCosmeticItem[]>(
    initialBackpackCache?.items ?? [],
  );
  // 首次没有缓存时显示 loading；一级导航返回时直接复用上次数据。
  const [loadingStore, setLoadingStore] = useState(!initialStoreCache);
  const [loadingBackpack, setLoadingBackpack] = useState(!initialBackpackCache);
  const [exchangingId, setExchangingId] = useState<number | null>(null);
  const [actingCode, setActingCode] = useState<string | null>(null);
  const storeRequestIdRef = useRef(0);
  const backpackRequestIdRef = useRef(0);

  const changeTab = useCallback(
    (next: string) => {
      if (next === 'backpack' && !isLoggedIn) {
        openAuth('login');
        return;
      }
      setTab(next === 'backpack' ? 'backpack' : 'store');
    },
    [isLoggedIn, openAuth],
  );

  const shopFilters = useMemo<ShopItemFilterState>(
    () => ({ owned: ownedFilter, priceSort }),
    [ownedFilter, priceSort],
  );

  const loadPoints = useCallback(async () => {
    try {
      const res = await fetchShopCurrencyApi();
      if (res.code !== 200) throw new Error(res.message || '加载失败');
      const nextPoints = Number(res.data?.points ?? 0);
      setPoints(nextPoints);
      setPageDataCache(pointsCacheKey, nextPoints);
    } catch (error) {
      message.error(formatApiError('加载积分失败', error));
    }
  }, [message, pointsCacheKey]);

  const loadStore = useCallback(
    async (options?: { cache?: boolean }) => {
      const shouldCache = options?.cache !== false;
      const requestId = ++storeRequestIdRef.current;
      setLoadingStore(true);
      try {
        const res = await fetchShopItemsApi({
          page: 1,
          size: 100,
          skipAuth: !isLoggedIn,
        });
        if (res.code !== 200) throw new Error(res.message || '加载失败');
        const nextItems = res.data || [];
        if (requestId !== storeRequestIdRef.current) return;
        setItems(nextItems);
        if (shouldCache) setPageDataCache(storeCacheKey, nextItems);
      } catch (error) {
        if (requestId === storeRequestIdRef.current) {
          message.error(formatApiError('加载商城失败', error));
        }
      } finally {
        if (requestId === storeRequestIdRef.current) setLoadingStore(false);
      }
    },
    [isLoggedIn, message, storeCacheKey],
  );

  const loadBackpack = useCallback(
    async (options?: { cache?: boolean }) => {
      const shouldCache = options?.cache !== false;
      const requestId = ++backpackRequestIdRef.current;
      setLoadingBackpack(true);
      try {
        const categoryMap: Partial<Record<ShopCategory, string>> = {
          avatar_frame: 'AVATAR_FRAME',
          profile_bg: 'PROFILE_BG',
          comment_card: 'COMMENT_CARD',
        };
        const res = await fetchCosmeticBackpackApi({
          page: backpackPage,
          size: backpackPageSize,
          effectMode: backpackEffectMode,
          category: categoryMap[backpackCategory],
          equipped: backpackEquipped,
          state: backpackState,
          keyword: backpackKeyword || undefined,
        });
        if (res.code !== 200) throw new Error(res.message || '加载失败');
        const nextItems = res.data || [];
        const nextTotal = res.total || 0;
        if (requestId !== backpackRequestIdRef.current) return;
        setBackpack(nextItems);
        setBackpackTotal(nextTotal);
        if (shouldCache) {
          setPageDataCache(backpackCacheKey, {
            items: nextItems,
            total: nextTotal,
          });
        }
        if (
          nextItems.length === 0 &&
          backpackPage > 1 &&
          nextTotal <= (backpackPage - 1) * backpackPageSize
        ) {
          setBackpackPage((page) => Math.max(1, page - 1));
        }
      } catch (error) {
        if (requestId === backpackRequestIdRef.current) {
          message.error(formatApiError('加载背包失败', error));
        }
      } finally {
        if (requestId === backpackRequestIdRef.current) {
          setLoadingBackpack(false);
        }
      }
    },
    [
      backpackCategory,
      backpackEffectMode,
      backpackEquipped,
      backpackKeyword,
      backpackPage,
      backpackPageSize,
      backpackState,
      backpackCacheKey,
      message,
    ],
  );

  const refreshAfterExchange = useCallback(
    async (cosmeticCode: string, orderStatus: number) => {
      const grantPending = orderStatus === SHOP_ORDER_STATUS.PAID;
      notifyCosmeticUpdated(accountId);

      // PAID 代表积分已扣除但权益仍在异步发放。不要把发放前的空背包/旧
      // 商城结果再次写入缓存，否则后续切换分类会持续命中这个旧快照。
      await Promise.all([
        loadStore({ cache: !grantPending }),
        loadBackpack({ cache: !grantPending }),
      ]);

      if (!grantPending) return;

      // Outbox/Kafka 发放通常很快完成；短暂轮询让购买成功后的背包能自动
      // 收敛到最新状态，不要求用户手动大刷新。
      for (let attempt = 0; attempt < 6; attempt += 1) {
        if (attempt > 0) {
          await new Promise<void>((resolve) => {
            window.setTimeout(resolve, 500);
          });
        }
        const res = await fetchCosmeticBackpackApi({
          page: 1,
          size: 1,
          keyword: cosmeticCode,
        });
        const delivered =
          res.code === 200 &&
          (res.data || []).some((item) => item.cosmeticCode === cosmeticCode);
        if (!delivered) continue;

        notifyCosmeticUpdated(accountId);
        await Promise.all([loadStore(), loadBackpack()]);
        return;
      }
    },
    [accountId, loadBackpack, loadStore],
  );

  useEffect(() => {
    if (!isLoggedIn) {
      setPoints(0);
      return;
    }
    const cachedPoints = getPageDataCache<number>(pointsCacheKey);
    if (cachedPoints === undefined) {
      void loadPoints();
    } else {
      setPoints(cachedPoints);
    }
  }, [isLoggedIn, loadPoints, pointsCacheKey]);

  useEffect(() => {
    const cachedStore = getPageDataCache<IShopItem[]>(storeCacheKey);
    if (cachedStore) {
      setItems(cachedStore);
      setLoadingStore(false);
      return;
    }
    void loadStore();
  }, [loadStore, storeCacheKey]);

  useEffect(() => {
    if (!isLoggedIn) {
      setBackpack([]);
      setBackpackTotal(0);
      setLoadingBackpack(false);
      return;
    }
    const cachedBackpack = getPageDataCache<BackpackCache>(backpackCacheKey);
    if (cachedBackpack) {
      setBackpack(cachedBackpack.items);
      setBackpackTotal(cachedBackpack.total);
      setLoadingBackpack(false);
      return;
    }
    void loadBackpack();
  }, [backpackCacheKey, isLoggedIn, loadBackpack]);

  useEffect(() => {
    if (!isLoggedIn) setTab('store');
  }, [isLoggedIn]);

  const categoryStoreItems = useMemo(
    () => filterByShopCategory(items, storeCategory),
    [items, storeCategory],
  );

  const storeItems = useMemo(
    () => applyShopItemFilters(categoryStoreItems, shopFilters),
    [categoryStoreItems, shopFilters],
  );

  const backpackItems = backpack;

  const resolveBackpackItem = useCallback(
    async (cosmeticCode: string) => {
      const cached = backpack.find(
        (item) => item.cosmeticCode === cosmeticCode,
      );
      if (cached) return cached;
      const res = await fetchCosmeticBackpackApi({
        page: 1,
        size: 1,
        keyword: cosmeticCode,
      });
      if (res.code !== 200) return undefined;
      return (res.data || []).find(
        (item) => item.cosmeticCode === cosmeticCode,
      );
    },
    [backpack],
  );

  const handleEquip = async (item: IUserCosmeticItem) => {
    if (!item.slot) return;
    setActingCode(item.cosmeticCode);
    try {
      const res = await equipCosmeticApi({
        slot: item.slot,
        code: item.cosmeticCode,
      });
      if (res.code !== 200) throw new Error(res.message || '装备失败');
      message.success(
        isAvatarFrameCosmeticCode(item.cosmeticCode)
          ? '已装备头像挂件'
          : isProfileBgCosmeticCode(item.cosmeticCode)
            ? '已装备，个人主页背景已更新'
            : '已装备，装扮已生效',
      );
      notifyCosmeticUpdated(accountId);
      void loadBackpack();
      void loadStore();
    } catch (error) {
      message.error(formatApiError('装备失败', error));
    } finally {
      setActingCode(null);
    }
  };

  const handleEquipFromStore = async (item: IShopItem) => {
    const bpItem = await resolveBackpackItem(item.cosmeticCode);
    if (!bpItem) {
      message.warning('背包中未找到该装扮，请刷新后重试');
      changeTab('backpack');
      return;
    }
    await handleEquip(bpItem);
  };

  const handleExchange = async (item: IShopItem) => {
    if (item.canBuy === false) {
      message.warning(item.cannotBuyReason || '当前不可兑换');
      return;
    }
    setExchangingId(item.id);
    try {
      const requestId =
        typeof crypto.randomUUID === 'function'
          ? crypto.randomUUID()
          : `exchange-${item.id}-${Date.now()}`;
      const res = await exchangeShopItemApi({
        itemId: item.id,
        quantity: 1,
        requestId,
      });
      if (res.code !== 200) throw new Error(res.message || '兑换失败');
      const orderStatus = res.data.status;
      if (
        orderStatus === SHOP_ORDER_STATUS.FAILED ||
        orderStatus === SHOP_ORDER_STATUS.CANCELLED
      ) {
        throw new Error(res.data.statusText || '兑换失败');
      }
      if (
        orderStatus !== SHOP_ORDER_STATUS.PAID &&
        orderStatus !== SHOP_ORDER_STATUS.COMPLETED
      ) {
        throw new Error('兑换订单未完成，请稍后查询订单状态');
      }
      message.success(
        orderStatus === SHOP_ORDER_STATUS.PAID
          ? '兑换成功，权益正在发放'
          : '兑换成功，请到背包装备后生效',
      );
      changeTab('backpack');
      const nextPoints = Number(res.data.pointsBalance);
      setPoints(nextPoints);
      setPageDataCache(pointsCacheKey, nextPoints);
      void refreshAfterExchange(item.cosmeticCode, orderStatus).catch(
        () => undefined,
      );
    } catch (error) {
      message.error(formatApiError('兑换失败', error));
    } finally {
      setExchangingId(null);
    }
  };

  const handleUnequip = async (item: IUserCosmeticItem) => {
    if (!item.slot) return;
    setActingCode(item.cosmeticCode);
    try {
      const res = await unequipCosmeticApi({ slot: item.slot });
      if (res.code !== 200) throw new Error(res.message || '卸下失败');
      message.success('已卸下');
      notifyCosmeticUpdated(accountId);
      void loadBackpack();
      void loadStore();
    } catch (error) {
      message.error(formatApiError('卸下失败', error));
    } finally {
      setActingCode(null);
    }
  };

  const handleUse = async (item: IUserCosmeticItem) => {
    setActingCode(item.cosmeticCode);
    try {
      const res = await consumeCosmeticApi({ code: item.cosmeticCode });
      if (res.code !== 200) throw new Error(res.message || '使用失败');
      message.success('已使用');
      notifyCosmeticUpdated(accountId);
      void loadBackpack();
    } catch (error) {
      message.error(formatApiError('使用失败', error));
    } finally {
      setActingCode(null);
    }
  };

  const previewName = user?.username || '预览用户';
  const previewAvatar = user?.avatar;

  const renderStoreCard = (item: IShopItem) => (
    <CosmeticShopCard
      key={item.id}
      cosmeticCode={item.cosmeticCode}
      name={item.name}
      description={item.description || item.cosmeticCode}
      pricePoints={item.pricePoints}
      stock={item.stock}
      repurchasePolicy={item.repurchasePolicy}
      limitCount={item.limitCount}
      limitWindowSeconds={item.limitWindowSeconds}
      icon={item.icon}
      owned={item.owned}
      equipped={item.equipped}
      canBuy={item.canBuy}
      cannotBuyReason={item.cannotBuyReason}
      previewName={previewName}
      previewAvatar={previewAvatar}
      loading={exchangingId === item.id || actingCode === item.cosmeticCode}
      actionLabel={!isLoggedIn ? '登录后兑换' : undefined}
      actionDisabled={
        isLoggedIn && (item.owned ? item.equipped : item.canBuy === false)
      }
      onAction={() => {
        if (!isLoggedIn) {
          openAuth('login');
          return;
        }
        if (item.owned) {
          void handleEquipFromStore(item);
          return;
        }
        void handleExchange(item);
      }}
    />
  );

  const renderBackpackCard = (item: IUserCosmeticItem) => {
    const isProfileBg = isProfileBgCosmeticCode(item.cosmeticCode);
    const isAvatarFrame = isAvatarFrameCosmeticCode(item.cosmeticCode);

    const equipAction =
      item.effectMode === 'EQUIP' ? (
        item.equipped ? (
          <Button
            block
            loading={actingCode === item.cosmeticCode}
            onClick={() => void handleUnequip(item)}
          >
            卸下
          </Button>
        ) : (
          <Button
            block
            type="primary"
            disabled={item.state === 'EXPIRED'}
            loading={actingCode === item.cosmeticCode}
            onClick={() => void handleEquip(item)}
          >
            {isProfileBg ? '装备到主页' : isAvatarFrame ? '装备挂件' : '装备'}
          </Button>
        )
      ) : null;

    const useAction =
      item.effectMode === 'CONSUMABLE' && item.canUse ? (
        <Button
          block
          disabled={item.state === 'EXPIRED'}
          type="primary"
          loading={actingCode === item.cosmeticCode}
          onClick={() => void handleUse(item)}
        >
          使用
        </Button>
      ) : null;

    return (
      <CosmeticShopCard
        key={item.cosmeticCode}
        cosmeticCode={item.cosmeticCode}
        name={item.name || item.cosmeticCode}
        description={
          item.slot ? SLOT_LABEL[item.slot as CosmeticSlot] : undefined
        }
        icon={item.previewUrl}
        slot={item.slot}
        owned
        equipped={item.equipped}
        state={item.state}
        assetJson={item.assetJson}
        previewName={previewName}
        previewAvatar={previewAvatar}
        extraActions={
          equipAction || useAction ? (
            <Space orientation="vertical" className="shop-page__action">
              {equipAction}
              {useAction}
            </Space>
          ) : undefined
        }
      />
    );
  };

  const renderCategorySegmented = (
    value: ShopCategory,
    onChange: (value: ShopCategory) => void,
  ) => (
    <Segmented
      className="shop-page__category"
      value={value}
      onChange={(next) => onChange(next as ShopCategory)}
      options={SHOP_CATEGORY_OPTIONS}
    />
  );

  const renderStoreFilters = () => (
    <div className="shop-page__filters">
      <Segmented
        className="shop-page__filter"
        value={ownedFilter}
        onChange={(value) => setOwnedFilter(value as ShopOwnedFilter)}
        options={SHOP_OWNED_FILTER_OPTIONS}
      />
      <Segmented
        className="shop-page__filter"
        value={priceSort}
        onChange={(value) => setPriceSort(value as ShopPriceSort)}
        options={SHOP_PRICE_SORT_OPTIONS}
      />
    </div>
  );

  const renderBackpackFilters = () => (
    <div className="shop-page__filters shop-page__backpack-filters">
      <Select
        allowClear
        className="shop-page__filter"
        placeholder="装扮类型"
        value={backpackEffectMode}
        options={[
          { label: '装备类', value: 'EQUIP' },
          { label: '消耗类', value: 'CONSUMABLE' },
        ]}
        onChange={(value) => {
          setBackpackEffectMode(value);
          setBackpackPage(1);
        }}
      />
      <Select
        allowClear
        className="shop-page__filter"
        placeholder="装备状态"
        value={
          backpackEquipped === undefined
            ? undefined
            : backpackEquipped
              ? 'EQUIPPED'
              : 'UNEQUIPPED'
        }
        options={[
          { label: '已装备', value: 'EQUIPPED' },
          { label: '未装备', value: 'UNEQUIPPED' },
        ]}
        onChange={(value) => {
          setBackpackEquipped(
            value === undefined ? undefined : value === 'EQUIPPED',
          );
          setBackpackPage(1);
        }}
      />
      <Select
        allowClear
        className="shop-page__filter"
        placeholder="有效状态"
        value={backpackState}
        options={[
          { label: '有效', value: 'ACTIVE' },
          { label: '已过期', value: 'EXPIRED' },
        ]}
        onChange={(value) => {
          setBackpackState(value);
          setBackpackPage(1);
        }}
      />
      <Input.Search
        allowClear
        className="shop-page__filter"
        placeholder="搜索装扮名称或编码"
        onSearch={(value) => {
          setBackpackKeyword(value.trim());
          setBackpackPage(1);
        }}
      />
    </div>
  );

  return (
    <div className="shop-page">
      <Tabs
        activeKey={tab}
        animated={false}
        onChange={changeTab}
        className="shop-page__tabs"
        tabBarExtraContent={
          isLoggedIn ? (
            <Tag color="orange" className="shop-page__points">
              积分 {points}
            </Tag>
          ) : (
            <Button type="link" onClick={() => openAuth('login')}>
              登录查看积分
            </Button>
          )
        }
        items={isLoggedIn ? SHOP_TABS : SHOP_TABS.slice(0, 1)}
      />

      <section className="shop-page__panel">
        {tab === 'store' ? (
          <Spin spinning={loadingStore}>
            {renderCategorySegmented(storeCategory, setStoreCategory)}
            {renderStoreFilters()}
            <div className="shop-page__grid">
              {storeItems.map(renderStoreCard)}
            </div>
            {!loadingStore && storeItems.length === 0 ? (
              <Empty description="暂无商品" />
            ) : null}
          </Spin>
        ) : (
          <Spin spinning={loadingBackpack}>
            {renderCategorySegmented(backpackCategory, (value) => {
              setBackpackCategory(value);
              setBackpackPage(1);
            })}
            {renderBackpackFilters()}
            <div className="shop-page__grid">
              {backpackItems.map(renderBackpackCard)}
            </div>
            {!loadingBackpack && backpackItems.length === 0 ? (
              <Empty
                description={
                  backpackTotal === 0
                    ? '背包空空如也，去商城兑换吧'
                    : '该分类下暂无装扮'
                }
              />
            ) : null}
            {backpackTotal > 0 ? (
              <div className="shop-page__pagination">
                <Pagination
                  current={backpackPage}
                  pageSize={backpackPageSize}
                  total={backpackTotal}
                  hideOnSinglePage
                  showSizeChanger
                  showTotal={(total) => `共 ${total} 件`}
                  onChange={(page, pageSize) => {
                    setBackpackPage(page);
                    setBackpackPageSize(pageSize);
                  }}
                />
              </div>
            ) : null}
          </Spin>
        )}
      </section>
    </div>
  );
}

export default ShopPage;
