# ITP 测试→生产环境迁移配置清单

> 生成时间：2026-10-14 18:41:01
> 数据来源：k8s-master 当前运行配置 + 仓库 `deploy/k8s/` 基线

> ⚠️ 本报告**不记录任何密钥明文**，只列出需要迁移、修改或确认的配置项。

## 一、执行摘要

- 当前 ns `itp` 运行 Deployment 共 **32** 个
- 其中 ITP 业务相关 **25** 个，非业务示例/测试负载 **7** 个
- Service 共 **31** 个
- 当前集群**没有业务 Secret**，全部配置以明文 env 内联在 Deployment 中
- 生产迁移时必须完成：镜像仓库地址替换、Service 名去随机后缀、密钥进 Secret、外部依赖地址变更、探针补齐

## 二、必须迁移的业务负载

| # | Deployment | 当前镜像 | 仓库基线镜像 | 版本差异 |
|---|-----------|---------|------------|---------|
| 1 | account | `os-harbor-svc.default.svc.cloudos:443/itp/account-server:2.0.80` | `os-harbor-svc.default.svc.cloudos:443/itp/account-server:2.0.80` | 一致 |
| 2 | alipay-account-server | `os-harbor-svc.default.svc.cloudos:443/itp/alipay-account:1.0.24` | `os-harbor-svc.default.svc.cloudos:443/itp/alipay-account:1.0.24` | 一致 |
| 3 | alipay-pay-sign-server | `os-harbor-svc.default.svc.cloudos:443/itp/alipay-pay-sign:1.1.59` | `os-harbor-svc.default.svc.cloudos:443/itp/alipay-pay-sign:1.1.59` | 一致 |
| 4 | blacklist-server | `os-harbor-svc.default.svc.cloudos:443/itp/blacklist-server:2.0.34` | `os-harbor-svc.default.svc.cloudos:443/itp/blacklist-server:2.0.34` | 一致 |
| 5 | card-pool-server | `os-harbor-svc.default.svc.cloudos:443/itp/card-pool-server:1.0.20` | `os-harbor-svc.default.svc.cloudos:443/itp/card-pool-server:1.0.19` | 当前: 1.0.20, 基线: 1.0.19 |
| 6 | collect-pay | `os-harbor-svc.default.svc.cloudos:443/itp/collect-pay-server:1.1.89` | `os-harbor-svc.default.svc.cloudos:443/itp/collect-pay-server:1.1.88` | 当前: 1.1.89, 基线: 1.1.88 |
| 7 | daily-ticket-server | `os-harbor-svc.default.svc.cloudos:443/itp/daily-ticket-server:1.0.66` | `os-harbor-svc.default.svc.cloudos:443/itp/daily-ticket-server:1.0.63` | 当前: 1.0.66, 基线: 1.0.63 |
| 8 | database | `os-harbor-svc.default.svc.cloudos:443/itp/database-server:0.6.7` | `os-harbor-svc.default.svc.cloudos:443/itp/database-server:0.6.7` | 一致 |
| 9 | es-server | `os-harbor-svc.default.svc.cloudos:443/itp/acc-es-server:2.0.22` | `os-harbor-svc.default.svc.cloudos:443/itp/acc-es-server:2.0.21` | 当前: 2.0.22, 基线: 2.0.21 |
| 10 | face-pay-server | `os-harbor-svc.default.svc.cloudos:443/itp/face-pay-server:1.0.79` | `os-harbor-svc.default.svc.cloudos:443/itp/face-pay-server:1.0.77` | 当前: 1.0.79, 基线: 1.0.77 |
| 11 | fep-acc | `os-harbor-svc.default.svc.cloudos:443/itp/fep-acc:1.0.10` | `os-harbor-svc.default.svc.cloudos:443/itp/fep-acc:1.0.10` | 一致 |
| 12 | fep-alipay | `os-harbor-svc.default.svc.cloudos:443/itp/fep-alipay:1.0.71` | `os-harbor-svc.default.svc.cloudos:443/itp/fep-alipay:1.0.71` | 一致 |
| 13 | fep-app | `os-harbor-svc.default.svc.cloudos:443/itp/fep-app:2.0.105` | `os-harbor-svc.default.svc.cloudos:443/itp/fep-app:2.0.105` | 一致 |
| 14 | fep-dev-server | `os-harbor-svc.default.svc.cloudos:443/itp/fep-dev-server:2.0.69` | `os-harbor-svc.default.svc.cloudos:443/itp/fep-dev-server:2.0.69` | 一致 |
| 15 | gate-txn-pay-server | `os-harbor-svc.default.svc.cloudos:443/itp/gate-txn-pay-server:2.0.103` | `os-harbor-svc.default.svc.cloudos:443/itp/gate-txn-pay-server:2.0.99` | 当前: 2.0.103, 基线: 2.0.99 |
| 16 | industry-data-server | `os-harbor-svc.default.svc.cloudos:443/itp/industry-data-server:2.0.22` | `os-harbor-svc.default.svc.cloudos:443/itp/industry-data-server:2.0.21` | 当前: 2.0.22, 基线: 2.0.21 |
| 17 | key-server | `os-harbor-svc.default.svc.cloudos:443/itp/key-server:2.0.15` | `os-harbor-svc.default.svc.cloudos:443/itp/key-server:2.0.15` | 一致 |
| 18 | para-server | `os-harbor-svc.default.svc.cloudos:443/itp/para-server:2.0.28` | `os-harbor-svc.default.svc.cloudos:443/itp/para-server:2.0.28` | 一致 |
| 19 | pay-sign-server | `os-harbor-svc.default.svc.cloudos:443/itp/pay-sign-server:2.1.12` | `os-harbor-svc.default.svc.cloudos:443/itp/pay-sign-server:2.1.10` | 当前: 2.1.12, 基线: 2.1.10 |
| 20 | recon-server | `os-harbor-svc.default.svc.cloudos:443/itp/recon-server:1.0.25` | `os-harbor-svc.default.svc.cloudos:443/itp/recon-server:1.0.17` | 当前: 1.0.25, 基线: 1.0.17 |
| 21 | security-server | `os-harbor-svc.default.svc.cloudos:443/itp/acc-security-server:2.0.16` | `os-harbor-svc.default.svc.cloudos:443/itp/acc-security-server:2.0.15` | 当前: 2.0.16, 基线: 2.0.15 |
| 22 | ticket-server | `os-harbor-svc.default.svc.cloudos:443/itp/ticket-server:2.1.111` | `os-harbor-svc.default.svc.cloudos:443/itp/ticket-server:2.1.111` | 一致 |
| 23 | trans-query | `os-harbor-svc.default.svc.cloudos:443/itp/trans-query:1.0.18` | `os-harbor-svc.default.svc.cloudos:443/itp/trans-query:1.0.18` | 一致 |
| 24 | web | `os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/web-nginx1.20:1.2.29-1790045565782` | `os-harbor-svc.default.svc.cloudos:443/user/1df71957d770436493a84f24d9344853/web-nginx1.20:1.2.29-1790045565782` | 一致 |
| 25 | web-admin | `os-harbor-svc.default.svc.cloudos:443/itp/web-admin:1.1.42` | `os-harbor-svc.default.svc.cloudos:443/itp/web-admin:1.1.41` | 当前: 1.1.42, 基线: 1.1.41 |

