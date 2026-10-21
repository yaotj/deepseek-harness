# account-server 定时任务

> 对应 sys_job 1 条。该类任务**刻意不带模块内 `@Scheduled`**，由 web-admin `sys_job` 统一驱动。

---

## sys_job 290 签约展示账号同步补偿

- **cron**: `0 0/5 * * * ?`
- **触发目标**: `accountQuartzTask.compensatePhoneSignSync()`
- **执行方**: `POST /phoneSignSyncCompensate`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 108 改为 290
- **实现要点**:
  - 单批 200 条、重试上限 10 次
  - 补偿签约展示账号（手机号）同步失败的路径
- **配置键**:
  - `SIGN_SYNC_SCAN_LIMIT: 200` 硬编码 —— 每轮最多扫 200 条
  - `SIGN_SYNC_MAX_RETRY: 10` 硬编码 —— 重试上限，超过后不再自动重推
  - `SIGN_SYNC_RESULT_MAX_LENGTH: 1024` 硬编码 —— `USER_PHONE_CHANGE_LOG.SIGN_SYNC_RESULT` 是 VARCHAR2(1024 CHAR)，超长截断，NEVER 让落状态因超长而失败
- **关键约束**:
  - `PhoneChangeServiceImpl` 无 `@Scheduled`，由 web-admin `accountQuartzTask.compensatePhoneSignSync()` 驱动；NEVER 在本类上加
  - 本任务是**桥接任务**（AccountQuartzTask 是桥接），实际补偿逻辑在 `PhoneChangeServiceImpl`，断点续传靠 `maxRetry` 字段
  - `SIGN_SYNC_RESULT` 超长截断是**刻意的**：宁可记录截断错误码，也不要让超长导致整条记录落失败
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=290`

---

## 判是否在跑（通用）

**MUST 查 `SYS_JOB_LOG`**，NEVER 在该模块里找 `@Scheduled`（该模块刻意不带）。
