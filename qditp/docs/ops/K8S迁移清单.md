# K8S 迁移清单（异网新集群）

> 口径：**当前集群（`k8s02`，业务节点 `172.20.211.21~26` + 网关节点 `172.20.211.200`）整体迁到异地异网新集群**，Oracle / HSM / FTP / ACC / 公网入口地址**全部会换**。范围含 istio 入向路由（`accountserver-gw` + `fep-app-vr`），不含 Oracle 实例本身的搬迁。
>
> 本文是**现状盘点 + 替换矩阵 + 风险与顺序**，不是执行 Runbook。流量切换的操作细节见 [`流量切换.md`](流量切换.md)；外部依赖与环境变量的历史记录见 [`生产环境清单.md`](生产环境清单.md)。
>
> **对象清单已导出成可复用 YAML：[`../../deploy/k8s/`](../../deploy/k8s/)**（2026-09-22 同批产出，敏感 env 已收口到 `05-secret-template.yaml`、值为空）。本文只描述「要改什么」，**具体字段现状看那套 YAML**。
>
> **所有数字与地址均为 2026-09-22 实测**。执行当天 MUST 重新现查一遍（镜像 tag、Service 名后缀、VS 下标都会漂移）。

## 一、现状盘点（2026-09-22 实测）

### 1.1 集群底座

- Kubernetes **v1.28.6**，containerd **1.7.11**，OS H3Linux 1.1.0（kernel 4.14）
- 7 个节点：3 control-plane（`.21` / `.22` / `.23`）+ 3 worker（`.24` / `.25` / `.26`）+ 1 网关节点 `.200`（`k8s02-gateway-866a2`）
- **业务 Pod 会被调度到 control-plane 与 gateway 节点上**（实测 `daily-ticket` / `fep-dev` / `security-server` / `victoria-logs` 都在 `.200`）——新集群若给 master 打了 taint，容量规划要按「只有 worker 可用」重算
- istio **1.24.6**（`istio-system`：istiod / jaeger / kiali / grafana），ns `itp` 带 `istio-injection=enabled` ⇒ **每个 Pod 都是 `2/2`（业务 + envoy sidecar）**
- StorageClass 只有一个：`apigateway2`（provisioner `apigateway2`，`Retain`，**不支持扩容**，只有 RWO）
- 命名空间：`itp`（业务）、`itp-gateway`（入向网关）、`istio-system`、`kube-prometheus`、`tms`、`svcgw4d572a71`、`default`（Harbor 在这里）

### 1.2 工作负载（ns itp，共 32 个 Deployment + 1 StatefulSet）

**必迁（24 个业务 Deployment）**，全部 `replicas=1`：

- 接入层：`fep-app`（`itp/fep-app`）、`fep-dev-server`、`fep-acc`、`fep-alipay`
- 业务层：`account`（`itp/account-server`）、`ticket-server`、`trans-query`、`pay-sign-server`、`gate-txn-pay-server`、`daily-ticket-server`、`face-pay-server`、`collect-pay`、`card-pool-server`、`recon-server`、`alipay-account-server`、`alipay-pay-sign-server`
- 公共能力：`para-server`、`key-server`、`blacklist-server`、`industry-data-server`
- ACC / 安全：`security-server`（`itp/acc-security-server`）、`es-server`（`itp/acc-es-server`）
- 管理后台：`web-admin`、`web`（nginx，镜像在 `user/1df71957.../web-nginx1.20`，不在 `itp/` 仓库下）

**配套（2 个）**：`victoria-logs`（StatefulSet，唯一有 PVC 的负载）、`database`（BYOK database MCP Server，研发辅助、不是 Oracle）

**可不迁（7 个，istio 示例遗留）**：`details-v1`、`productpage-v1`、`ratings-v1`、`reviews-v1/v2/v3`、`kennethreitz-httpbin`，连带 `bookinfo-gateway` / `httpbin` 两个 Gateway、`bookinfo` / `httpbin` 两个 VS、`kennethreitz-httpbin-dr` 一个 DestinationRule。**新集群直接不建**。

**仓库有模块但集群里没部署**：`collect-ticket-server`、`acc-secure-server`。迁移前 MUST 确认是「刻意未上线」还是「漏了」——前者不必迁。

