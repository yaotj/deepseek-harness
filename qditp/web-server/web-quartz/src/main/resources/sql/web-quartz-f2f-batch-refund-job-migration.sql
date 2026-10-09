
DELETE FROM sys_job WHERE job_id IN (200, 205, 210);

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (200, '单程票未取票批量退款', 'DEFAULT', 'f2fBatchRefundQuartzTask.refundSingleTicket()', '0 0 20 * * ?', '3', '1', '0', 'admin', SYSDATE,
        '甲方需求1，每天20点触发 face-pay-server POST /internal/f2f/batch-refund/single-ticket：扫 F2F_ORDER 里 ORDER_STATUS=PAID 且 BIZ_TYPE=01 且过了静默期的单，逐笔按 DAILY_BATCH 来源发起退款。静默期 f2f.batchRefund.silenceMinutes 默认60分钟、回溯 f2f.batchRefund.lookbackDays 默认7天、单批 f2f.batchRefund.limit 默认200。retCode=9998 表示上一轮仍在执行，属限流不是失败。旧表存量仍由 collect-pay-server 的 SingleTicketRefundTask 负责，两边互不覆盖');

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (205, 'TVM充值未到账批量退款', 'DEFAULT', 'f2fBatchRefundQuartzTask.refundTopup()', '0 0 20 * * ?', '3', '1', '0', 'admin', SYSDATE,
        '甲方需求2，每天20点触发 face-pay-server POST /internal/f2f/batch-refund/topup：口径同 job 200，只把 BIZ_TYPE 换成 02 充值。裁决时已明确不退 TOPUP_SUSPECT 状态的单（那是写卡结果未知、要人工核），本任务只扫 PAID');

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (210, '非现金收款批量退款', 'DEFAULT', 'f2fBatchRefundQuartzTask.refundNoCash()', '0 0 9,15,21 * * ?', '3', '1', '0', 'admin', SYSDATE,
        '甲方需求3，每天9、15、21点三次触发 face-pay-server POST /internal/f2f/batch-refund/no-cash：口径同 job 200，BIZ_TYPE=04 非现金收款。甲方原文就是一天三次，NEVER 合并成一次');

COMMIT;
