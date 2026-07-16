# CoBloom 工程编码规范 (Conventions)

本规范旨在确保前后端开发人员、以及 AI 编程助手（Agent）在协作时保持代码风格、业务逻辑和数据处理上的一致性。

## 1. 后端风格与规范 (Java / Spring Boot)

### 1.1 异常处理规范
- 全局异常必须通过 `ApiExceptionHandler` 统一捕获。
- 禁止在 Controller 层直接抛出裸异常，统一返回业务封装的 `Result<T>` 或引发自定义的业务异常（如 `BusinessException`）。

### 1.2 上下文与权限隔离
- 严禁从前端请求参数中直接信任 `userId`。所有涉及用户数据的操作，必须通过 `UserContext.getCurrentUserId()` 从 `JwtAuthFilter` 解析出的安全上下文中获取，确保数据完全隔离。

### 1.3 实体与传输对象 (DTO/Entity)
- **Entity**: 必须严格对应数据库表结构（如 `GrowthRecord`, `QaRecord`），禁止将 Entity 直接作为接口入参暴露给前端。
- **DTO**: 用于接收前端请求或向前端返回特定的视图数据（如 `AskRequest`, `RecordRequest`）。

## 2. 前端风格与规范 (Vue 3 / Vite)

### 2.1 组合式 API 与响应式
- 统一使用 `<script setup>` 语法。
- 组件内部状态使用 `ref()`，复杂对象或表单数据使用 `reactive()`。

### 2.2 状态管理与网络请求
- 用户的认证状态（Token、用户信息）统一封装在 Pinia 仓库（`stores/auth.js`）中。
- 所有 API 请求必须经过 `api/http.js` 的 Axios 实例，必须在请求拦截器中自动注入 `Authorization: Bearer <Token>`。
- 前端应通过响应拦截器统一处理 401（未登录/Token过期）并重定向至 `/login`。

## 3. RAG 与 AI 专用规范

### 3.1 级联更新与数据一致性
- 当用户 **修改 (Update)** 一条成长记录时，必须执行“先删后增”或“差异覆盖”逻辑，将该记录原有的 `RecordChunk` 和向量数据同步更新。
- 当用户 **删除 (Delete)** 记录时，必须在同一事务中级联删除其对应的 `RecordChunk`、`RecordTag` 以及 `CompanionFeedback`，严禁产生孤立的死数据。

### 3.2 兜底与边界处理
- 当新用户没有建立任何记录时，`QaService` 的 RAG 检索会返回空列表。此时 Prompt 组装层必须有兜底话术（例如：“[当前用户暂无相关历史记录背景]”），防止大模型产生未定义行为或后端报 `NullPointerException`。