# CloudOS 5.0 流程指南 ①：前端打包 → 上传应用仓库 → 部署应用 → 部署验证

> 本文是**流程视角**的调用链说明，与同目录的《CloudOS 5.0_应用仓库管理.md》《CloudOS 5.0_镜像仓库管理.md》（功能目录视角）互补：功能目录回答「这个接口怎么调」，本文回答「要做成一件事，按什么顺序调、上一步的哪个字段喂给下一步、在哪一步判定成功」。
> 场景取自软件中心（应用仓库 + 应用管理）最常见的一条：**前端构建产物打成 web 包 → 上传应用仓库 → 用应用管理部署 → 验证部署是否真的可用**。
> 接口出处：`CloudOS 5.0_PaaS_API_E7501.md`（应用仓库 §、应用组 §、应用 §、应用环境管理 §）与 `UniCloudOS Plat E7501 API文档.md`（§6.16 登录、§6.30 获取 token）。

---

## 0. 全局约定（每一步都适用）

| 项 | 值 |
| --- | --- |
| Base URL | `http://{平台IP}:11000`（Web 端口缺省 11000） |
| 请求头 | `Content-Type: application/json`、`Accept: application/json`、`X-Auth-Token: {token}` |
| 例外 | 上传应用包用 `multipart/form-data`；**无响应体的请求不能带 `Accept`** |
| 统一响应 | `{code, msg, data, total, status}` |

**成功判据（三步连判，缺一不可）**：
1. HTTP 2xx；
2. `status == true`；
3. `code` 与 `msg` 均为空串（文档原文：「正常成功情况下，code 为空串」）。部分示例返回 `"code": 0`，与「空串」表述不一致，**以实测为准**。

> ⚠️ 全文档**没有错误码表**（原文标注「后续提供」）。失败时只能依赖 `msg` 文本，排障须同时看平台侧日志。

---

## 1. 一次性前置（首跑采集，之后可复用）

| # | 要拿的东西 | 怎么拿 | 用于 |
| --- | --- | --- | --- |
| P1 | `X-Auth-Token` | Plat §6.16 `POST /sys/oapi/v1/double_factor/login`，body `{"username":"admin","password":"<base64>","domain":"default"}` → 取 `res.token` | 所有请求头 |
| P2 | `azone_id`（可用域）/ `cluster_id`（集群） | `GET /api/cloud/app-mgmt/v1.0/appenvs/resource?type=container&subType=k8sCluster&page=1&size=50` → 取 `data[].id` | 创建应用 |
| P3 | `svc_engine_id`（应用引擎） | 同上接口换 `type=engine&subType=Spring Cloud`（或 `dubbo` / `istio`）→ 取 `data[].id`；已有应用组也可从 `GET /appgroups` 的 `data[].svc_engine_id` 直接复用 | 创建应用组 |
| P4 | `namespace` | `创建应用组` 返回值 `data.namespace`，或 `GET /appgroups` 的 `data[].namespace` | 查运行日志 |

token 也可走 Keystone 原生 `POST http://{ip}:5000/v3/auth/tokens`（Plat §6.30）。集群内部调用可直连 `os-keystone-svc:5000` / `os-svcmgt-svc:11021`，此时**不带 `/sys/oapi` 前缀**。

---

## 2. 主链路总览

| Step | 目的 | 方法 + 路径 | 关键输入（来自上一步） | 关键产出 |
| --- | --- | --- | --- | --- |
| 1 | 前端构建产物打成 web 包 | 本地 `npm run build` → 压缩包 | — | 待上传文件 + 分片 |
| 2 | 上传到应用仓库 | `POST /api/cloud/deploy/v1.0/packages` | 文件、`name`、`version`、`pkgType`、`id`、`totalChunks`/`chunkNumber` | `data: true`（**不返回任何 ID**） |
| 3 | 回查包与版本 ID | `GET /api/cloud/deploy/v1.0/packages?name={包名}` | Step 2 的 `name` | `data[0].id` = 包ID；`data[0].versions[0].id` = **版本ID** |
| 4 | 准备应用组（已存在则跳过） | `POST /api/cloud/app-mgmt/v1.0/appgroups` | P3 `svc_engine_id` | `data.uuid` = appgroup_uuid、`data.namespace` |
| 5 | 创建应用 | `POST /api/cloud/app-mgmt/v1.0/appgroups/{appgroup_uuid}/apps` | Step 3 版本ID → `metadata.version_id`；P2 `azone_id`/`cluster_id` | `data: {}`（**不返回 app uuid**） |
| 6 | 取应用 UUID | `GET /api/cloud/app-mgmt/v1.0/apps?name={应用名}` | Step 5 的 `name` | `data[0].uuid`、`status`、`namespace` |
| 7 | 部署验证 | 见 §5 验证门 | Step 6 的 `uuid`/`namespace` | 判定「部署成功」 |