## 三、生产环境应清理的非业务负载

以下负载是 Istio Bookinfo 示例或 httpbin 测试镜像，与 ITP 业务无关，生产迁移时不应部署：

- `details-v1`：`os-harbor-svc.default.svc.cloudos:443/itp/details-v1:1.20.2`
- `kennethreitz-httpbin`：`os-harbor-svc.default.svc.cloudos:443/itp/kennethreitz-httpbin:latest`
- `productpage-v1`：`os-harbor-svc.default.svc.cloudos:443/itp/productpage-v1:1.20.2`
- `ratings-v1`：`os-harbor-svc.default.svc.cloudos:443/itp/ratings-v1:1.20.2`
- `reviews-v1`：`os-harbor-svc.default.svc.cloudos:443/itp/reviews-v1:1.20.2`
- `reviews-v2`：`os-harbor-svc.default.svc.cloudos:443/itp/reviews-v2:1.20.2`
- `reviews-v3`：`os-harbor-svc.default.svc.cloudos:443/itp/reviews-v3:1.20.2`

## 四、Service NodePort 映射（生产需确认）

当前 31 个 Service 全部为 NodePort（除 7 个非业务 ClusterIP）。生产迁移时：
- **必须保留端口号**：大量 `service.*.url` 硬编码了这些 NodePort
- **必须去掉随机后缀**：当前 Service 名带 `-n4ba6-`、`-hsa9w-` 等随机后缀，迁移后应使用稳定名如 `account-svc`
- **注意 Service port ≠ targetPort ≠ NodePort**：例如 `ticket-server-bsyju-svc` Service port 是 9100，NodePort 是 30014，targetPort 才是容器真实端口 9103

