import {
  BellOutlined,
  CommentOutlined,
  HeartOutlined,
  UserAddOutlined,
} from '@ant-design/icons';
import type { NotificationCategoryKey } from '@/types/notification';

export interface NotificationCategoryConfig {
  key: NotificationCategoryKey;
  label: string;
  description: string;
  icon: typeof BellOutlined;
}

export const NOTIFICATION_CATEGORY_LIST: NotificationCategoryConfig[] = [
  {
    key: 'system',
    label: '系统通知',
    description: '审核结果、举报处理等',
    icon: BellOutlined,
  },
  {
    key: 'like_favorite',
    label: '获赞与收藏',
    description: '帖子、评论、回复的赞与收藏',
    icon: HeartOutlined,
  },
  {
    key: 'follow',
    label: '新增关注',
    description: '有人关注了你',
    icon: UserAddOutlined,
  },
  {
    key: 'comment',
    label: '评论与回复',
    description: '帖子评论与回复互动',
    icon: CommentOutlined,
  },
];

export function getCategoryUnread(
  categories: { category: NotificationCategoryKey; unreadCount: number }[],
  key: NotificationCategoryKey,
): number {
  return categories.find((c) => c.category === key)?.unreadCount ?? 0;
}