**顺序不可颠倒的两处**：Step 3 必须在 Step 2 之后（上传不回 ID，只能回查）；Step 5 必须在 Step 4 之后（创建应用的路径以 appgroup uuid 为父资源）。

---

## 3. 数据流地图（谁喂给谁）

```
构建产物 zip ─┐
              ├─(form-data: file/name/version/pkgType/id/totalChunks/chunkNumber)
              │   POST /deploy/v1.0/packages          → data:true
              │
GET /deploy/v1.0/packages?name=xxx                    → data[0].versions[0].id  ──┐
                                                                                  ├─ metadata.version_id
GET /appenvs/resource?type=container                  → data[].id (cluster/azone) ┼─ cluster_id / azone_id
POST /appgroups  (svc_engine_id 来自 P3)              → data.uuid ────────────────┤ (URL 父段)
                                                                                  │
POST /appgroups/{uuid}/apps  ◀────────────────────────────────────────────────────┘
   → data:{}（无 ID）
GET /apps?name=xxx                                    → data[0].uuid ─┬─ app_uuid（后续所有接口）
                                                      → data[0].namespace ─┘ namespace（日志接口）
```

三个容易踩空的地方：
- **上传不返回 ID**：`data` 只有 `true`，版本 ID 必须回查列表拿。
- **创建应用不返回 uuid**：`data` 是空对象 `{}`，只能靠 `GET /apps?name=` 反查。应用名重复时会拿到多条，**MUST 用 `app_group_id` + `name` + `version` 三重定位**。
- **`metadata.version_id` ≠ 包 ID**：用的是 `versions[].id`（形如 `vJUMozMLUd` 的短 ID），不是包自身的 UUID（形如 `975ba05a-...`）。填错会导致创建成功但拉不到包，最终状态停在 `Error`。

---

## 4. 分步详解

### Step 2 · 上传应用包（分片）

```
POST /api/cloud/deploy/v1.0/packages
Content-Type: multipart/form-data
X-Auth-Token: {token}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `file` | 是 | 当前分片的二进制 |
| `totalChunks` / `chunkNumber` | 是 | 总片数 / 当前片序号（从 1 起，前端分片库默认约定） |
| `id` | 是 | 应用包版本 ID — 文档标注必填但**未说明由谁生成**；[需实测] 一般是客户端为整个版本预生成的同一个 ID，各分片复用 |
| `pkgType` | 是 | 应用包版本类型；创建应用侧的枚举是 `helm,jar,war,image,software,web`，前端产物取 `web` — [需实测] `pkgType` 是否同枚举 |
| `name` / `version` | 是 | 包名 / 版本号，后续回查与 `metadata.version_id` 定位全靠这两个值 |
| `visible` | 是 | 是否上传至公有仓库 |
| `label`/`tagId`/`logo`/`description`/`feature` | 否 | 展示与元信息 |

**每片都要检查 `status == true`**，任一片失败只重传该片（分片续传语义由平台侧按 `id`+`chunkNumber` 判定，文档未描述去重行为 — [需实测]）。

**产物**：仅 `{"data": true}`。**不要**把这一步的 `data` 当 ID 用。

### Step 3 · 回查包与版本 ID

```
GET /api/cloud/deploy/v1.0/packages?page=1&size=20&name={包名}
```
从 `data[0].versions[]` 里按 `version` 字段挑出刚上传的那条，取 `id`。同时核对：
- `versions[].state`：`used` / `unused`；
- `versions[].file_name`、`file_size`：确认落盘的是本次产物（大小对得上是唯一的上传自检手段）；
- `versions[].metadata.file_url`：平台侧存储路径（形如 `/opt/package-files/package/{版本ID}/{文件名}`）。

> 前端构建版本号建议带**构建时间戳或 git commit**，否则同 `name`+`version` 重传会覆盖/混淆，回查无法分辨是哪一次。

### Step 4 · 创建应用组（可跳过）

```
POST /api/cloud/app-mgmt/v1.0/appgroups
{"name":"group-test","type":"Spring Cloud","svc_engine_id":"dve2xoly3pumdnvfntzi","namespace":"xxxfsadf"}
```
→ `data.uuid`、`data.namespace`。若复用已有应用组，`GET /appgroups?page=1&pageSize=20` 取 `uuid`，其 `type` 与 `svc_engine_id` 决定后续能否与该应用的服务治理方式匹配。

### Step 5 · 创建应用

```
POST /api/cloud/app-mgmt/v1.0/appgroups/{appgroup_uuid}/apps
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `name` / `version` / `description` | 是 | 应用名（后续反查 uuid 的键） |
| `package_type` | 是 | `helm,jar,war,image,software,web` — 前端包用 `web` |
| `resource_type` | 是 | `container` / `vm` |
| `workload_type` | 容器时必选 | `Deployment`（无状态）/ `Statefulset`（有状态）/ `DaemonSet` |
| `cluster_type` | 否 | `k8sCluster`（共享）/ `kaasCluster`（独享） |
| `cluster_id` / `azone_id` | 是 | 共享集群填 `azone_id`，独享集群填 `cluster_id`（文档原文：「若是独享集群，则 key 为 cluster_id」） |
| `appEntranceUrl` | 否 | 拼在访问地址后的入口路径，前端站点常填 `/index.html`，也可事后用 Step 7.4 修改 |
| `metadata` | 是 | **按 `package_type` 完全不同**，是唯一没有统一 schema 的字段 |

