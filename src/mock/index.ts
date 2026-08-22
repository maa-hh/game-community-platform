import Mock from 'mockjs';
import {
  ENABLE_MOCK,
  CODE_ACCESS_EXPIRED,
  CODE_REFRESH_EXPIRED,
  REFRESH_COOKIE_NAME,
} from '@/service/config';

// 模拟真实网络延迟（毫秒）
Mock.setup({
  timeout: '400-900',
});

// Mock 用户库（模拟后端数据库，注册后写入 localStorage，刷新后仍可登录）
interface IMockUser {
  id: number;
  accountId: number;
  email: string;
  password: string;
  nickname: string;
}

const MOCK_USERS_KEY = 'game_community_mock_users';

/** access 短期有效期（秒）— mock 设短一点方便观察刷新 */
const ACCESS_TTL_SEC = 30;
/** refresh 长期有效期（秒） */
const REFRESH_TTL_SEC = 7 * 24 * 60 * 60;

const DEFAULT_USERS: IMockUser[] = [
  {
    id: 1,
    accountId: 10001,
    email: 'test@game.com',
    password: 'abc123',
    nickname: '游戏达人',
  },
  {
    id: 2,
    accountId: 10002,
    email: 'player@game.com',
    password: 'pass99',
    nickname: '硬核玩家',
  },
];

function loadMockUsers(): IMockUser[] {
  try {
    const raw = localStorage.getItem(MOCK_USERS_KEY);
    if (!raw) return [...DEFAULT_USERS];
    const stored = JSON.parse(raw) as IMockUser[];
    if (
      !Array.isArray(stored) ||
      stored.length === 0 ||
      stored.some((user) => !Number.isInteger(user.accountId))
    ) {
      return [...DEFAULT_USERS];
    }
    const emails = new Set(stored.map((u) => u.email.toLowerCase()));
    const merged = [...stored];
    DEFAULT_USERS.forEach((user) => {
      if (!emails.has(user.email.toLowerCase())) {
        merged.push(user);
      }
    });
    return merged;
  } catch {
    return [...DEFAULT_USERS];
  }
}

function saveMockUsers(users: IMockUser[]): void {
  localStorage.setItem(MOCK_USERS_KEY, JSON.stringify(users));
}

let mockUsers: IMockUser[] = loadMockUsers();

// 验证码存储：bizType:email → code（与后端 Redis codeKey 一致）
const verifyCodeStore: Record<string, { code: string; expireAt: number }> = {};
const sendCodeHistory: Record<string, number[]> = {};

const SEND_INTERVAL_MS = 60 * 1000;
const DAILY_SEND_LIMIT = 10;
const CODE_EXPIRE_MS = 5 * 60 * 1000;
const CODE_EXPIRE_SEC = CODE_EXPIRE_MS / 1000;
const MOCK_VERIFY_CODE = '123456';

type CodeBizType =
  'REGISTER' | 'RESET_PASSWORD' | 'CHANGE_EMAIL' | 'CANCEL_ACCOUNT';

function normalizeBizType(bizType?: string): CodeBizType {
  if (bizType === 'RESET_PASSWORD') return 'RESET_PASSWORD';
  if (bizType === 'CHANGE_EMAIL') return 'CHANGE_EMAIL';
  if (bizType === 'CANCEL_ACCOUNT') return 'CANCEL_ACCOUNT';
  return 'REGISTER';
}

function codeStoreKey(bizType: CodeBizType, email: string): string {
  return `${bizType}:${normalizeEmail(email)}`;
}

function parseRequestBody<T extends Record<string, unknown>>(
  body?: string | T,
): T {
  if (!body) return {} as T;
  if (typeof body === 'string') {
    try {
      return JSON.parse(body) as T;
    } catch {
      return {} as T;
    }
  }
  return body;
}

function normalizeEmail(email: string): string {
  return email.trim().toLowerCase();
}

function findUserByEmail(email: string): IMockUser | undefined {
  const target = normalizeEmail(email);
  return mockUsers.find((item) => normalizeEmail(item.email) === target);
}

function findUserByAccountId(accountId: number): IMockUser | undefined {
  return mockUsers.find((item) => item.accountId === accountId);
}

