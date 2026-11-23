
-- 动态取号（MAX(job_id)+5）+ NOT EXISTS 幂等守卫：NEVER DELETE 写死 job_id，
-- 固定号 DELETE 会误删测试库占用同号的其它调度（IT-066，见 web-quartz-oneside-job-migration.sql）。
INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT (SELECT NVL(MAX(job_id),0)+5 FROM sys_job), '行程扣费重试', 'DEFAULT', 'gateTxnPayQuartzTask.retryDefaultChannelDebits()', '0 0 1 * * ?', '3', '1', '0', 'admin', SYSDATE,
        '甲方需求5。POST /internal/gate-txn-pay/debit/retry/default：扫 GATE_TXN_PAY 的 DEBIT_STATUS IN (RETRY,FAIL) 且 ISSUE_CHANNEL_CODE 非 07（含为空的历史行），出口是 pay-sign 与支付中心。含终态 FAIL 是业主裁决。2026-09-20 按渠道拆成 220/255 两条，两类扣费出口不同，NEVER 合回一条。三道防重扣闸 gate.debitRetry.maxTimes=5 / lookbackDays=7 / backoffMinutes=720 两条任务共用；本条专属 default.enabled 与 default.batchSize=200。cron 2026-10-09 由 0 0/1 * * * ?（每小时）改为 0 0 1 * * ?（每天凌晨 1 点），避免夜间高频冲击支付中心。详见 ADR-D149'
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'gateTxnPayQuartzTask.retryDefaultChannelDebits()'
);

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT (SELECT NVL(MAX(job_id),0)+5 FROM sys_job), '支付宝出行重试扣费', 'DEFAULT', 'gateTxnPayQuartzTask.retryAlipayChannelDebits()', '0 0 1 * * ?', '3', '1', '0', 'admin', SYSDATE,
        '甲方需求18「支付宝出行重试扣费」。POST /internal/gate-txn-pay/debit/retry/alipay：扫 GATE_TXN_PAY 的 DEBIT_STATUS IN (RETRY,FAIL) 且 ISSUE_CHANNEL_CODE=07，走 PaySignInitiator 的支付宝分支打 alipay-pay-sign，不经过 pay-sign 与支付中心。与 220 同为每天凌晨 1 点是业主选择，两条各有独立 AtomicBoolean 与单批条数、互不阻塞。本条专属 alipay.enabled 与 alipay.batchSize=200；次数上限/回溯窗口/退避与 220 共用。2026-09-20 新增，2026-09-21 任务名由「行程扣费重试(支付宝出行)」改为「支付宝出行重试扣费」（触发目标与 cron 未变，NEVER 回退成旧名），cron 2026-10-09 由 0 0/1 * * * ?（每小时）改为 0 0 1 * * ?（每天凌晨 1 点），避免夜间高频冲击支付中心。详见 ADR-D149'
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'gateTxnPayQuartzTask.retryAlipayChannelDebits()'
);

COMMIT;