| Service | 类型 | Cluster-IP | Service Port | NodePort | TargetPort |
|---------|------|-----------|-------------|---------|-----------|
| account-n4ba6-svc | NodePort | 10.100.234.130 | 9098 | 30013 | 9098 |
| alipay-account-server-2n6kc-svc | NodePort | 10.100.167.117 | 30021 | 30021 | 8080 |
| alipay-pay-sign-server-t3o7n-svc | NodePort | 10.100.205.84 | 30022 | 30022 | 8080 |
| blacklist-server-wmfzs-svc | NodePort | 10.100.149.153 | 9102 | 30017 | 9102 |
| card-pool-server-86mc1-svc | NodePort | 10.100.120.64 | 30033 | 30033 | 9111 |
| collect-pay-c23ku-svc | NodePort | 10.100.164.98 | 30024 | 30024 | 8080 |
| daily-ticket-server-rdbe5-svc | NodePort | 10.100.30.50 | 30027 | 30027 | 9108 |
| database-gcwp8-svc | NodePort | 10.100.151.215 | 30031 | 30031 | 8686 |
| details | ClusterIP | 10.100.119.31 | 9080 | <none> | 9080 |
| es-server-38bzh-svc | NodePort | 10.100.211.122 | 8080 | 30011 | 8080 |
| face-pay-server-svc | NodePort | 10.100.223.30 | 30025 | 30025 | 58101 |
| fep-acc-wracu-svc | NodePort | 10.100.12.212 | 30030 | 30030 | 9110 |
| fep-alipay-rec8g-svc | NodePort | 10.100.147.8 | 30020 | 30020 | 8080 |
| fep-app-hr32k-svc | NodePort | 10.100.146.184 | 9101 | 30010 | 9101 |
| fep-dev-server-748lq-svc | NodePort | 10.100.137.136 | 30009 | 30009 | 9104 |
| gate-txn-pay-server-jomf4-svc | NodePort | 10.100.176.1 | 30019 | 30019 | 9106 |
| industry-data-server-6dv5u-svc | NodePort | 10.100.207.52 | 30018 | 30018 | 8080 |
| key-server-vcbmt-svc | NodePort | 10.100.108.140 | 8080 | 30015 | 9103 |
| para-server-pufrl-svc | NodePort | 10.100.96.245 | 30026 | 30026 | 9107 |
| pay-sign-server-hsa9w-svc | NodePort | 10.100.157.86 | 8080 | 30016 | 8080 |
| productpage | ClusterIP | 10.100.217.160 | 9080 | <none> | 9080 |
| ratings | ClusterIP | 10.100.249.118 | 9080 | <none> | 9080 |
| recon-server-bjzdy-svc | NodePort | 10.100.251.78 | 30034 | 30034 | 9112 |
| reviews | ClusterIP | 10.100.87.231 | 9080 | <none> | 9080 |
| security-server-8h3eo-svc | NodePort | 10.100.243.155 | 8080 | 30012 | 8080 |
| ticket-server-bsyju-svc | NodePort | 10.100.214.195 | 9100 | 30014 | 9100 |
| trans-query-57wpd-svc | NodePort | 10.100.190.31 | 30035 | 30035 | 9113 |
| victoria-logs-pbi6a-svc | NodePort | 10.100.183.227 | 30032 | 30032 | 9428 |
| web-3zilk-svc | NodePort | 10.100.45.132 | 30029 | 30029 | 80 |
| web-admin-4e2zf-svc | NodePort | 10.100.98.241 | 30028 | 30028 | 8080 |

