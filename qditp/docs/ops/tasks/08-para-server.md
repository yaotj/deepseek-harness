# para-server 定时任务

> 对应 sys_job 1 条。该类任务**刻意不带模块内 `@Scheduled`**，由 web-admin `sys_job` 统一驱动。

---

## sys_job 285 ACC 参数文件同步

- **cron**: `0 2/10 * * * ?`
- **触发目标**: `paraQuartzTask.scanFtpPara()`
- **执行方**: `POST /para/import/ftp/quartz`
- **状态**: 正常 / 禁止并发
- **历史编号**: 2026-09-21 由 job_id 106 改为 285
- **实现要点**:
  - 按版本号 + MD5 判变更后下载解析入库
  - 避开 2:00 对账、2:30 销卡与 4 点解约确认
  - `retCode != 0000` 时任务抛异常
- **配置键**: 无（cron 在 sys_job 表里，para-server 侧无 `@Value` 配置该任务频率）
- **关键约束**:
  - 本模块刻意无 `@Scheduled`，由 web-admin `sys_job` 驱动；NEVER 在本模块内加
  - 按版本号 + MD5 判变：相同版本+MD5 的文件不会重复入库，NEVER 改为按时间戳判定
  - 与 2:00 对账（job 225）、2:30 销卡（job 260）、4:00 解约确认（job 215）错峰，cron `0 2/10 * * * ?` 避开这些时段
- **判是否在跑**: 查 `SYS_JOB_LOG`，`JOB_ID=285`

---

## 判是否在跑（通用）

**MUST 查 `SYS_JOB_LOG`**，NEVER 在该模块里找 `@Scheduled`（该模块刻意不带）。
