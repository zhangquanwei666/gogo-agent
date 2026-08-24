# gogo-agent

企业差旅场景的 AI 智能助手。用一句话描述行程，助手负责查价、比价、走审批。

前后端一体的单体工程：Spring Boot 提供接口，同时把 `frontend/dist` 作为静态资源托管，
生产环境单端口即可跑起来。

> **当前状态：智能体流水线已跑通，对话接口尚未开放。**
> 账号体系和会话列表已打通；问题改写 + 意图识别的完整链路由
> `AgentPipelineService` 编排，产出 `PipelineResult`。
> 还缺 MasterAgent 和对外的对话接口，详见 [当前进度](#当前进度)。

## 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 运行时 | Java 21 | |
| 框架 | Spring Boot 4.1.0 | jakarta 命名空间，自带 Jackson 3 |
| 持久层 | MyBatis-Plus 3.5.17 + MySQL | 雪花 ID、逻辑删除、字段自动填充 |
| 鉴权 | Sa-Token 1.39.0 + Redis | random-128 token，会话存 Redis |
| 智能体 | AgentScope Java 2.0.2 | 阿里开源多智能体框架 |
| 模型 | OpenAI 兼容协议，按档位配置 | fast / stable / strong / strong-thinking 四档，业务只声明档位 |
| 向量化 | text-embedding-v4（自研客户端） | 意图识别 L2 用；AgentScope 2.0.2 没有 embedding 能力，只能自己调 |
| 异步 | Reactor `Mono` | 流水线全链路非阻塞，同步 HTTP 调用挪到 `boundedElastic` |
| JSON | fastjson2 | 解析模型输出的结构化结果，跟 Jackson 各管一摊互不干扰 |
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
| 问题改写智能体 | ✅ 已接入流水线，输出 JSON 结构化解析 |
| 意图识别 L0/L1/L2（分流 + 规则 + 向量） | ✅ 代码完成，种子语料就绪 |
| 意图识别 L3（大模型兜底） | ✅ `IntentResultParser` 已把模型输出转成结构化结果 |
| 智能体流水线编排 | ✅ `AgentPipelineService`，快路径优先 + 全链路降级 |
| MasterAgent（意图落地执行） | ❌ 未实现，流水线已留好调度位 |
| 对话接口（发送消息 / 流式回复） | ❌ 未实现 |
| 机票 / 酒店 / 火车票 / 用车搜索 | ❌ 纯 UI，点击提示「功能还在开发中」 |

## 快速开始

### 环境要求

- JDK 21
- Maven 3.9+
- Node 20.19+ / 22.12+（Vite 8 的要求）
- MySQL 8.x、Redis 6+

### 1. 初始化数据库

建库后执行建表脚本，四张表：`user_account`、`chat_conversation`、`chat_message`，
以及 `agentscope_session`（AgentScope 的会话状态持久化，由 `AgentscopeSessionService` 读写）。

```bash
mysql -u <user> -p <database> < src/main/resources/db/DBSQL.sql
```

### 2. 配置连接信息

改 `src/main/resources/application.yaml` 里的 `spring.datasource`、`spring.data.redis`
和 `agent.model.*` 各档位的模型密钥（见 [配置说明](#配置说明)）。

`agent.intent.l2.embedding.api-key` 可以先留空——L2 会静默禁用，意图识别退回 L1 + L3，不影响启动。

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
| `agent.model.fast` / `stable` / `strong` / `strong-thinking` | 模型分档，每档独立配 `api-key` / `base-url` / `model-name` / `stream` / `enable-thinking` |
| `agent.prompt.location` | 提示词目录，`classpath:prompt/` 打进 jar |
| `agent.prompt.cache` | 提示词缓存开关 |
| `agent.intent.seed-location` | 意图种子语料，L2 建索引用 |
| `agent.intent.l0.length-threshold` | 含连词且长度超它才判为复合问题，直接下沉 L3 |
| `agent.intent.l2.threshold` / `margin` | L2 相似度阈值与 top1/top2 最小分差，两个都要看真实数据调 |
| `agent.intent.l2.embedding.api-key` | 向量化密钥。**留空则整个 L2 静默禁用**，识别由 L1 和 L3 承担，服务照常启动 |
| `sa-token.active-timeout` | token 有效期，默认 30 天 |

模型分档的意思是：业务代码只声明「我要快模型」，具体挂哪个模型、开不开思考全在配置里，
换模型不用重新编译。四档之间不共享不继承，可以分别挂在不同账号和接入地址上。

`agent.intent.*` 里除 `seed-location` 外的键当前都**没有写进 `application.yaml`**，
走的是 `IntentProperties` 的默认值（L0 阈值 10 字、L1 置信度 0.90、L2 阈值 0.85 / margin 0.05、
embedding 维度 1024）。要调只需在 yaml 里补对应的键，不用改代码。

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
│   ├── baseagent/        ChatAgent 接口 + QueryRewritingAgent + IntentRecognitionAgent
│   ├── pipeline/         AgentPipelineService（流水线编排）+ PipelineResult
│   ├── core/             AgentContext（贯穿链路的上下文）、AgentResult、
│   │                     AgentRegistry（按 bean 名取智能体）、LlmJsonUtils
│   ├── intent/           意图识别
│   │   ├── rule/         IntentRuleMatcher，L1 正则规则，三态裁决
│   │   ├── vector/       IntentVectorStore（建索引 + 检索）+ IntentVectorMatcher（阈值裁决）
│   │   ├── embedding/    EmbeddingClient，OpenAI 兼容的 /embeddings 调用
│   │   ├── seed/         种子语料加载与校验
│   │   └── ...           IntentRecognitionRouter（L0→L2 快路径）、IntentRecognitionResult、
│   │                     IntentResultParser（L3 输出转结构化）、IntentProperties
│   ├── llm/              ModelProperties / ModelConfig，模型分档
│   ├── prompt/           PromptLoader，提示词从文件加载 + 注入时间变量
│   ├── rewrite/          QueryRewriteParser（JSON 解析）+ QueryRewriteResult
│   └── enums/            AgentNameEnum、IntentCategory、IntentLevelEnum
├── controller/           只做参数收敛和 DTO 转换
│                         Login / Register / UserAccount / Chat 四个
├── service/              业务逻辑，入参出参都是 BO
│                         含 ChatHistoryService（跨智能体的历史读取）、
│                         AgentscopeSessionService（AgentScope 状态持久化）
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
├── intent_seed.yml       意图种子语料，L2 启动时向量化建索引
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

### 已实现：AgentPipelineService（流水线编排）

**核心设计：把改写放在意图识别之后，而不是之前。**

```
① 原始问题走 L0→L2 快路径（不碰 L3）
     命中 ──→ 直接调度，改写和 L3 两次模型调用都省了
     未命中 ↓
② 调改写模型补全指代、省略（无历史时跳过——没有上文就没有指代可消解）
③ 拿改写后的问题走完整识别（内部再跑一次 L1/L2，不行才 L3）
④ 调度给 MasterAgent（预留，bean 不存在时到此为止）
```

直觉上「先改写再识别」更顺，但那样每一句话都要先付一次改写的模型调用。而「我的报销进度到哪了」
这类高频、表达清晰的问题，L1 一条正则 1ms 就判完了，根本不需要改写。顺序反过来之后，
这部分流量的模型调用次数**从 2 次降到 0 次**。

代价是带指代的追问（「那个多少钱」）会先白跑一次快路径，但快路径的成本是 L1 正则加一次 embedding，
和一次大模型调用比可以忽略，这笔账划算。

第 ③ 步还有一处剪枝：改写**没有真的改动文本**时直接走 `callL3`，跳过重复的 L1/L2——
同一段文本在第 ① 步已经跑过快路径且未命中，再跑一遍结果必然一样，纯属白花一次 embedding。

其余几条约定：

- **全链路非阻塞**。返回 `Mono<PipelineResult>`，快路径里 embedding 是同步 HTTP，
  用 `boundedElastic` 挪出去，不占 Netty 事件循环线程。
- **全流程不抛异常**。改写失败退回原文继续走，识别失败返回 `unknown` 交给 MasterAgent 追问，
  最外层还有 `onErrorResume` 兜底。
- **中断单独建模**。`PipelineResult.interrupted` 和识别失败是两回事——用户主动中断不该被当成
  识别失败去追问，也不该计入统计。AgentScope 当前版本的优雅中断不一定带 `INTERRUPTED` 标记，
  所以还兜底认了那句固定的英文恢复文本。
- **`rewriteTriggered` / `llmCalls` 是设计自证**。「快路径省下了多少次模型调用」这个比例，
  就是分级设计到底值不值的直接证据。

`AgentRegistry` 按 **bean 名**取智能体，注意和 `AgentNameEnum` 区分：后者是落
`chat_message.agent_name` 的人类可读名（PascalCase），前者是 `@Component("...")` 的容器标识
（camelCase），拿错了 `getBean` 直接抛。

### 已实现：意图识别（分级分类器）

判断用户这句话属于哪个业务意图、该交给哪个子智能体。四级串联，**每一级都可以主动弃权**：

```
L0  连词 + 长度判定     不产出意图，只决定要不要跳过 L1/L2 直接下沉 L3
L1  正则规则匹配        ~1ms，无外部调用
L2  向量相似度          ~50ms，含一次 embedding 调用
L3  大模型              ~800ms，负责复杂意图、多意图，同时是整条链路的兜底
```

分级的价值一半在提速，另一半在**弃权**：L1 检出一句话跨了两个子智能体、L2 发现 top1 和 top2
咬得太紧，都返回「我判不了」交给下一级，而不是硬给一个五五开的结论。

L1 的裁决是三态而不是「命中/未命中」：

| 裁决 | 含义 | 下一步 |
|---|---|---|
| `HIT` | 规则命中 | 直接采信，不再往下 |
| `MISS` | 规则没覆盖到 | 继续走 L2，L2 很可能能识别 |
| `AMBIGUOUS` | 一句话跨了多个子智能体 | **连 L2 一起跳过**，直接进 L3 |

多出来的 `AMBIGUOUS` 是为了省掉一次没有意义的 embedding 调用——向量检索同样只返回单一意图，
复合句给它也是白给。

意图类别定义在 `IntentCategory`，16 类，每类绑定一个目标子智能体的 **Spring bean 名**，
路由直接拿它 `getBean`。这份映射必须和 `prompt/intent-recognition-agent-system.md` 里的表、
`intent_seed.yml` 的 intent 字段三处一致——对不上的种子在启动时报错，
对不上的模型输出会被静默收敛成 `unknown`。

三层的产出是**同一个 `IntentRecognitionResult`**，`toJsonMap()` 出来的 JSON 结构与 L3 提示词
约定的完全一致（`intents[] / primary_intent / multi_intent / overall_reason`）。
规则命中、向量命中、模型兜底三条路径下游拿到同一个形状，主智能体只写一套解析逻辑。

`intent_seed.yml` 的样本刻意**不含 L1 触发词**——能被 L1 正则命中的说法永远走不到 L2，
放进语料纯属冗余。写样本时的自检：丢给 `IntentRuleMatcher`，如果 L1 就能命中，这条对 L2 没价值。

L2 整级可降级：没配 `api-key`、建索引失败、运行时调用异常，一律跳过走 L3。
向量检索是加速手段不是必需品，配不全就退回 L3，不该让服务起不来。

### 已实现：QueryRewritingAgent（问题改写）

把带指代、省略的追问补全成可以脱离上下文独立理解的问题：
「那家酒店多少钱」→「上海浦东丽思卡尔顿酒店多少钱」。

流程：

```
触发判定    由流水线控制：快路径未命中 + 历史非空，两条都满足才调 LLM
    ↓
拼历史      最近 10 条消息，每条截断 200 字
            上下文优先用 AgentContext.history，为空再按 conversationId 查 chat_message
    ↓
渲染提示词   prompt/query-rewriting-agent-system.md + -user.md
            PromptLoader 注入 {{current_date}} / {{current_weekday}}，
            让「下周一」能归一化成绝对日期
    ↓
调用 LLM    超时 8s
    ↓
JSON 解析    QueryRewriteParser 取 rewritten_question / step_back_question
            / related / reason；解析失败或改写后长度超原文 N 倍，都退回原文
    ↓
写回 context.rewrittenQuery，下游统一读 effectiveQuery()
```

改写失败一律降级用原文，不中断对话。下游不需要关心改写有没有真的发生。

「长度超原文 N 倍就退回」这条防御是有意保留的：JSON 化解决了格式问题，但解决不了模型
自作主张扩写——它可能返回一个格式完全合法、内容却塞进大量臆测细节的 `rewritten_question`。

已知待改进项见 [待办](#待办)。

## 待办

按优先级排列：

1. **轮换 `application.yaml` 里的数据库 / Redis 密码和模型 API Key**，改为环境变量注入
2. **实现 `MasterAgent`**，以 `@Component("masterAgent")` 落地即可自动接上流水线的调度位，
   `AgentPipelineService.dispatchToMaster` 不用改。入参形态已定死：
   改写结果和意图 JSON 各作为一条 SYSTEM 消息垫在原始对话之前
3. **新增对话接口** `POST /api/v1/chat/send`，模型分档里 `stream` 已配好，建议直接上 SSE。
   同时把 `context.agentTrace` 写进 `chat_message.extra` 供排查
4. **补齐消息相关接口**（列表、反馈、会话改名/删除）—— service 层已完成，只差 controller
5. **给意图识别的系统提示词加日期变量**。`PromptLoader` 已支持注入
   `{{current_date}}` / `{{current_weekday}}` / `{{current_time}}`，改写提示词已经在用，
   但 `intent-recognition-agent-system.md` 里一个占位符都没有
6. **补测试**。当前无 `src/test` 目录，至少要三个：
   改写链路的多轮对话评测集；意图种子的冗余护栏（断言每条 sample 都不会被 L1 抢先命中）；
   流水线的快路径命中率回归（`rewriteTriggered` / `llmCalls` 就是为此埋的）
7. **清理 `IntentSeed.keywords`**。`IntentRuleMatcher` 不读它，`L1KeywordClassifier` 删除后
   没有任何匹配逻辑消费这个字段——但 `IntentSeedLoader` 仍在解析并校验它
   （「keywords 和 samples 不能同时为空」），所以它是**被校验但不生效**的死数据，
   比纯粹没人读更容易误导：改了它启动不报错，但不会有任何效果
8. 收紧 CORS。`config/CorsConfig` 当前是 `allowedOriginPattern("*")` + `allowCredentials(true)`，
   生产应换成明确的域名白名单
9. **`frontend/package-lock.json` 的 registry 指向问题**。原先锁的是内网私服
   `nexus.91xunhui.cn`，离开内网时 `npm install` 会全量 ENOTFOUND，且 npm 会报一个
   与真实原因无关的 `Exit handler never called!`。现已改写为公共镜像，
   若要回内网构建需连带考虑这一处