## 五、生产迁移必须修改/确认的配置项

### 5.1 镜像仓库地址

当前所有镜像引用：

```
os-harbor-svc.default.svc.cloudos:443
```
这是**当前集群内 Service DNS**，新集群不存在。生产迁移时必须全部替换为新 Harbor 地址。

### 5.2 外部依赖地址（生产必须重新开通并换地址）

| 依赖 | 当前测试环境地址/键 | 生产需确认 | 使用方 |
|------|------------------|-----------|--------|
| Oracle 业务库 | `172.20.222.3:1521/AFCITPDB` | 生产 Oracle 地址、实例、用户 | 除无 DB 模块外全部 |
| 加密机 HSM | `172.20.201.4:6666/7002` | 生产 HSM 地址与端口 | acc-security-server |
| ACC 清分中心 | `172.20.211.11:32605` | 生产 ACC 地址 | account-server、card-pool-server |
| FTP（卡池） | `172.20.215.3` | 生产 FTP 地址 | card-pool-server |
| FTP（参数文件） | `172.20.214.101:21` | 生产 FTP 地址 | para-server |
| FTP（对账文件） | 见 `RECON_FTP_*` | 生产 FTP 地址 | recon-server |
| 支付中心 | `dtcustomer.bestonepay.com` | 生产商户号、回调地址 | pay-sign、daily-ticket、collect-pay、alipay-pay-sign |
| 支付宝 | `openapi.alipay.com` | 生产 appId、商户配置 | fep-alipay、alipay-pay-sign |
| 公网回调入口 | `58.56.166.170:48000` | 新公网入口，需同步支付中心/支付宝/APP/设备侧 | daily-ticket、collect-pay、face-pay、pay-sign |

### 5.3 密钥治理（必须进 K8s Secret）

当前所有敏感信息以**明文 env** 内联在 Deployment 中。生产迁移时必须改为 `secretKeyRef`，至少包括：

| 敏感项 | 键名示例 | 使用方 |
|--------|---------|--------|
| DB 口令 | `other.sql.password` / `DB_PASSWORD` / `spring.datasource.password` | 16+ 个服务 |
| 报文签名密钥 | `itp.signKey` / `jhm.key` / `app.signKey` | account、collect-pay、face-pay 等 |
| 商户私钥 | `pay.center.merchant-private-key` / `pay.center.paycenter-public-key` | alipay-pay-sign、collect-pay |
| HSM/3DES 密钥 | `security.publicKey/privateKey` / `acc.3des.key` / `appserver.3des.key` | security-server、key-server |
| FTP 口令 | `CARD_POOL_FTP_PASSWORD` / `RECON_FTP_PASSWORD` / `ftp.password` / `para.ftp.password` | card-pool、recon、es-server、para |
| 后台 Token | `token.secret` | web-admin |

### 5.4 单副本约束（生产严禁扩容）

以下服务因进程内锁 / 内存 JobStore / 无分布式锁，**必须保持单副本**：

- `recon-server`：进程内 `AtomicBoolean` 锁，多副本会重复生成/投递对账文件
- `web-admin`：Quartz 内存 JobStore，多副本每个副本各跑一遍任务
- `face-pay-server`：7 个 `@Scheduled` 任务，无分布式锁
- `collect-pay`：`SingleTicketRefundTask` 4 处无分布式锁

### 5.5 探针缺失（建议迁移时补齐）

当前 32 个 Deployment **全部没有 livenessProbe / readinessProbe**。生产迁移时建议为每个服务补 `/actuator/health` 探针。

⚠️ 注意：`web-admin` 的 `/actuator/health` 返回 200 但 body 为 401（被鉴权拦截），属正常，配置探针时需注意。

### 5.6 `/etc/localtime` hostPath

每个业务 Pod 都挂载了宿主 `/etc/localtime`。生产节点时区 **MUST 为 CST**，否则时间错 8 小时，影响对账账期和乘车码时效。

