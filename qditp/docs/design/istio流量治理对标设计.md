# Istio 流量治理对标设计（参考 mlc 配置模板）

> 状态：**挂起**（2026-10-28 起草；同日用户裁决「现阶段没必要做」，集群未做任何改动，本文留作将来启动时的底稿）。
> 重启条件建议：出现入口重试 / 超时导致的真实故障，或需要版本灰度、连接池治理时，直接从 §十一 P0 起步。
> 适用范围：ns `itp`（测试）/ ns `itpb`（红岛）/ 辽阳东三套集群的 ITP 全量应用。
> 端口 / Service 名 / 副本数一律**现查**（`kubectl get svc,deploy -n <ns>`），本文出现的具体值只是撰写时快照，NEVER 当现状引用。

## 一、对标对象拆解

mlc 模板把流量治理分三层，层层职责单一：

| 层 | 资源 | 职责 |
|---|---|---|
| 入口层 | 入口 VirtualService（挂 Gateway） | Host 精确分流到聚合层网关；每条路由显式 `retries.attempts: 0`；`headers.response.remove` 清掉 `x-envoy-upstream-service-time` |
| 服务层 | 服务级 VirtualService（不挂 Gateway，网格内生效） | 命名路由 + URI 前缀匹配分流到 subset；默认路由显式 `timeout` |
| 版本层 | DestinationRule | subset（版本标签）+ 连接池（h2 升级 / 上限）+ `outlierDetection` 熔断剔除 |

## 二、itp 现状盘点

入向链路（测试环境实测，详见 `docs/ops/流量切换Runbook.md` §二「入向链路全貌（实测）」）：

```
公网 :48000 → 入向网关节点 → itp-gateway（ns itp-gateway，istio ingressgateway）
  → ns itp 的 VirtualService fep-app-vr（Gateway accountserver-gw，hosts '*'）
  → 各业务 Service（NodePort 30009~30035）
```

已有：Gateway `accountserver-gw`；VS `fep-app-vr` 15 条路由（7 条 SCG exact + 8 条前缀）；ns `itp` 已开 `istio-injection=enabled`。
红岛入口是 **Kong 1.4.0 前置**（`fep-app-vr` 是否同链路未确认，见 `docs/ops/environments/红岛.md` §「入向路由分发（实测：Kong 1.4.0，非 fep-app-vr）」）。

东西向：服务间调用走 `rpc` 模块 HTTP 直连 `service.*.url`，现值多为**节点 IP + NodePort**（如 `http://<节点IP>:30024`），流量不经过 sidecar，mesh 的遥测 / 策略 / mTLS 全部覆盖不到。

## 三、差距对照

| 能力 | mlc 模板 | itp 现状 | 差距等级 |
|---|---|---|---|
| 重试策略 | 每条路由显式 `attempts: 0` | **未配置** → istio 默认重试（2 次）在入口生效，支付 / 扣款请求可能被网关层重放 | P0 |
| 响应头清理 | remove `x-envoy-upstream-service-time` | 未配置，内部上游耗时对外可见 | P0 |
| 路由超时 | 默认路由显式 `timeout: 15s` | 未配置 → envoy 默认路由超时隐式生效（约 15s），长任务接口有被切 504 的隐患 | P0 |
| DestinationRule | subset + 连接池 + 熔断 | 全集群**一个 DR 都没有**：无连接池上限、无熔断剔除 | P1 |
| 命名路由 | 每条有 `name`（指标可区分） | 无 | P2 |
| 版本灰度 subset | v1 / v2 按 URI 引流 | 无（应用层靠 route-sentinel 业务名单分流） | P2 |
| 东西向入 mesh | 服务级 VS 治理东西向 | rpc 直连 NodePort，完全绕过 mesh | P3 |

> Host 分流**不照搬**：mlc 用 `Host` 精确匹配是因为它对外有三个域名入口；itp 设备 / APP 都是固定 IP+端口直连、按路径前缀区分业务，保持现有前缀路由形态即可。

## 四、设计原则（先于任何配置）