**web 类型的 metadata**：`创建应用` 一节**未给 web 示例**（只给了 helm / war / image / jar / DaemonSet 五种）。可参照 `升级应用`（`PATCH /apps/{app_uuid}/advanced-config`）的 web 请求体字段：`version_id`、`nginx_version`、`enable_ssl`、`crt_file_id`、`key_file_id`、`setting_file_id`、`service_name`、`affinity`、`pvcResource`、`resources`。 — [需实测] 创建时 web 的 metadata 必填子集。

> 通用容器 metadata 里 `resources[]` 是**两个元素的数组**：`[0]` 放 `security`，`[1]` 放完整的 K8S 工作负载对象（`apiVersion/kind/metadata/spec`）。其中 `metadata.name`、`metadata.namespace`、`labels.app`、`spec.selector.matchLabels.app` **四处必须与应用名/应用组命名空间对齐**，文档在多处重复强调「label 等标签需要与应用名对齐」；不一致会造成 Deployment 起来了但 Service 选不到 Pod。

**产物**：`{"code":"","msg":"","data":{},"status":true}` — **无 uuid**。

### Step 6 · 反查应用 UUID

```
GET /api/cloud/app-mgmt/v1.0/apps?name={应用名}
```
取 `data[0].uuid`（后续统一称 `app_uuid`）、`namespace`、`status`、`release_id`、`package_version_id`、`instance_num`、`instance_ok_num`。

---

## 5. 部署验证：门 1~4 必查，门 5 可选（按顺序，前一道不过不必查后一道）

### 门 1 · 应用状态收敛到 `OK`

```
GET /api/cloud/app-mgmt/v1.0/apps/{app_uuid}
```
轮询 `data.status`。状态机全集：`OK / Deploying / Upgrading / Gray_upgrading / Starting / Stopping / Stopped / Deleting / Error`。

- 判据：`status == "OK"` 且 `instance_ok_num == instance_num`。
- 中间态（`Deploying`/`Starting`）持续存在是**在跑**，不是失败；`Error` 直接跳门 3 看日志。
- **`PUT /apps/{app_uuid}` + `{"action":"start|stop|restart"}`** 用于创建后未自动拉起、或验证失败需重来时。该接口只返回 `data:true`，仍需回门 1 确认状态。

### 门 2 · 实例真的 Ready

```
GET /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/instances?duration=60
```
逐个核对 `data[]`：`status == "Running"`、`ready == true`、`ready_container_count == container_count`、`restartTime == 0`（非 0 说明在重启，看门 3）。同时留下 `name`（Pod 名）与 `containers[].name`（容器名），门 3 要用。

### 门 3 · 日志无启动异常

