# User-Service 前端 PRD

> 说明：本文是早期前端 PRD 草案。当前前后端接口、公开账号号 `accountId`、HttpOnly Cookie refreshToken、头像待审临时 URL 和正式公共 URL 等细节，以 `docs/user-module-api.md` 与 `docs/user-module-technical-overview.md` 为准。

## 一、目标

围绕 `user-service` 的后端能力，实现一套轻量、可维护、容易上手的用户中心前端。第一阶段聚焦认证和个人资料，不做复杂运营后台。

核心页面：

| 页面 | 功能 |
|------|------|
| 登录页 | 账号 ID + 密码登录 |
| 注册页 | 手机号验证码注册 |
| 个人中心 | 展示当前用户详细信息 |
| 编辑资料 | 修改昵称、签名、手机号、游戏账号、头像 |
| 公开资料页 | 展示其他用户简单版或详细版信息 |
| 注销确认 | 当前用户注销账号 |

## 二、技术架构选择

建议使用：

| 技术 | 选择 | 原因 |
|------|------|------|
| 构建工具 | Vite | 启动快，配置少，上手容易 |
| 框架 | Vue 3 | 国内生态成熟，学习成本低 |
| 语言 | TypeScript | DTO/VO 类型可直接对齐后端 |
| 路由 | Vue Router | 标准页面路由方案 |
| 状态管理 | Pinia | 比 Vuex 更轻，适合用户态管理 |
| HTTP | Axios | 拦截器处理 token、错误码、重复请求 |
| UI 组件 | Element Plus | 表单、上传、弹窗、消息提示稳定 |
| 表单校验 | Element Plus Form Rules | 与页面组件一致，维护简单 |
| 代码规范 | ESLint + Prettier | 降低多人协作成本 |
| 测试 | Vitest + Playwright | 单测和端到端测试都能覆盖 |

选择理由：

1. Vue 3 + Vite + TypeScript 对新成员友好，目录结构清晰。
2. Element Plus 对后台、用户中心、表单上传场景覆盖完整。
3. Pinia 可以把登录态集中到 `userStore`，避免 token 和用户信息散落在页面里。
4. Axios 拦截器可以统一处理 `Authorization`、业务错误、登录过期跳转。

## 三、目录结构

```
web/
├── package.json
├── vite.config.ts
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── router/
│   │   └── index.ts
│   ├── stores/
│   │   └── user.ts
│   ├── api/
│   │   ├── request.ts
│   │   └── user.ts
│   ├── types/
│   │   ├── common.ts
│   │   └── user.ts
│   ├── views/
│   │   ├── auth/
│   │   │   ├── LoginView.vue
│   │   │   └── RegisterView.vue
│   │   └── user/
│   │       ├── ProfileView.vue
│   │       ├── ProfileEditView.vue
│   │       └── PublicProfileView.vue
│   ├── components/
│   │   └── user/
│   │       ├── AvatarUploader.vue
│   │       ├── UserInfoPanel.vue
│   │       └── CancelAccountDialog.vue
│   └── styles/
│       ├── variables.css
│       └── main.css
```

## 四、路由设计

| 路径 | 页面 | 权限 | 说明 |
|------|------|------|------|
| `/login` | `LoginView` | 无 | 账号密码登录 |
| `/register` | `RegisterView` | 无 | 手机号验证码注册 |
| `/user/profile` | `ProfileView` | 登录 | 当前用户信息 |
| `/user/profile/edit` | `ProfileEditView` | 登录 | 编辑当前用户资料 |
| `/user/:id` | `PublicProfileView` | 登录 | 查看其他用户详情 |

路由守卫：

1. 页面 `meta.requiresAuth = true` 时检查 `userStore.token`。
2. token 不存在跳转 `/login`。
3. 登录成功后回到原目标页。

## 五、接口封装

### 5.1 通用响应类型

```ts
export interface Result<T> {
  code: number
  message: string
  data: T
}
```

### 5.2 用户类型

```ts
export interface LoginVO {
  accessToken: string
  accessTokenExpireIn: number
  userId: number
  accountId: number
  username: string
  avatar?: string
  type: number
  gameAccount?: string
  auditStatus: number
}

export interface UserSimpleVO {
  id: number
  username: string
  avatar?: string
  gameAccount?: string
}

export interface UserVO {
  id: number
  accountId: number
  username: string
  avatar?: string
  pendingAvatarUrl?: string
  signature?: string
  phone?: string
  status: number
  type: number
  gameAccount?: string
  auditStatus: number
  version: number
  followCount: number
  fansCount: number
}

export interface UpdateUserInfoDTO {
  version: number
  username?: string
  signature?: string
  phone?: string
  gameAccount?: string
}
```

### 5.3 `api/request.ts`

职责：

