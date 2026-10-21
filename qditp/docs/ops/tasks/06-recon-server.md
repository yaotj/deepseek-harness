# recon-server 定时任务

> 对应 sys_job 1 条。该类任务**刻意不带模块内 `@Scheduled`**，由 web-admin `sys_job` 统一驱动。

---

## sys_job 225 给 ACC 上传扣费交易

- **cron**: `0 0 2 * * ?`
- **触发目标**: `reconQuartzTask.runDailyBatch()`
- **执行方**: `POST /internal/recon/daily/run`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 109 改为 225、任务名由「日终对账」改为「给ACC上传扣费交易」，**NEVER 回退成 109 或旧名**
- **实现要点**:
  - 甲方需求 6
  - 日终对账**唯一调度源**，账期 T-2 日
  - recon-server 侧无任何调度，频率只由本行 cron 决定
  - 产出 `ITP.EXP / ITP.PAY / ITP.BUS / ITP.DETAIL` 四文件并 FTP 投递到 `/itp/recon`
  - **扣费交易由 `ITP.PAY` 承载**（`ReconExportMapper.selectPayGateSummary` / `selectPayExpSummary`，源表只有 `GATE_TXN_PAY` 一张）
  - **2026-09-20 按业主要求由 `0 30 2` 改为 `0 0 2`**，同批把销卡（现 job 260）从 `0 0 2` 挪到 `0 30 2` 错开，两条都已在 `AFCITPDB` 执行并回查，**NEVER 改回去**
  - 同步等批次收口，单次可跑数分钟（服务端 `run-timeout-millis` 240s < `ReconClient` 响应超时 5min）
  - 与 2:30 销卡、3:30 参数扫描仍属同一时段，上线前 **MUST 确认 Oracle 与 FTP 在该时段的负载**
- **配置键**: 无（cron 在 sys_job 表里，recon-server 侧无 `@Value` 配置该任务频率）
- **关键约束**:
  - recon-server 侧无任何 `@Scheduled`，**频率只由 sys_job 225 的 cron 决定**；NEVER 在 recon-server 内加 `@Scheduled`
  - 单次执行可跑数分钟（服务端 `run-timeout-millis=240s` < `ReconClient` 响应超时 5min），Quartz 禁止并发（`@DisallowConcurrentExecution`）
  - 与 2:30 销卡（job 260）、3:30 参数扫描（job 285）属同一时段，上线前 MUST 确认 Oracle 与 FTP 负载
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=225`

---

## 判是否在跑（通用）

**MUST 查 `SYS_JOB_LOG`**，NEVER 在该模块里找 `@Scheduled`（该模块刻意不带）。

---

## @Scheduled 第三方对账（默认关闭，防双跑）

- **调度表达式**: `@Scheduled(cron = "${recon.thirdparty.scheduler.cron:0 30 3 * * ?}")`
- **类**: `ThirdPartyReconScheduler`
- **状态**: 随模块启动生效 / **默认关闭**（cron 默认 `0 30 3 * * ?`，可通过配置覆盖）
- **关键约束**:
  - `recon.thirdparty.scheduler.cron` 默认值是 `0 30 3 * * ?`，**默认关闭**是为了防止与 sys_job 225 双跑 —— 上线前 MUST 确认该开关状态
  - 本任务与 sys_job 225 **互斥**：225 负责 ACC 上传扣费交易，第三方对账负责与第三方账单比对，两者数据源不同，NEVER 合并
