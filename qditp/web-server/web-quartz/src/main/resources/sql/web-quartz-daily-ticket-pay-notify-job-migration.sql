DELETE FROM sys_job WHERE job_id = 330;

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (330, '日票支付结果通知补偿', 'DEFAULT', 'dailyTicketQuartzTask.compensatePayNotify()', '0 0/5 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每5分钟通过 RPC 调用 daily-ticket-server POST /internal/daily-ticket/pay/notify，扫描 DAILY_TICKET_PAY_NOTIFY 中 PENDING 的普通日票/旅游票支付结果通知并重试 IF8B-05。daily-ticket 仅提供内部接口，不在服务内使用 @Scheduled；并发执行关闭以避免同一批通知重复投递。2026-09-21 随「200 以下全量重编号」由 127 改为 330；同批修掉本脚本首行 DELETE 误写成 125（黑名单加黑通知补偿、现 320）的历史错误。');

COMMIT;