1. 创建 Axios 实例。
2. `baseURL` 从环境变量读取：`VITE_API_BASE_URL`。
3. 请求拦截器写入 `Authorization: Bearer ${accessToken}`。
4. `withCredentials = true`，让浏览器自动携带 `refreshToken` Cookie。
5. 如果本地已有用户信息，可在开发联调阶段临时写入 `X-User-Id`、`X-User-Type`、`X-Game-Account`、`X-Session-Id`，绕过 Gateway 直接测 `user-service`。
6. 响应拦截器统一处理 `code !== 200`。
7. 遇到 `401` 时自动调用 `POST /user/token/refresh`，成功后重试原请求。
8. 刷新失败时清理 store 并跳转登录页。

### 5.4 `api/user.ts`

| 函数 | 对应接口 | 说明 |
|------|----------|------|
| `sendCode(payload)` | `POST /user/sendCode` | 发送验证码 |
| `registerByPhone(payload)` | `POST /user/register/phone` | 手机号验证码注册 |
| `loginByAccount(payload)` | `POST /user/login/account` | 账号密码登录 |
| `logout()` | `POST /user/logout` | 登出 |
| `getCurrentUser()` | `GET /user/me` | 当前用户详情 |
| `getUserDetail(id)` | `GET /user/{id}` | 用户详细信息 |
| `getUserSimple(id)` | `GET /user/simple/{id}` | 用户简单信息 |
| `updateUserInfo(payload)` | `PUT /user/info` | 修改用户资料，必须携带 `version` |
| `refreshToken()` | `POST /user/token/refresh` | 刷新 Access Token |
| `uploadAvatar(file)` | `POST /user/avatar` | 上传头像 |
| `cancelAccount()` | `POST /user/cancel` | 注销账号 |

## 六、状态管理

### `stores/user.ts`

状态：

| 字段 | 类型 | 说明 |
|------|------|------|
| `accessToken` | `string` | Access Token |
| `userId` | `number | null` | 当前用户 ID |
| `accountId` | `number | null` | 当前公开账号 ID |
| `profile` | `UserVO | null` | 当前用户详情 |
| `isLoggedIn` | `boolean` | 是否登录 |

Actions：

| 函数 | 说明 |
|------|------|
| `login(payload)` | 调用登录接口，保存 access token 和基础用户信息 |
| `fetchProfile()` | 获取当前用户详情 |
| `logout()` | 调用登出接口并清理本地状态 |
| `clearSession()` | 清理 token、用户信息和本地缓存 |
| `refreshAccessToken()` | 调用刷新接口更新 access token |
| `updateProfile(payload)` | 修改资料时从 `profile.version` 带上版本号，提交成功后刷新 profile |
| `cancelAccount()` | 注销账号后清理登录态并跳转登录页 |

持久化：

1. access token 存 `localStorage`。
2. profile 可存 `sessionStorage` 或只存在内存中。
3. refresh token 不存前端状态，由 `HttpOnly Cookie` 承载。
4. 登录态恢复时先从 access token 恢复，再调用 `/user/me` 刷新服务端状态。

## 七、页面需求

### 7.1 登录页 `LoginView`

布局：

1. 左侧或顶部显示产品名“游戏社区”。
2. 中间是账号 ID、密码、登录按钮。
3. 提供跳转注册入口。

表单字段：

| 字段 | 校验 |
|------|------|
| `accountId` | 必填，只允许数字 |
| `password` | 必填，6-32 位 |

交互：

1. 点击登录时禁用按钮并显示 loading。
2. 登录成功保存 access token，浏览器自动接收 `refreshToken` Cookie，跳转 `/user/profile`。
3. 登录失败显示后端错误信息。
4. 回车可提交。

### 7.2 注册页 `RegisterView`

表单字段：

| 字段 | 校验 |
|------|------|
| `phone` | 必填，中国大陆手机号 |
| `code` | 必填，6 位数字 |
| `username` | 必填，2-32 个字符 |
| `password` | 必填，6-32 位 |
| `confirmPassword` | 必须等于 password |
| `gameAccount` | 可空，最长 64 |

交互：

1. 点击获取验证码后调用 `/user/sendCode`。
2. 按钮进入 60 秒倒计时。
3. 开发环境可以显示返回验证码，生产环境不展示。
4. 注册成功提示用户公开账号 ID，并提供立即登录按钮。

### 7.3 个人中心 `ProfileView`

展示内容：

| 区域 | 内容 |
|------|------|
| 用户头部 | 头像、昵称、账号 ID、游戏账号 |
| 数据概览 | 关注数、粉丝数 |
| 资料详情 | 手机号、个性签名、用户类型、状态 |
| 操作 | 编辑资料、登出、注销账号 |

交互：

