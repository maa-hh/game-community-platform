# Getting Started with Create React App

This project was bootstrapped with [Create React App](https://github.com/facebook/create-react-app).

## Available Scripts

In the project directory, you can run:

### `npm start`

Runs the app in the development mode.\
Open [http://localhost:3000](http://localhost:3000) to view it in the browser.

The page will reload if you make edits.\
You will also see any lint errors in the console.

### `npm test`

Launches the test runner in the interactive watch mode.\
See the section about [running tests](https://facebook.github.io/create-react-app/docs/running-tests) for more information.

### `npm run build`

Builds the app for production to the `build` folder.\
It correctly bundles React in production mode and optimizes the build for the best performance.

The build is minified and the filenames include the hashes.\
Your app is ready to be deployed!

See the section about [deployment](https://facebook.github.io/create-react-app/docs/deployment) for more information.

### 生产部署

不要把 `npm start` 暴露给真实用户。它是开发服务器，会注入 React Refresh
错误覆盖层，用户可能看到 `Script error`，且开发隧道断流时还可能拿到不完整的
bundle。

生产部署流程：

```bash
# 前端与 API 同域、由 Nginx/Caddy 反代 API 时可直接使用默认配置
npm run build
npm run serve:production
```

`serve:production` 会静态托管 `build`，并把 `/user`、`/social`、`/article`、
`/notification`、`/hot-article` 等 API 请求代理到 `127.0.0.1:8080`，也支持
SSE 长连接。后端不在本机 8080 时可覆盖：

```bash
BACKEND_URL=https://api.example.com npm run serve:production
```

如果 API 使用独立域名，创建 `.env.production.local` 并在构建前设置：

```bash
REACT_APP_BASE_URL=https://api.example.com
REACT_APP_ENABLE_MOCK=false
npm run build
```

历史路由（例如 `/game/123`）需要配置服务器回退到 `build/index.html`；API
路径则需要反代到后端网关。`.env.production.local` 不应提交到 git。

### `npm run eject`

**Note: this is a one-way operation. Once you `eject`, you can’t go back!**

If you aren’t satisfied with the build tool and configuration choices, you can `eject` at any time. This command will remove the single build dependency from your project.

Instead, it will copy all the configuration files and the transitive dependencies (webpack, Babel, ESLint, etc) right into your project so you have full control over them. All of the commands except `eject` will still work, but they will point to the copied scripts so you can tweak them. At this point you’re on your own.

You don’t have to ever use `eject`. The curated feature set is suitable for small and middle deployments, and you shouldn’t feel obligated to use this feature. However we understand that this tool wouldn’t be useful if you couldn’t customize it when you are ready for it.

## Learn More

You can learn more in the [Create React App documentation](https://facebook.github.io/create-react-app/docs/getting-started).

To learn React, check out the [React documentation](https://reactjs.org/).
