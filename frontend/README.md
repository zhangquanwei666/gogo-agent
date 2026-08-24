# GoGo 智能商旅 - 前端

React 19 + TypeScript + Vite。目前包含登录、注册、首页三个页面。

首页的预订面板（机票 / 酒店 / 火车票 / 用车）和对话输入框还没接后端，
点击会提示「功能还在开发中」。已打通的是登录注册、当前用户查询和会话列表。

## 环境要求

Node **20.19+ / 22.12+**（Vite 8 的要求）。

## 本地开发

```bash
npm install
npm run dev
```

开发服务器在 http://localhost:5173。

后端接口通过 `vite.config.ts` 里的 proxy 转发到 `http://127.0.0.1:18080`，
所以开发阶段不依赖后端的 CORS 配置。启动前先把 Spring Boot 应用跑起来。

> **`npm install` 报 `Exit handler never called!` 时**，别去查这个报错本身——它是 npm 自己的
> bug，大量请求失败时会盖掉真实原因。去看它提示的日志文件，多半是一片 `ENOTFOUND`。
>
> 根因是 `package-lock.json` 里的 `resolved` 地址不可达。npm 下载 tarball 时**优先读
> lockfile 的 `resolved`，不看 `registry` 配置**，所以改 registry 没用。本项目的 lockfile
> 曾锁在内网私服 `nexus.91xunhui.cn` 上，离开内网就全量失败，现已改写为公共镜像
> `registry.npmmirror.com`。若要换源，替换 lockfile 里的地址前缀即可，
> `integrity` 哈希不用动（公共镜像与私服代理的 tarball 都源自 npmjs，字节一致）。

## 构建

```bash
npm run build
```

产物输出到 `dist/`。后端的 `application.yaml` 里已经把
`spring.web.resources.static-locations` 指向了 `file:./frontend/dist/`，
构建完直接访问 http://localhost:18080/ 就能看到页面。

## 接口约定

后端 HTTP 状态码恒为 200，成败靠响应体里的 `code` 区分。
`api/request.ts` 统一把 `code !== 200` 转成 `ApiError` 抛出，页面用 `code` 决定怎么展示
（401 清 token 回登录页，其余弹提示）。

登录成功后 `tokenName` / `tokenValue` 存进 localStorage，
后续请求由 `buildHeaders` 自动带上，业务代码不用关心。

## 目录结构

```
src/
├── api/          接口封装，request.ts 统一处理 token、响应和错误
├── components/   AppHeader、BookingPanel、ConversationPanel、
│                 BrandPanel、FormField、useToast
├── pages/        LoginPage、RegisterPage、HomePage
├── styles/       auth.css、home.css
└── types/        跟后端 DTO 对应的类型定义
```
