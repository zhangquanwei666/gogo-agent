# gogo-agent

企业差旅场景的 AI 智能助手。用一句话描述行程，助手负责查价、比价、走审批。

前后端一体的单体工程：Spring Boot 提供接口，同时把 `frontend/dist` 作为静态资源托管，
生产环境单端口即可跑起来。

> **当前状态：骨架完成，对话链路尚未接通。**
> 账号体系和会话列表已打通，智能体代码已就位但缺编排器和对话接口，详见 [当前进度](#当前进度)。

## 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 运行时 | Java 21 | |
| 框架 | Spring Boot 4.1.0 | jakarta 命名空间，自带 Jackson 3 |
| 持久层 | MyBatis-Plus 3.5.17 + MySQL | 雪花 ID、逻辑删除、字段自动填充 |
| 鉴权 | Sa-Token 1.39.0 + Redis | random-128 token，会话存 Redis |
| 智能体 | AgentScope Java 2.0.2 | 阿里开源多智能体框架 |
| 模型 | OpenAI 兼容协议 → qwen-plus | 走兼容接口，换厂商只改 base-url |
| 前端 | React 19 + TypeScript + Vite 8 | |

选型上有两个坑已经在 `pom.xml` 里注释说明：

- Sa-Token 必须用 `sa-token-spring-boot3-starter`。boot2 starter 依赖 `javax.servlet`，
  Boot 4 是 jakarta 命名空间，用错会在启动时 ClassNotFoundException。
- 需要手动补 Jackson 2 依赖。Boot 4 自带的是 Jackson 3（`tools.jackson.*`），
  而 `sa-token-redis-jackson` 用的是 Jackson 2，两套包名不同可以共存。

## 当前进度

| 模块 | 状态 |
|---|---|
| 注册 / 登录 / 登出 / 查询当前用户 | ✅ 已打通 |
| 会话列表查询 | ✅ 已打通 |
| 前端登录页 / 注册页 / 首页 | ✅ 页面完成 |
| 消息读写、点赞点踩 | ⚠️ service 层完成，未暴露接口 |
| 问题改写智能体 | ⚠️ 代码完成，未接入调用链 |
| 智能体编排器 | ❌ 未实现 |
| 对话接口（发送消息 / 流式回复） | ❌ 未实现 |
| 机票 / 酒店 / 火车票 / 用车搜索 | ❌ 纯 UI，点击提示「功能还在开发中」 |

## 快速开始

### 环境要求

- JDK 21
- Maven 3.9+
- Node 20.19+ / 22.12+（Vite 8 的要求）
- MySQL 8.x、Redis 6+

### 1. 初始化数据库

建库后执行建表脚本，三张表：`user_account`、`chat_conversation`、`chat_message`。

```bash
mysql -u <user> -p <database> < src/main/resources/db/DBSQL.sql
```

### 2. 配置连接信息

改 `src/main/resources/application.yaml` 里的 `spring.datasource`、`spring.data.redis`
和 `agentscope.openai`（见 [配置说明](#配置说明)）。

### 3. 启动后端

```bash
mvn spring-boot:run
```

服务监听 **18080**。

### 4. 启动前端（开发模式）

```bash
cd frontend && npm install && npm run dev
```

开发服务器在 **5173**，`/api` 请求由 `vite.config.ts` 里的 proxy 转发到 18080，
开发阶段不依赖任何 CORS 配置。

### 5. 生产构建

```bash
cd frontend && npm run build
```

产物输出到 `frontend/dist`。后端的 `spring.web.resources.static-locations` 已指向该目录，
构建完直接访问 http://localhost:18080/ 即可，不需要单独部署前端。

## 配置说明

配置集中在 `src/main/resources/application.yaml`：

| 配置项 | 用途 |
|---|---|
| `spring.datasource` | MySQL 连接 |
| `spring.data.redis` | Sa-Token 会话存储 |
| `agentscope.openai.base-url` / `api-key` / `model-name` | 模型接入，OpenAI 兼容协议 |
| `agent.prompt.location` | 提示词目录，`classpath:prompt/` 打进 jar |
| `agent.prompt.cache` | 提示词缓存开关 |
| `sa-token.active-timeout` | token 有效期，默认 30 天 |

两个开发期便利项，上线前记得处理：

- **调提示词**：把 `agent.prompt.location` 改成 `file:./prompt/`、`cache` 设为 `false`，
  改完文件刷新请求就生效，不用重启。
- **SQL 日志**：`mybatis-plus.configuration.log-impl` 当前是 `StdOutImpl`，会打印全部 SQL，
  生产环境需要关掉。

> ⚠️ **敏感配置目前明文写在 `application.yaml` 里并已提交进 Git**，
> 包括数据库密码、Redis 密码和模型 API Key。上线前必须轮换这几组凭据并改为环境变量注入。
> 详见 [待办](#待办)。

## 接口一览

统一前缀 `/api/v1`。**HTTP 状态码恒为 200**，成败由响应体里的 `code` 区分
（200 成功，400 参数、401 未认证、404 不存在、409 冲突、500 系统异常），
错误码定义见 `common/ErrorCodeEnum`。

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/auth/register` | 免 | 注册，入参 `username` / `email` / `password` / `realName`，角色固定 USER |
| POST | `/auth/login` | 免 | 登录，`type` 取 `USERNAME` \| `EMAIL`，配合 `account` + `password` |
| POST | `/auth/logout` | 免 | 登出，token 已过期时调用也不报错 |
| GET | `/user/current` | 需 | 查当前登录用户 |
| POST | `/chat/conversation/list` | 需 | 查当前用户的全部会话，按最后更新时间倒序 |

登录成功返回 `tokenName` / `tokenValue`，前端存进 localStorage，
后续请求放在 `Authorization` 请求头里。

鉴权策略是**默认拦截 `/api/**`、白名单逐条放行**（见 `config/SaTokenConfigure`）。
新增公开接口需要手动往白名单加一行——忘了加只是接口被拦，马上能发现；
用 `/api/v1/auth/**` 整段通配虽然省事，但以后往这组加接口会自动继承免鉴权且没有任何提示。

所有涉及用户身份的接口，`userId` 一律从 token 取，不接受前端传入。

## 目录结构

```
src/main/java/com/quanwei/gogo/agent/
├── agent/                智能体
│   ├── baseagent/        ChatAgent 接口 + QueryRewriteAgent
│   ├── core/             AgentContext（贯穿链路的上下文）、AgentResult
│   ├── llm/              LlmClient，对 AgentScope Model 的薄封装
│   ├── prompt/           PromptLoader，提示词从文件加载
│   ├── rewrite/          改写产出
│   └── enums/            AgentNameEnum
├── controller/           只做参数收敛和 DTO 转换
├── service/              业务逻辑，入参出参都是 BO
├── dao/                  数据访问收口，service 不直接碰 mapper
├── mapper/               MyBatis-Plus Mapper 接口
├── entity/               数据库实体，不越过 dao 边界
├── dto/                  传输层结构，只在 controller 边界出现
├── bo/                   业务对象
├── common/               响应基类、错误码、各类枚举
├── config/               鉴权、跨域、MyBatis-Plus、字段填充
└── exception/            BizException + 全局异常处理

src/main/resources/
├── application.yaml
├── db/DBSQL.sql          建表脚本
├── mapper/               Mapper XML
└── prompt/               提示词，纯文本，改动不需要重新编译

frontend/src/
├── api/                  接口封装，request.ts 统一处理 token 和错误
├── components/           AppHeader / BookingPanel / ConversationPanel 等
├── pages/                LoginPage / RegisterPage / HomePage
├── styles/
└── types/                跟后端 DTO 对应的类型定义
```

分层约定：**DTO 只在 controller 边界，BO 进 service，Entity 不越过 dao**。

## 智能体设计

链路上的每个智能体都实现 `ChatAgent<T>`，由编排器按顺序驱动。
智能体之间不直接依赖，只通过 `AgentContext` 这个可变对象传递数据：
读自己需要的字段 → 干活 → 写回自己的产出。

两条硬约定：

1. **智能体不向外抛异常**，失败也要返回 `AgentResult.fail(...)`，
   由编排器决定是降级继续还是中断。单个智能体挂掉不能让整轮对话失败。
2. **`supports()` 返回 false 时直接跳过**，不产生任何 LLM 调用——这是控制延迟和成本的主要手段。

### 已实现：QueryRewriteAgent（问题改写）

把带指代、省略的追问补全成可以脱离上下文独立理解的问题：
「那家酒店多少钱」→「上海浦东丽思卡尔顿酒店多少钱」。

流程：

```
supports()  历史非空 + 正则命中指代特征词，两条都满足才调 LLM
    ↓
拼历史      最近 6 条消息，每条截断 200 字
    ↓
渲染提示词   prompt/query-rewrite-system.txt + query-rewrite-user.txt
    ↓
调用 LLM    temperature=0，超时 8s
    ↓
防御式解析   NO_REWRITE 标记 / 剥引号 / 过长判定为模型扩写，都退回原文
    ↓
写回 context.rewrittenQuery，下游统一读 effectiveQuery()
```

改写失败一律降级用原文，不中断对话。下游不需要关心改写有没有真的发生。

已知待改进项见 [待办](#待办)。

## 待办

按优先级排列：

1. **轮换 `application.yaml` 里的数据库 / Redis 密码和模型 API Key**，改为环境变量注入
2. **`LlmClient` 支持按 agent 指定模型** —— 改写这类任务应该走小模型，
   当前所有 agent 共用 `qwen-plus`，卡在首字延迟的关键路径上
3. **改写输出改为 JSON 结构化**，替掉 `parseOutput` 里的启发式防御
4. **提示词注入当前日期**，把「下周一」归一化成绝对日期（商旅是强时间相关场景）
5. **实现 `AgentOrchestrator`**，按顺序驱动 `List<ChatAgent<?>>`，
   把执行链路写进 `chat_message.extra` 供排查
6. **新增对话接口** `POST /api/v1/chat/send`，`agentscope.openai.stream` 已配好，建议直接上 SSE
7. **补齐消息相关接口**（列表、反馈、会话改名/删除）—— service 层已完成，只差 controller
8. **补测试**。当前无 `src/test` 目录，改写链路尤其需要一个多轮对话评测集
9. 收紧 CORS。`config/CorsConfig` 当前是 `allowedOriginPattern("*")` + `allowCredentials(true)`，
   生产应换成明确的域名白名单
