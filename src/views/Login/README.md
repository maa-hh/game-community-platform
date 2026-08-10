# 登录页 `views/Login`

## 目录结构

```
views/Login/
├── index.tsx                 # 页面编排：模式切换、提示、成功跳转
├── style.less                # 本页样式（不写 inline style）
├── constants.ts              # 类型、邮箱校验规则、倒计时常量
├── README.md
└── components/
    ├── LoginForm/            # 登录表单
    ├── RegisterForm/         # 注册表单（含发验证码与倒计时）
    └── AuthSuccessLoading/   # 登录成功后的转圈等待
```

## 职责划分

| 文件 | 职责 |
|------|------|
| `index.tsx` | Tab、Alert、`successRedirecting` 时预拉首页并跳转 |
| `LoginForm` | 邮箱 + 密码登录 UI 与校验 |
| `RegisterForm` | 注册 UI、发送验证码、倒计时 |
| `AuthSuccessLoading` | Spin + 文案 |
| `style.less` | `.login-page` / `.login-success` 样式 |

## 数据流

```
LoginForm / RegisterForm
  → dispatch(loginAction | registerAction | sendCodeAction)
  → auth store + service + mock
  → successRedirecting → fetchHomeData → navigate(from)
```