1. 页面进入时调用 `fetchProfile()`。
2. 登出前弹出确认。
3. 注销账号必须二次确认，明确提示注销后无法继续登录。
4. 若 `pendingAvatarUrl` 存在，应展示“待审核头像预览”和“当前正式头像”两个状态。

### 7.4 编辑资料 `ProfileEditView`

字段：

| 字段 | 控件 | 校验 |
|------|------|------|
| 头像 | `AvatarUploader` | jpg、png、webp，大小限制 |
| 昵称 | 输入框 | 2-32 字 |
| 个性签名 | 文本域 | 最多 120 字 |
| 手机号 | 输入框 | 手机号格式 |
| 游戏账号 | 输入框 | 最多 64 字 |

交互：

1. 头像上传后立即调用 `/user/avatar`。
2. 资料保存调用 `/user/info`，并携带当前 `profile.version`。
3. 审核不通过时展示后端原因。
4. 如果后端返回“资料已更新，请刷新页面后重试”，前端应先刷新 `/user/me` 再提示用户重新编辑。
5. 保存成功后刷新用户 store 并返回个人中心。

### 7.5 公开资料页 `PublicProfileView`

展示：

1. 优先调用 `/user/{id}` 获取详细信息。
2. 如果权限不足或被黑名单拦截，可以降级调用 `/user/simple/{id}`。
3. 不展示手机号。

## 八、组件设计

### `AvatarUploader`

Props：

| 字段 | 类型 | 说明 |
|------|------|------|
| `modelValue` | `string` | 当前头像 URL |
| `disabled` | `boolean` | 是否禁用 |

Events：

| 事件 | 说明 |
|------|------|
| `update:modelValue` | 上传成功后更新头像 URL |
| `uploaded` | 上传成功 |
| `failed` | 上传失败 |

内部逻辑：

1. 上传前校验类型和大小。
2. 使用 `FormData` 调用 `uploadAvatar`。
3. 上传中显示 loading。
4. 审核不通过展示错误，不更新头像。

### `UserInfoPanel`

Props：

| 字段 | 类型 | 说明 |
|------|------|------|
| `user` | `UserVO | UserSimpleVO` | 用户信息 |
| `mode` | `'simple' | 'detail'` | 展示模式 |

用途：

1. 个人中心详情展示。
2. 公开资料页展示。
3. 后续评论区用户卡片复用。

### `CancelAccountDialog`

Props：

| 字段 | 类型 | 说明 |
|------|------|------|
| `modelValue` | `boolean` | 是否显示 |

交互：

1. 用户输入“确认注销”后按钮才可点击。
2. 点击确认调用 `cancelAccount()`。
3. 成功后清理登录态并跳转 `/login`。

## 九、易用性要求

1. 所有表单错误就近展示，不只使用顶部提示。
2. 登录、注册、保存资料、上传头像都必须有 loading 状态。
3. 用户修改资料后立即刷新页面数据。
4. 登录过期时自动跳转登录页，并保留目标路径。
5. 注册成功后清晰展示账号 ID，因为后续账号密码登录需要账号 ID。
6. 注销是高风险操作，必须二次确认。
7. 开发环境允许直连 `user-service`，通过 Axios 拦截器写测试 Header；联调 Gateway 后关闭该逻辑。

## 十、环境变量

```env
VITE_API_BASE_URL=http://localhost:8081
VITE_ENABLE_DEV_USER_HEADER=true
```

说明：

| 变量 | 说明 |
|------|------|
| `VITE_API_BASE_URL` | 后端 API 地址 |
| `VITE_ENABLE_DEV_USER_HEADER` | 开发联调时是否自动透传用户 Header |

## 十一、测试方案

### 11.1 单元测试

| 测试 | 覆盖 |
|------|------|
| `userStore.spec.ts` | 登录态保存、清理、资料刷新 |
| `userApi.spec.ts` | API 参数和响应处理 |
| `AvatarUploader.spec.ts` | 文件类型、大小、上传成功失败 |

### 11.2 E2E 测试

使用 Playwright。

流程：

1. 打开注册页。
2. 输入手机号并获取验证码。
3. 完成注册。
4. 使用返回账号 ID 登录。
5. 进入个人中心。
6. 修改昵称和签名。
7. 上传头像。
8. 登出。
9. 再次登录。
10. 注销账号。

## 十二、验收标准

| 编号 | 标准 |
|------|------|
| FE-01 | 登录成功后 token 持久化 |
| FE-02 | 刷新页面后能恢复登录态 |
| FE-03 | 未登录访问个人中心会跳转登录页 |
| FE-04 | 注册成功能展示账号 ID |
| FE-05 | 资料修改成功后页面立即更新 |
| FE-06 | 头像上传有类型、大小、审核失败提示 |
| FE-07 | 登出后本地 token 清空 |
| FE-08 | 注销账号必须二次确认 |
| FE-09 | 主要页面在桌面和移动端都可正常使用 |