function getTodaySendCount(key: string): number {
  const now = Date.now();
  const history = sendCodeHistory[key] || [];
  const todayHistory = history.filter((t) => now - t < 24 * 60 * 60 * 1000);
  sendCodeHistory[key] = todayHistory;
  return todayHistory.length;
}

function clearRefreshCookie() {
  document.cookie = `${REFRESH_COOKIE_NAME}=; Path=/; Max-Age=0; SameSite=Strict`;
}

/** 自描述 token：type.accountId.expireAt.random — 模拟真实 access JWT 的公开字段。 */
function issueTokenPair(accountId: number) {
  const now = Date.now();
  const accessExpireAt = now + ACCESS_TTL_SEC * 1000;
  const refreshExpireAt = now + REFRESH_TTL_SEC * 1000;
  const rand = () => Math.random().toString(36).slice(2, 10);
  return {
    accessToken: `access.${accountId}.${accessExpireAt}.${rand()}`,
    refreshToken: `refresh.${accountId}.${refreshExpireAt}.${rand()}`,
    accessExpiresIn: ACCESS_TTL_SEC,
    refreshExpiresIn: REFRESH_TTL_SEC,
  };
}

/**
 * 模拟服务端 Set-Cookie: refresh=...; HttpOnly
 * 注意：浏览器 JS 无法真正设置 HttpOnly，开发环境用普通 Cookie 模拟路径
 */
function setRefreshCookie(refreshToken: string, maxAgeSec: number) {
  document.cookie = `${REFRESH_COOKIE_NAME}=${encodeURIComponent(
    refreshToken,
  )}; Path=/; Max-Age=${maxAgeSec}; SameSite=Strict`;
}

function readRefreshCookie(): string {
  const match = document.cookie
    .split(';')
    .map((item) => item.trim())
    .find((item) => item.startsWith(`${REFRESH_COOKIE_NAME}=`));
  if (!match) return '';
  return decodeURIComponent(match.slice(REFRESH_COOKIE_NAME.length + 1));
}

function parseToken(
  token: string,
  type: 'access' | 'refresh',
): { accountId: number; expireAt: number } | null {
  const parts = token.split('.');
  if (parts[0] !== type || parts.length < 4) return null;
  const accountId = Number(parts[1]);
  const expireAt = Number(parts[2]);
  if (!accountId || !expireAt) return null;
  return { accountId, expireAt };
}

function getClientAccessToken(): string {
  return (
    (window as Window & { __MOCK_ACCESS_TOKEN__?: string })
      .__MOCK_ACCESS_TOKEN__ || ''
  );
}

/** 校验短期 access；失败返回业务错误体 */
function assertAccessToken() {
  const token = getClientAccessToken();
  const parsed = parseToken(token, 'access');
  if (!parsed) {
    return {
      code: CODE_ACCESS_EXPIRED,
      message: 'accessToken 无效，请刷新或重新登录',
      data: null,
    };
  }
  if (Date.now() > parsed.expireAt) {
    return {
      code: CODE_ACCESS_EXPIRED,
      message: 'accessToken 已过期',
      data: null,
    };
  }
  if (!findUserByAccountId(parsed.accountId)) {
    return {
      code: CODE_ACCESS_EXPIRED,
      message: 'accessToken 对应用户不存在',
      data: null,
    };
  }
  return null;
}

function getAccessUser(): IMockUser | null {
  const denied = assertAccessToken();
  if (denied) return null;
  const parsed = parseToken(getClientAccessToken(), 'access');
  if (!parsed) return null;
  return findUserByAccountId(parsed.accountId) || null;
}

/** JSON 只返回 access；refresh 写入 Cookie */
function buildAuthResponse(user: IMockUser) {
  const tokens = issueTokenPair(user.accountId);
  setRefreshCookie(tokens.refreshToken, tokens.refreshExpiresIn);
  return {
    accessToken: tokens.accessToken,
    accessExpiresIn: tokens.accessExpiresIn,
    user: {
      accountId: user.accountId,
      email: user.email,
      username: user.nickname,
    },
  };
}

