# IT-030 红岛 fep-app-vr 7 条 exact 误指 route-sentinel-svc:8080 致 503

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-030` 一行。
> 关联 `IT-021`（红岛 route-sentinel 网关表缺失，已解决）——本单是 IT-021「切流落地」环节的端口错配子缺陷。

## 1. 背景 / 现象

- 发现时间：2026-10-28（模拟 fep-app-vr 入向流量复验时发现）
- 发现渠道：自查（从 ingress `istio-proxy` 向 `localhost:8082` POST 7 exact 路径复验）
- 影响范围：红岛 `itpb` 入口 VS `fep-app-vr` 的 **7 条 exact**（split-route 路径：`/itpbom/ci/bom/*` 5 条 + `/itptvm/ci/tvm/*` 2 条）；即 TVM/BOM 域走 SCG `route-sentinel` 的分流请求
- 现象描述：
  从 ingress（绑定 `accountserver-gw`，监听 **8082**）发 `POST` 到任一 7 exact 路径，Envoy 直接返回
  **HTTP 503**（非应用层 4xx/5xx），且 route-sentinel Pod **收不到**该请求 → 证明 503 来自 Envoy 找不到上游，
  不是 route-sentinel 或 face-pay 业务错误。7 条 exact 在初始部署时 `destination` 写成了 `route-sentinel-svc:8080`。

## 2. 根因分析

- 排查过程：
  1. route-sentinel 日志显示曾收到 `GET` 该路径但报 `HttpRequestMethodNotSupportedException: GET not supported`
     → 证明 `ingress → fep-app-vr → route-sentinel` 链路在「route-sentinel 收到请求」这一跳之前是通的；
  2. 改用 `POST` 仍 503 且 route-sentinel **无新日志** → 503 在 Envoy 侧，请求根本没到 route-sentinel；
  3. 查 `kubectl get svc -n itpb route-sentinel-svc`：`spec.ports` 仅
     `{"name":"http-31944","nodePort":31944,"port":31944,"targetPort":8080}`
     —— **只有 NodePort 31944，没有 ClusterIP 端口 8080**；
  4. 对照 `route-sentinel-internal-svc`：`spec.ports` 有 `{"port":8080,"targetPort":8080}`（ClusterIP），
     集群内 `<svc>.itpb.svc:8080` 可达；
  5. 结论：VS 把 7 exact 写成 `route-sentinel-svc:8080`，但 `route-sentinel-svc` 在集群内**没有 8080 这个 service 端口**
     （它暴露的是 31944），Envoy 解析 upstream `route-sentinel-svc:8080` 找不到对应 cluster port → **503**。
- 根因结论：
  红岛 route-sentinel 部署时建了两个 Service：
  - `route-sentinel-svc`：**仅 NodePort 31944**（供集群外 / 节点级访问，如本机 NodePort 31944 探活网关），**不含 ClusterIP 8080**；
  - `route-sentinel-internal-svc`：ClusterIP `8080→8080`，供集群内服务间调用。
  入向 VS（集群内 Envoy 路由）的 upstream **必须用 ClusterIP service 的端口**，即 `route-sentinel-internal-svc:8080`。
  初始部署把 7 exact 写成了 `route-sentinel-svc:8080`（照搬了测试环境 Runbook 的 `route-sentinel-svc:8080` 写法），
  而测试环境 `route-sentinel-svc` 同时有 ClusterIP 8080，红岛不一样 → 错配。
  > ⚠️ `docs/ops/流量切换Runbook.md` §二/§三 写的「7 条 SCG exact 永远指 `route-sentinel-svc:8080`」是**测试 `ns itp`** 的口径；
  > 红岛 `ns itpb` 因 `route-sentinel-svc` 仅 NodePort，**MUST 用 `route-sentinel-internal-svc:8080`**。该差异已在 Runbook §二补防呆注，避免下次切流再踩。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| 方案A | VS 7 exact 改指 `route-sentinel-internal-svc:8080`（ClusterIP 8080） | 不动 Service、不动 Pod；patch 即生效；与 8 前缀一致用 internal-svc | 需生产 VS 写操作 | 低 |
| 方案B | 给 `route-sentinel-svc` 增加 ClusterIP 端口 8080（改 Service） | 不改 VS | 改 Service 影响 NodePort 语义、可能被平台回收；internal-svc 已存在，重复造端口 | 中 |
| 方案C | 7 exact 直指 face-pay-server（绕过 route-sentinel） | 最简单 | 破坏 SCG 分流设计（legacy/split 路由表失效），等于回退到无网关 | 高 |

## 4. 决策与结论

- 选定方案：**方案A**
- 决策理由：
  - `internal-svc` 本就是为集群内调用而建、8 条前缀已在用，改 VS 最小风险且即时生效；
  - 方案 B 改 Service 有平台回收风险（见 IT-020 教训）、且重复造端口；
  - 方案 C 放弃 SCG 分流设计不可接受。
- 影响范围 / 关联模块：
  - `fep-app-vr` VS（ns `itpb`）、`route-sentinel`（ns `itpb`）；
  - 关联 `IT-021`（网关表缺失，已解决，本单是其「切流落地」环节的端口错配子缺陷）。
- 生产写授权：本 VS patch 属 `AGENTS.md` §5.C 生产红线，已获用户 2026-10-28 授权（「改 internal-svc:8080（推荐）」）。

## 5. 验证与回滚

- 验证方式 / 结果：
  - patch 后读回 VS：`kubectl get vs fep-app-vr -n itpb -o json` 确认 **indices 0–6（7 exact）** `route[0].destination.host=route-sentinel-internal-svc`、`port=8080`；**indices 7–14（8 前缀）** 不变（各 internal-svc:8080）。✅
  - 模拟流量复验：从 ingress pod（`istio-ingressgateway-*-mwvd2`，ns `istio-ingress`）的 `istio-proxy` 容器
    `curl -X POST http://localhost:8082/itpbom/ci/bom/notiTakeTicketResult` → **HTTP 200**，
    body `{"retCode":"2002","retMsg":"orderNo不能为空"}`
    （face-pay-server 业务层返回，证明 `ingress:8082 → fep-app-vr VS(exact→route-sentinel-internal-svc:8080) → route-sentinel → face-pay-server` 全链路通；503 已消除）。
  - 注：`2002` 是 face-pay 对「缺 orderNo」的校验错误，非 Envoy 503、非 SCG 404，链路确证。
- 回滚预案：
  - **复现 503 的回滚（错误示范，勿用）**：把 7 exact 改回 `route-sentinel-svc:8080` 即复现故障；
  - **真正回滚**（如需临时摘除 route-sentinel）：走 IT-021 §5 既定路径，把 7 exact 直接指 `face-pay-server-internal-svc:8080`（绕过 SCG，应急），或恢复 `route-sentinel-svc` 的 ClusterIP 8080 端口（方案 B）；
  - patch 用 `test` op 防下标漂移（Runbook §四 NEVER）。

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-030`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的端口 / 路径数字均已标注来源（端口 31944 / 8080 经 `kubectl get svc` 现查；7 exact 路径经 `schema-local-h2.sql` 与 VS jsonpath 现查）
- 偏差说明：本单是一次已授权的生产 VS 写操作（`AGENTS.md` §5.C），用户在 2026-10-28 模拟流量复验语境下授权；修复后已验证闭环。IT-021 §7 原「`route-sentinel-svc` 与 `route-sentinel-internal-svc` 同指 backend、无需改」结论已更正为本单事实。