- 资源配额两档：`1C2Gi`（requests=limits）或 `limits 2C4Gi / requests 1C2Gi`。24 个业务服务合计 requests 约 **26 vCPU / 52Gi**，limits 约 **38 vCPU / 76Gi**（不含 sidecar）。新集群按 limits 留量。
- **全部 Deployment 都没有 livenessProbe / readinessProbe**，也没有 HPA / PDB / NetworkPolicy / PeerAuthentication / AuthorizationPolicy。这是迁移**最该顺手补上**的一项（没有探针，「Pod Running 但端口不监听」这类故障只能靠人工探活发现，见 AGENTS.md §7）。
- **单副本是强约束、NEVER 在新集群顺手扩副本**：`recon-server`（进程内 `AtomicBoolean` 无 DB 锁）、`web-admin`（Quartz 内存 JobStore）、`face-pay-server`（7 个 `@Scheduled`）、`collect-pay`（`SingleTicketRefundTask` 4 处）。

### 1.3 网络与入向路由

```
公网 58.56.166.170:48000
  → 网关节点 172.20.211.200
    → ns itp-gateway / Deployment itp-gateway（image: auto，label istio=ingressgateway）
       svc itp-gateway  ClusterIP  50908→8080(http) / 45609→443(https)
    → ns itp / Gateway accountserver-gw（selector istio=ingressgateway，server port 8080 HTTP，hosts *）
    → VirtualService fep-app-vr（gateways=[accountserver-gw]，hosts=[*]）
```

`fep-app-vr` 8 条前缀的实测落点（**含 rewrite 差异，迁移时逐条复制，NEVER 凭记忆重写**）：

- `/fep-app/` → `fep-app-hr32k-svc:9101`，rewrite `/`
- `/fep-dev/` → `fep-dev-server-748lq-svc:30009`，rewrite `/`
- `/fep-acc/` → `fep-acc-wracu-svc:30030`，rewrite `/`
- `/fep-alipay/` → `fep-alipay-rec8g-svc:30020`，rewrite `/`
- `/itptvm/` → `face-pay-server-svc:30025`，rewrite **`/itptvm/`**（不是 `/`）
- `/itpbom/` → `face-pay-server-svc:30025`，rewrite **`/itpbom/`**（不是 `/`）
- `/itpagm/` → `fep-dev-server-748lq-svc:30009`，rewrite `/`
- `/para-server/` → `para-server-pufrl-svc:30026`，rewrite `/`

Service 侧两个坑：

- **31 个 Service 全是 NodePort，端口段 30009~30035**。其中多数「Service port 等于 NodePort 号」（如 `face-pay-server-svc` 是 `30025:30025`），少数是真实容器端口（`account-n4ba6-svc` 是 `9098:30013`、`fep-app-hr32k-svc` 是 `9101:30010`、`ticket-server-bsyju-svc` 是 `9100:30014`、`pay-sign-server-hsa9w-svc` / `blacklist` / `key` / `es` / `security` 是 `8080:30xxx`）。**新集群 MUST 逐个照抄，NEVER 统一成一种形态** —— 一堆 `service.*.url` 写死了这些端口号。
- **Service 名带随机后缀**（`-n4ba6-` / `-hsa9w-` / `-c23ku-` …），是 PaaS 控制台生成的。新集群若由控制台重建，后缀必然不同 ⇒ **所有按 Service DNS 配的 `service.*.url` 全部失效**。这是迁移最大的一类改动面（见 §2.2）。

### 1.4 配置与密钥现状（**最大风险区**）

- **没有任何业务 Secret**。ns `itp` 只有 1 个 secret：`default-dockercfg-f6af3`（镜像拉取用，且只有 7 个示例 Pod 在引用；24 个业务 Deployment 的 `imagePullSecrets` 为空 —— 靠节点级 containerd 凭据拉 Harbor）。
- **全部配置以明文 env 内联在 Deployment 里**，其中包含这些敏感键（**只列键名与所在 Deployment，值不在本文出现**）：
  - DB 口令：`other.sql.password`（16 个服务）/ `DB_PASSWORD`（`card-pool` / `recon` / `face-pay`）/ `spring.datasource.password` + `spring.datasource.druid.master.password`（`web-admin`）
  - 报文密钥：`itp.signKey`（`account` / `alipay-account` / `security-server`）、`jhm.key`（`collect-pay` / `face-pay`）、`app.signKey`（`collect-pay`）
  - HSM / 3DES：`security.publicKey` / `security.privateKey`（`security-server`）、`acc.3des.key` / `appserver.3des.key`（`key-server`）
  - 后台：`token.secret`、`spring.datasource.druid.statViewServlet.login-password`（`web-admin`）
  - 支付：`pay.center.merchant-private-key` / `pay.center.paycenter-public-key`（`alipay-pay-sign`，当前是占位值）
  - FTP：`CARD_POOL_FTP_PASSWORD`、`RECON_FTP_PASSWORD`（`card-pool` / `recon`）、`ftp.password`（`es-server`）、`para.ftp.password`（`para-server`）
  - MCP：`MCP_SECURITY_ADMIN_PASSWORD`、`ENTROPY_MCP_DATABASE_CONNECTIONS_QDITP_PASSWORD`（`database`）