1. **金融链路零重试**：所有 VS 路由 MUST 显式 `retries.attempts: 0`。支付、扣款、签约回调均为非幂等或幂等靠业务唯一索引兜底的链路，网关层重试会造成重复扣款风险（对照 `docs/architecture/transaction-and-consistency.md` 的幂等约束）。
2. **单副本服务不做实例剔除**：`recon-server` / `web-admin` / `face-pay-server` / `collect-pay` 定时任务无分布式锁、按单副本运行（`deploy/k8s/README.md` §「使用约束」）。给唯一 Pod 配 `outlierDetection`，剔除瞬间 = 该服务全量 `no healthy upstream` 503。**单副本服务的 DR 不配熔断，或显式 `maxEjectionPercent: 0`**。
3. **显式超时、不依赖默认值**：每条路由写死 `timeout`，长任务接口单独放行（见 §六矩阵）。
4. **平台管理约束**：红岛 itpb 的 Deployment / env 由 CloudOS 管，加 label / env MUST 走 `PATCH /apps/{uuid}/advanced-config`，`kubectl patch` 会被平台数分钟内回滚（`docs/tracking/IT-020-JVM参数被平台回滚.md` §「决策」）。
5. **两套分流各管各的**：route-sentinel = 业务名单分流（应用层，按 URI 名单切新旧链路）；istio subset = 版本灰度（实例层，按 Pod label 切版本）。NEVER 用 subset 替代业务分流。

## 五、L0 入口层加固（P0，只动 `fep-app-vr`）

目标：重试归零、响应头清理、显式超时、命名路由。改动 ONLY 在 `fep-app-vr` 一个对象上，操作按 `docs/ops/流量切换Runbook.md` §三「标准操作四步」（json patch + `test` op + 三重验证）。

每条路由统一补三个字段：

```yaml
spec:
  http:
  - name: fep-app            # 新增：命名路由
    match:
    - uri:
        prefix: /fep-app/
    rewrite:
      uri: /
    retries:
      attempts: 0          # 新增：关闭 istio 默认重试
    timeout: 15s           # 新增：显式超时（长任务例外见 §六）
    headers:
      response:
        remove:
        - x-envoy-upstream-service-time   # 新增：不外泄上游耗时
    route:
    - destination:
        host: fep-app-hr32k-svc
        port:
          number: 9101
```

注意事项：

- 7 条 SCG exact 路由同样补 `retries: 0` / `timeout` / `name`，**顺序与 `rewrite` 一律不动**（Runbook §四「NEVER 清单」：exact 必须排在前缀之前、rewrite 不可顺手改）。
- 回滚源 = 仓库基线 `deploy/k8s/30-istio-ingress.yaml`，改完同步回写基线。
- 验证门：envoy `config_dump` 读回 `retry_policy.num_retries=0` 与 `timeout`；NodePort 探活；设备 / APP 真实请求落点确认。

## 六、L1 超时矩阵与长任务例外（P0 配套）

初版矩阵（上线前 MUST 按各链路实测耗时复核，NEVER 照抄）：

| 路由 / 链路 | 建议 timeout | 理由 |
|---|---|---|
| `/fep-app/`（APP 域聚合） | 15s | 下游含支付渠道外呼，留足余量 |
| `/fep-alipay/`（支付宝回调） | 10s | 回调要求快速应答 |
| `/itptvm/` `/itpbom/`（设备交易） | 10s | 设备侧有自身超时，网关不宜更宽松 |
| SCG 7 条 exact | 10s | 同上 |
| `/para-server/` 参数发布类长任务 | 60s 或单独命名路由 | 参数发布 / 批量下发可能超过默认 |
| recon 对账导出（内部） | 不限于入口，见 §八东西向 | 批处理链路 |

判据：timeout 宁紧勿松——超时暴露的是真慢，放宽掩盖的是雪崩。

## 七、L2 DestinationRule 基线（P1）

为全部 Java 服务补 DR，先只做「连接池 + 负载均衡」，**熔断按副本数分档**：

```yaml
apiVersion: networking.istio.io/v1
kind: DestinationRule
metadata:
  name: face-pay-server
  namespace: itp
spec:
  host: face-pay-server-svc          # 现查：Service 名带 PaaS 随机后缀，按环境填
  trafficPolicy:
    connectionPool:
      tcp:
        connectTimeout: 3s
        maxConnections: 1000
      http:
        http1MaxPendingRequests: 256
        http2MaxRequests: 1000
        idleTimeout: 30s
        maxRetries: 0                # DR 层重试也归零
    loadBalancer:
      simple: ROUND_ROBIN
```

分档（副本数现查 `kubectl get deploy -n <ns>`）：