### 5.7 存储

- `victoria-logs`：StatefulSet，15Gi RWO PVC（StorageClass `apigateway2`），生产需确认是否保留历史日志
- `recon-server`：当前无 PVC，对账文件落在容器本地。生产迁移时应挂载 RWX PVC 到 `RECON_STORAGE_ROOT`，否则 Pod 重启丢分片

## 六、Web 前端配置

当前 web Deployment 挂载的 ConfigMap：`websyspresetweb-version-1.29`

生产迁移时只需要保留当前使用的这一个 ConfigMap，其余 40+ 个 `websyspresetweb-version-*` 历史版本可直接丢弃。

## 七、需要废弃的 ConfigMap

- `key-server-config-version-1.0`、`key-server-config1-version-1.0`、`-1.1`：当前无 Pod 挂载，疑似孤儿
- `websyspresetweb-version-*`（40+ 个历史版本）：只有 `1.29` 被 web 挂载，其余是 PaaS 发布残留
- `istio-ca-root-cert`、`kube-root-ca.crt`：平台自动生成，无需迁移

## 八、生产迁移检查清单

[ ] 新集群底座：K8s + Istio（如继续用）或替代入向网关 + StorageClass + Harbor 就绪
[ ] 新节点时区已置为 CST（`timedatectl` 确认）
[ ] 新 Harbor 地址确认，旧镜像已迁移或可重新构建
[ ] Oracle / HSM / ACC / 三个 FTP / 支付中心 / 支付宝 网络策略已开通
[ ] 新公网入口地址确定，并已通知支付中心、支付宝、APP、闸机/TVM/BOM 设备侧修改回调/对接地址
[ ] `deploy/k8s/` 中所有镜像地址已替换为新 Harbor
[ ] Service 名已改为无随机后缀的稳定名，所有 `service.*.url` 已同步更新
[ ] 全部敏感配置已从明文 env 改为 K8s Secret + `secretKeyRef`
[ ] `recon-server` 已挂载 RWX PVC，`victoria-logs` 已按需要保留或清空历史日志
[ ] 为每个业务 Deployment 补齐 livenessProbe / readinessProbe
[ ] `recon-server`、`web-admin`、`face-pay-server`、`collect-pay` 保持单副本
[ ] 非业务负载（bookinfo + httpbin）未部署到新集群
[ ] 按依赖顺序部署并逐服务用 `/actuator/health` 探活
[ ] 公网入口切流后，旧集群业务 Deployment 缩到 0（保留回滚位）

## 九、各业务 Deployment 当前环境变量键名汇总

以下键名在生产迁移时需要逐一确认并正确注入。完整清单见 `k8s-current-config/env-keys.txt`。

### account

- `service.ticket.url`
- `server.port`
- `other.sql.username`
- `employee-card.acc-query-url`
- `VLOGS_URL`
- `other.sql.host`
- `service.security.url`
- `service.paySign.url`
- `service.security.hce-card-data.url`
- `itp.signKey`
- `service.gateTxnPay.url`
- `employee-card.app-register-url`
- `service.cardPool.url`
- `other.web.unSafeUrlChars`
- `employee-card.sign-type`
- `other.sql.password`
- `service.paySign.openLogger`
- `DB_HOST`
- `DB_USERNAME`
- `DB_PASSWORD`

### alipay-account-server

- `service.ticket.url`
- `server.port`
- `other.sql.username`
- `other.sql.host`
- `itp.signKey`
- `service.cardPool.url`
- `other.web.unSafeUrlChars`
- `other.sql.password`
- `VLOGS_URL`

### alipay-pay-sign-server

- `VLOGS_URL`
- `service.key.openLogger`
- `pay.center.merchant-private-key`
- `pay.center.gateway-url`
- `alipay.external.blacklist-url`
- `pay.center.close-result-notify-url`
- `pay.center.sign-type`
- `service.ticket.url`
- `pay.center.blacklist-notify-url`
- `server.port`
- `other.sql.username`
- `service.account.url`
- `other.sql.host`
- `service.security.url`
- `pay.center.gatewayUrl`
- `pay.center.request-refund-url`
- `service.key.url`
- `pay.center.callback-url`
- `service.para.url`
- `service.blacklist.url`
- ... 等共 28 个相关键