- ConfigMap 只有两类在用/半用：`websyspresetweb-version-*`（**40+ 个历史版本**，`web` 只挂当前一个 `websyspresetweb-version-1.29` 的 `nginx.conf`，其余是 PaaS 发布残留，**不迁**）；`key-server-config-version-1.0` / `key-server-config1-version-1.0` / `-1.1` **无任何 Pod 挂载，是孤儿，不迁**（迁移前 MUST 确认它们不是「本该挂上却漏了」）。
- **每个业务 Pod 都 hostPath 挂 `/etc/localtime`** ⇒ 新集群节点时区 MUST 是 CST，否则全线时间错 8 小时（对账账期、乘车码时效直接受影响）。
- `security-server` 另有一处 hostPath：`/home/app/logs/security-server` → 容器 `/home/javaapp/app/logs/`。**这是节点本地目录，Pod 换节点即看不到旧日志**；新集群要么照建目录，要么换成 PVC。

### 1.5 存储

- **唯一 PVC**：`victoria-logs-victoria-logs-data-victoria-logs-0`，15Gi RWO，sc `apigateway2`，`Retain`。日志数据要不要带走是业务决定（§3 问了）。
- **`recon-server` 的对账文件落在容器本地**：`RECON_STORAGE_ROOT=/home/javaapp/app/recon`，**无 PVC、无 hostPath** ⇒ Pod 一重启产物即丢。AGENTS.md §2.2.2 已记「共享存储（RWX PVC）与单副本约束未落地」，**迁移是补这一项的最佳时机**；但 `apigateway2` 只给 RWO，新集群 MUST 确认有 RWX 供应者（NFS / CephFS），否则这项仍然落不了地。
- 其余服务全部无状态，状态都在 Oracle。

### 1.6 外部依赖（**异网迁移必须逐条重新开通 + 换地址**）

- Oracle：`172.20.222.3:1521 / AFCITPDB`（用户 `qditp`）—— 出现在 20 个服务的 `other.sql.host` / `DB_HOST` / `spring.datasource.url`
- 加密机 HSM：`security.firstIp=172.20.201.4`，端口 `security.firstPort=6666` / `security.secondPort=7002`（TCP 长连，`security-server`）
- HCE 卡数据：`172.20.201.2:12301/secret-web/...`（`account`）
- ACC：`172.20.211.11:32605`（`account` 的 `employee-card.acc-query-url`、`card-pool` 的 `ACC_BASE_URL=.../aclc`）
- FTP 三处**不同主机**：`172.20.215.3`（`card-pool` `/itp/qrLoigcNum` + `recon` `/itp/recon`）、`172.20.214.101`（`para-server` `/parameter/cur/`）、`10.4.22.69`（`es-server`，**网段与其余全不同，异网迁移八成要重新申请**）
- 支付中心（公网域名，地址可不变但**出网策略要重开**）：`dtcustomer.bestonepay.com`（`ngpayment-gateway` / `ngopenplatform` / `testngbackV2`）
- 支付宝：`openapi.alipay.com`
- **回调入口 `58.56.166.170:48000`**：被 8 处 env 写死（`pay.center.callback-url`、`refund-notify-url`、`daily-ticket.pay.notify-url`、`PAY_CENTER_PAY_NOTICE_URL`、`PAY_CENTER_REFUND_NOTICE_URL`、`PAY_CENTER_SUPPLEMENT_NOTICE_URL`、`NOTIFY_APP_REFUND_NOTICE_URL`、`pay.sign.request-pay-notify-url` / `request-refund-notify-url` / `default-notify-url`）。**异网迁移后这个地址会变，且变更必须同步到支付中心与支付宝的商户配置** —— 对端不改，回调永远打回旧集群。
- Harbor：`os-harbor-svc.default.svc.cloudos:443`。**这是当前集群内的 Service DNS**，新集群不存在 ⇒ 所有 32 个镜像引用都解析不了。见 §3 R1。

## 二、替换矩阵（迁移时必须逐项改的东西）

### 2.1 一次性基础设施

- 新集群装 istio（**建议对齐 1.24.6**，跨大版本升级会连带改 `networking.istio.io` API 版本与 Gateway 行为），建 ns `itp` + `istio-injection=enabled`、ns `itp-gateway`
- 新 Harbor（或可达的镜像仓库）+ 32 个镜像搬运；确认新集群节点有拉取凭据，或补 `imagePullSecrets`
- StorageClass：至少一个 RWO（给 victoria-logs），想修 recon 的话还要一个 RWX
- 节点时区置 CST（`/etc/localtime` hostPath 依赖）