| 档 | 服务 | outlierDetection |
|---|---|---|
| 入口 fep-*（多副本） | fep-app / fep-dev / fep-acc / fep-alipay | `consecutive5xxErrors: 5`、`interval: 10s`、`baseEjectionTime: 30s`、`maxEjectionPercent: 50` |
| 核心交易（按实际副本） | gate-txn-pay / pay-sign / daily-ticket / ticket 等 | 多副本同上；单副本**不配** |
| 单副本豁免 | recon-server / web-admin / face-pay-server / collect-pay | 不配（原则 2） |

`h2UpgradePolicy: UPGRADE` **暂缓**：`rpc` 模块的 HTTP 客户端是否支持 h2 未评估，先保持 http1，列入验证项。

## 八、L3 服务级 VS 与 subset 灰度（P2）

对标 mlc 的 para 三件套，为需要灰度的服务建「服务级 VS + DR subset」：

- Deployment 模板加 `version: v1` label（灰度实例 `v2`）；红岛侧 MUST 走 `advanced-config`（原则 4）。
- DR 增加 `subsets: [{name: v1, labels: {version: v1}}, {name: v2, labels: {version: v2}}]`。
- 服务级 VS 按 header / URI 引流到 v2，默认兜 v1——形态直接照 mlc para 模板。
- 价值场景：大版本发布先引流内部测试请求（带灰度 header）到 v2，验证后全量，替代「直接滚更全量」。
- 与 route-sentinel 的分工见原则 5；7 条 SCG exact 链路 NEVER 挂 subset。

## 九、东西向入 mesh（P3，远期）

把 `service.*.url` 从「节点 IP + NodePort」迁到「ClusterIP svc DNS」（`<svc>.<ns>.svc:<port>`），让东西向流量经过 sidecar，获得遥测 / mTLS / 连接池与超时治理。

风险与前置（缺一不可）：

- IT-016 教训：地址 MUST 按环境全量注入、NEVER 漏配（漏配即解析到默认 ns 致 DNS 失败）。
- ns `itpb` 的 sidecar 注入覆盖面**待现查**（测试 ns `itp` 已开 `istio-injection=enabled`）。
- 不经网关的内部直连方（recon 对账源、gate-txn-pay 补款、web-admin Quartz）也走这些地址，迁移 MUST 全链路回归。

## 十、环境差异适配

| 环境 | 入口形态 | 本方案落法 |
|---|---|---|
| 测试（ns itp） | istio VS 直接分发 | 全部层级直接适用 |
| 红岛（ns itpb） | Kong 1.4.0 前置 + istio-envoy，`fep-app-vr` 是否同链路**待查** | 先定「Kong 路由 vs fep-app-vr 谁是唯一真源」（红岛.md §「入向路由分发」的警告：两套并存会互相覆盖）；Kong 管入口路由、istio 管 L1~L3 治理，职责 MUST 划清后再落地 |
| 辽阳东 | 与测试同构（基线 `30-istio-ingress-liaoyang.yaml`） | 同测试，destination 现查重录 |

## 十一、实施路线

| 阶段 | 内容 | 验证门 | 回滚 |
|---|---|---|---|
| P0 | §五 fep-app-vr 加固 + §六超时矩阵 | envoy `config_dump` 读回 retry/timeout；探活 200；真实请求落点 | apply 仓库基线 |
| P1 | §七 DR 基线（测试先行，观察 ≥1 周再上红岛） | 各服务请求无新增 5xx；`istioctl analyze` 无 WARNING | 删 DR 即恢复 |
| P2 | §八 选 1 个低风险服务（如 trans-query）试点 subset 灰度 | 灰度 header 引流命中 v2；默认流量全在 v1 | 删 VS/DR subset |
| P3 | §九 东西向 DNS 化 | 全链路回归 + 遥测可见 | env 改回 NodePort |

## 十二、NEVER 清单

- NEVER 在非幂等链路开 istio 重试（VS 与 DR 两层都归零）。
- NEVER 给单副本服务配 `outlierDetection`（剔除唯一 Pod = 全量 503）。
- NEVER 用 subset 做业务分流（那是 route-sentinel 的职责）。
- NEVER 红岛绕过 `advanced-config` 直接 `kubectl patch` Deployment。
- NEVER 未过 envoy `config_dump` + 探活 + 落点三重验证就宣告路由生效（Runbook §三）。
- NEVER 照搬本文的 Service 名 / 端口 / 副本数，一律现查。
