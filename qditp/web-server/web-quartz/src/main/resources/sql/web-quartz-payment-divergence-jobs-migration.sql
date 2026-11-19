-- IT-081 P1 / P3 定时任务种子 SQL（web-admin Quartz）。
--
-- P1 支付状态主动回查：web-admin -> PaymentCompensateQuartzTask.compensatePayStatus()
--   -> pay-sign POST /internal/payment/compensatePayStatus
--   作用：扫 PAY_TXN_DETAIL 里静默超过 10 分钟的非终态单，逐笔调支付中心 payQuery 收口；
--   回调丢失 / 曾被 8003 拒收的单不再依赖支付中心重推。
--
-- P3 跨域分叉核对：web-admin -> GateTxnPayQuartzTask.checkDivergence()
--   -> gate-txn-pay POST /internal/gate-txn-pay/divergenceCheck
--   作用：扣费域已 SUCCESS 而支付域仍非终态时告警（补款收口按口径差异 warn，其余 error）。
--   只发现 + 告警，NEVER 自动改支付域（补款场景原单支付本未发生）。
--
-- 取号：动态 max+5 + not exists 双保险，NEVER 写死号段（照 web-quartz 既有脚本口径）。
-- status 刻意写 '1'（暂停）：服务发版后先在「监控管理 -> 定时任务」执行一次，
-- 调度日志确认成功后再切「正常」；Quartz 是内存 JobStore，INSERT 后 MUST 重启 web-admin 才生效。
-- 清单外的列取值照现有行抄：job_group='DEFAULT'、misfire_policy='3'（错过不补跑）、
-- concurrent='1'（'0' 才是允许并发）、create_by='admin'。
--
-- 执行后回查：
--   select job_id, job_name, invoke_target, cron_expression, status from sys_job
--    where invoke_target in ('paymentCompensateQuartzTask.compensatePayStatus()',
--                            'gateTxnPayQuartzTask.checkDivergence()');
-- 回滚：
--   delete from sys_job where invoke_target in ('paymentCompensateQuartzTask.compensatePayStatus()',
--                                              'gateTxnPayQuartzTask.checkDivergence()');

insert into sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select (select max(job_id) + 5 from sys_job), '支付状态主动回查', 'DEFAULT', 'paymentCompensateQuartzTask.compensatePayStatus()',
       '0 */5 * * * ?', '3', '1', '1', 'admin', sysdate,
       '每5分钟扫静默超10分钟的非终态支付单，调支付中心payQuery收口，回调丢失不再依赖对方重推'
from dual
where not exists (select 1 from sys_job where invoke_target = 'paymentCompensateQuartzTask.compensatePayStatus()');

insert into sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
select (select max(job_id) + 5 from sys_job), '跨域分叉核对', 'DEFAULT', 'gateTxnPayQuartzTask.checkDivergence()',
       '0 15 * * * ?', '3', '1', '1', 'admin', sysdate,
       '每小时核对：扣费域SUCCESS而支付域非终态的单，告警待人工核对，不自动改支付域'
from dual
where not exists (select 1 from sys_job where invoke_target = 'gateTxnPayQuartzTask.checkDivergence()');