### blacklist-server

- `VLOGS_URL`
- `app.notify.blacklist-url`
- `other.web.unSafeUrlChars`
- `other.sql.password`
- `server.port`
- `other.sql.username`
- `service.alipay-pay-sign.url`
- `other.sql.host`
- `DB_HOST`
- `DB_USERNAME`
- `DB_PASSWORD`

### card-pool-server

- `CARD_POOL_PORT`
- `CARD_POOL_FTP_CONNECT_TIMEOUT_MILLIS`
- `CARD_POOL_FTP_DATA_TIMEOUT_SECONDS`
- `DB_HOST`
- `ACC_BASE_URL`
- `CARD_POOL_IMPORT_CHUNK_SIZE`
- `CARD_POOL_FTP_PASSWORD`
- `CARD_POOL_FTP_SO_TIMEOUT_MILLIS`
- `DB_USERNAME`
- `CARD_POOL_FTP_MAX_FILE_SIZE`
- `CARD_POOL_FTP_PORT`
- `ACC_SECURE_URL`
- `CARD_POOL_FTP_USERNAME`
- `CARD_POOL_FTP_BASE_DIR`
- `VLOGS_URL`
- `DB_PASSWORD`
- `CARD_POOL_FTP_HOST`

### collect-pay

- `VLOGS_URL`
- `pay.center.app-refund-notice-url`
- `jhm.key`
- `pay.center.notice-app-taketicketresult-url`
- `pay.center.gateway-url`
- `pay.center.pay-center-query-url`
- `pay.center.notice-app-refundresult-url`
- `pay.center.sign-type`
- `service.ticket.url`
- `server.port`
- `other.sql.username`
- `service.account.url`
- `other.sql.host`
- `app.signType`
- `pay.center.pay-center-refund-url`
- `pay.center.pay-center-pay-url`
- `pay.center.callback-url`
- `pay.center.notice-app-taketicketfailureresult-url`
- `app.signKey`
- `other.web.unSafeUrlChars`
- ... 等共 24 个相关键

### daily-ticket-server

- `daily-ticket.pay.sign-type`
- `server.port`
- `other.sql.username`
- `VLOGS_URL`
- `other.sql.host`
- `daily-ticket.pay.notify-url`
- `daily-ticket.pay.gateway-url`
- `daily-ticket.pay.return-url`
- `service.cardPool.url`
- `other.web.unSafeUrlChars`
- `other.sql.password`
- `RECON_INTERNAL_TOKEN`
- `SERVICE_RECON_URL`
- `DAILY_TICKET_NOTIFY_APP_REFUND_RESULT_URL`
- `DAILY_TICKET_NOTIFY_APP_PAY_RESULT_URL`
- `daily-ticket.pay.refund-notify-url`

### database

- `SERVER_PORT`
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `ENTROPY_MCP_DATABASE_BYOK_URL_GUARD_BLOCK_PRIVATE_NETWORKS`
- `ENTROPY_MCP_DATABASE_BYOK_URL_GUARD_ALLOWED_DRIVERS`
- `MCP_SECURITY_ADMIN_PASSWORD`
- `ENTROPY_MCP_DATABASE_CONNECTIONS_QDITP_JDBC_URL`
- `ENTROPY_MCP_DATABASE_CONNECTIONS_QDITP_USERNAME`
- `ENTROPY_MCP_DATABASE_CONNECTIONS_QDITP_PASSWORD`

### es-server

- `ftp.excel-dir`
- `ftp.password`
- `ftp.ip`
- `server.port`
- `other.sql.username`
- `other.sql.host`
- `ftp.username`
- `ftp.report-target-dir`
- `ftp.custom-target-dir`
- `ftp.port`
- `netty.port`
- `other.web.unSafeUrlChars`
- `other.sql.password`
- `ftp.custom-local-dir`
- `ftp.report-local-dir`
- `VLOGS_URL`

### face-pay-server

