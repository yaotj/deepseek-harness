# IT-095 collect-pay 测试环境停服（云管台 Stopped + K8s 副本 0，Deployment / Service 资源保留未删）

> 本处理文档对应 `docs/tracking/问题清单.md` 索引表中 `IT-095` 一行。

## 1. 背景 / 现象

- 发现时间：2026-11-20（用户裁定「下线 collect-pay」，明确测试环境）
- 发现渠道：用户指令（测试环境下线 collect-pay）
- 影响范围：测试环境 `ns itp` 的 `collect-pay` Deployment / Service；`fep-app` 的 `service.facePay.url` 已改指 face-pay 30025，collect-pay 无入向流量；`gate-txn-pay` 补款链路 / `recon` 对账源 / web-admin `NoticeAppTask` 旧表通知端点均已在 2026-09-16 ~ 2026-10-13 期间切到 face-pay，collect-pay 无活调用方
- 现象描述：测试环境 `collect-pay` Deployment 此前 1/1 在跑（云管台 status OK、镜像 1.1.93.1），按用户要求停服

## 2. 根因分析

- 排查过程：
  1. 2026-11-20 14:37 实测 `kubectl -n itp get deploy collect-pay` → `1/1`、`AGE=127d`，确认当前在跑（与 `docs/ops/environments/测试环境.md` L57 旧记「2026-10-13 已下线 0 pod」不符——旧记是「判断 0 pod」的口径，非实测）。
  2. `itp-ops.sh cloudos act collect-pay stop --env test --yes` → 平台返回 `data: true`，但 K8s 侧 Deployment 仍 `1/1`（云管台 stop 只改平台 status，不动 K8s 副本数）。
  3. `itp-ops.sh cloudos raw POST /api/cloud/app-mgmt/v1.0/apps/815eac1b97e842e4a41967046fd978af/scale?replicas=0 --env test --yes` → K8s 副本数 `0/0`、Pod 清空；云管台 `cloudos app collect-pay` 状态 `Stopped`。
- 根因结论：「云管台 stop」与「K8s scale 0」是两件事，必须**两步都做**才真正下线。`cloudos scale` 子命令仅支持 1..20，0 必须走 `raw POST .../scale?replicas=0`（`itpops/cloudos/scaling.py` L227 的 `1 <= int(target) <= 20` 校验拦截了 0）。

## 3. 方案对比

| 方案 | 思路 | 优点 | 缺点 | 风险 |
|------|------|------|------|------|
| A（已采用） | `cloudos act stop` + `raw POST scale?replicas=0` 两步 | 平台侧 status `Stopped` + K8s 侧 0 副本，Deployment / Service 资源保留未删（随时可恢复） | 需要走一次 raw API（脚本未封装 0 副本分支） | 低：Service 保留，下游若有直连调用仍可达（但当前已无活调用方） |
| B | `kubectl -n itp scale deploy/collect-pay --replicas=0` 直接改 K8s | 一步到位 | 绕开云管台，平台 status 与 K8s 脱节（下次 `cloudos deploy` 切镜像时平台 status 仍 OK、K8s 0 副本，状态不一致） | 中：与「改应用 env MUST 走 CloudOS PATCH advanced-config」同型，违反「平台是 Service 真源」纪律 |
| C | 删除 Deployment + Service | 彻底清理 | 不可恢复（要重建需走 `cloudos svc create` + 手动建 Deployment，且 Service 随机后缀每次漂移） | 高：无回滚路径 |

## 4. 决策与结论

- 选定方案：A（`cloudos act stop` + `raw POST scale?replicas=0`，Deployment / Service 资源保留未删）
- 决策理由：
  1. 测试环境「下线」的语义是**停跑**、不是**删除**——恢复只需 `act start` + `scale 1`（两条命令），不需要重建资源；
  2. 走平台侧 status 与 K8s 副本数两条路径，平台 status 与 K8s 实跑保持一致（符合 IT-020 / IT-034「env 全量回传 / 实例实跑为准」的既有纪律）；
  3. `cloudos scale` 的 1..20 校验是脚本约定，0 副本走 raw 直调是脚本允许的兜底路径（`raw` 写操作带 `--yes` 门，与脚本其它写命令同一套护栏）。
- 影响范围 / 关联模块：
  - `collect-pay-server` 测试环境停服（云管台 `Stopped` + K8s 副本 0、Deployment / Service 资源保留未删）
  - 红岛 `itpb` 从未部署 collect-pay（2026-10-23 实测 24 个 Deployment 未含），本次不动红岛
  - `fep-app` 的 `service.facePay.url` 已指 face-pay 30025（2026-11-20 前叫 `service.collectPay.url`、该键已随 `CollectPayClient` 删除），**不受本次停服影响**
  - 文档口径统一：仓内所有「collect-pay 2026-10-13 已下线 0 pod / 退居后台」改为「**2026-11-20 测试环境云管台 Stopped + K8s 副本 0、Deployment / Service 资源保留未删；红岛从未部署**」（17 个文件，见下）

## 5. 验证与回滚

- 验证方式 / 结果：
  - `itp-ops.sh cloudos app collect-pay --env test` → `status: Stopped`
  - `ssh k8s-master "kubectl -n itp get deploy,pod | grep collect-pay"` → `deployment.apps/collect-pay 0/0`、无 Pod
  - `kubectl get svc -n itp | grep collect-pay` → `collect-pay-c23ku-svc`（NodePort 30024）与 `collect-pay-internal`（ClusterIP 8080）仍保留
- 回滚预案：
  - 恢复 = `itp-ops.sh cloudos act collect-pay start --env test --yes` + `itp-ops.sh cloudos scale collect-pay 1 --env test --yes`（两步）
  - 平台 status 与 K8s 副本数必须**同步恢复**（只 `act start` 不 `scale 1` 时 K8s 仍是 0 副本、Pod 起不来；只 `scale 1` 不 `act start` 时平台 status 仍 Stopped、状态脱节）
  - 恢复后 MUST 复验 `fep-app` 的 `service.facePay.url` 仍指 face-pay 30025（NEVER 被回滚成 collect-pay 30024）

## 6. 流程符合度自评

- [x] 已在 `问题清单.md` 索引表加行（编号 `IT-095`）
- [x] 已建本处理文档并记录处理过程与决策
- [x] 处理后已回填索引「状态 / 下一步 / 更新」
- [x] 索引行未复制本文档的叙述（索引只写「一句话现象 + 状态 + 下一步 + 关联文档」）
- [x] 本文档内的版本号 / 端口 / 行号等数字是否已标注来源或「MUST 现查」
  - 镜像 tag `1.1.93.1`（云管台 `cloudos app collect-pay` 2026-11-20 14:37 实测）
  - Deployment 资源名 `collect-pay`、uuid `815eac1b97e842e4a41967046fd978af`（云管台现查）
  - Service 随机后缀 `c23ku` / `internal`（`kubectl get svc -n itp | grep collect-pay` 现查）
  - `itpops/cloudos/scaling.py` L227 的 `1 <= int(target) <= 20` 校验（仓内代码现读）
- 偏差说明（如有）：
  - `docs/ops/environments/测试环境.md` L57 旧记「2026-10-13 已下线 0 pod」与 2026-11-20 实测（1/1 在跑）不符，本次一并订正为 2026-11-20 停服口径。该旧记是「判断 0 pod」的结论、非「实测 0 pod」，属历史口径漂移，不影响本次决策。
