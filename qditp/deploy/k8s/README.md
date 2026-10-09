# deploy/k8s — 集群对象基线清单（2026-09-22 从 `k8s02` 集群导出）

本目录是**现集群 ns `itp` + `itp-gateway` 的可复用 YAML 基线**，用途有两个：①异网新集群迁移的起点（配套文档 [`../../docs/ops/K8S迁移清单.md`](../../docs/ops/K8S迁移清单.md)）；②给现集群一份可 diff 的部署描述 —— 此前全部对象都是 PaaS 控制台点出来的，仓库里没有任何部署文件。

## 生成方式

`ssh k8s-master` 上 `kubectl get ... -o yaml` 全量导出后按以下规则清洗：

- 去掉 `status`、`metadata.{creationTimestamp,resourceVersion,uid,generation,managedFields}`
- 去掉 PaaS / kubectl 注入的注解：`kubectl.kubernetes.io/last-applied-configuration`、`deployment.kubernetes.io/revision`、`kubectl.kubernetes.io/restartedAt`、`updateEnvVar`、`updateImage`（**后两个是控制台留下的 env / image 历史快照，里面含明文口令**）
- 去掉集群分配值：Service 的 `clusterIP` / `clusterIPs` / `ipFamilies` 等；Deployment 的 `progressDeadlineSeconds` / `revisionHistoryLimit`；Pod 的 `dnsPolicy` / `schedulerName` / 空 `affinity` 等默认字段
- **敏感 env 一律改成 `secretKeyRef`**，值不落文件，键名收口到 `05-secret-template.yaml`（详见下节）
- 不导出 7 个 istio 示例负载（bookinfo `details`/`productpage`/`ratings`/`reviews-v1~v3` + `httpbin`）及其 Gateway / VS / DestinationRule；不导出 40+ 个 `websyspresetweb-version-*` 历史 ConfigMap（只留 `web` 当前挂载的那一个）；不导出三个无人挂载的孤儿 `key-server-config*` ConfigMap

带真实值的原始导出留在**仓库外** `~/k8s-export/qditp-20260922/`（4 个 `raw-*.yaml`）。**那批文件含明文口令与密钥，NEVER 拷进仓库、NEVER 提交**。

## 文件清单

- `00-namespaces.yaml` — ns `itp`（带 `istio-injection=enabled`）与 `itp-gateway`
- `05-secret-template.yaml` — Secret `itp-secrets` 模板，**23 个键、值全为空**，`kubectl apply` 前必须先填
- `10-deployments/` — 24 个业务 Deployment，逐个一文件
- `15-optional/database.yaml` — BYOK database MCP Server（研发辅助，**不是 Oracle**，生产可不部署）
- `16-statefulsets/victoria-logs.yaml` — 日志服务，含 `volumeClaimTemplates`（15Gi RWO / sc `apigateway2`）
- `20-services.yaml` — 26 个 Service（全 NodePort，30009~30035）
- `30-istio-ingress.yaml` — ns `itp` 的 `accountserver-gw` + `fep-app-vr`（8 条前缀路由）
- `31-itp-gateway-ns.yaml` — ns `itp-gateway` 的 istio ingressgateway Deployment + 两个 Service
- `40-configmap-web-nginx.yaml` — `web`（nginx）挂载的 `nginx.conf`

## 使用约束

- **`05-secret-template.yaml` 的值 NEVER 填进仓库**：本地填好后 `kubectl apply -f`，或改用外部密钥管理。仓库里这份永远保持空值。
- **一个共享 Secret 的取舍**：23 个键放在同一个 `itp-secrets` 里（`other.sql.password` 被 12 个服务共用、`itp.signKey` 4 个、`DB_PASSWORD` 3 个、`RECON_INTERNAL_TOKEN` 4 个）。若将来某个服务需要不同值，**MUST 拆成独立 Secret 并改对应 Deployment 的 `secretKeyRef.name`**，NEVER 在同一个 Secret 里塞同名不同值。
- **不要直接把这套 apply 到新集群**：至少三类东西必须先改 —— 镜像仓库地址（`os-harbor-svc.default.svc.cloudos:443` 是旧集群内的 Service DNS）、Service 名里的 PaaS 随机后缀、env 里写死的旧节点 IP / 旧 Service DNS。逐类改法见 `docs/ops/K8S迁移清单.md` §2.2。
- **这份基线不含探针**：现集群 24 个 Deployment 一个 liveness / readiness 都没有，导出时原样保留。新集群部署时建议补 `/actuator/health`。
- **单副本约束照抄自现集群、NEVER 改大**：`recon-server` / `web-admin` / `face-pay-server` / `collect-pay` 的定时任务无分布式锁。
- **本目录是快照、不是真相源**：现集群改过 env / 镜像后这里不会自动跟。判断线上实际值仍 **MUST** 现查 `kubectl get deploy <名> -n itp -o yaml`（AGENTS.md §8）。重新导出即重跑上面的清洗流程并覆盖本目录。