- `PAY_CENTER_SUPPLEMENT_NOTICE_URL`
- `DB_HOST`
- `DB_USERNAME`
- `DB_PASSWORD`
- `JHM_KEY`
- `PAY_CENTER_PAY_URL`
- `PAY_CENTER_QUERY_URL`
- `PAY_CENTER_REFUND_URL`
- `PAY_CENTER_REFUND_QUERY_URL`
- `PAY_CENTER_CHECKOUT_COUNTER_URL`
- `PAY_CENTER_PAY_NOTICE_URL`
- `NOTIFY_APP_TAKE_TICKET_RESULT_URL`
- `NOTIFY_APP_TAKE_TICKET_FAULT_RESULT_URL`
- `NOTIFY_APP_REFUND_RESULT_URL`
- `SERVICE_TICKET_URL`
- `SERVICE_ACCOUNT_URL`
- `NOTIFY_APP_REFUND_NOTICE_URL`
- `NOTIFY_APP_PAY_RESULT_URL`
- `PAY_CENTER_REFUND_NOTICE_URL`
- `VLOGS_URL`

### fep-acc

- `other.web.unSafeUrlChars`
- `server.port`
- `service.account.url`
- `VLOGS_URL`

### fep-alipay

- `alipay.notify.push-trans-data-url`
- `service.key.openLogger`
- `alipay.notify.close-result-url`
- `service.ticket.url`
- `server.port`
- `alipay.notify.blacklist-url`
- `service.account.url`
- `service.security.url`
- `service.alipay-pay-sign.openLogger`
- `service.paySign.url`
- `service.key.url`
- `service.para.url`
- `service.blacklist.url`
- `service.industryData.url`
- `other.web.unSafeUrlChars`
- `alipay.notify.card-data-url`
- `service.paySign.openLogger`
- `service.alipay-pay-sign.url`
- `VLOGS_URL`
- `service.transQuery.url`

### fep-app

- `service.collectPay.url`
- `service.key.openLogger`
- `service.ticket.url`
- `service.dailyTicket.url`
- `server.port`
- `service.account.url`
- `service.security.url`
- `service.paySign.url`
- `industry.signature-type`
- `service.gateTxnPay.url`
- `service.key.url`
- `service.para.url`
- `service.blacklist.url`
- `service.industryData.url`
- `other.web.unSafeUrlChars`
- `service.paySign.openLogger`
- `VLOGS_URL`
- `SERVICE_TRANS_QUERY_URL`
- `SERVICE_COLLECT_PAY_URL`

### fep-dev-server

- `service.gateTxnPay.url`
- `alipay.notify.pay-callback-url`
- `service.key.url`
- `pay.center.callback-url`
- `service.para.url`
- `other.web.unSafeUrlChars`
- `service.ticket.url`
- `server.port`
- `service.account.url`
- `service.alipay-pay-sign.url`
- `service.security.url`
- `VLOGS_URL`

### gate-txn-pay-server

- `pay.sign.request-refund-url`
- `pay.sign.contract-result-url`
- `pay.sign.request-pay-url`
- `pay.sign.contract-advisory-url`
- `server.port`
- `other.sql.username`
- `pay.sign.contract-config-url`
- `VLOGS_URL`
- `other.sql.host`
- `service.paySign.url`
- `pay.sign.termination-url`
- `pay.sign.contract-url`
- `service.para.url`
- `other.web.unSafeUrlChars`
- `other.sql.password`
- `service.paySign.openLogger`
- `service.account.url`
- `RECON_INTERNAL_TOKEN`
- `SERVICE_RECON_URL`

### industry-data-server

- `other.web.unSafeUrlChars`
- `server.port`
- `service.security.url`
- `VLOGS_URL`

### key-server

- `appserver.3des.key`
- `key.sync.kek-idx`
- `other.web.unSafeUrlChars`
- `other.sql.password`
- `server.port`
- `other.sql.username`
- `VLOGS_URL`
- `other.sql.host`
- `service.security.url`
- `acc.3des.key`
- `key.sync.days`
- `DB_HOST`
- `DB_USERNAME`
- `DB_PASSWORD`

### para-server

