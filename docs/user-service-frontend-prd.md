# User-Service 前端 PRD（v2 · React）

> **权威后端**：`docs/v2/user-service.md`、`docs/architecture/user-service-manifest.json`  
> **设计规范**：根目录 [`DESIGN-vercel.md`](../DESIGN-vercel.md)（Geist / Vercel 设计语言）  
> **代码目录**：[`frontend/`](../frontend/)（React 19 + Vite 6 + TypeScript）

---

## 一、目标与范围

围绕 **user-service v2** 能力，提供可联调、可演示的用户中心前端。第一阶段聚焦 **认证 + 资料 + 账户生命周期**；与 content/social 等模块共用 `frontend/` 壳，本 PRD 只约束 **用户域页面与 API 封装**。

| 优先级 | 页面 / 能力 | 路由（建议） | 后端接口 |
|--------|-------------|--------------|----------|
| P0 | 登录 / 注册 | `/auth` | A3/A2/A1 |
| P0 | 个人中心 | `/app/profile` | B1 |
| P0 | 编辑资料 / 改密 / 头像 | `/app/profile` | B2/B4/B3 |
| P1 | 用户查询 | `/app/users`、`/app/users/:accountId` | C1/C2/C4 |
| P1 | 申请注销 / 撤销注销 | `/app/profile` 危险操作区 | D1/D2 |
| P2 | 管理员封禁 / 解封 | `/app/admin/users`（可选） | D3/D4 |

**不在本阶段**：完整运营后台、Feign 调试页、定时任务可视化。

---

## 二、技术栈（已定：React）

| 层级 | 选型 | 说明 |
|------|------|------|
| 构建 | **Vite 6** | `frontend/vite.config.ts`，dev 端口 **5173** |
| UI 框架 | **React 19** | 函数组件 + Hooks |
| 语言 | **TypeScript** | 与 model 模块 DTO/VO 对齐 |
| 路由 | **React Router 7** | `frontend/src/app/App.tsx` |
| 状态 | **React Context** | `AuthContext` 管理登录态与 `/user/me` |
| HTTP | **fetch 封装** | `frontend/src/api/client.ts`（Bearer + Cookie refresh） |
| 样式 | **CSS 变量 + 组件类** | 逐步迁移至 Geist/Vercel 令牌（见 §四） |
| 测试（规划） | Vitest + Playwright | P1 起补充 |

> 旧版 PRD 中的 Vue/Element Plus **已废弃**；以 `frontend/` 现有 React 工程为准。

---

## 三、后端对接（v2 要点）

### 3.1 网关与代理

| 项 | 值 |
|----|-----|
| 网关 | `http://127.0.0.1:8080` |
| user-service | `8081`，经网关 `Path=/user/**` |
| 前端 dev | `VITE_API_BASE_URL=/api` → Vite proxy 到网关（见 `vite.config.ts`） |
| Refresh Token | **HttpOnly Cookie**，`credentials: "include"` |
| Access Token | `localStorage` key `game-community-token`，Header `Authorization: Bearer` |

### 3.2 认证接口

| 接口 | 方法 | 鉴权 | 请求 | 响应 |
|------|------|------|------|------|
| 发验证码 | `POST /user/sendCode` | 无 | `SendCodeDTO`: `phone`, **`bizType`** | `Result<Void>` **不含验证码** |
| 注册 | `POST /user/register/phone` | 无 | `RegisterDTO` | `Result<Long>` **accountId** |
| 登录 | `POST /user/login/account` | 无 | `accountId`, `password`, `type?` | `LoginVO` + Set-Cookie |
| 刷新 | `POST /user/token/refresh` | Cookie | — | `TokenRefreshVO` |
| 登出 | `POST /user/logout` | JWT | — | `Result<Void>` |

**bizType 枚举**（发码必填）：`REGISTER` | `LOGIN` | `CHANGE_PHONE` | `RESET_PASSWORD` | `BIND_PHONE` | `VERIFY_PHONE`

**RegisterDTO v2**：
- 密码：**7–32 位，必须含字母+数字**
- 验证码：**6 位数字**
- 可选 **`steamAccount`**（≤64），**不是** `gameAccount`

**LoginVO / UserVO v2**：
- 对外 ID：**`accountId`**（号池 CAS，非 `id+9999` 硬编码展示逻辑）
- Steam：**`steamAccount`**
- 账号状态在 **`UserAccount`**：`status`（NORMAL / BANNED / CANCELLING / CANCELLED）
- 头像：**`avatar`**（正式）+ **`pendingAvatarUrl`**（待审预览，仅本人）

