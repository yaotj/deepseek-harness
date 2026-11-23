
-- 动态取号（MAX(job_id)+5）+ NOT EXISTS 幂等守卫：NEVER DELETE 写死 job_id，
-- 固定号 DELETE 会误删测试库占用同号的其它调度（IT-066，见 web-quartz-oneside-job-migration.sql）。
INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT (SELECT NVL(MAX(job_id),0)+5 FROM sys_job), '支付宝支付通道同步补偿', 'DEFAULT', 'alipayChannelSyncQuartzTask.compensate()', '0 0/5 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每5分钟触发 alipay-pay-sign-server POST /internal/alipay/channelSync/compensate：扫 ALIPAY_SIGN_INFO 里 CHANNEL_SYNC_STATUS 非 SUCCESS 且未超重试上限的行，逐条重推账户域支付通道并回写三态。ADR-D132 的唯一驱动源，停用等于签约首推不可达的行永久停在非 SUCCESS、账户域缺一条支付通道且对上游不可见。每轮条数由下游 alipay.channel-sync.batch-size 决定，任务侧不循环。本轮结果看 sys_job_log 的 job_message'
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'alipayChannelSyncQuartzTask.compensate()'
);