- `para.ftp.passive-mode`
- `para.ftp.remote-dir`
- `server.port`
- `para.ftp.connect-timeout-millis`
- `other.sql.username`
- `para.ftp.port`
- `VLOGS_URL`
- `other.sql.host`
- `para.ftp.data-timeout-millis`
- `para.ftp.username`
- `para.ftp.ip`
- `other.web.unSafeUrlChars`
- `other.sql.password`
- `para.ftp.password`
- `para.ftp.local-dir`
- `DB_HOST`
- `DB_USERNAME`
- `DB_PASSWORD`

### pay-sign-server

- `pay.sign.request-pay-notify-url`
- `pay.sign.request-pay-path`
- `other.sql.username`
- `service.account.url`
- `pay.sign.sign-type`
- `pay.sign.request-refund-path`
- `service.gateTxnPay.url`
- `service.blacklist.url`
- `other.web.unSafeUrlChars`
- `pay.sign.merchant-no`
- `pay.sign.wechat.app-id`
- `other.sql.password`
- `itp.signType`
- `pay.sign.alipay.app-id`
- `pay.sign.wechat.entrust-url`
- `app.notify.sign-result-url`
- `pay.sign.charset`
- `app.notify.termination-result-url`
- `pay.sign.test-force-amount`
- `pay.sign.api-version`
- ... 等共 37 个相关键

### recon-server

- `SERVER_PORT`
- `RECON_FTP_REMOTE_ROOT`
- `RECON_FTP_HOST`
- `RECON_FTP_ENABLED`
- `RECON_SRC_DAILY_TICKET_URL`
- `DB_HOST`
- `DB_USERNAME`
- `RECON_SRC_GATE_TXN_PAY_URL`
- `RECON_SRC_COLLECT_PAY_URL`
- `RECON_FTP_PASSWORD`
- `RECON_FTP_PORT`
- `VLOGS_URL`
- `RECON_INTERNAL_TOKEN`
- `RECON_FTP_USERNAME`
- `DB_PASSWORD`

### security-server

- `itp.tacKeyIdex`
- `security.publicKey`
- `security.secondPort`
- `security.firstPort`
- `server.port`
- `itp.signKey`
- `other.web.unSafeUrlChars`
- `security.privateKey`
- `itp.mac1KeyIdex`
- `itp.mac2KeyIdex`
- `VLOGS_URL`

### ticket-server

- `app.notify.counting-ticket-times-url`
- `app.notify.counting-ticket-times-enabled`
- `app.notify.alipay-industry-data-url`
- `service.alipayAccount.url`
- `app.notify.industry-data-enabled`
- `service.fepDev.url`
- `service.dailyTicket.url`
- `server.port`
- `other.sql.username`
- `VLOGS_URL`
- `other.sql.host`
- `service.security.url`
- `service.paySign.url`
- `app.notify.alipay-push-trans-data-url`
- `service.gateTxnPay.url`
- `service.para.url`
- `service.industryData.url`
- `other.web.unSafeUrlChars`
- `other.sql.password`
- `app.notify.industry-data-url`

### trans-query

- `VLOGS_URL`
- `SERVER_PORT`
- `SERVICE_PAY_SIGN_URL`
- `SERVICE_PAY_SIGN_OPEN_LOGGER`
- `SERVICE_PARA_URL`
- `SERVICE_GATE_TXN_PAY_URL`
- `OTHER_WEB_UNSAFEURLCHARS`

### web

- （无非默认配置键）

### web-admin

- `spring.datasource.druid.statViewServlet.login-password`
- `spring.datasource.druid.statViewServlet.url-pattern`
- `token.header`
- `spring.datasource.druid.master.username`
- `pagehelper.supportMethodsArguments`
- `spring.datasource.druid.master.url`
- `service.account.url`
- `service.alipay-pay-sign.openLogger`
- `service.paySign.url`
- `xss.urlPatterns`
- `spring.datasource.password`
- `spring.datasource.druid.master.password`
- `token.secret`
- `service.blacklist.url`
- `user.password.lockTime`
- `spring.datasource.url`
- `service.alipay-pay-sign.url`
- `CARD_POOL_SERVICE_URL`
- `server.port`
- `VLOGS_URL`
- ... 等共 30 个相关键
