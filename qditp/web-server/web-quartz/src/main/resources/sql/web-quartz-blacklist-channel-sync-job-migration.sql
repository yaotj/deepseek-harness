
DELETE FROM sys_job WHERE job_id IN (320, 325);

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (320, '黑名单加黑通知补偿', 'DEFAULT', 'blacklistChannelSyncQuartzTask.compensateAdd()', '0 0/5 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每5分钟触发 blacklist-server POST /internal/blacklist/channel-sync/compensate-add：扫 BLACKLIST 里 STATUS=ACTIVE 且 CHANNEL_SYNC_STATUS 为 PENDING/FAILED 的行，逐条重推支付宝渠道黑名单通知并回写三态。加黑通知的唯一兜底，服务内 afterCommit 那条快速路径是进程内非持久的、JVM 崩溃即丢。停用的后果是欠费卡在支付宝渠道仍能过闸。每轮条数由 limit 决定，不传即下游缺省 200，任务侧不循环。本轮结果看 sys_job_log 的 job_message 与服务端 scanned/success/failed');

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (325, '黑名单解除通知补偿', 'DEFAULT', 'blacklistChannelSyncQuartzTask.compensateRelease()', '0 2/5 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每5分钟触发 blacklist-server POST /internal/blacklist/channel-sync/compensate-release：扫 BLACKLIST 里 STATUS=RELEASING 且 CHANNEL_SYNC_STATUS 为 PENDING/FAILED 的行重推解除通知，推成功才搬历史表并删主表行。比 320 更要紧：两阶段解除下通知没推成功那行仍算黑名单，停用等于运营已点解除、用户却永远过不了闸。载体在主表，BLACKLIST_RELEASED 的 CHANNEL_SYNC_* 已降级为审计、NEVER 据它扫表。与 320 错开2分钟，避免同时打同一服务');
