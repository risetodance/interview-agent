# AI 面试平台

> 仓库全局约定见 `AGENTS.md` / `CLAUDE.md`，各子项目细节见 `app/`、`frontend/`、`miniprogram/` 下的 AGENTS.md。

## 快速开始

### 一、启动中间件

```bash
docker compose up -d   # postgres / redis / rustfs 等
```

### 二、启动应用

```bash
./gradlew bootRun      # 后端 8080
cd frontend && pnpm dev # 前端 5173
```

应用启动时会**自动初始化 root 管理员账号**（详见下节），无需手工执行任何 SQL 即可登录。

### 三、首次登录与配置

1. 用 `root` / `123456` 登录（前端 http://localhost:5173）
2. 登录后请**立即修改密码**（个人中心 → 修改密码）
3. 进入管理后台 **→ AI 模型配置**（路径 `/admin/ai-models`），指派主模型（见下节）

> 在指派主模型之前，如果有普通用户触发面试 / 简历评分等 AI 功能，页面会提示「AI 模型未配置，请联系管理员配置模型」——这是预期行为，配好主模型后即恢复。

---

## root 管理员（自动初始化）

应用启动时通过 `RootUserInitializer`（CommandLineRunner）检查 `users` 表：

- 无 `username='root'` 的用户 → 插入（id=0, role=ADMIN, password=BCrypt('123456')）
- 已存在 → 跳过（保留运维改过的密码，不覆盖）

| 字段 | 值 |
|---|---|
| 用户名 | root |
| 默认密码 | 123456（BCrypt 哈希存储，登录后请尽快修改） |
| id | 0（PostgreSQL 显式插入，不消费自增序列） |
| role | ADMIN（前端 AdminRouteGuard 认这个角色） |

> **安全提醒**：123456 仅为首次登录方便，正式环境务必改密码。

---

## AI 模型配置（凭证 + 角色指派）

应用的对话大模型（主模型 + 小模型 / Reranker）采用**凭证 + 角色指派**模型，配置存在数据库里、动态管理。

模型设计：
- **凭证**（`ai_model_config` 表）：一条凭证 = 一个供应商的连接信息（baseUrl + apiKey + 模型名 + 温度），不绑定任何角色。
- **角色槽位**（`ai_model_active_role` 表）：固定两行——`CHAT`（主模型）和 `SMALL_CHAT`（小模型 / Reranker），各自指向一条凭证的 id。
- 一条凭证可同时被两个角色引用（例如主和小都用同一个 MiniMax key 的 M2.5）。
- 小模型槽位为空 = 禁用，运行时退化使用主模型。

### 配置入口

管理后台 **→ AI 模型配置**（路径 `/admin/ai-models`）：

- **新建凭证**：填供应商 + Base URL + API Key + 模型名
  - 「拉取可用模型」按钮：调供应商 `/v1/models` 拉模型列表；拉取成功后输入框右侧出现 **下拉 icon**，点击可重复选择已拉取的模型（不重新调后端），关闭弹窗后失效需重新拉取
  - 编辑态：填了新 API Key 则拉取/测试都走新值（保存前可先验证）；留空则用已存配置
- **启用为角色**：列表行点「启用为主模型」或「启用为小模型」→ 立即热生效，无需重启。一条凭证可同时占两个槽
- **禁用小模型**：小模型卡片「禁用（退化主模型）」按钮；主模型不允许禁用
- **测试连接 / 删除**

> `api_key` 是**明文存储**（不做加密），查询接口和列表页永远不返回 key，固定显示 `******`。

### 通过 SQL 初始化（可选）

也可以用 `sql/009_create_ai_model_config.sql` 脚本批量插入凭证和角色指派（编辑文件把 `<YOUR_REAL_API_KEY>` 替换为真实 key 后执行）：

```bash
psql -h <POSTGRES_HOST> -U <POSTGRES_USER> -d <POSTGRES_DB> -f sql/009_create_ai_model_config.sql
```

脚本幂等、可重复执行。

### 配置说明

- 不再需要 `application.yml` 里的 `MINIMAX_API_KEY` / `AI_MODEL` / `spring.ai.openai.base-url` 等 chat 段配置——它们已经被数据库表取代。
- Embedding 模型（DashScope `text-embedding-v4`）继续走 yml 写死，不受影响。
