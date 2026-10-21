# web-admin + micro 公共构件定时任务

> 对应 sys_job 3 条（RuoYi 自带示例，无业务含义）+ 公共构件 @Scheduled 4 处（随 WebAutoConfig 的 `@EnableScheduling` 全局生效）。

---

## sys_job 1 系统默认（无参）

- **cron**: `0/10 * * * * ?`
- **触发目标**: `ryTask.ryNoParams`
- **状态**: 暂停 / 禁止并发
- **备注**: RuoYi 自带示例任务，无业务含义
- **配置键**: 无业务配置键（cron `0/10 * * * * ?` 在 sys_job 表里）
- **关键约束**: RuoYi 自带示例，**无业务含义**，状态为「暂停」，NEVER 启用；与本系统业务无关，NEVER 修改其实现

---

## sys_job 2 系统默认（有参）

- **cron**: `0/15 * * * * ?`
- **触发目标**: `ryTask.ryParams('ry')`
- **状态**: 暂停 / 禁止并发
- **备注**: RuoYi 自带示例任务，无业务含义
- **配置键**: 无业务配置键（cron `0/15 * * * * ?` 在 sys_job 表里）
- **关键约束**: RuoYi 自带示例，**无业务含义**，状态为「暂停」，NEVER 启用；参数 `'ry'` 是示例参数，NEVER 改成业务参数

---

## sys_job 3 系统默认（多参）

- **cron**: `0/20 * * * * ?`
- **触发目标**: `ryTask.ryMultipleParams('ry', true, 2000L, 316.50D, 100)`
- **状态**: 暂停 / 禁止并发
- **备注**: RuoYi 自带示例任务，无业务含义
- **配置键**: 无业务配置键（cron `0/20 * * * * ?` 在 sys_job 表里）
- **关键约束**: RuoYi 自带示例，**无业务含义**，状态为「暂停」，NEVER 启用；多参数示例（String/boolean/long/double/int），NEVER 改成业务参数

---

## @Scheduled Druid 统计重置

- **触发目标**: `SqlConfiguration.resetAllDruidStatData()`
- **调度表达式**: `${other.monitor.resetAllMetersCron}`（默认 `0 0 2 * * ?`）
- **状态**: 随模块启动生效 / `@Async`
- **执行方**: `resource/micro/sql-datasource`
- **说明**: WebAutoConfig 的 `@EnableScheduling` 全局生效，各业务模块无需自行开启
- **配置键**:
  - `other.monitor.resetAllMetersCron: 0 0 2 * * ?` —— 默认每天 2 点重置，可通过配置覆盖
- **关键约束**:
  - 公共构件 `@Scheduled`，随 `WebAutoConfig` 的 `@EnableScheduling` 全局生效，各业务模块无需自行开启
  - `@Async` 执行，不影响主线程；NEVER 在任务内做耗时同步操作

---

## @Scheduled Prometheus 指标重置

- **触发目标**: `ResetMetersJob.resetAllMeters()`
- **调度表达式**: `${other.monitor.resetAllMetersCron}`（默认 `0 0 2 * * ?`）
- **状态**: 随模块启动生效 / `@Async`
- **执行方**: `resource/micro/web`
- **说明**: 同上
- **配置键**:
  - `other.monitor.resetAllMetersCron: 0 0 2 * * ?` —— 与 Druid 统计重置共用同一 cron
- **关键约束**:
  - 公共构件 `@Scheduled`，随 `WebAutoConfig` 的 `@EnableScheduling` 全局生效
  - `@Async` 执行，NEVER 在任务内做耗时同步操作

---

## @Scheduled Prometheus 指标打印

- **触发目标**: `ResetMetersJob.logAllMetric()`
- **调度表达式**: `${other.monitor.logAllMetersCron}`（默认 `0/30 * * * * ?`）
- **状态**: 随模块启动生效 / `@Async`
- **执行方**: `resource/micro/web`
- **说明**: 同上
- **配置键**:
  - `other.monitor.logAllMetersCron: 0/30 * * * * ?` —— 默认每 30 秒打印一次，可通过配置覆盖
- **关键约束**:
  - 公共构件 `@Scheduled`，随 `WebAutoConfig` 的 `@EnableScheduling` 全局生效
  - `@Async` 执行，NEVER 在任务内做耗时同步操作

---

## @Scheduled 本地缓存过期清理

- **触发目标**: `LocalCache.cleanExpiredEntries()`
- **调度表达式**: fixedDelay 60000
- **状态**: 随模块启动生效
- **执行方**: `web-server/web-common`
- **说明**: web-common 内的进程内缓存清理，与业务无关
- **配置键**: 无（fixedDelay 60000ms 硬编码）
- **关键约束**:
  - 公共构件 `@Scheduled`，随 `WebAutoConfig` 的 `@EnableScheduling` 全局生效
  - 与业务无关，NEVER 修改其逻辑
  - fixedDelay 60000ms = 1 分钟，硬编码在代码里，NEVER 改成配置键（改了会破坏调用方预期）

---

## 判是否在跑

RuoYi 示例任务（job 1/2/3）状态为「暂停」，无需关注。

公共构件 @Scheduled 查模块日志（无 `SYS_JOB_LOG` 记录）。
