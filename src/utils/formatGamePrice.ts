import type { IGamePrice } from '@/types/game';
import { parseDateTime } from '@/utils/formatTime';

export interface IGamePriceDisplay {
  isFree: boolean;
  hasDiscount: boolean;
  discountPercent?: number;
  currentPriceText: string | null;
  originalPriceText: string | null;
  discountEndDateText?: string | null;
  discountDaysLeftText?: string | null;
}

function formatCurrencyAmount(cents: number, currency?: string): string {
  const amount = (cents / 100).toFixed(2);
  if (currency === 'CNY') return `¥ ${amount}`;
  if (currency) return `${amount} ${currency}`;
  return amount;
}

function calendarDayDiff(earlier: Date, later: Date): number {
  const startEarlier = new Date(
    earlier.getFullYear(),
    earlier.getMonth(),
    earlier.getDate(),
  );
  const startLater = new Date(
    later.getFullYear(),
    later.getMonth(),
    later.getDate(),
  );
  return Math.round(
    (startLater.getTime() - startEarlier.getTime()) / 86_400_000,
  );
}

function parseDiscountEndAt(value?: string | number | null): Date | null {
  if (value == null) return null;

  if (typeof value === 'number') {
    const ms = value < 1e12 ? value * 1000 : value;
    const date = new Date(ms);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  const trimmed = value.trim();
  if (!trimmed) return null;

  if (/^\d+$/.test(trimmed)) {
    const num = Number(trimmed);
    const ms = num < 1e12 ? num * 1000 : num;
    const date = new Date(ms);
    return Number.isNaN(date.getTime()) ? null : date;
  }

  return parseDateTime(trimmed);
}

function resolveDiscountDeadline(
  endAt: Date,
): Pick<IGamePriceDisplay, 'discountEndDateText' | 'discountDaysLeftText'> {
  const now = new Date();
  const daysLeft = calendarDayDiff(now, endAt);
  const endDateText = `${endAt.getMonth() + 1} 月 ${endAt.getDate()} 日截止`;

  if (daysLeft < 0) {
    return { discountEndDateText: null, discountDaysLeftText: null };
  }

  let daysLeftText = `还有 ${daysLeft} 天截止`;
  if (daysLeft === 0) daysLeftText = '今天截止';
  else if (daysLeft === 1) daysLeftText = '还有 1 天截止';

  return {
    discountEndDateText: endDateText,
    discountDaysLeftText: daysLeftText,
  };
}

function resolveDiscountPercent(price: IGamePrice): number {
  if (price.discountPercent != null && price.discountPercent > 0) {
    return Math.round(price.discountPercent);
  }
  const { initial, finalPrice } = price;
  if (
    initial != null &&
    finalPrice != null &&
    initial > finalPrice &&
    initial > 0
  ) {
    return Math.round((1 - finalPrice / initial) * 100);
  }
  return 0;
}

export function resolveGamePriceDisplay(
  price?: IGamePrice,
): IGamePriceDisplay | null {
  if (!price) return null;

  if (price.free) {
    return {
      isFree: true,
      hasDiscount: false,
      currentPriceText: '免费',
      originalPriceText: null,
    };
  }

  if (
    (price.finalPrice ?? 0) === 0 &&
    (price.initial ?? 0) === 0 &&
    (!price.formatted?.trim() || price.formatted.trim() === '暂无价格')
  ) {
    return null;
  }

  const discountPercent = resolveDiscountPercent(price);
  const { initial, finalPrice } = price;
  const hasDiscount =
    discountPercent > 0 &&
    initial != null &&
    finalPrice != null &&
    initial > finalPrice;

  const currentPriceText =
    finalPrice != null
      ? formatCurrencyAmount(finalPrice, price.currency)
      : price.formatted?.trim() || null;

  const originalPriceText =
    hasDiscount && initial != null
      ? formatCurrencyAmount(initial, price.currency)
      : null;

  if (!currentPriceText) return null;

  const discountEndAt = hasDiscount
    ? parseDiscountEndAt(price.discountEndAt)
    : null;
  const deadline =
    discountEndAt != null ? resolveDiscountDeadline(discountEndAt) : {};

  return {
    isFree: false,
    hasDiscount,
    discountPercent: hasDiscount ? discountPercent : undefined,
    currentPriceText,
    originalPriceText,
    ...deadline,
  };
}

export function formatGamePrice(price?: IGamePrice): string | null {
  const display = resolveGamePriceDisplay(price);
  return display?.currentPriceText ?? null;
}
