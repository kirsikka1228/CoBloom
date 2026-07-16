# CoBloom AI 知识库与智能问答平台

CoBloom 是一个课程设计级的 AI 知识库与智能问答平台，包含 Spring Boot 后端、Vue3 前端、H2 数据库、Mock AI、Embedding 检索、RAG 问答、知识图谱、推荐系统和 Docker 部署配置。

## 技术栈

- 后端：Java 21、Spring Boot 3、Spring Security、JWT、BCrypt、MyBatis-Plus、H2
- 前端：Vue 3、Vite、Vue Router、Pinia、Element Plus、Axios、Marked、ECharts
- AI/RAG：Mock AI、Hash Embedding、Cosine Similarity、TopK 检索
- 部署：Docker、Docker Compose、Nginx

## 目录结构

```text
.
├── backend                 # Spring Boot 后端
├── frontend                # Vue3 前端
├── docker-compose.yml      # Docker 一键启动配置
├── DEPLOYMENT.md           # 部署说明
└── README.md
```

## 环境要求

本地运行需要：

- Java 21+
- Maven 3.9+
- Node.js 18+
- npm

Docker 运行需要：

- Docker
- Docker Compose

## 本地构建与启动

### 1. 启动后端

进入后端目录：

```bash
cd backend
```

下载依赖并启动：

启动前必须通过环境变量提供密钥，真实密钥不要写入或提交到 `application.yml`：

```powershell
$jwtBytes = New-Object byte[] 48
$jwtRng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$jwtRng.GetBytes($jwtBytes)
$env:COBLOOM_JWT_SECRET = [Convert]::ToBase64String($jwtBytes)
$jwtRng.Dispose()
$env:COBLOOM_LLM_API_KEY = "请替换为新生成的LLM API Key"
$env:COBLOOM_CORS_ALLOWED_ORIGINS = "http://localhost:5173,http://localhost"
```

```bash
mvn spring-boot:run
```

也可以先打包再运行：

```bash
mvn clean package -DskipTests
java -jar target/cobloom-backend-0.1.0.jar
```

后端默认地址：

```text
http://localhost:8080
```

出于安全考虑，Swagger 和 H2 Console 默认关闭或不对未认证用户开放。开发调试时如需开启，必须使用仅限本机的开发配置，不要在演示或部署环境开放。

H2 数据库文件默认生成在：

```text
backend/data/
```

### 2. 启动前端

新开一个终端，进入前端目录：

```bash
cd frontend
```

安装依赖：

```bash
npm install
```

启动开发服务器：

```bash
npm run dev
```

前端开发地址：

```text
http://localhost:5173
```

### 3. 前端生产构建

```bash
cd frontend
npm run build
```

构建产物生成在：

```text
frontend/dist/
```

## Docker 一键启动

项目已提供 Docker 部署配置，可以在项目根目录执行：

```bash
docker-compose up --build
```

如果本机使用新版 Docker Compose，也可以执行：

```bash
docker compose up --build
```

启动前复制 `.env.example` 为 `.env`，填写新生成的密钥。`.env` 已被 Git 忽略，不要提交。启动后访问：

- 前端：http://localhost
- 后端：http://localhost:8080
- Swagger：默认不对外开放
- H2 Console：默认关闭

停止服务：

```bash
docker-compose down
```

## Docker 构建说明

后端镜像：

- 使用 Maven 构建 Spring Boot jar
- 使用 `openjdk:21` 运行 jar
- 暴露 `8080` 端口

前端镜像：

- 使用 `node:18` 构建 Vue3 dist
- 使用 Nginx 托管静态文件
- `/api` 请求代理到后端服务 `backend:8080`
- 支持 Vue Router history 路由 fallback

## 创建账号

项目不提供共享的默认账号或固定密码。首次运行后，请通过注册页面创建自己的账号。部署环境中的账号密码只保存在数据库中，不应写入源代码、文档或前端默认值。

## 核心功能

- 用户注册、登录、JWT 鉴权
- 笔记 CRUD
- Markdown 上传与编辑
- AI 摘要与关键词提取
- Embedding 生成与余弦相似度检索
- RAG 智能问答
- 问答历史与引用来源
- 问答历史删除
- 推荐系统
- 全局知识图谱
- 单篇笔记局部知识图谱
- H2 数据库存储

## H2 数据库说明

本项目使用 H2 作为课程 MVP 数据库方案，目的是降低部署复杂度，方便快速演示完整系统闭环。

当前配置适用于课程设计、本地开发和答辩演示。生产环境中可以替换为 MySQL 或 PostgreSQL，并补充正式的数据迁移、备份和运维方案。

## 常用验证命令

后端打包：

```bash
cd backend
mvn clean package -DskipTests
```

前端构建：

```bash
cd frontend
npm run build
```

检查 Docker Compose 配置：

```bash
docker compose config
```

一键启动：

```bash
docker compose up --build
```

## 更多部署说明

详细 Docker 部署说明见：

```text
DEPLOYMENT.md
```
