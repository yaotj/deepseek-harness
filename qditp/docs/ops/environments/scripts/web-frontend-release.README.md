# web-frontend-release.sh — web 前端（CloudOS）一键发版

把「本地打包 → 登录 CloudOS → 上传 web 包 → 升级应用 → 核验」串成一条命令。
鉴权 / HTTP 复用同目录 `lib/cloudos-*`（与 `itp-ops.sh cloudos` 同源）。

- **概念流程 + 底层 API + 坑位**：见 `docs/ops/web前端云平台发版流程.md`（本文件只讲脚本怎么用）。
- **给 AI 的操作提示词**：技能 `qditp-web-frontend-release`（`~/.workbuddy/skills/`）。
- 与后端 `qditp-deploy-release`（jar→镜像→k8s 滚更）**是两条不同链路**：web 前端走
  「CloudOS 应用包 + 应用升级（advanced-config）」，**不走 docker 镜像构建**。

## 何时用

综管台 / 前端 `web/` 出一版新前端，要发到 H3C 云平台（CloudOS）的 `web-frontend`
应用包并把应用升级过去。红岛生产（`CLOUDOS_ENV=hongdao`，`172.120.209.151 / itpbadmin`）。

## 前置

- 本机直连 CloudOS：红岛靠 USB 网卡 `en9`（须 active，配到 K8s 控制区 172.120.2.x）；
  连通性自检 `itp-ops.sh cloudos health`。
- 构建：`web/node_modules` 已装（缺则 `cd web && npm install`）；node / npm 可用。

## 用法

```bash
docs/ops/environments/scripts/web-frontend-release.sh <版本> [选项]
```

| 参数 | 说明 |
|---|---|
| `<版本>` | 必填。目标版本号（如 `1.2.44`）；写入 `web/package.json` 并产出 `web/web-frontend-<版本>.zip` |
| `--yes` | **执行生产写**（上传包 + 升级应用）。缺省 = dry-run（只本地出 zip + 打印将发的请求，不碰生产） |
| `--skip-build` | 复用已存在的 `web/web-frontend-<版本>.zip`，跳过本地构建（**回滚常配这个**：旧版本已在仓库、无需重新出包） |
| `--skip-upgrade` | 只上传包、不升级应用 |
| `--app <名>` | 应用名（默认 `web-frontend`） |
| `--pkg <名>` | 软件中心里的应用包名（默认 `web-frontend`） |
| `--app-uuid <u>` | 直接指定应用 uuid（默认现查 `GET /apps`） |

可覆盖环境变量：`CLOUDOS_ENV` / `WEB_APP_NAME` / `WEB_PKG_NAME` / `WEB_PKG_TYPE`（默认 `web`）/ `WEB_APP_UUID`。

## 常用示例

```bash
# 1) 先看要发什么（不碰生产）：本地出 zip + 打印将发的上传/升级请求
web-frontend-release.sh 1.2.44

# 2) 真正发版：构建 + 上传 + 升级 + 轮询到满员
web-frontend-release.sh 1.2.44 --yes

# 3) 回滚到上一个版本（旧版本已在软件中心 → 自动跳过上传，无需本地 zip）
web-frontend-release.sh 1.2.43.2 --yes --skip-build

# 4) 只把包传到软件中心、暂不升级应用
web-frontend-release.sh 1.2.44 --yes --skip-upgrade
```

## 四段流程（脚本内部）

1. **本地出 zip**：写 `web/package.json` 的 `version` = 目标版本 → `npm run build:zip` →
   `web/web-frontend-<版本>.zip`（zip 内**必须根级 `index.html`**，脚本会自检；带 `dist/` 前缀会 404）。
2. **登录**：`cloudos_get_token`（RSA 加密口令，token 缓存 + 401 自动重登）。
3. **上传 web 包**：`POST /api/cloud/deploy/v1.0/packages`（multipart，**`pkgType=web`**，客户端预生成
   `id`），回查确认落盘；该版本已存在则跳过。
4. **升级应用**：`PATCH /api/cloud/app-mgmt/v1.0/apps/{uuid}/advanced-config`，body 由
   `GET /apps/{uuid}` 的 `metadata` 读现状后**只改 `version_id` / `version`**（`strategy` / `nginx_version` /
   `resources` / `affinity` 照抄）；再轮询到「目标版本 + 满员 + status=OK」。

**回滚点**：脚本每步会打印 `ROLLBACK_POINT=<升级前线上版本>`；回滚＝重跑一个旧版本
（`--yes`，可加 `--skip-build`）。

## 完成判据（全部满足才算发版成功）

- 上传回查命中 `<版本>`（`GET /packages?name=web-frontend`）；
- 升级后 `GET /apps/{uuid}` 的 `version == 目标`、`status == OK`、实例 `ok == num`；
- 已记下 `ROLLBACK_POINT`（旧版本）以备回滚。

## 已知坑

- **生产写 MUST 带 `--yes`**；升级会短暂业务不可用（滚动重建），低峰执行。
- 内嵌浏览器/自动化**传不了本地文件**——所以「上传」这步走脚本（HTTP 直传），不靠 UI 拖拽。
- 应用名 / 包名若改名、或平台改 `advanced-config` 字段结构，改脚本里的 `build_upgrade_body` 那段是唯一要动的地方。
- `docs/`（含本脚本）在根 `svn:ignore`、**不入 SVN**；它随 `qd/` git 仓库走。