```
GET /api/cloud/app-mgmt/v1.0/namespaces/{namespace}/pods/{pod_name}/log?container={容器名}&tailingLines=500&sinceSeconds=600
```
`namespace` 来自门 1/应用详情，`pod_name` 来自门 2。`zoneId`（共享集群必填）/ `clusterId`（KaaS 必填）用 §1 P2 采到的值。
`data` 是**字符串**（日志正文），不是结构化对象，只能用文本判据。下载全量走 `.../pods/{pod}/log/download`（`octet-stream`）。

### 门 4 · 对外真的可访问

```
GET /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/service      → data.vip + data.services[].port_map_list
GET /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/route       → data[].route（域名）、is_https_open
```
- NodePort 型：真实入口 = 任一节点 IP + `port_map_list[].node_port`；`container_port` 是容器监听端口，`cluster_port` 是 Service 端口。
- 域名型：`route`（形如 `router.{namespace}.svc.cloudos.com`）+ `binded_service` 必须指向本应用的 `service_name`。
- **端到端判据**：`curl -i http://{入口}/` 返回 200 且前端首页 HTML 里引用的资源路径与 `appEntranceUrl` 一致（前端站点最常见的失败是 200 但白屏，即入口路径没配好）。
- 访问方式尚未创建时用 `POST /apps/{app_uuid}/service`（`type`/`service_name`/`port_map_list`/`session_keep`）；入口路径用 `PUT /apps/{appId}/entrance` + `{"appEntranceUrl":"/index.html"}`（成功返回 `data:"success"`）。

### 门 5（可选）· web 配置落地核对

```
GET /api/cloud/app-mgmt/v1.0/apps/{app_uuid}/file?upgradeId={升级版本ID}&fileType=web_conf
```
`fileType` 取值：`web_conf`（nginx 配置）/ `web_crt`（证书公钥）/ `web_key`（证书私钥）。`upgradeId` 来自应用详情的 `release_id`/升级版本 — [需实测] 精确来源。用于确认下发的 nginx 配置与前端期望的 `root`/`location` 一致。

---

## 6. 失败定位速查

| 现象 | 最可能断点 | 怎么确认 |
| --- | --- | --- |
| 上传最后一片返回成功，但列表查不到新版本 | 分片未全部落地 / `id` 各片不一致 | 逐片核对响应 `status`；比对 `chunkNumber` 是否 1..N 无缺 |
| 创建应用返回 `status:true` 但门 1 一直 `Error` | `metadata.version_id` 填成包 ID 而非版本 ID；或 `azone_id`/`cluster_id` 与实际集群不匹配 | 门 3 看 Pod 日志的拉包/调度报错；回头核对 Step 3 取到的短 ID |
| 门 1 `OK` 但门 2 `ready:false` | `resources[1]` 里 `labels.app` 与 `spec.selector.matchLabels.app` 和应用名不一致 | 比对四处标签；`GET /apps/{uuid}` 的 `release_name` |
| 门 2 全 Ready、门 4 连接被拒 | 只查了 `vip`（集群内地址）没走 `node_port`；或 Service 未创建 | `GET /apps/{uuid}/service` 的 `services[].type` 与 `port_map_list[].node_port` |
| 门 4 返回 200 但页面白屏 | `appEntranceUrl` 与前端 `publicPath`/路由 base 不一致 | 门 5 取 `web_conf` 看 `root`/`location`；改 `PUT /apps/{appId}/entrance` |
| 想重来一次 | — | `DELETE /apps/{app_uuid}`（可带 `deletePvc`）、`DELETE /appgroups/{uuid}`；应用包用 `删除` 接口清理后再上传 |

---

## 7. 本文档的已知缺口（联调前必须实测，勿当现状引用）

1. **PaaS 文档不含登录/取 token 接口**，token 只能从 UniCloudOS Plat §6.16 / §6.30 获取。
2. **错误码表缺失**（原文「后续提供」），失败只能读 `msg` 文本。
3. 上传接口的 `id`（版本 ID）**生成方与幂等语义未说明**。
4. `pkgType` 与 `package_type` 是否同一枚举未说明。
5. `创建应用` **缺 `web` 类型的 metadata 示例**，只有 `升级应用` 给了 web 字段。
6. `查询应用配置` 的 `upgradeId` 来源未说明。
7. 统一响应 `code` 时而 `""` 时而 `0`，判据需实测固化。
8. 灰度/回滚链路（`strategy=gray` + `PUT /apps/{uuid}/route` 权重 + `upgrading_versions`）**不在本流程内**，属另一条流程，见后续文档。