### 3.3 资料接口

| 接口 | 说明 |
|------|------|
| `GET /user/me` | 当前用户完整 `UserVO` |
| `PUT /user/info` | `UpdateUserInfoDTO`：**必带 `version`**；改手机号需 **`phone` + `phoneCode`** |
| `POST /user/avatar` | multipart `avatar`，jpg/png/webp ≤2MB → 待审 URL |
| `PUT /user/password` | 改密成功后 **强制全端下线**（清 Cookie + token） |

### 3.4 查询接口

| 接口 | 说明 |
|------|------|
| `GET /user/{accountId}` | 他人资料；**手机号脱敏**（非本人/非管理员不可见） |
| `GET /user/simple/{accountId}` | 轻量信息 |
| `GET /user/simple/search` | 用户名前缀分页 |
| `GET /user/ids?ids=` | 批量（内部 **userId**，≤100） |

### 3.5 账户生命周期

| 接口 | 说明 |
|------|------|
| `POST /user/cancel` | 进入 **7 天冷静期**（`CANCELLING`） |
| `POST /user/cancel/revoke` | 冷静期内撤销 |
| `POST /user/{userId}/ban` | 管理员 + `BanUserDTO` |
| `POST /user/{userId}/unban` | 管理员解封 |

---

## 四、视觉设计（DESIGN-vercel.md）

遵循 **Geist 减法美学**：近白画布 + 近黑墨线，色彩仅用于 Hero 渐变与链接/语义色。

### 4.1 CSS 设计令牌（`frontend/src/styles/tokens.css` 目标）

```css
:root {
  /* Surface */
  --canvas: #fafafa;
  --canvas-elevated: #ffffff;
  --hairline: #ebebeb;

  /* Text */
  --ink: #171717;
  --body: #4d4d4d;
  --mute: #8f8f8f;
  --faint: #a1a1a1;

  /* Accent & Semantic */
  --link: #0070f3;
  --error: #ee0000;
  --warning: #f5a623;

  /* Radius */
  --rounded-sm: 6px;   /* 输入框、app 按钮 */
  --rounded-md: 12px;  /* 卡片 */
  --rounded-pill: 100px; /* 营销 CTA */

  /* Spacing base 4px */
  --space-xs: 8px;
  --space-sm: 12px;
  --space-md: 16px;
  --space-lg: 24px;
  --space-xl: 32px;
}
```

### 4.2 字体

| 用途 | 字体 | 回退 |
|------|------|------|
| 标题/正文 | **Geist Sans** | Inter, system-ui |
| 代码/行号/eyebrow | **Geist Mono** | JetBrains Mono, monospace |

Hero 标题：`48px / 600 / letter-spacing -2.4px`（`display-xl`）

### 4.3 组件映射

| DESIGN 组件 | 前端实现 | 场景 |
|-------------|----------|------|
| `nav-bar` | `AppShell` 顶栏 | 已登录区导航 |
| `button-primary` | `.btn-primary` 黑底 pill | 「进入社区」「保存」 |
| `button-ghost-sm` | `.btn-ghost` 6px 圆角 | 次要操作 |
| `text-input` | `.field input` hairline 边框 | 表单 |
| `feature-card` | `.form-card` / `.profile-card` | 资料块 |
| `code-block` | API 调试信息（dev only） | 可选 |

### 4.4 Do / Don't（摘自 DESIGN-vercel）

- **Do**：大标题用负字距；卡片先 1px hairline 再考虑阴影；营销 CTA 用 pill，表单内按钮用 6px。
- **Don't**：大面积填充 violet/cyan；正文用 `#000`；混用 pill 与 square 于同一操作区。

> 当前 `styles.css` 为暖色游戏社区主题；**用户域新页/改版**优先采用上述 Vercel 令牌，全站迁移可分期。

---

## 五、目录结构（React · 实际）

