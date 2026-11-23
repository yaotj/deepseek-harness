
-- 动态取号（MAX(job_id)+5）+ NOT EXISTS 幂等守卫：NEVER DELETE 写死 job_id，
-- 固定号 DELETE 会误删测试库占用同号的其它调度（IT-066，见 web-quartz-oneside-job-migration.sql）。
INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
SELECT (SELECT NVL(MAX(job_id),0)+5 FROM sys_job), '补站扣费周期查询更新', 'DEFAULT', 'gateTxnPayQuartzTask.retryRecentUnpaidDebits()', '0 0 9,15 * * ?', '3', '1', '0', 'admin', SYSDATE,
        'POST /internal/gate-txn-pay/debit/retry/recent：2026-11-20 起只捞卡在 INIT 的过闸单（落库超 minAgeSeconds=60 秒、排除离线码待重算态），逐笔重发扣费；不分渠道。RETRY/FAIL 归 220/255 按退避慢跑，两条任务 NEVER 抢同一批单，否则同一笔欠费双发扣款。业主裁决不设退避：刻意不写 DEBIT_RETRY_TIMES 与 DEBIT_NEXT_RETRY_TIME；原「近 10 分钟窗口」与 gate.debitRetry.recent.windowMinutes 键已删除。cron 2026-11-20 由 0 0/1 * * * ?（每分钟）改为 0 0 9,15 * * ?（每天 9/15 各一次），对齐旧 ITP。与220/255并行，NEVER合并。concurrent=1，web-admin MUST 单副本。2026-09-21新增，编号先后为135→265→235→345。详见ADR-D154 与 ADR-D176'
FROM dual
WHERE NOT EXISTS (
    SELECT 1 FROM sys_job WHERE invoke_target = 'gateTxnPayQuartzTask.retryRecentUnpaidDebits()'
);

COMMIT;
