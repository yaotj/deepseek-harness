# 综管台（管理后台）开发工作流

> 本文档是 `AGENTS.md` §5.E（综管台工作流）的落地细则（开发 6 步 + 验收发版闭环）。
> 动手前 **MUST** 先读 `docs/architecture/web-server.md`（结构事实）与本文档。
> 结构事实以 `docs/architecture/web-server.md` 为准；本文档只讲「怎么在综管台上开发」。

## 0. 一句话定位

- 综管台 = `web-server`（RuoYi-Vue3 改造）+ `web/`（Vue3 前端），定位是「权限/菜单后台 + 可配置定时任务调度器」，**不是 BFF、不是聚合层**。
- `web-server` 只在「定时任务」一条路径上调业务服务（`web-admin` 已引入 `rpc`）；**HTTP 接口层 NEVER 调业务服务**，运营查询接口后端在业务模块的 `/page/**`。
- 共用同一 Oracle 实例，但只读写 `sys_*`；与业务模块的自研 `other.sql.*` 是两套完全不同的数据源装配，配置写法不可互抄。

## 1. 模块与依赖方向

| 子模块 | 职责 |
|---|---|
| **web-admin** | 唯一可执行模块 + RuoYi 原生 controller 层（端口 8080，context-path `/`，未设 `spring.application.name`） |
| **web-system** | `sys_*` 域的 domain / mapper / service |
| **web-quartz** | Quartz 调度与任务 CRUD |
| **web-generator** | 代码生成器（Velocity 模板） |
| **web-framework** | Spring Security / JWT、Druid 动态数据源、AOP（日志/数据权限/限流）、全局异常 |
| **web-common** | 基础设施：注解、`AjaxResult` / `BaseEntity`、工具类、XSS 与 Referer 过滤 |

**依赖方向**：`web-admin → web-quartz / web-generator / web-system → web-framework → web-common`。
新增后台功能 **MUST** 遵循此方向：controller 放 `web-admin`，domain/service/mapper 放 `web-system`，安全/切面放 `web-framework`，**NEVER** 让 `web-common` 反向依赖上层。

## 2. 开发工作流（6 步，对齐 §5.E）

1. **Analyze**：明确是「`sys_*` 后台管理功能」还是「业务查询页面」——两者落点不同（见 §3 红线）。确定影响的子模块与接口编号。
2. **Search**：先读 `docs/architecture/web-server.md` 与本文档；复用 `web-system` 已有 service / mapper，前端复用 `web/src/api/system|monitor|tool`。
3. **Plan**：列出新增/修改文件（controller 在 `web-admin`、domain/service/mapper 在 `web-system`）；简单功能可跳过确认。
4. **Implement**：
   - 后端：Spring Security + JJWT 鉴权已就位，新接口 **MUST** 走既有 Filter / 注解；**禁止在 `/page/**` 之类路径新增无鉴权端点**（现网该缺口是已知安全债，勿扩散）。
   - 前端：Vue 3.5 `<script setup>` + Element Plus；新增页面 **MUST** 同时补菜单 SQL（写法参考 `web-server/sql/*-menu.sql`）。
   - 代码生成器只用于 `sys_*` RuoYi 风格代码；**NEVER** 用它生成业务模块代码（业务模块走自研 `mybatis-adaptor`）。
5. **Review**：改构建相关 ⇒ `mvn -o clean test-compile`（web-server 模块）；改文档 ⇒ `lint-prompts.py` ERROR 0；**改前端 ⇒ MUST 走本文档 §5.1 验证四件套**（全量语法自检 + 同基线回归 + 产物内锚点，缺一不可）。
6. **Document**：类名 / URL / 菜单 / 接口变动 **MUST** 同步回写 `docs/architecture/web-server.md` 与本文档；涉及事实 **MUST** 回写（AGENTS.md §5.2「回写义务（通用）」第 5 步）。

## 3. 红线（MUST / NEVER）

- **NEVER** 在 `web-admin` controller 注入 `XxxClient` 调业务服务（会滑向聚合层、破坏边界）；跨服务动作只许经 `web-quartz` 的 `XxxQuartzTask → XxxClient` 路径。
- 新增运营查询接口若后端在业务模块 `/page/**`，**NEVER** 误放进 `web-server`（菜单虽在 `web-server`，后端约定在业务模块）。
- `token.secret` 现网是 26 位弱口令，新部署 **MUST** 改为 `${WEB_TOKEN_SECRET:}` 由 Secret 注入；更换后所有已签发 JWT 失效，须低峰期执行。
- Druid 主从与业务模块自研 `other.sql.*` 配置写法**不可互相复制**。
- 多份 properties 明文含 DB / Druid 口令，触碰 **MUST** 提示人工复核，**NEVER** 回显口令值。
- `web-server` 端口 8080 与三个支付宝模块冲突，同机启动必冲突。
- **API 路径前缀 MUST 与 nginx 反代一致**（`/web-server/`、`/para-server/`、`/face-pay-server/`…）。前缀写错**不报 404**，而是落进静态 catch-all：GET/HEAD 回退 `index.html` 返 200（**假成功**），PUT/POST/DELETE 判 405 ⇒ 前端只显示「系统接口NNN异常」且**后端零日志**。排查「接口异常但查不到后端日志」先核前缀。
- **侧边栏菜单样式 MUST 写全局 `web/src/assets/styles/sidebar.scss`**：`Sidebar/index.vue` 的 `<style scoped>` 对菜单 DOM **一律无效**（scope 哈希不匹配）。改样式前先确认「编译出的选择器能否命中真实元素」，否则改完不生效还查不出原因。
- **码表唯一真源、各码值空间 MUST 独立**：`utils/payChannel.js` / `utils/cardType.js` / `utils/f2fChannel.js` 与 alipay 页的发卡渠道分属**不同空间**，`02`/`03` 在各空间含义完全不同 —— **「同列出现」≠「同一码值空间」**，合并即把设备说成卡种。

