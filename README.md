# 游戏社区平台后端

`master` 分支只保留项目入口说明。完整的后端源码、SQL、部署脚本和技术文档位于 [`backend` 分支](https://github.com/maa-hh/game-community-platform/tree/backend)。

## 仓库结构

- `backend`：Spring Boot 微服务后端完整代码
- `master`：仓库入口 README，便于识别项目和进入后端分支
- 前端：本地独立仓库 `game-community`

## 后端开发

```bash
git clone git@github.com:maa-hh/game-community-platform.git
cd game-community-platform
git switch backend
mvn test
```

后端服务、配置、数据库迁移和隐私边界说明见 [`backend 分支文档`](https://github.com/maa-hh/game-community-platform/tree/backend/docs/README.md)。真实环境变量、密钥、日志和运行产物不提交到 Git。
