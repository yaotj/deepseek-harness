
DELETE FROM sys_job WHERE job_id IN (122, 123);

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (122, '退款回查补偿', 'DEFAULT', 'refundCompensateQuartzTask.compensateRefundQuery()', '0 0/10 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每10分钟触发 pay-sign-server POST /internal/payment/compensateRefundQuery：扫 PAY_REFUND_DETAIL 停在 PROCESSING 的退款单，出网调支付中心退款查询并收口，收口后重算原单退款汇总。间隔必须大于下游 staleMinutes=5 分钟，禁止单次调度内循环。停用会让退款单永久悬挂。单条结果看 REFUND_STATUS，不要看 submitted');

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (123, '退款汇总跨表对账', 'DEFAULT', 'refundCompensateQuartzTask.compensateRefundSummary()', '0 15 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每小时第15分触发 pay-sign-server POST /internal/payment/compensateRefundSummary：不出网，只重算 PAY_TXN_DETAIL 的 REFUND_AMOUNT/REFUND_STATUS。skipped 长期非0是预期（含明细已SUCCESS但原支付订单不存在的不可自愈记录，2026-09-15 实测7条，该数只增不减、是时点快照不是阈值），日志按 WARN 记录，需人工核对而非本轮失败');

COMMIT;
