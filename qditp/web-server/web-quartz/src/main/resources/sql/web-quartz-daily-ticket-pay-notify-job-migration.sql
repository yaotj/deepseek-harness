-- 动态取号（MAX(job_id)+5）+ NOT EXISTS 幂等守卫：NEVER DELETE 写死 job_id，
-- 固定号 DELETE 会误删测试库占用同号的其它调度（IT-066，见 web-quartz-oneside-job-migration.sql）。
INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT (SELECT NVL(MAX(job_id),0)+5 FROM sys_job), '日票支付结果通知补偿', 'DEFAULT', 'dailyTicketQuartzTask.compensatePayNotify()', '0 0/5 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每5分钟通过 RPC 调用 daily-ticket-server POST /internal/daily-ticket/pay/notify，扫描 DAILY_TICKET_PAY_NOTIFY 中 PENDING 的普通日票/旅游票支付结果通知并重试 IF8B-05。daily-ticket 仅提供内部接口，不在服务内使用 @Scheduled；并发执行关闭以避免同一批通知重复投递。2026-09-21 随「200 以下全量重编号」由 127 改为 330；同批修掉本脚本首行 DELETE 误写成 125（黑名单加黑通知补偿、现 320）的历史错误。'
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'dailyTicketQuartzTask.compensatePayNotify()'
);

COMMIT;
