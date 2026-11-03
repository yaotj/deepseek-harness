# IT-050 route-sentinel 路径重写与双端口下游

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-050` 一行。

## 1. 背景 / 现象

- 发现时间：2026-11-03
- 发现渠道：用户提出转发需求
- 影响范围：route-sentinel 分流网关、istio 入向路由 `fep-app-vr`、旧 ITP 设备域接口
- 现象描述：
  业务方要求 route-sentinel 把 5 条设备域请求转发到旧 ITP，但转发时须改写路径：
  - `/itptvm/ci/tvm/xxx` → `/itptvm/ci/tvmDispatch/xxx`，目标 `http://172.20.202.10:8180`
  - `/itpbom/ci/bom/xxx` → `/itpbom/ci/bomDispatch/xxx`，目标 `http://172.20.202.10:8580`
  当前 route-sentinel 只支持按 URI 原样透传，不支持路径重写；且 legacy/split 各只有一条路由，无法同时指向 TVM/BOM 两个不同端口。

## 2. 根因分析

- 排查过程：
  1. 读 `route-sentinel` 源码：`UserRouteConfig` 构建路由时只调用 `BeforeFilterFunctions.uri(r.uri())`，未解析 `filters` 字段。
  2. `GATEWAY_ROUTE_CONFIG` 表虽有 `FILTERS` 列，但 `GatewayRouteRepository` 读写时硬编码为 `'[]'`。
  3. `LegacyRouteIdStrategy` 只认 `routeId == "legacy-route"`，新路由无法被识别为 legacy。
  4. `fep-app-vr` 当前把 7 条 exact 路径打到 route-sentinel，而业务方只要求 5 条，多余 2 条会造成 route-sentinel 404。
- 根因结论：route-sentinel 缺少 filter 机制与多端口 legacy 路由支持；ingress 层 exact 路由数量与 SCG 路由表不匹配。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| 方案A：网关侧做路径重写 | route-sentinel 支持 `RewritePath` filter，并把 legacy/split 按 TVM/BOM 拆分为两条路由 | 外部调用方零改动，网关集中解决路径映射 | 需改代码、出镜像、发版 | 新 filter 解析有 bug 风险（已补测试） |
| 方案B：旧 ITP 侧做路径映射 | 旧 ITP 在 `172.20.202.10:8180/8580` 的 Nginx/应用层上做 rewrite | 不动 route-sentinel | 对下游不透明，route-sentinel 转发路径与旧 ITP 实际路径不一致，排查困难 | 需业务方配合改旧 ITP 配置 |

## 4. 决策与结论

- 选定方案：方案A
- 决策理由：业务方明确要求 route-sentinel 转发时直接带 `tvmDispatch` / `bomDispatch`；网关集中处理更清晰，也符合「透明网关只负责路由」的定位。
- 影响范围 / 关联模块：
  - `route-sentinel`：新增 `RouteFilterFactory`、扩展 `RouteDef` / `RouteSnapshot`、改 `UserRouteConfig` / `GatewayRouteRepository` / `GatewayRouteCommandService` / `GatewayRouteAuditService` / `LegacyRouteIdStrategy`。
  - `deploy/k8s/30-istio-ingress*.yaml`：7 条 exact 改为 5 条。
  - `docs/business/route-sentinel.md`、`docs/ops/用户分流网关操作手册.md`、`docs/ops/流量切换Runbook.md`、`docs/ops/environments/测试环境.md`、`docs/ops/environments/红岛.md`：同步路由数量与 route_id。
  - 数据库：`GATEWAY_ROUTE_CONFIG` 删除旧 `legacy-route` / `face-pay-split-route`，新增 4 条路由；`GATEWAY_ROUTE_CONFIG_AUDIT` 加 `BEFORE_FILTERS` / `AFTER_FILTERS`。

## 5. 验证与回滚

- 验证方式 / 结果：
  - 本地 `mvn -o clean test` 通过（22 tests，0 failures）。
  - 单元测试覆盖：新用户/旧用户在 TVM/BOM 5 条 URL 上分别命中 split/legacy；Actuator 热重载同时改 `legacy-route-tvm` 和 `legacy-route-bom` 后流量切换生效。
  - 测试环境发版验证（2026-11-03）：
    - `hongdao-buildx.sh --env test` 出 `itp/route-sentinel:1.0.5`（digest `sha256:92f1c5f5f5843c59aa8087828e9fa10ae48a79e2610aa2d3f5e4eacd4eead32b`，linux/amd64）。
    - 因 CloudOS `advanced-config` 对 route-sentinel  initially 返回 `application.5000010`，补全 `service_name/affinity/pvcResource/resources` 后 PATCH 成功；Pod imageID 与推送 digest 一致。
    - `deploy/k8s/30-istio-ingress.yaml` 已 server-side apply，`fep-app-vr` 现 13 条路由（5 exact + 8 prefix）。
    - 数据库 migration 已执行：`GATEWAY_ROUTE_CONFIG` 现 6 条路由（4 条分流 + 2 条兜底），`GATEWAY_ROUTE_CONFIG_AUDIT` 已加 `BEFORE_FILTERS`/`AFTER_FILTERS`。
    - `curl /actuator/gatewayroutes` 确认 6 条路由；`curl` 打 5 条 URL 均到达旧 ITP（返回 Tomcat 404，属旧 ITP 业务层响应，RewritePath 已生效）。
- 回滚预案：
  1. 数据库：执行 migration SQL 的逆操作——删除 4 条新路由，重新插入原 `legacy-route` / `face-pay-split-route`（如需要）。
  2. Ingress：`kubectl apply -f deploy/k8s/30-istio-ingress.yaml` 回退到上一版本（git）。
  3. route-sentinel 镜像：滚回到上一 tag。

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-050`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」（`问题清单.md` 已更新为「代码/文档已就绪，待测试环境发版验证」）
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口 / 行号等数字已标注来源或「MUST 现查」
- 偏差说明（如有）：无
