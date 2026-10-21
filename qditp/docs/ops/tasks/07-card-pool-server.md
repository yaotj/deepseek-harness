# card-pool-server 定时任务

> 对应 sys_job 1 条。该类任务**刻意不带模块内 `@Scheduled`**，由 web-admin `sys_job` 统一驱动。

---

## sys_job 240 卡池数据导入

- **cron**: `0 0/5 * * * ?`
- **触发目标**: `cardPoolQuartzTask.runMaintenance()`
- **执行方**: `POST /internal/card-pools/maintenance`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 按业主口径由 job_id 107「卡池维护」改名改号为 240，触发目标与 cron 一字未动，**NEVER 据新名字以为只做导入**
- **实现要点**:
  - 回收超时预占 + 补货 + 推进批次
  - **NEVER 据新名字以为这条任务只做导入** —— 回收超时预占是账户/支付宝开户失败路径的唯一兜底（ADR-D52）
- **配置键**: 无外部可调参数（batchSize 等硬编码在 CardPoolServiceImpl 内部）
- **关键约束**:
  - 本模块刻意无 `@Scheduled`，由 web-admin `sys_job` 驱动；NEVER 在本模块内加
  - ADR-D52：**回收超时预占是账户/支付宝开户失败路径的唯一兜底**，NEVER 移除或禁用本任务的回收逻辑
  - 本任务名含「导入」但实则是**维护型任务**（回收 + 补货 + 推进批次），改名时 NOT 仅做导入
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=240`

---

## 判是否在跑（通用）

**MUST 查 `SYS_JOB_LOG`**，NEVER 在该模块里找 `@Scheduled`（该模块刻意不带）。
