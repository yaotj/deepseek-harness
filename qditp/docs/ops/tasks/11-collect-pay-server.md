# collect-pay-server 定时任务

> ⚠️ **部署状态**：`collect-pay-server` 模块代码与 Deployment yaml（`deploy/k8s/10-deployments/collect-pay.yaml`）仍在仓库，但 **2026-10-13 起集群内 0 pod（已下线，按用户要求）**。因此下文 4 个 `@Scheduled` 任务**当前不在运行**；本文件的「NEVER 停」「MUST 单副本」等约束**仅在模块被重新拉起后才生效**。若需切回，MUST 先复核 `fep-app` 的 `service.collectPay.url` 是否被回滚成 30024（见 `docs/ops/流量切换Runbook.md`）。

> 对应模块内 @Scheduled 4 处。覆盖 TBL_TVM_APP_ORDER / TBL_TVM_ORDER_TOPUP / TBL_BOM_ORDER_PAY 旧表存量。**仅覆盖旧表，新表 F2F_* 由 sys_job 200/205/210 覆盖，两套覆盖面互不重叠。**

---

## @Scheduled APP 购票未取票退款

- **触发目标**: `SingleTicketRefundTask.refundAppNotTakeTickets()`
- **调度表达式**: `0 0 20 * * ?`
- **执行方**: 扫 `TBL_TVM_APP_ORDER`（T-1 全天）
- **状态**: 随模块启动生效 / 无分布式锁 / MUST 单副本
- **实现要点**:
  - 仅覆盖旧表，新表 `F2F_*` 由 sys_job 200 覆盖
  - 2026-09-20 已补 `log.error`，本轮全失败时不再静默
  - 与 sys_job 200 并行存在但**覆盖面互不重叠**，NEVER 认为重复
- **配置键**: 无外部可调参数（扫描 `TBL_TVM_APP_ORDER` 逻辑在 `SingleTicketRefundTask.refundAppNotTakeTickets()` 内部）
- **关键约束**:
  - **无分布式锁 / MUST 单副本** —— face-pay-server 与 collect-pay-server 的 `@Scheduled` 任务均无锁，多副本会重复扫同一批旧表行
  - 覆盖旧表 `TBL_TVM_APP_ORDER`（T-1 全天），与新表 `F2F_*` 的覆盖面**互不重叠**，NEVER 认为重复或合并
  - 2026-09-20 已补 `log.error`，此前全失败静默不报；**NEVER 改回静默**

---

## @Scheduled TVM 充值未取票退款

- **触发目标**: `SingleTicketRefundTask.refundTvmTopupNotTakeTickets()`
- **调度表达式**: `0 0 20 * * ?`
- **执行方**: 扫 `TBL_TVM_ORDER_TOPUP`
- **状态**: 随模块启动生效 / 无分布式锁 / MUST 单副本
- **实现要点**: 仅覆盖旧表，新表由 sys_job 205 覆盖
- **配置键**: 无外部可调参数（扫描 `TBL_TVM_ORDER_TOPUP` 逻辑在 `SingleTicketRefundTask.refundTvmTopupNotTakeTickets()` 内部）
- **关键约束**:
  - **无分布式锁 / MUST 单副本**
  - 覆盖旧表 `TBL_TVM_ORDER_TOPUP`，与新表 `F2F_*` 覆盖面互不重叠，NEVER 认为重复

---

## @Scheduled BOM 售票未取票退款

- **触发目标**: `SingleTicketRefundTask.refundBomSaleNotTakeTickets()`
- **调度表达式**: `0 1 9,15,21 * * ?`
- **执行方**: 扫 `TBL_BOM_ORDER_PAY`（TRANS_TYPE=SALE）
- **状态**: 随模块启动生效 / 无分布式锁 / MUST 单副本
- **实现要点**: 仅覆盖旧表，新表由 sys_job 210 覆盖
- **配置键**: 无外部可调参数（扫描 `TBL_BOM_ORDER_PAY` TRANS_TYPE=SALE 逻辑在 `SingleTicketRefundTask.refundBomSaleNotTakeTickets()` 内部）
- **关键约束**:
  - **无分布式锁 / MUST 单副本**
  - 覆盖旧表 `TBL_BOM_ORDER_PAY`（TRANS_TYPE=SALE），与新表 `F2F_*` 覆盖面互不重叠，NEVER 认为重复

---

## @Scheduled BOM 充值未取票退款

- **触发目标**: `SingleTicketRefundTask.refundBomTopupNotTakeTickets()`
- **调度表达式**: `0 0 9,15,21 * * ?`
- **执行方**: 扫 `TBL_BOM_ORDER_PAY`（TRANS_TYPE=TOPUP）
- **状态**: 随模块启动生效 / 无分布式锁 / MUST 单副本
- **实现要点**: 仅覆盖旧表；2026-09-20 已补 `log.error`
- **配置键**: 无外部可调参数（扫描 `TBL_BOM_ORDER_PAY` TRANS_TYPE=TOPUP 逻辑在 `SingleTicketRefundTask.refundBomTopupNotTakeTickets()` 内部；该方法实际调用 `bomOrderService.refundBomTopupNotTopup()`，方法名与调用名不一致是已知 typo，NEVER 修复）
- **关键约束**:
  - **无分布式锁 / MUST 单副本**
  - 覆盖旧表 `TBL_BOM_ORDER_PAY`（TRANS_TYPE=TOPUP），与新表 `F2F_*` 覆盖面互不重叠，NEVER 认为重复
  - 2026-09-20 已补 `log.error`；**NEVER 改回静默**
  - 方法名 `refundBomTopupNotTakeTickets` 内部调用 `refundBomTopupNotTopup()` 是已知 typo（`NotTopup` vs `NotTakeTickets`），NEVER 修复 —— 修复会导致 Javadoc/注释引用断裂

---

## 与 face-pay-server 新任务的覆盖关系

collect-pay 那 4 个旧表 `@Scheduled` **原样保留、NEVER 停**，覆盖旧表存量；face-pay 的 sys_job 200/205/210 覆盖新表 `F2F_*`。两套覆盖面互不重叠。旧表存量按裁决人工清退。

---

## 判是否在跑

查模块日志（无 `SYS_JOB_LOG` 记录）。
