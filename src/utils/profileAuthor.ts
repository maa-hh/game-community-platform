import type { FeedItemData } from '@/types/profile';
import type { ContentCardAuthor } from '@/types/content';
import type { PostAuthor } from '@/types/post';
import { isFieldBusy, type FieldAuditStatus } from '@/service/types';
import { authorFrom } from '@/utils/mapPost';
import { getUserInfo } from '@/utils/storage';

export type ProfileAuthorSource = {
  accountId?: number;
  username?: string;
  avatar?: string;
  email?: string;
  usernameAuditStatus?: FieldAuditStatus;
  pendingUsername?: string | null;
  avatarAuditStatus?: FieldAuditStatus;
  pendingAvatarUrl?: string | null;
};

/** 与个人页 Hero 一致：含审核中 pending 字段 */
export function buildProfileDisplayAuthor(
  user: ProfileAuthorSource | null | undefined,
): PostAuthor | null {
  if (!user?.accountId) return null;

  const nickname =
    (isFieldBusy(user.usernameAuditStatus)
      ? user.pendingUsername
      : user.username) ||
    user.username ||
    user.email?.split('@')[0] ||
    `玩家${user.accountId}`;

  const avatar = isFieldBusy(user.avatarAuditStatus)
    ? user.pendingAvatarUrl || user.avatar
    : user.avatar;

  return authorFrom(user.accountId, nickname, avatar);
}

/** 信息流：自己的帖子统一用当前用户展示名，避免回退成「玩家0」 */
export function applyOwnerAuthor<T extends { author: ContentCardAuthor }>(
  items: T[],
  owner: PostAuthor | null,
): T[] {
  if (!owner?.accountId) return items;

  return items.map((item) => {
    const sameAccount =
      item.author.accountId > 0 && item.author.accountId === owner.accountId;

    if (!sameAccount) return item;

    return {
      ...item,
      author: {
        ...item.author,
        accountId: owner.accountId,
        nickname: owner.nickname,
        avatar: owner.avatar,
      },
    };
  });
}

/** 个人主页 FeedItemData 包装 */
export function applyProfileOwnerAuthor(
  items: FeedItemData[],
  owner: PostAuthor | null,
): FeedItemData[] {
  return applyOwnerAuthor(items, owner);
}

/** 用本地登录用户资料覆盖本人帖子作者（首页/关注流等） */
export function applyCurrentUserOwnerAuthor<
  T extends { author: ContentCardAuthor },
>(items: T[]): T[] {
  return applyOwnerAuthor(items, buildProfileDisplayAuthor(getUserInfo()));
}
