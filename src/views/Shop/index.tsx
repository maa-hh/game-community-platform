import React, { useCallback, useEffect, useMemo, useState } from 'react';
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
  message,
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

const SHOP_TABS = [
  { key: 'store', label: '兑换', icon: <ShoppingOutlined /> },
  { key: 'backpack', label: '背包', icon: <GiftOutlined /> },
];

function ShopPage() {
  const { user } = useAppSelector((state) => state.auth);
  const [tab, setTab] = useState<ShopTabKey>('store');
  const [storeCategory, setStoreCategory] =
    useState<ShopCategory>('avatar_frame');
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
  const [backpackTotal, setBackpackTotal] = useState(0);
  const [ownedFilter, setOwnedFilter] = useState<ShopOwnedFilter>('all');
  const [priceSort, setPriceSort] = useState<ShopPriceSort>('default');
  const [points, setPoints] = useState(0);
  const [items, setItems] = useState<IShopItem[]>([]);
  const [backpack, setBackpack] = useState<IUserCosmeticItem[]>([]);
  // 请求在首次 effect 中立即发起，首帧直接显示 loading，避免先闪出空状态。
  const [loadingStore, setLoadingStore] = useState(true);
  const [loadingBackpack, setLoadingBackpack] = useState(true);
  const [exchangingId, setExchangingId] = useState<number | null>(null);
  const [actingCode, setActingCode] = useState<string | null>(null);

  const changeTab = useCallback((next: string) => {
    setTab(next === 'backpack' ? 'backpack' : 'store');
  }, []);

  const shopFilters = useMemo<ShopItemFilterState>(
    () => ({ owned: ownedFilter, priceSort }),
    [ownedFilter, priceSort],
  );

  const loadPoints = useCallback(async () => {
    try {
      const res = await fetchShopCurrencyApi();
      if (res.code !== 200) throw new Error(res.message || '加载失败');
      setPoints(Number(res.data?.points ?? 0));
    } catch (error) {
      message.error(formatApiError('加载积分失败', error));
    }
  }, []);

  const loadStore = useCallback(async () => {
    setLoadingStore(true);
    try {
      const res = await fetchShopItemsApi({ page: 1, size: 100 });
      if (res.code !== 200) throw new Error(res.message || '加载失败');
      setItems(res.data || []);
    } catch (error) {
      message.error(formatApiError('加载商城失败', error));
    } finally {
      setLoadingStore(false);
    }
  }, []);

  const loadBackpack = useCallback(async () => {
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
      setBackpack(res.data || []);
      setBackpackTotal(res.total || 0);
      if (
        (res.data || []).length === 0 &&
        backpackPage > 1 &&
        (res.total || 0) <= (backpackPage - 1) * backpackPageSize
      ) {
        setBackpackPage((page) => Math.max(1, page - 1));
      }
    } catch (error) {
      message.error(formatApiError('加载背包失败', error));
    } finally {
      setLoadingBackpack(false);
    }
  }, [
    backpackCategory,
    backpackEffectMode,
    backpackEquipped,
    backpackKeyword,
    backpackPage,
    backpackPageSize,
    backpackState,
  ]);

  useEffect(() => {
    void loadPoints();
    void loadStore();
    void loadBackpack();
  }, [loadPoints, loadStore, loadBackpack]);

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
      notifyCosmeticUpdated();
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
      setPoints(Number(res.data.pointsBalance));
      void loadStore();
      void loadBackpack();
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
      notifyCosmeticUpdated();
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
      actionDisabled={item.owned ? item.equipped : item.canBuy === false}
      onAction={() => {
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
            <Space direction="vertical" className="shop-page__action">
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
          <Tag color="orange" className="shop-page__points">
            积分 {points}
          </Tag>
        }
        items={SHOP_TABS}
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
