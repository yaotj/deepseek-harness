-- 设备台账同步定时任务种子 SQL（web-server Quartz -> ParaQuartzTask.syncDevice() -> para-server /para/device-sync/quartz）。
--
-- 取号依据：docs/architecture/web-server.md §七 现行 sys_job 全量清单（2026-10-13 查库实测 33 条，
-- 现行最大 job_id = 400），业务任务编号 200~400 区间步长 5，本任务取 405。
-- 取号仍用 select max+1 + not exists 双保险，NEVER 只信注释里的号段。
--
-- cron 说明：每日 03:00，错开 225 对账（02:00）、260 销卡（02:30）、215 解约确认（04:00）。
--
-- status 刻意写 '1'（暂停）：代码尚未部署时任务不应被调度；para-server 发版后先在
-- 「监控管理 -> 定时任务」对该任务「执行一次」，调度日志确认 retCode=0000 后再切「正常」。
-- 注意 Quartz 是内存 JobStore：直接 INSERT 本行不重启 web-admin 不生效（见 web-server.md §7.0.1）。
--
-- 清单外的列取值一律照现有行抄：job_group='DEFAULT'、misfire_policy='3'（错过不补跑）、
-- concurrent='1'（'0' 才是允许并发）、create_by='admin'。
--
-- 执行后回查：
--   select job_id, job_name, invoke_target, cron_expression, status from sys_job where invoke_target like 'paraQuartzTask.syncDevice%';
-- 回滚（本行由本文件新增，可安全删除；删除后同样须重启 web-admin 或后台保存一次才从调度器移除）：
--   delete from sys_job where invoke_target = 'paraQuartzTask.syncDevice()';
insert into sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select (select max(job_id) + 5 from sys_job), 'ACC设备台账同步', 'DEFAULT', 'paraQuartzTask.syncDevice()',
       '0 0 3 * * ?', '3', '1', '1', 'admin', sysdate, '每日全量同步ACC设备台账到para-server TBL_DEVICE_INFO，业主管理模式字段不受同步影响'
from dual
where not exists (select 1 from sys_job where invoke_target = 'paraQuartzTask.syncDevice()');
