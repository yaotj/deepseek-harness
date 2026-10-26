# web 前端（云平台 CloudOS）发版流程 Runbook

面向「给 H3C 云平台上的 web 前端应用包发一个新版本并升级上线」的操作。本仓前端（综管台
`web/`）出 zip 后，经云平台「应用包仓库 添加版本」+「应用实例 升级」两步上线。

- **本地出 zip 的判据**见 [web-admin-development-workflow.md](../architecture/web-admin-development-workflow.md)
  §5「验收、打包与发版闭环」（§5.2 打包、§5.3 发版）；本文只写云平台 CloudOS 里的 UI 操作步骤。
- **本文的版本号 / 地址 / 实例数均为 2026-10-26 实测快照，MUST 每次现查、NEVER 当现状引用**
  （判据见 [工程与文档规范.md](../提示词/工程与文档规范.md) §五「文档维护元规则」）。

## AI 执行提示词（agent 操作卡 · 工具无关）

> 给**任何** AI agent 的执行卡（不进 `~/.workbuddy/`，就在仓库里，经 `docs/提示词/任务路由表.md` 路由到本文）。
> 遇「前端发版 / 升级 web-frontend / 回滚前端」时照此执行；**NEVER 自造流程、NEVER 套用后端
> jar/镜像那套（`qditp-deploy-release`）**——web 前端走 CloudOS 应用包 + 应用升级，不打 docker 镜像。

- **目标**：把前端 `web/` 发到 CloudOS 的 `web-frontend` 应用并升到 `<版本>`。
- **一条命令**（生产写 MUST `--yes`；缺省 dry-run 不碰生产）：
  `python3 docs/ops/environments/scripts/web_frontend_release.py <版本> --yes`
- **执行纪律**：带 `--yes` 前**先跑一遍 dry-run** 看将发的请求；升级会滚动重建、期间业务不可用，**低峰执行**。
- **回滚**：脚本打印的 `ROLLBACK_POINT=<升级前版本>`；重跑 `python3 web_frontend_release.py <旧版本> --yes --skip-build`
  （旧版本已在软件中心，自动跳过上传）。
- **完成判据**：上传回查命中 `<版本>` + `GET /api/cloud/app-mgmt/v1.0/apps/{uuid}` 的
  `version==目标` 且 `status=OK` 且实例 `ok==num`。
- **NEVER**：照文档抄线上版本号（MUST 现查）；用包仓库「部署」按钮升级已存在应用；`$VAR` 后紧跟中文不加 `{}`。
- **排障用 API**：登录 `POST /api/sys/oapi/v1/double_factor/login`（RSA）；上传 `POST /api/cloud/deploy/v1.0/packages`
  （`pkgType=web`）；升级 `PATCH /api/cloud/app-mgmt/v1.0/apps/{uuid}/advanced-config`（body=应用详情 `metadata` 读现状改 `version_id`/`version`）。

## 〇、脚本自动化（首选）

`python3 docs/ops/environments/scripts/web_frontend_release.py <版本> [--yes]` 一条命令走完
「本地出 zip → 登录 → 上传 web 包 → 升级应用 → 核验」，复用 `lib/cloudos-*` 的鉴权与 HTTP 通道：

```bash
# dry-run：本地出 zip + 打印将发的上传/升级请求（不碰生产）
python3 docs/ops/environments/scripts/web_frontend_release.py 1.2.43.3
# 真发：上传 + 升级 + 轮询到满员；生产写 MUST 带 --yes
python3 docs/ops/environments/scripts/web_frontend_release.py 1.2.43.3 --yes
# 变体：--skip-build（复用已出 zip）/ --skip-upgrade（只上传不升级）/ --app、--pkg、--app-uuid
```

**升级的真实 API**（脚本与手工都照此）：`PATCH /api/cloud/app-mgmt/v1.0/apps/{uuid}/advanced-config`，
body 由 `GET /apps/{uuid}` 的 `metadata` 读现状后**只改 `version_id` / `version`**（`strategy` /
`nginx_version` / `resources` / `affinity` 等照抄）；`version_id` 取 `GET /packages?name=web-frontend`
里目标版本的 `id`。上传端点 `POST /api/cloud/deploy/v1.0/packages`（multipart，`pkgType=web`，
**不是 jar**）。