### 2.2 地址类改动（按类型分组，每类的改法不同）

**① 节点 IP + NodePort（写死了旧集群节点 IP，新集群必错）**——实测出现在这些键上：

- 指 `172.20.211.23`：`account`(ticket 除外的多条)、`alipay-account`、`alipay-pay-sign`（account/para/blacklist/industryData/ticket）、`blacklist`（alipay-pay-sign）、`collect-pay`（ticket/account）、`fep-app`（collectPay/dailyTicket/account/para/industryData）、`fep-alipay`（ticket/account/paySign/alipay-pay-sign/blacklist/industryData）、`fep-dev`（gateTxnPay/ticket/account/alipay-pay-sign/security/para）、`fep-acc`（account）、`gate-txn-pay`（paySign）、`industry-data`（security）、`pay-sign`（account/gateTxnPay/blacklist）、`ticket-server`（7 条）
- 指 `172.20.211.200`（网关节点）：`account`（ticket/security）、`fep-app`（ticket/security/paySign/key/blacklist）、`fep-dev`（key）、`key-server`（security）
- **建议一次性全部改成 Service DNS**（`http://<svc>.itp.svc:<port>`），迁移后就不再依赖任何节点 IP。已有正确样例：`gate-txn-pay-server-jomf4-svc.itp.svc:30019`。

**② Service DNS（形态对，但名字里的随机后缀会变）**——出现在 `fep-alipay`/`fep-app`（transQuery）、`gate-txn-pay`（para/account/recon）、`face-pay`（ticket/account）、`trans-query`（paySign/para/gateTxnPay）、`web-admin`（account/paySign/blacklist/alipay-pay-sign/para/recon/cardPool）、`recon`（三个源）、`collect-pay`/`daily-ticket`（recon）、`account`/`alipay-account`/`daily-ticket`（cardPool）、全部服务的 `VLOGS_URL`。
**新集群建 Service 时 MUST 用无随机后缀的稳定名**（如 `account-svc`），否则每次重建都要改一圈 env。

**③ 指向自身 Pod 的死配置（现在就是坏的，迁移顺手修）**：

- `alipay-pay-sign`：`service.security.url=http://127.0.0.1:9012`、`service.key.url=http://127.0.0.1:9103`
- `fep-alipay`：`service.security.url=http://127.0.0.1:9012`、`service.key.url=http://127.0.0.1:9103`、`service.para.url=http://127.0.0.1:9107`
- `collect-pay`：`pay.center.callback-url=http://localhost:9098/ci/app/ticketCollectPayNotify`

**④ 占位 / 本地路径 / 测试地址残留**：

- `collect-pay`：`pay.center.gateway-url=https://pay-gateway.example.com/api`（占位域名）、`server.tomcat.basedir=/Users/zhoucong/logs`（开发机路径）
- `es-server`：`ftp.excel-dir` / `ftp.custom-local-dir` / `ftp.report-local-dir` 都是 `d://`（Windows 路径）
- `testngbackV2` 测试回调地址：`pay-sign`（sign-result / termination-result）、`collect-pay`（3 条 notice-app-*）、`daily-ticket`（2 条）、`face-pay`（4 条 NOTIFY_APP_*）、`ticket-server`（industry-data / counting-ticket-times）、`account`（employee-card.app-register-url）。**迁生产前 MUST 全换成甲方 APP 的正式地址**。
- `knife4j.production=false`（15 个服务，接口文档对外可见）、`web-admin` 的 `spring.datasource.druid.statViewServlet.enabled=true` + `spring.devtools.restart.enabled=true` + `logging.level.com.chinasofti.huateng=debug`、`account` 的 `account.card-pool.debug-manual-allocate=true` + `debug-manual-card-id`（**开着会绕过真实卡池分配**）。这些都是测试期取向，**生产集群 MUST 逐条复核**。

**⑤ 密钥治理**：把 §1.4 列的键从 env 明文改为 **K8s Secret + `envFrom`/`secretKeyRef`**，仓库侧对应配置保持 `${ENV:}` 空默认（AGENTS.md §5.2）。异网迁移是唯一「不改代码就能换掉全部凭据」的窗口——**新环境 MUST 换新口令，NEVER 照搬旧值**。

### 2.3 入向路由与对外契约

