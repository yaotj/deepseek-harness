
DELETE FROM sys_job WHERE job_id = 340;

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (340, '支付宝退款回查补偿', 'DEFAULT', 'alipayRefundQueryQuartzTask.compensate()', '0 0/10 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '本任务占用 job_id 340：2026-09-21 随「200 以下全量重编号」由 137 改为 340（多日票批量退款(当日/月度) 同批改为 245 / 250）。改号历史：本脚本原先写 135、与那两条撞号(先执行的会被后执行的 DELETE 干掉)，2026-09-20 先改为 137。每10分钟触发 alipay-pay-sign-server POST /internal/alipay/refund/compensateQuery：扫 ALIPAY_REFUND_LOG 里 REFUND_STATUS=PROCESSING 且已过5分钟静默期、CREATE_TIME 在7天窗口内的明细，逐条调支付中心退款查询(两个键都送 refundOrderNo 与 merchantRefundNo)并按终态 CAS 收口，成功时重算 ALIPAY_PAY_LOG 退款汇总。该端点是这套补偿的唯一驱动源，停用等于退款申请里未拿到业务应答的那支永久停在 PROCESSING、只能人工核。cron 间隔 MUST 大于下游静默期5分钟。每轮条数由下游 alipay.refund-query.batch-size 决定，任务侧不循环；本轮结果看 sys_job_log 的 job_message');