## 一、前置：本地出 zip

按 [web-admin-development-workflow.md](../architecture/web-admin-development-workflow.md) §5.2「打包」
产出 `web-frontend-<版本>.zip`（**zip 内 MUST 是根级 `index.html`**，带 `dist/` 前缀会整体铺深一层、页面 404）。
**回滚点＝线上当前版本**：发版前先现查线上版本号，**NEVER 照本文抄版本号**。

## 二、云平台两步升级

云平台入口 `https://172.120.209.151/`（2026-10-26 快照，**MUST 现查**；自签证书需在浏览器
「设置 → 浏览器 → 安全」开启「忽略证书校验」）。

### 第 1 步：应用包仓库「添加版本」

`软件中心 → 应用包仓库 → <应用包，如 web-frontend> → 版本管理 → 添加版本`：

- **版本号**：填新版本号（示例 `1.2.43`，现查实际目标）
- **上传方式**（二选一）：
  - `本地上传`：拖 zip 文件。**自动化 / 内嵌浏览器不支持文件上传，MUST 人工拖**
  - `远端地址`：填可访问的包 URL；免认证则取消「是否需要认证」勾选（要认证则补用户名/密码）
- `确定` → 版本列表新增一行，状态「**未使用**」

### 第 2 步：应用实例「升级」

`应用管理 → <应用实例，如 web-frontend> 详情 → 升级`（**升级已存在的应用 MUST 走这里**，见 §五）：

- **升级策略**：滚动升级（滚动期间实例数短暂 N-1/N）
- **软件包版本**（下拉）与 **目标应用版本**（文本框）：**两个都改成新版本**（示例 `1.2.43`）
- `确定` → 弹「提示：升级过程中业务不可用，请谨慎操作！」→ 再 `确定` 才真正发起（平台真实调用
  `PATCH /api/cloud/app-mgmt/v1.0/apps/{uuid}/advanced-config`，见 §〇；脚本已封装）

## 三、核验（缺一不可）

- 详情「**版本信息**」已变新版本；右上出现「成功：升级请求发送成功」
- **变更记录** 新增一行「升级应用 <app> 成功」（操作人 / 起止时间 / 状态）
- 实例（正常/全部）回到满员、无「停止或异常」；可切「事件」tab 看滚动期 K8s 事件

## 四、回滚

应用详情「**回滚**」按钮，回到升级前版本。**回滚点＝线上升级前当前版本，MUST 现查，
NEVER 照本文抄版本号**（同 [web-admin-development-workflow.md](../architecture/web-admin-development-workflow.md) §5.3「发版」）。

## 五、坑位（2026-10-26 实测）

- **应用包仓库的「部署」按钮 ≠ 升级**：它弹的是「创建应用」向导；已存在的应用要升级，走
  「应用管理 → 实例 → 升级」（§二 第 2 步）。**NEVER 用「部署」去升级已存在的应用**。
- **升级框有两个版本字段**：`软件包版本`（下拉）+ `目标应用版本`（文本框），**只改一个不生效**，
  两个都要选/填到新版本。
- 下拉 `软件包版本` 的可访问名会在「请选择 / 当前值」间跳动，**自动化按固定名字定位易踩空**；
  本次靠「点开下拉 → 点可见的 `1.2.43` 选项」才选中。**NEVER 把某次看到的可访问名当稳定选择器**。
- 添加版本「本地上传」要拖 zip，**自动化浏览器传不了本地文件**，只能人工拖。
- 升级提交有「业务不可用」二次确认，**MUST 低峰执行 + 留回滚**
  （同 [AGENTS.md](../AGENTS.md) §5.C「生产红线（MUST，缺一不可）」的灰度/回滚预案判据）。
- 用初始密码登录会弹「请及时修改密码」，**MUST 尽快改掉初始口令**。
