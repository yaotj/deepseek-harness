/*
 * sys_job 新增两条退款补偿任务（对应 pay-sign-server 的两个内部端点）。
 *
 * 执行方式与生效条件（MUST 先读完再执行）：
 *   1. Quartz 用内存 JobStore，任务只在 web-admin 启动时由 @PostConstruct 从 sys_job 全量加载。
 *      因此「运行中直接 INSERT sys_job 不会生效」，本脚本执行完 MUST 重启 web-admin，
 *      或在后台「定时任务」界面对这两条任务各做一次修改保存，才会真正注册进调度器。
 *   2. web-admin MUST 单副本：多副本时每个副本各加载一份，同一时刻会重复触发下游补偿。
 *   3. misfire_policy=3 表示「不补跑」，web-admin 重启期间错过的调度不会追补。
 *   4. 判断任务有没有跑 MUST 查 SYS_JOB_LOG，QRTZ_* 表在内存 JobStore 下恒为 0 行。
 *
 * 编号依据：2026-09-15 实测 SELECT JOB_ID, JOB_NAME, INVOKE_TARGET, CRON_EXPRESSION FROM SYS_JOB
 * ORDER BY JOB_ID 共 14 行，最大 JOB_ID 是 121（不是 109），故新增取 122 / 123：
 *     1   系统默认（无参）        ryTask.ryNoParams                                        0/10 * * * * ?
 *     2   系统默认（有参）        ryTask.ryParams('ry')                                    0/15 * * * * ?
 *     3   系统默认（多参）        ryTask.ryMultipleParams(...)                             0/20 * * * * ?
 *     4   解约申请确认            terminationQuartzTask.confirmTermination(...)            0 0 4 * * ?
 *     5   支付宝出行销卡          alipayTerminationQuartzTask.cancelCard()                 0 0 2 * * ?
 *     6   签约结果通知补发        notifyCompensateQuartzTask.compensateSignNotify()        0 0/10 * * * ?
 *     7   解约结果通知补发        notifyCompensateQuartzTask.compensateTerminationNotify() 0 5/10 * * * ?
 *     105 黑名单可解除性盘点      blacklistReleaseInspectQuartzTask.inspect()              0 0 10,16 * * ?
 *     106 ACC参数文件同步         paraQuartzTask.scanFtpPara()                             0 2/10 * * * ?
 *     107 卡池维护                cardPoolQuartzTask.runMaintenance()                      0 0/5 * * * ?
 *     108 签约展示账号同步补偿    accountQuartzTask.compensatePhoneSignSync()              0 0/5 * * * ?
 *     109 日终对账                reconQuartzTask.runDailyBatch()                          0 30 2 * * ?
 *     120 离线码金额补偿          gateTxnPayQuartzTask.recoverOfflineFare()                0 0/1 * * * ?
 *     121 公交换乘推送            gateTxnPayQuartzTask.pushMetroTransfer()                 0 0/1 * * * ?
 *
 * 其余列取值照现有行抄：job_group='DEFAULT'、misfire_policy='3'、concurrent='1'（禁止并发）、
 * status='0'（正常）、create_by='admin'。
 *
 * 幂等：先 DELETE 这两个 JOB_ID 再 INSERT，重复执行不会撞主键。
 * JOB_ID 是 IDENTITY 列但可显式赋值，与 sys_job 现有 105~121 那批同一做法。
 *
 * job 123 remark 里那个「不可自愈记录条数」是**实测时点快照，不是恒定事实**，引用前 MUST 现查：
 *   SELECT COUNT(DISTINCT R.ORDER_NO) FROM PAY_REFUND_DETAIL R
 *    WHERE R.REFUND_STATUS = 'SUCCESS'
 *      AND NOT EXISTS (SELECT 1 FROM PAY_TXN_DETAIL P WHERE P.ORDER_NO = R.ORDER_NO);
 * 该值只会随「退款回查收口成功」而**增加**（收口把明细写成 SUCCESS，若原支付订单本就不存在即新增一条）。
 * 本脚本初版写「当前6条」，当天 2.0.88 修掉 refundQuery 字段名缺陷（ADR-D92）、收口
 * RF2026062516090566197559552 之后即变成 7 条，第 7 条是 ORDER_NO 280638294097559552。
 * 那次「改一行 remark 就过期一次」本身就是判据：**NEVER 把这个数字当成告警阈值或断言基线**。
 */

DELETE FROM sys_job WHERE job_id IN (122, 123);

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (122, '退款回查补偿', 'DEFAULT', 'refundCompensateQuartzTask.compensateRefundQuery()', '0 0/10 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每10分钟触发 pay-sign-server POST /internal/payment/compensateRefundQuery：扫 PAY_REFUND_DETAIL 停在 PROCESSING 的退款单，出网调支付中心退款查询并收口，收口后重算原单退款汇总。间隔必须大于下游 staleMinutes=5 分钟，禁止单次调度内循环。停用会让退款单永久悬挂。单条结果看 REFUND_STATUS，不要看 submitted');

INSERT INTO sys_job (job_id, job_name, job_group, invoke_target, cron_expression, misfire_policy, concurrent, status, create_by, create_time, remark)
VALUES (123, '退款汇总跨表对账', 'DEFAULT', 'refundCompensateQuartzTask.compensateRefundSummary()', '0 15 * * * ?', '3', '1', '0', 'admin', SYSDATE,
        '每小时第15分触发 pay-sign-server POST /internal/payment/compensateRefundSummary：不出网，只重算 PAY_TXN_DETAIL 的 REFUND_AMOUNT/REFUND_STATUS。skipped 长期非0是预期（含明细已SUCCESS但原支付订单不存在的不可自愈记录，2026-09-15 实测7条，该数只增不减、是时点快照不是阈值），日志按 WARN 记录，需人工核对而非本轮失败');

COMMIT;