// 开发环境 mock 数据：拦截 URL 返回虚拟数据
if (ENABLE_MOCK) {
  // 发送邮箱验证码（与后端 send-code 对齐）
  Mock.mock(/\/user\/auth\/send-code/, 'post', (options: { body?: string }) => {
    const { email, bizType } = parseRequestBody<{
      email?: string;
      bizType?: CodeBizType;
    }>(options.body);

    if (!email) {
      return { code: 400, message: '请输入邮箱', data: null };
    }

    const normalizedEmail = normalizeEmail(email);
    const type = normalizeBizType(bizType);
    const historyKey = codeStoreKey(type, normalizedEmail);

    if (type === 'REGISTER' && findUserByEmail(normalizedEmail)) {
      return { code: 409, message: '该邮箱已被注册', data: null };
    }
    if (type === 'RESET_PASSWORD' && !findUserByEmail(normalizedEmail)) {
      return { code: 404, message: '该邮箱未注册', data: null };
    }
    if (type === 'CHANGE_EMAIL' && findUserByEmail(normalizedEmail)) {
      return { code: 409, message: '该邮箱已被占用', data: null };
    }
    if (type === 'CANCEL_ACCOUNT') {
      return {
        code: 400,
        message: '请登录后在账号安全中申请注销验证码',
        data: null,
      };
    }

    const now = Date.now();
    const todayCount = getTodaySendCount(historyKey);

    if (todayCount >= DAILY_SEND_LIMIT) {
      return {
        code: 429,
        message: '今日发送次数已达上限，请明天再试',
        data: null,
      };
    }

    const history = sendCodeHistory[historyKey] || [];
    const lastSend = history[history.length - 1];
    if (lastSend && now - lastSend < SEND_INTERVAL_MS) {
      const waitSec = Math.ceil((SEND_INTERVAL_MS - (now - lastSend)) / 1000);
      return {
        code: 429,
        message: `发送过于频繁，请 ${waitSec} 秒后再试`,
        data: null,
      };
    }

    verifyCodeStore[historyKey] = {
      code: MOCK_VERIFY_CODE,
      expireAt: now + CODE_EXPIRE_MS,
    };
    sendCodeHistory[historyKey] = [...history, now];

    return {
      code: 200,
      message: 'success',
      data: { expireIn: CODE_EXPIRE_SEC },
    };
  });

  // 登录
  Mock.mock(/\/user\/auth\/login/, 'post', (options: { body?: string }) => {
    const { email, password } = parseRequestBody<{
      email?: string;
      password?: string;
    }>(options.body);

    if (!email) {
      return { code: 400, message: '请输入邮箱', data: null };
    }
    if (!password) {
      return { code: 400, message: '请输入密码', data: null };
    }

    const user = findUserByEmail(email);

    if (!user) {
      return { code: 404, message: '该邮箱未注册', data: null };
    }

    if (user.password !== password) {
      return { code: 401, message: '密码错误', data: null };
    }

    return {
      code: 200,
      message: 'success',
      data: buildAuthResponse(user),
    };
  });

  // 注册
  Mock.mock(/\/user\/auth\/register/, 'post', (options: { body?: string }) => {
    const { email, password, code } = parseRequestBody<{
      email?: string;
      password?: string;
      code?: string;
    }>(options.body);

    if (!email) {
      return { code: 400, message: '请输入邮箱', data: null };
    }
    if (!password) {
      return { code: 400, message: '请设置密码', data: null };
    }
    if (!code) {
      return { code: 400, message: '请输入验证码', data: null };
    }

    const normalizedEmail = normalizeEmail(email);

    if (findUserByEmail(normalizedEmail)) {
      return { code: 409, message: '该邮箱已被注册', data: null };
    }

    const stored = verifyCodeStore[codeStoreKey('REGISTER', normalizedEmail)];
    if (!stored) {
      return { code: 400, message: '验证码已过期，请重新获取', data: null };
    }

    if (Date.now() > stored.expireAt) {
      return { code: 400, message: '验证码已过期，请重新获取', data: null };
    }

    if (stored.code !== code) {
      return { code: 400, message: '验证码错误', data: null };
    }

    const newUser: IMockUser = {
      id: Date.now(),
      accountId: 10000 + mockUsers.length + 1,
      email: normalizedEmail,
      password,
      nickname: normalizedEmail.split('@')[0] || '新玩家',
    };
    mockUsers = [...mockUsers, newUser];
    saveMockUsers(mockUsers);
    delete verifyCodeStore[codeStoreKey('REGISTER', normalizedEmail)];

    return {
      code: 200,
      message: 'success',
      data: { email: newUser.email },
    };
  });

  // 找回密码
  Mock.mock(
    /\/user\/auth\/reset-password/,
    'post',
    (options: { body?: string }) => {
      const { email, password, code } = parseRequestBody<{
        email?: string;
        password?: string;
        code?: string;
      }>(options.body);

      if (!email) {
        return { code: 400, message: '请输入邮箱', data: null };
      }
      if (!password) {
        return { code: 400, message: '请设置新密码', data: null };
      }
      if (!code) {
        return { code: 400, message: '请输入验证码', data: null };
      }

      const normalizedEmail = normalizeEmail(email);
      const user = findUserByEmail(normalizedEmail);
      if (!user) {
        return { code: 404, message: '该邮箱未注册', data: null };
      }

      const stored =
        verifyCodeStore[codeStoreKey('RESET_PASSWORD', normalizedEmail)];
      if (!stored || Date.now() > stored.expireAt) {
        return { code: 400, message: '验证码已过期，请重新获取', data: null };
      }
      if (stored.code !== code) {
        return { code: 400, message: '验证码错误', data: null };
      }

      mockUsers = mockUsers.map((item) =>
        normalizeEmail(item.email) === normalizedEmail
          ? { ...item, password }
          : item,
      );
      saveMockUsers(mockUsers);
      delete verifyCodeStore[codeStoreKey('RESET_PASSWORD', normalizedEmail)];
      clearRefreshCookie();

      return { code: 200, message: 'success', data: null };
    },
  );

  // 登出
  Mock.mock(/\/user\/auth\/logout/, 'post', () => {
    clearRefreshCookie();
    return { code: 200, message: 'success', data: null };
  });

  // 修改密码
  Mock.mock(/\/user\/password/, 'put', (options: { body?: string }) => {
    const denied = assertAccessToken();
    if (denied) return denied;

    const user = getAccessUser();
    if (!user) {
      return {
        code: CODE_ACCESS_EXPIRED,
        message: '请先登录',
        data: null,
      };
    }

    const { oldPassword, newPassword } = parseRequestBody<{
      oldPassword?: string;
      newPassword?: string;
    }>(options.body);

    if (!oldPassword) {
      return { code: 400, message: '请输入原密码', data: null };
    }
    if (!newPassword) {
      return { code: 400, message: '请输入新密码', data: null };
    }
    if (user.password !== oldPassword) {
      return { code: 400, message: '原密码不正确', data: null };
    }
    if (oldPassword === newPassword) {
      return { code: 400, message: '新密码不能与原密码相同', data: null };
    }

    mockUsers = mockUsers.map((item) =>
      item.id === user.id ? { ...item, password: newPassword } : item,
    );
    saveMockUsers(mockUsers);
    clearRefreshCookie();

    return { code: 200, message: 'success', data: null };
  });

  // 修改邮箱
  Mock.mock(/\/user\/email/, 'put', (options: { body?: string }) => {
    const denied = assertAccessToken();
    if (denied) return denied;

    const user = getAccessUser();
    if (!user) {
      return {
        code: CODE_ACCESS_EXPIRED,
        message: '请先登录',
        data: null,
      };
    }

    const { email, code } = parseRequestBody<{
      email?: string;
      code?: string;
    }>(options.body);

    if (!email) {
      return { code: 400, message: '请输入新邮箱', data: null };
    }
    if (!code) {
      return { code: 400, message: '请输入验证码', data: null };
    }

    const normalizedEmail = normalizeEmail(email);
    if (normalizeEmail(user.email) === normalizedEmail) {
      return { code: 400, message: '新邮箱不能与当前邮箱相同', data: null };
    }
    if (findUserByEmail(normalizedEmail)) {
      return { code: 409, message: '该邮箱已被占用', data: null };
    }

    const stored =
      verifyCodeStore[codeStoreKey('CHANGE_EMAIL', normalizedEmail)];
    if (!stored || Date.now() > stored.expireAt) {
      return { code: 400, message: '验证码已过期，请重新获取', data: null };
    }
    if (stored.code !== code) {
      return { code: 400, message: '验证码错误', data: null };
    }

    mockUsers = mockUsers.map((item) =>
      item.id === user.id ? { ...item, email: normalizedEmail } : item,
    );
    saveMockUsers(mockUsers);
    delete verifyCodeStore[codeStoreKey('CHANGE_EMAIL', normalizedEmail)];

    return {
      code: 200,
      message: 'success',
      data: { email: normalizedEmail },
    };
  });

  // 注销：向当前绑定邮箱发验证码（需登录）
  Mock.mock(/\/user\/cancel\/send-code/, 'post', () => {
    const denied = assertAccessToken();
    if (denied) return denied;

    const user = getAccessUser();
    if (!user) {
      return {
        code: CODE_ACCESS_EXPIRED,
        message: '请先登录',
        data: null,
      };
    }

    const historyKey = codeStoreKey('CANCEL_ACCOUNT', user.email);
    const now = Date.now();
    const todayCount = getTodaySendCount(historyKey);
    if (todayCount >= DAILY_SEND_LIMIT) {
      return {
        code: 429,
        message: '今日发送次数已达上限，请明天再试',
        data: null,
      };
    }

    const history = sendCodeHistory[historyKey] || [];
    const recent = history.filter((t) => now - t < SEND_INTERVAL_MS);
    if (recent.length > 0) {
      return { code: 429, message: '发送过于频繁，请稍后再试', data: null };
    }

    const code = String(Math.floor(100000 + Math.random() * 900000));
    verifyCodeStore[historyKey] = {
      code,
      expireAt: now + CODE_EXPIRE_MS,
    };
    sendCodeHistory[historyKey] = [...history, now];

    return {
      code: 200,
      message: 'success',
      data: { expireIn: CODE_EXPIRE_SEC },
    };
  });

  // 申请注销
  Mock.mock(/\/user\/cancel$/, 'post', (options: { body?: string }) => {
    const denied = assertAccessToken();
    if (denied) return denied;

    const user = getAccessUser();
    if (!user) {
      return {
        code: CODE_ACCESS_EXPIRED,
        message: '请先登录',
        data: null,
      };
    }

    const { code } = parseRequestBody<{ code?: string }>(options.body);
    if (!code) {
      return { code: 400, message: '请输入验证码', data: null };
    }

    const stored = verifyCodeStore[codeStoreKey('CANCEL_ACCOUNT', user.email)];
    if (!stored || Date.now() > stored.expireAt) {
      return { code: 400, message: '验证码已过期，请重新获取', data: null };
    }
    if (stored.code !== code) {
      return { code: 400, message: '验证码错误', data: null };
    }

    delete verifyCodeStore[codeStoreKey('CANCEL_ACCOUNT', user.email)];
    clearRefreshCookie();

    return { code: 200, message: 'success', data: null };
  });

  // 刷新
  Mock.mock(/\/user\/auth\/refresh/, 'post', () => {
    const refreshToken = readRefreshCookie();

    if (!refreshToken) {
      return {
        code: CODE_REFRESH_EXPIRED,
        message: 'refreshToken Cookie 不存在或已失效',
        data: null,
      };
    }

    const parsed = parseToken(refreshToken, 'refresh');
    if (!parsed) {
      return {
        code: CODE_REFRESH_EXPIRED,
        message: 'refreshToken 无效',
        data: null,
      };
    }

    if (Date.now() > parsed.expireAt) {
      return {
        code: CODE_REFRESH_EXPIRED,
        message: 'refreshToken 已过期，请重新登录',
        data: null,
      };
    }

    const user = findUserByAccountId(parsed.accountId);
    if (!user) {
      return {
        code: CODE_REFRESH_EXPIRED,
        message: '用户不存在，请重新登录',
        data: null,
      };
    }

    const tokens = issueTokenPair(user.accountId);
    setRefreshCookie(tokens.refreshToken, tokens.refreshExpiresIn);

    return {
      code: 200,
      message: 'success',
      data: {
        accessToken: tokens.accessToken,
        accessExpiresIn: tokens.accessExpiresIn,
      },
    };
  });

  // Banner 列表（需有效 accessToken）
  Mock.mock(/\/api\/banner/, 'get', () => {
    const denied = assertAccessToken();
    if (denied) return denied;
    return {
      code: 200,
      message: 'success',
      data: [
        {
          id: 1,
          title: '艾尔登法环 DLC 上线',
          imageUrl: 'https://picsum.photos/seed/banner1/800/300',
          link: '/game/1',
        },
        {
          id: 2,
          title: '黑神话：悟空 全球热销',
          imageUrl: 'https://picsum.photos/seed/banner2/800/300',
          link: '/game/2',
        },
        {
          id: 3,
          title: 'Steam 夏季特卖开启',
          imageUrl: 'https://picsum.photos/seed/banner3/800/300',
          link: '/game/3',
        },
      ],
    };
  });

  // 游戏列表
  Mock.mock(/\/api\/games/, 'get', () => {
    const denied = assertAccessToken();
    if (denied) return denied;
    const names = [
      '艾尔登法环',
      '黑神话：悟空',
      '赛博朋克2077',
      '博德之门3',
      '只狼',
      '空洞骑士',
    ];
    return {
      code: 200,
      message: 'success',
      data: names.map((name, index) => ({
        id: index + 1,
        name,
        cover: `https://picsum.photos/seed/game${index + 1}/200/120`,
        rating: Number((8 + Math.random() * 2).toFixed(1)),
        tags: Mock.Random.shuffle(['动作', 'RPG', '开放世界', '独立']).slice(
          0,
          2,
        ),
      })),
    };
  });

  // 热榜 Top100
  Mock.mock(/\/api\/hot-article\/rank/, 'get', () => ({
    code: 200,
    message: 'success',
    data: Array.from({ length: 8 }, (_, index) => ({
      id: index + 1,
      rank: index + 1,
      accountId: 10000 + (index % 3) + 1,
      title: `热榜示例帖 #${index + 1}`,
      summary: '这是一条用于 Mock 的热榜帖子摘要。',
      coverUrl: `https://picsum.photos/seed/hot${index + 1}/640/360`,
      postType: (index % 3) + 1,
      categoryId: 1,
      categoryNames: ['综合讨论'],
      authorName: `玩家${(index % 3) + 1}`,
      authorAvatar: `https://i.pravatar.cc/150?u=hot${index + 1}`,
      likeCount: 120 - index * 5,
      commentCount: 30 - index,
      replyCount: 10,
      viewCount: 800 - index * 20,
      liked: false,
      hotScore: 999 - index * 37,
      publishedTime: new Date(Date.now() - index * 3600_000).toISOString(),
    })),
  }));

  // 推荐页数据（旧接口保留）
  Mock.mock(/\/api\/recommend/, 'get', () => {
    const denied = assertAccessToken();
    if (denied) return denied;
    return {
      code: 200,
      message: 'success',
      data: {
        banners: [
          {
            id: 10,
            title: '本周编辑推荐',
            imageUrl: 'https://picsum.photos/seed/rec1/800/300',
            link: '/recommend',
          },
        ],
        hotGames: [
          {
            id: 101,
            name: '艾尔登法环',
            cover: 'https://picsum.photos/seed/game1/200/120',
            rating: 9.5,
            tags: ['动作', '开放世界'],
          },
          {
            id: 102,
            name: '黑神话：悟空',
            cover: 'https://picsum.photos/seed/game2/200/120',
            rating: 9.2,
            tags: ['动作', 'RPG'],
          },
        ],
      },
    };
  });

  // Mock 仅在开发环境输出刷新链路，生产构建忽略该调试日志。
  // eslint-disable-next-line no-console
  console.info(
    '[Mock] refresh 走 Cookie（模拟 HttpOnly）；access≈30s，过期后自动刷新',
  );
}