- 重建 `itp-gateway`（istio ingressgateway）+ `accountserver-gw` + `fep-app-vr`（8 条前缀 + rewrite 照抄，destination host 换成新 Service 名）
- 新公网入口地址确定后：改 8 处回调 env（§1.6 末条）+ **通知支付中心（bestonepay）、支付宝、APP 侧、闸机 / TVM / BOM 设备侧改地址**。设备侧改配置通常要停机窗口，**这是整个迁移里最长的前置项，MUST 最早启动**。

## 三、必须先定的 5 个问题（不定就没法排期）

- **R1 镜像来源**：新集群的 Harbor 地址？镜像是「从旧 Harbor 导出 32 个 tar 再导入」还是「在新环境重新 build 全部模块」？后者要先确认新环境有 mise/JDK21/Maven 与 SVN 可达（`svn://130.251.101.176/qditp`），且会触发 AGENTS.md §7 那套「先 install model/rpc」的序列。
- **R2 谁来建对象**：继续用 PaaS 控制台点，还是这次落 YAML/IaC？**建议落 YAML** —— 控制台生成的随机后缀 Service 名是本次最大改动面的根因，而且当前没有任何可复现的部署描述文件。
- **R3 数据库**：新集群连哪个 Oracle？是「连同一个库（只改网络）」还是「新库 + 数据迁移」？后者要单独排 schema + 数据核对（110+ 张表），本文不覆盖。
- **R4 日志**：victoria-logs 的 15Gi 历史日志带不带走？不带走则新集群起空实例即可。
- **R5 切换方式**：双跑一段（新旧集群同时连同一个库，靠公网入口灰度）还是停机一次性切？**双跑有硬约束**：`recon-server` / `web-admin` / `face-pay-server` / `collect-pay` 的定时任务无分布式锁，两套同时跑会重复扣费、重复推通知、重复投对账文件 ⇒ **双跑期间新集群这 4 个服务 MUST 保持 0 副本或关掉 Quartz**。

## 四、建议顺序

1. **前置（最长）**：申请异网到 Oracle / HSM / ACC / 3 个 FTP / 支付中心 / 支付宝的网络策略；确定新公网入口并向支付中心、支付宝、APP、设备侧提改地址需求
2. 新集群底座：istio 1.24.6 + 两个 ns + StorageClass + 镜像仓库 + 节点时区
3. **YAML 基线已就绪**：`deploy/k8s/` 即 2026-09-22 的导出结果（已去 `status` / PaaS 注解 / 集群分配值，敏感 env 已换成 `secretKeyRef`）。**重新导出就重跑那套清洗流程并覆盖该目录**，NEVER 把带真实值的 `raw-*.yaml` 拷进仓库
4. 配置治理：密钥进 Secret、地址统一成 Service DNS、修 §2.2③④ 那批死配置、补 liveness/readiness 探针
5. 按依赖顺序部署（公共能力 `para` / `key` / `blacklist` / `security` / `industry-data` / `card-pool` → 业务层 → 接入层 → 网关 → 后台），每个服务用 `/actuator/health` 探活（**注意 `rollout status` 成功 ≠ 起来了**，见 AGENTS.md §7）
6. 建 Gateway + VS，用「同一份报文打新旧两套、比对 retCode」做契约基线核对
7. 切公网入口；**旧集群业务 Deployment 缩到 0（NEVER 直接删）**，保留至少一个对账周期（T+2）作回滚位
8. 回滚位：旧集群 YAML + 原 image tag + 公网入口指回旧地址，三者缺一不可

## 五、NEVER 清单

- **NEVER 在新集群把那 4 个单副本服务扩到 2 副本**（`recon-server` / `web-admin` / `face-pay-server` / `collect-pay`）
- **NEVER 双跑期间让两套集群的定时任务同时对同一个库生效**
- **NEVER 把 Service 端口「统一规整」**——一堆 `service.*.url` 写死了现有端口号，改端口等于同时改几十条 env
- **NEVER 照搬旧密钥值到新环境**；也 NEVER 在文档 / 提交信息 / 对话里回显这些值
- **NEVER 漏掉 `/etc/localtime`**：新节点非 CST 会让对账账期与乘车码时效全线错 8 小时
- **NEVER 只迁 `/itptvm/` `/itpbom/` 的 destination 而把 rewrite 写成 `/`**——这两条的 rewrite 带前缀
- **NEVER 迁那 7 个 istio 示例负载**（bookinfo + httpbin）与 40+ 个 `websyspresetweb-version-*` 历史 ConfigMap
- **NEVER 认为「改完 env 就生效」**：涉及公共构件（`model` / `rpc` / `resource/micro`）的改动必须重建镜像（AGENTS.md §7 末三条）