```
frontend/
├── package.json
├── vite.config.ts          # proxy /api → gateway
├── .env.development        # VITE_API_BASE_URL=/api
├── src/
│   ├── main.tsx
│   ├── app/
│   │   ├── App.tsx         # 路由表
│   │   └── AppShell.tsx    # 登录后布局
│   ├── features/auth/
│   │   └── AuthContext.tsx
│   ├── api/
│   │   ├── client.ts       # fetch + refresh + tokenStore
│   │   └── user.ts         # user-service API
│   ├── pages/
│   │   ├── AuthPage.tsx    # 登录/注册
│   │   ├── ProfilePage.tsx # 资料/改密/头像
│   │   ├── UserLookupPage.tsx
│   │   └── UserHomePage.tsx
│   ├── components/
│   │   ├── ActionButton.tsx
│   │   ├── Notice.tsx
│   │   ├── AvatarImage.tsx
│   │   └── ProtectedRoute.tsx
│   └── styles.css          # + tokens.css（规划）
```

---

## 六、路由

| 路径 | 组件 | 权限 | 说明 |
|------|------|------|------|
| `/` | `HomePage` | 无 | 落地页 |
| `/auth` | `AuthPage` | 无 | 登录/注册 Tab |
| `/app/profile` | `ProfilePage` | 登录 | 资料中心 |
| `/app/users` | `UserLookupPage` | 登录 | 搜索用户 |
| `/app/users/:accountId` | `UserHomePage` | 登录 | 他人主页 |

`ProtectedRoute`：无 token 且 refresh 失败 → 重定向 `/auth`，`state.from` 保存回跳路径。

---

## 七、API 封装（`api/user.ts` 目标签名）

```ts
export const userApi = {
  sendCode: (phone: string, bizType: SendCodeBizType) => request<void>(...),
  registerByPhone: (payload: RegisterPayload) => request<number>(...), // accountId
  loginByAccount: (payload: { accountId: number; password: string; type?: number }) => request<LoginVO>(...),
  refreshToken: () => request<TokenRefreshVO>(...),
  logout: () => request<void>(...),
  me: () => request<UserVO>(...),
  updateInfo: (payload: UpdateUserInfoDTO) => request<void>(...),
  changePassword: (payload: ChangePasswordDTO) => request<void>(...),
  uploadAvatar: (file: File) => request<string>(...),
  getUser: (accountId: number) => request<UserVO>(...),
  getSimpleUser: (accountId: number) => request<UserSimpleVO>(...),
  searchUsers: (q: UserSearchQuery) => request<PageResult<UserSimpleVO>>(...),
  cancelAccount: () => request<void>(...),
  revokeCancel: () => request<void>(...),
  banUser: (userId: number, dto: BanUserDTO) => request<void>(...),
  unbanUser: (userId: number) => request<void>(...),
};
```

### 7.1 `client.ts` 职责

1. `API_PREFIX` = `import.meta.env.VITE_API_BASE_URL ?? "/api"`
2. 请求：`Authorization: Bearer ${token}`，`credentials: "include"`
3. 响应：`code !== 200` → `ApiError`
4. **401 自动 refresh** 并重试一次；失败清 token，跳转 `/auth`
5. **禁止**依赖发码接口返回验证码（v2 仅短信/mock 日志）

### 7.2 类型（与 model 对齐）

```ts
export type SendCodeBizType =
  | "REGISTER" | "LOGIN" | "CHANGE_PHONE"
  | "RESET_PASSWORD" | "BIND_PHONE" | "VERIFY_PHONE";

export type LoginVO = {
  accessToken: string;
  accessTokenExpireIn: number;
  refreshToken?: string; // 仅调试；生产在 Cookie
  userId: number;
  accountId: number;
  username: string;
  avatar?: string;
  type: number;
  steamAccount?: string;
  auditStatus?: number;
};

export type UserVO = {
  id: number;
  accountId: number;
  username: string;
  avatar?: string;
  pendingAvatarUrl?: string;
  signature?: string;
  phone?: string;       // 本人/管理员可见
  status: number;       // UserAccount.status
  type: number;
  steamAccount?: string;
  auditStatus?: number;
  version: number;
  banUntil?: string;
  banReason?: string;
  followCount?: number;
  fansCount?: number;
};
```

---

## 八、状态管理（`AuthContext`）

| 字段 | 说明 |
|------|------|
| `user` | 当前 `UserVO`，来自 `/user/me` |
| `loading` | 启动恢复会话中 |
| `isAuthenticated` | `Boolean(user)` |

| 方法 | 行为 |
|------|------|
| `applyLogin(login)` | 写 token → `refreshMe()` |
| `refreshMe()` | 无 token 时先 `/token/refresh` → `/user/me` |
| `logout()` | `POST /logout` → 清 token/user |

