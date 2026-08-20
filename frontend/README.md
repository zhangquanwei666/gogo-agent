# GoGo 智能商旅 - 前端

React 19 + TypeScript + Vite。目前包含登录、注册两个页面。

## 环境要求

Node **20.19+ / 22.12+**（Vite 8 的要求）。当前机器上是 Node 14，跑不起来，需要先升级。

## 本地开发

```bash
npm install
npm run dev
```

开发服务器在 http://localhost:5173。

后端接口通过 `vite.config.ts` 里的 proxy 转发到 `http://127.0.0.1:18080`，
所以后端不需要任何 CORS 配置。启动前先把 Spring Boot 应用跑起来。

## 构建

```bash
npm run build
```

产物输出到 `dist/`。后端的 `application.yaml` 里已经把
`spring.web.resources.static-locations` 指向了 `file:./frontend/dist/`，
构建完直接访问 http://localhost:18080/ 就能看到页面。

## 目录结构

```
src/
├── api/          接口封装，request.ts 统一处理响应和错误
├── components/   通用组件：品牌侧栏、表单字段、提示条
├── pages/        LoginPage、RegisterPage
├── styles/       auth.css
└── types/        跟后端 DTO 对应的类型定义
```
