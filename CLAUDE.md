# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

天机学堂（tjxt）— 基于 Spring Boot 3.3.5 / Spring Cloud 2023.0.3 / Spring Cloud Alibaba 2023.0.3.2 的在线教育微服务平台，Java 17，Maven 多模块。

## 常用命令

```bash
# 编译全部模块（跳过测试）
mvn clean compile -DskipTests

# 运行全部测试
mvn test

# 运行单个测试类
mvn test -pl tj-aigc -Dtest=ChatServiceTest

# 打包全部模块
mvn clean package -DskipTests

# 打包单个模块
mvn clean package -pl tj-aigc -am -DskipTests

# 启动单个服务（开发环境）
mvn spring-boot:run -pl tj-gateway -Dspring.profiles.active=local
```

## 模块架构

项目共 17 个模块，全部依赖 `tj-common`（基础工具库、统一响应体 `R<T>`、异常体系、MyBatis-Plus/Redisson/RabbitMQ/XXL-Job 自动配置）。

- **tj-api** — Feign 客户端接口 + DTO 定义，所有服务间通信的合约层
- **tj-gateway** — Spring Cloud Gateway 网关（端口 10010），统一入口 + 认证
- **tj-auth-service** — 认证鉴权服务（含 gateway-sdk 和 resource-sdk）
- **tj-user / tj-course / tj-learning / tj-exam / tj-trade / tj-pay-service / tj-media / tj-search / tj-message-service / tj-remark / tj-promotion / tj-data** — 各业务服务
- **tj-aigc** — AI 对话服务（端口 8094），核心 AI 模块

Nacos 负责服务发现与配置管理，Sentinel 负责熔断限流，Seata 负责分布式事务。

## tj-aigc 核心架构

AI 对话服务支持三种模式，通过配置 `tj.ai.chat-type` 切换：

| 值 | 实现类 | 说明 |
|---|---|---|
| `ENHANCE`（默认） | `ChatServiceImpl` | 单智能体，DashScope 通义千问 + RAG（QuestionAnswerAdvisor） |
| `ROUTE` | `AgentServiceImpl` | 多智能体路由：RouteAgent 分析意图 → 分发到专项 Agent |
| `APP` | `AppAgentChatService` | 调用阿里云 DashScope 应用智能体（原生 SDK） |

### 智能体体系（`com.tianji.aigc.agent`）

- `Agent` 接口 — 定义 `processStream()`、`process()`、`systemMessage()`、`tools()`、`advisors()`
- `AbstractAgent` — 基于 Spring AI `ChatClient` 的通用实现，管理流式生成与取消
- `RouteAgent` — 意图路由，分析用户问题后返回目标 Agent 类型名
- `RecommendAgent` / `ConsultAgent` / `BuyAgent` / `KnowledgeAgent` — 专项 Agent

### RAG 与工具调用

- RAG 通过 `QuestionAnswerAdvisor` + Redis 向量存储实现，`RecommendAgent` 和 `ConsultAgent` 使用
- 工具类 `CourseTools.queryCourseById()` 和 `OrderTools.prePlaceOrder()` 通过 Feign 调用下游服务
- `ToolResultHolder`（基于 ConcurrentHashMap 的线程持有者）在工具调用和 SSE 流之间传递结果

### 会话记忆

`MessageWindowChatMemory`（窗口大小 100），支持 Redis（默认）或 MySQL/JDBC 存储。路由模式下 `RecordOptimizationAdvisor` 会清理中间路由消息。

### 音频服务

- TTS 文本转语音 — OpenAI TTS API，流式输出 `audio/mp3`
- STT 语音转文本 — OpenAI Whisper API，繁体自动转简体（opencc4j）

### 动态提示词

系统提示词存储在 Nacos Config 中，`SystemPromptConfig` 通过 `NacosConfigManager` 加载并在配置变更时热重载，无需重启。

## 开发注意事项

- 各服务使用 `tj.auth.resource.excludePath` 配置无需认证的路径（如 `/chat` 的 SSE 端点）
- 响应包装由 `@NoWrapper` 注解控制，SSE 流式端点必须标记此注解以避免被包装成 `R<T>`
- `UserContext` 通过 ThreadLocal 传递用户信息，跨 Feign 调用时由 `RequestIdRelayConfiguration` 中继
- `ConsultAgent.systemMessage()` 当前返回 `buyAgentSystemMessage` 而非 `consultAgentSystemMessage`，可能为 bug
- Nacos 配置的 `dataId` 和 `group` 在 `AIProperties` 中定义，实际提示词内容通过 Nacos 控制台管理