持久化：**仅 accessToken**；refresh 在 Cookie；profile 不长期缓存 localStorage。

---

## 九、页面交互规格

### 9.1 登录 / 注册（`AuthPage`）

**登录**
- 字段：`accountId`（数字）、`password`
- 成功 → `applyLogin` → 回跳 `state.from` 或 `/app/dashboard`

**注册**
- 字段：`username`、`password`（7–32，含字母数字）、`phone`、`code`（6 位）
- 发码：`sendCode(phone, "REGISTER")`，按钮 **60s 倒计时**
- **生产**：不展示验证码；**开发**：提示查看 mock 手机日志或后端日志（15245537300）
- 成功：展示 **accountId**，引导切换登录 Tab

### 9.2 个人中心（`ProfilePage`）

| 区块 | 内容 |
|------|------|
| 头部 | 头像（正式/待审）、昵称、accountId、steamAccount |
| 表单 | 昵称、签名、手机号（改号需先 REGISTER/CHANGE_PHONE 发码 + phoneCode 字段） |
| 头像 | 压缩后上传；`auditStatus===1` 时轮询 `refreshMe` |
| 改密 | 旧密码/新密码/确认；成功后 **logout 并跳转 /auth** |
| 危险 | 申请注销（二次确认 + 7 天说明）；冷静期显示「撤销注销」 |

### 9.3 用户查询

- 搜索：`/user/simple/search?username=&page=&size=`
- 详情：`/user/{accountId}`；无权限降级 `/user/simple/{accountId}`

---

## 十、环境变量

```env
# frontend/.env.development
VITE_API_BASE_URL=/api
VITE_PROXY_TARGET=http://127.0.0.1:8080
```

| 变量 | 说明 |
|------|------|
| `VITE_API_BASE_URL` | 浏览器请求前缀；dev 用 `/api` 走 Vite 代理 |
| `VITE_PROXY_TARGET` | Vite 代理目标（网关 8080） |

**联调顺序**：MySQL/Redis → `start-user-service.sh` → `start-gateway.sh`（或 `start-all.sh`）→ **`start-frontend.sh`**

---

## 十一、与现网代码差异（待对齐清单）

| 项 | 现状 | PRD v2 要求 |
|----|------|-------------|
| `sendCode` | 缺 `bizType`，误当作返回 string | 传 `bizType`，返回 `void` |
| 字段名 | `gameAccount` | 改为 **`steamAccount`** |
| 注册密码 | 前端仅 6 位提示 | **7 位 + 字母数字** |
| 改手机号 | 未传 `phoneCode` | 发码 `CHANGE_PHONE` + 验证码字段 |
| 注销 | 未实现 | `cancelAccount` / `revokeCancel` |
| 视觉 | 暖色游戏主题 | 用户域逐步切 Geist/Vercel 令牌 |

---

## 十二、测试与验收

### 12.1 E2E 主路径

1. 注册：发码（REGISTER）→ 填表 → 获得 accountId  
2. 登录 → `/app/profile`  
3. 改昵称/签名 → 审核中状态展示  
4. 上传头像 → pendingAvatarUrl  
5. 改密 → 强制重新登录  
6. 登出 → 访问 `/app` 重定向 `/auth`  
7. 注销申请 → 撤销（可选）

### 12.2 验收标准

| ID | 标准 |
|----|------|
| FE-01 | Access Token 持久化；Refresh 走 Cookie |
| FE-02 | 刷新页面可恢复登录（refresh + me） |
| FE-03 | 未登录访问 `/app/*` 跳转 `/auth` |
| FE-04 | 注册成功展示 **accountId** |
| FE-05 | 资料保存带 **version**；冲突提示刷新 |
| FE-06 | 发码 **不传/不展示** 响应验证码 |
| FE-07 | 改密后全端下线 |
| FE-08 | 注销二次确认 + 冷静期文案 |
| FE-09 | 视觉符合 DESIGN-vercel 令牌（用户域页面） |
| FE-10 | `start-frontend.sh` 一键装依赖并启动 |

---

## 十三、启动脚本

```bash
# 项目根目录
./start-frontend.sh              # 前台
./start-frontend.sh background   # 后台，日志 logs/frontend.log
```

脚本行为：检测 Node/npm → 缺失则提示或尝试安装依赖 → 清理 **5173** 端口与同 PID → `npm install`（如需）→ `npm run dev`。
