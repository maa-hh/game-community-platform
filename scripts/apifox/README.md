# Apifox 导入说明

## 文件

| 文件 | 说明 |
|------|------|
| `auth-api.postman_collection.json` | 认证接口全集（30+ 用例，含预期说明） |
| `auth-api.postman_environment.json` | 本地开发环境变量 |

## 导入步骤

1. 打开 **Apifox** → 进入项目（或新建项目「游戏社区」）
2. 左上角 **导入** → 选择 **Postman**
3. 导入 `auth-api.postman_collection.json`
4. 再次 **导入** → 选择 **Postman 环境**：
   - 本地：`auth-api.postman_environment.json`
   - 正式：`auth-api.postman_environment.prod.json`
5. 右上角切换对应环境
6. 在「环境管理」中修改域名、邮箱、密码、`code` 等

### 正式环境注意

`application-prod` 会 **关闭 mock、强制真实 SMTP**。发码时：

- `newEmail` 必须是 **真实存在、能收信** 的邮箱（不要用 `test-register@qq.com` 这类虚构 QQ 邮箱）
- 若收件人无效，QQ SMTP 返回 550，接口应返回 `400` + `收件邮箱不存在或无法接收邮件...`（而非 500）
- 确保服务器 `.env` 已配置 `SMTP_*`、`JWT_*`

## 使用提示

- **Cookie**：Apifox 默认自动管理 Cookie，登录后 refresh/logout 可直接测
- **验证码**：执行「1.1 注册发码」后，从邮件或 Redis 读取：
  ```bash
  docker exec redis redis-cli GET "email:code:REGISTER:test-register@qq.com"
  ```
  将结果填入环境变量 `code`
- **Mock 模式**：`.env` 设 `EMAIL_MOCK_ENABLED=true` 时 `code` 固定 `123456`
- **全流程**：按文件夹「8. 全流程推荐顺序」说明，或运行自动化测试（见下）

## 自动化测试（可选）

在 Apifox 中选中集合 → **自动化测试** → 添加测试场景 → 按顺序勾选：

`1.1` → `2.1` → `3.1` → `5.1` → `6.1`

（`2.1` 前需手动更新 `code` 环境变量，或在 1.1 的后置脚本中从 Redis 拉取）

## 错误码速查

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 参数/验证码错误 |
| 401 | 密码错误 |
| 40102 | refreshToken 失效 |
| 403 | 账号锁定/封禁 |
| 404 | 邮箱未注册 |
| 409 | 邮箱已注册 |
| 429 | 发送过于频繁 |
