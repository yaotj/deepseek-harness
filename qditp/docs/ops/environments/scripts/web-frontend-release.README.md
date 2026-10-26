# web 前端（CloudOS）一键发版 — `web_frontend_release.py`

把「本地出 zip → 登录 CloudOS → 上传 web 包 → 升级应用 → 核验 / 回滚」串成**一条 Python 命令**。
纯 Python 单文件：RSA 登录用 `cryptography`、HTTP 用标准库 `urllib`、JSON 用 `json`、
zip 校验用 `zipfile`、构建调 `npm` 子进程。（取代原 `web-frontend-release.sh`。）

- **概念流程 + 底层 API + 坑位**：见 `docs/ops/web前端云平台发版流程.md`。
- **给 AI 的操作卡**：技能 `qditp-web-frontend-release`（`.agents/skills/`）。
- 与后端 `qditp-deploy-release`（jar→镜像→k8s 滚更）**是两条不同链路**：web 前端走
  CloudOS 应用包 + 应用升级（`advanced-config`），**不打 docker 镜像**。

## 前置

- 本机直连 CloudOS：红岛靠 USB 网卡 `en9`（须 active，配到 K8s 控制区 172.120.2.x）；
  连通性自检 `itp-ops.sh cloudos health`。
- 构建：`web/node_modules` 已装（缺则 `cd web && npm install`）；node / npm 可用。
- Python 依赖：`cryptography`（RSA 登录）。缺则 `pip3 install cryptography`。

## 用法

```bash
python3 docs/ops/environments/scripts/web_frontend_release.py <版本> [选项]
```

| 参数 | 说明 |
|---|---|
| `<版本>` | 必填。目标版本号（如 `1.2.44`）；写入 `web/package.json` 并产出 `web/web-frontend-<版本>.zip` |
| `--yes` | **执行生产写**（上传包 + 升级应用）。缺省 = dry-run（本地出 zip + 打印将发的请求，不碰生产） |
| `--skip-build` | 复用已存在的 `web/web-frontend-<版本>.zip`，跳过本地构建（**回滚常配这个**） |
| `--skip-upgrade` | 只上传包、不升级应用 |
| `--app <名>` | 应用名（默认 `web-frontend`） |
| `--pkg <名>` | 软件中心里的应用包名（默认 `web-frontend`） |
| `--app-uuid <u>` | 直接指定应用 uuid（默认现查 `GET /apps`） |

可覆盖环境变量：`CLOUDOS_ENV`（`hongdao` 默认 / `test`）/ `CLOUDOS_HOST` / `CLOUDOS_USERNAME` /
`CLOUDOS_PASSWORD` / `CLOUDOS_RSA_PUBKEY_B64`（test 环境必填）/ `WEB_APP_NAME` / `WEB_PKG_NAME`。

## 常用示例

```bash
# 1) dry-run：本地出 zip + 打印将发的上传/升级请求（不碰生产）
python3 docs/ops/environments/scripts/web_frontend_release.py 1.2.44
# 2) 真发版：构建 + 上传 + 升级 + 轮询到满员
python3 docs/ops/environments/scripts/web_frontend_release.py 1.2.44 --yes
# 3) 回滚到上一版（旧版本已在软件中心 → 跳过上传，无需本地 zip）
python3 docs/ops/environments/scripts/web_frontend_release.py 1.2.43.2 --yes --skip-build
# 4) 只传包、不升级应用
python3 docs/ops/environments/scripts/web_frontend_release.py 1.2.44 --yes --skip-upgrade
```

## 四段流程（脚本内部）

1. **本地出 zip**：写 `web/package.json` 的 `version` → `npm run build:zip` →
   `web/web-frontend-<版本>.zip`（`zipfile` 自检**根级 `index.html`**；带 `dist/` 前缀会 404）。
2. **登录**：`cryptography` 做 RSA-1024 PKCS#1 v1.5 加密口令 → `POST /api/sys/oapi/v1/double_factor/login`
   拿 token（进程内持有；`X-Auth-Token` 头走后续请求）。
3. **上传 web 包**：`POST /api/cloud/deploy/v1.0/packages`（`urllib` 手拼 multipart，**`pkgType=web`**，
   `id` 客户端预生成），回查确认落盘；该版本已存在则跳过。
4. **升级应用**：`GET /apps/{uuid}` 的 `metadata` 读现状 → 组 `advanced-config` body（只改
   `version_id` / `version`）→ `PATCH /api/cloud/app-mgmt/v1.0/apps/{uuid}/advanced-config` → 轮询到
   「目标版本 + status=OK + 实例满员」。

**回滚点**：升级前脚本打印 `ROLLBACK_POINT=<升级前线上版本>`；回滚＝重跑旧版本（`--yes --skip-build`）。

## 完成判据（全部满足才算发版成功）

- 上传回查命中 `<版本>`；
- 升级后 `GET /apps/{uuid}` 的 `version == 目标`、`status == OK`、实例 `ok == num`；
- 已记下 `ROLLBACK_POINT`（旧版本）。

## 已知坑

- 生产写 **MUST 带 `--yes`**；升级会滚动重建 Pod、**期间业务不可用**，低峰执行。
- 登录口令 MUST 真 RSA 加密（`cryptography` 保证 PKCS#1 v1.5，128 字节 / 172 字符）；登录失败先看
  `itp-ops.sh cloudos auth-debug`（账号 / 公钥 / 网络）。
- `docs/`（含本脚本）在根 `svn:ignore`、**不入 SVN**；随 `qd/` git 仓库走。
- 应用名 / 包名若改名、或平台改 `advanced-config` 字段结构，改 `build_upgrade_body()` 那段是唯一要动的地方。
