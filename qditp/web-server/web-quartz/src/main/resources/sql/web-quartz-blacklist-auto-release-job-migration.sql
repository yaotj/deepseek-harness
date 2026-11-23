
-- 动态取号（MAX(job_id)+5）+ NOT EXISTS 幂等守卫：NEVER DELETE 写死 job_id，
-- 固定号 DELETE 会误删测试库占用同号的其它调度（IT-066，见 web-quartz-oneside-job-migration.sql）。
INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT (SELECT NVL(MAX(job_id),0)+5 FROM sys_job), '自动解除黑名单', 'DEFAULT', 'blacklistAutoReleaseQuartzTask.autoRelease()', '0 0 10,16 * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每天10点与16点各触发一次 blacklist-server POST /internal/blacklist/auto-release：扫 BLACKLIST 里 STATUS=ACTIVE 且 BLACK_CAUSE=01（欠费类）的行，按 CHANNEL_CODE 路由到对应欠费源（01 地铁APP 查闸机出站扣费、02 支付宝查支付宝出行、99 未知渠道跳过不处理），两者都返回已结清才走 deleteBlackList 的两阶段解除。BLACK_CAUSE 只取 01 是安全边界：02 挂失补卡即便欠费清了也不能自动放行（旧卡会恢复过闸），09 其他无判据，这两类只能人工解除。欠费查询失败（事实不明）一律跳过、NEVER 当成已结清。本轮结果看 sys_job_log 与服务端 scanned/released/unsettled/unknown/skipped/failed；unknown 与 failed 会打 ERROR、下一轮重入，skipped 打 WARN 且不会自愈（加黑时没送 channelCode，要从加黑入口治）。与 105 只读盘点不同，本任务会真的改数据'
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'blacklistAutoReleaseQuartzTask.autoRelease()'
);