## 4. 构建与运行

- 构建顺序遵循全局：`resource/micro` → `model` + `rpc` → 目标模块（`web-admin` 为可执行模块）。
- 前端 `web/`：`npm run dev`（`.env.development`）/ 构建走 `vite.config.js` + `nginx.conf` 反向代理。
- 业务查询页面（`api/para`、`api/trans`）经反向代理转发到业务模块 `/page/**`，不落在 `web-server`。

## 5. 验收、打包与发版闭环

> §2 的 6 步只到「改完」。综管台前端是「源码 → 构建物 → zip → 云平台镜像 → nginx」**五段链路**，任一段没核就等于没验证。
> 本文档只写判据；操作步骤见 `AGENTS.md` §5.E 与技能 `qditp-deploy-release`；回写见 §6。

### 5.1 验证四件套（缺一不可）

| # | 动作 | 判据 |
|---|---|---|
| ① | 前端**全量语法自检**：用 `@babel/parser` 解析 `web/src` 全部 `.vue` / `.js` | 记录「文件数 / script 块数 / ERROR 数」，**ERROR 0** |
| ② | **同基线回归**：对既有 IT 判据串逐项比对（IT-001/003/004/005/006/007 等） | **0 处不符**；有意引入的差异逐条说明 |
| ③ | **产物内锚点**：开包核 `index.html` md5 + 关键 chunk 内容 / CSS 规则原文 | 与源码意图一致 |
| ④ | 后端改动：`mvn -o clean test-compile`；涉 SQL 再跑真库 smoke | 通过 |

- **① 不能用 `vite build` 代替**：构建只暴露首个错误、且慢；**NEVER 只看 diff 就认为完成**。
- 自检脚本 MUST 在 `web/` 目录内跑：`@babel/parser` 不在 `/tmp` 的模块解析路径上（放 `/tmp` 会 `ERR_MODULE_NOT_FOUND`）。
- **② 不能省**：提交前跑一遍，防「改了 A 页碰坏 B 页」——本仓库已出现过多起。

### 5.2 打包

- `cd web && npx vite build`；版本号改 `web/package.json`。
- `cd dist && zip -qr ../web-frontend-<版本>.zip .` —— **zip 内 MUST 是根级 `index.html`**。前端镜像的有效步骤就是
  `ADD <zip> /usr/share/nginx/` + `RUN unzip -o <zip> -d html`，带 `dist/` 前缀会整体铺深一层、页面直接 404。
- `web/dist` 被忽略，`web-server/web-admin/src/main/resources/static` **是纳管的**：刷新用
  `rsync -a --delete web/dist/ <static>/`，随后 `diff -rq` 校验一致。

### 5.3 发版

- 后端：技能 `qditp-deploy-release`。
- **前端镜像由云平台流水线构建、不在本仓 jkube 体系** ⇒ 本机只能出 zip 并 `scp` 到 `k8s-master:/home/java/`，
  **发版 MUST 走云平台**。**NEVER** 以为 `kubectl set image deployment/web` 能靠本仓产物完成。
- **回滚点＝线上当前版本**：发版前先复核线上 `JAR_NAME`，**NEVER 照文档抄版本号** —— 「上一版构建」≠「线上版本」。

### 5.4 上线后核验

① `index.html` md5 与本地一致；② 新 chunk 返回 200 / 旧 chunk 返回 404；③ 关键 chunk md5 与本地一致；④ 线上 CSS/JS 用 `rg` 抽规则原文比对。

## 6. 回写义务

- 结构 / 约定变动 **MUST** 回写 `docs/architecture/web-server.md` 与本文档（AGENTS.md §5.2「回写义务（通用）」第 5 步）。
- 路由表（`AGENTS.md` §5.1 / `docs/提示词/任务路由表.md`）漏了综管台类任务时，**MUST** 补进路由表（属高频 6 条时同步 §5.1），NEVER 让下一个 AI 重新摸索。
