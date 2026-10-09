
DELETE FROM sys_job WHERE job_id = 345;

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (345, '补站扣费周期查询更新', 'DEFAULT', 'gateTxnPayQuartzTask.retryRecentUnpaidDebits()', '0 0/1 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        'POST /internal/gate-txn-pay/debit/retry/recent：扫近10分钟内 DEBIT_STATUS IN (INIT,RETRY,FAIL) 且落库超60秒的过闸单逐笔重发扣费；不分渠道、多捞 INIT、排除离线码待重算态。业主裁决不设退避：刻意不写 DEBIT_RETRY_TIMES 与 DEBIT_NEXT_RETRY_TIME，防重扣只靠窗口上下界。与220/255并行，NEVER合并。concurrent=1，web-admin MUST 单副本。2026-09-21新增，编号先后为135→265→235→345。详见ADR-D154');

COMMIT;
