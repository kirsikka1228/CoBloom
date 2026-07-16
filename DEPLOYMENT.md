# CoBloom 部署说明

## 1. 一键启动方式

在项目根目录执行：

```bash
docker-compose up --build
```

如果本机使用新版 Docker Compose，也可以执行：

```bash
docker compose up --build
```

停止服务：

```bash
docker-compose down
```

## 2. 部署密钥与访问地址

先复制 `.env.example` 为 `.env`，填写 `COBLOOM_JWT_SECRET` 和 `COBLOOM_LLM_API_KEY`。生产密钥不得写入镜像、配置文件或 Git 仓库；如果密钥曾经提交过，必须在服务商后台撤销并重新生成。

- 前端：http://localhost
- 后端：http://localhost:8080
- Swagger：默认不对外开放
- H2 Console：默认关闭

前端容器通过 Nginx 托管 Vue3 `dist` 静态文件，并将 `/api` 请求代理到 Docker Compose 内部服务地址：

```text
http://backend:8080/api
```

因此浏览器访问 `http://localhost` 时，登录、笔记、问答、图谱等接口会通过同源 `/api` 转发到后端。

## 3. H2 数据库说明

本项目使用 H2 作为课程 MVP 数据库方案，主要用于快速演示、降低部署复杂度，并验证从前端、后端、RAG、知识图谱到数据持久化的完整闭环。

当前后端配置使用文件模式 H2：

```text
jdbc:h2:file:./data/cobloom;MODE=MySQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE
```

在 Docker 容器内运行时，H2 数据文件位于后端容器的 `/app/data` 目录。该方案适合课程设计、单机演示和功能验收。

生产环境中可以替换为 MySQL / PostgreSQL，并将数据目录、连接池、账号密码、备份策略和迁移脚本纳入正式运维管理。

## 4. 设计取舍说明

- 未使用 MySQL：为了降低课程演示部署复杂度，避免评审环境还需要额外安装和初始化数据库。
- 未使用 pgvector：为了保证课程可运行性，并符合当前轻量 RAG 原型系统的约束。
- 未引入外部向量数据库：当前使用本地 embedding + cosine similarity，便于展示 RAG 检索链路和可解释评分逻辑。
- 当前架构定位：轻量 RAG 原型系统，重点展示知识库、智能问答、Embedding 检索、知识图谱和部署闭环。

## 5. 部署验证

启动后可以按以下顺序验证：

1. 打开前端：

```text
http://localhost
```

2. 检查后端登录接口可达，Swagger 和 H2 Console 在未认证状态下应返回拒绝访问：

3. 使用注册页面创建测试账号，再验证登录接口可访问。下面的值仅为请求格式占位符，不是可用凭据：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"<test-username>\",\"password\":\"<test-password>\"}"
```

4. 登录前端后验证：

- 笔记列表可打开
- Markdown 上传可用
- AI 问答页面可提交问题
- RAG 问答接口不报错
- 图谱页面可正常加载

## 6. 文件结构

```text
.
├── backend
│   └── Dockerfile
├── frontend
│   ├── Dockerfile
│   └── nginx.conf
├── docker-compose.yml
└── DEPLOYMENT.md
```
