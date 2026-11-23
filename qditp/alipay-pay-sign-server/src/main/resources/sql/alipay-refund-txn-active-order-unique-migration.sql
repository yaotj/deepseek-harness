-- 在途退款按原订单号唯一：ALIPAY_REFUND_TXN_DETAIL 部分唯一索引（2026-11-22）
--
-- 背景：与旧表同因——幂等靠 count(PROCESSING) 先查后插无库层互斥，
--       UK_ARTD_REFUND_ORDER 建在随机生成的 (REFUND_ORDER_NO, TXN_DATE) 上，挡不住同单并发双退款。
--
-- 方案：虚拟列两步法（ADR-D135）。本表插入时状态是 INIT（AlipayTxnRefundService.openRefund），
--       随后 markRequesting 才转 PROCESSING；回查补偿还会写 RETRY 语义的退避。
--       因此虚拟列覆盖 INIT / PROCESSING / RETRY 三态——与 AlipayTxnRefundService.selectActiveRefundOrderNo
--       的在途判据逐字一致。终态 SUCCESS / FAIL / CLOSED 为 NULL，不约束，保留合法的部分退款。
--
-- 分区表注意：本表按 TXN_DATE RANGE 分区。该唯一索引 WITHOUT 分区键 ⇒ Oracle 建为 GLOBAL 索引，
--            这是有意为之：按 ORDER_NO 的唯一性必须跨分区成立（同订单两笔退款可能落在不同 TXN_DATE）。
--            NEVER 改成带 TXN_DATE 的 LOCAL 唯一索引，那会让跨分区同单在途漏拦。
--
-- 前置条件（MUST 先执行第 0 步，冲突行数必须为 0 才能继续）：
--   本机 172.20.222.3 不可达，本脚本未在有数据的库上实测。存量若已有同单多笔在途，
--   CREATE UNIQUE INDEX 会报 ORA-01452；NEVER 删行/改状态掩盖，MUST 人工核对支付中心真实退款结果。

-- ============ 第 0 步：存量冲突检查（执行前必做，期望 0 行） ============
-- SELECT ORDER_NO, COUNT(*) AS ACTIVE_CNT
-- FROM QDITP.ALIPAY_REFUND_TXN_DETAIL
-- WHERE REFUND_STATUS IN ('INIT', 'PROCESSING', 'RETRY')
-- GROUP BY ORDER_NO
-- HAVING COUNT(*) > 1;

-- ============ 第 1 步：加虚拟列 ============
ALTER TABLE QDITP.ALIPAY_REFUND_TXN_DETAIL
  ADD (UK_ACTIVE_ORDER VARCHAR2(128 CHAR)
       GENERATED ALWAYS AS (
         CASE WHEN REFUND_STATUS = 'INIT' OR REFUND_STATUS = 'PROCESSING'
                   OR REFUND_STATUS = 'RETRY'
              THEN ORDER_NO ELSE NULL END
       ) VIRTUAL);

COMMENT ON COLUMN QDITP.ALIPAY_REFUND_TXN_DETAIL.UK_ACTIVE_ORDER IS
  '在途退款唯一键：INIT/PROCESSING/RETRY 时取 ORDER_NO，终态为 NULL 不参与唯一约束；供 UK_ARTD_ACTIVE_ORDER 使用';

-- ============ 第 2 步：建 GLOBAL 唯一索引（刻意不带分区键，理由见文件头） ============
CREATE UNIQUE INDEX QDITP.UK_ARTD_ACTIVE_ORDER
  ON QDITP.ALIPAY_REFUND_TXN_DETAIL (UK_ACTIVE_ORDER);

-- ============ 第 3 步：回查 ============
-- SELECT INDEX_NAME, UNIQUENESS, STATUS, PARTITIONED FROM USER_INDEXES WHERE INDEX_NAME = 'UK_ARTD_ACTIVE_ORDER';
--   期望 UNIQUENESS=UNIQUE / STATUS=VALID / PARTITIONED=NO（GLOBAL）
-- SELECT COLUMN_NAME FROM USER_IND_COLUMNS WHERE INDEX_NAME = 'UK_ARTD_ACTIVE_ORDER';
--   期望单列 UK_ACTIVE_ORDER
-- SELECT COLUMN_NAME, DATA_TYPE, VIRTUAL_COLUMN FROM USER_TAB_COLS
--  WHERE TABLE_NAME = 'ALIPAY_REFUND_TXN_DETAIL' AND COLUMN_NAME = 'UK_ACTIVE_ORDER';
--   期望 VIRTUAL_COLUMN=YES

-- ============ 还原 SQL（仅在需要回退时执行） ============
-- DROP INDEX QDITP.UK_ARTD_ACTIVE_ORDER;
-- ALTER TABLE QDITP.ALIPAY_REFUND_TXN_DETAIL DROP COLUMN UK_ACTIVE_ORDER;
-- COMMIT;

-- ============ 代码侧依赖 ============
-- AlipayTxnRefundService.openRefund 的 catch 分支：命中唯一索引后调 selectActiveRefundOrderNo
-- 回查在途单号，记 ERROR 日志并抛业务异常，**绝不出网**。DDL 未执行前该 catch 只挡同一随机单号重复，
-- 挡不住同单并发；DDL 执行后才真正闭合。
