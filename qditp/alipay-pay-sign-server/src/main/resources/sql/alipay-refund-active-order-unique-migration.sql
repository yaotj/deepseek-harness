-- 在途退款按原订单号唯一：ALIPAY_REFUND_LOG 部分唯一索引（2026-11-22）
--
-- 背景：退款幂等只靠「同一 ORDER_NO 存在 PROCESSING 即拒绝」的状态短路（先查后插、无库层互斥），
--       而现有 UK_ARL_REFUND_ORDER_NO 建在每次随机生成的 REFUND_ORDER_NO 上——两条并发请求
--       各生成不同单号、都能插入、都出网发起真实退款 ⇒ 重复退款/资损。
--
-- 方案：虚拟列两步法（ADR-D135，MCP 校验器拒裸 CASE 索引，但接受 ALTER TABLE 里的 CASE）：
--       ① 加虚拟列 UK_ACTIVE_ORDER_NO = CASE WHEN 在途 THEN ORDER_NO ELSE NULL END
--       ② 对虚拟列建朴素唯一索引。Oracle 唯一索引不约束 NULL，故终态行不受约束，
--          同一订单退款成功后仍可再次发起（合法的部分退款），只在「同时存在两笔在途」时拦第二笔。
--
-- 在途口径：本表插入时即 REFUND_STATUS='PROCESSING'（无 INIT 落库态），故只覆盖 PROCESSING + DELETE_FLAG='0'。
--           新表 ALIPAY_REFUND_TXN_DETAIL 插入是 INIT、随后 markRequesting 才转 PROCESSING，
--           其脚本 alipay-refund-txn-active-order-unique-migration.sql 覆盖 INIT/PROCESSING/RETRY 三态，两者刻意不同。
--
-- 前置条件（MUST 先执行第 0 步，冲突行数必须为 0 才能继续）：
--   本机 172.20.222.3 不可达，本脚本未在有数据的库上实测。存量若已有同单多笔在途，
--   CREATE UNIQUE INDEX 会报 ORA-01452；此时 NEVER 删行、NEVER 改状态掩盖，
--   MUST 人工到支付中心核对那几笔真实退款结果后再处置。

-- ============ 第 0 步：存量冲突检查（执行前必做，期望 0 行） ============
-- SELECT ORDER_NO, COUNT(*) AS ACTIVE_CNT
-- FROM QDITP.ALIPAY_REFUND_LOG
-- WHERE REFUND_STATUS = 'PROCESSING' AND DELETE_FLAG = '0'
-- GROUP BY ORDER_NO
-- HAVING COUNT(*) > 1;

-- ============ 第 1 步：加虚拟列 ============
ALTER TABLE QDITP.ALIPAY_REFUND_LOG
  ADD (UK_ACTIVE_ORDER_NO VARCHAR2(64 CHAR)
       GENERATED ALWAYS AS (
         CASE WHEN REFUND_STATUS = 'PROCESSING' AND DELETE_FLAG = '0'
              THEN ORDER_NO ELSE NULL END
       ) VIRTUAL);

COMMENT ON COLUMN QDITP.ALIPAY_REFUND_LOG.UK_ACTIVE_ORDER_NO IS
  '在途退款唯一键：PROCESSING 且未删除时取 ORDER_NO，终态为 NULL 不参与唯一约束；供 UK_ARL_ACTIVE_ORDER 使用';

-- ============ 第 2 步：建朴素唯一索引 ============
CREATE UNIQUE INDEX QDITP.UK_ARL_ACTIVE_ORDER
  ON QDITP.ALIPAY_REFUND_LOG (UK_ACTIVE_ORDER_NO);

-- ============ 第 3 步：回查 ============
-- SELECT INDEX_NAME, UNIQUENESS, STATUS FROM USER_INDEXES WHERE INDEX_NAME = 'UK_ARL_ACTIVE_ORDER';
-- SELECT COLUMN_NAME FROM USER_IND_COLUMNS WHERE INDEX_NAME = 'UK_ARL_ACTIVE_ORDER';
-- SELECT COLUMN_NAME, DATA_TYPE, VIRTUAL_COLUMN FROM USER_TAB_COLS
--  WHERE TABLE_NAME = 'ALIPAY_REFUND_LOG' AND COLUMN_NAME = 'UK_ACTIVE_ORDER_NO';
-- 期望：UNIQUENESS=UNIQUE / STATUS=VALID；索引单列 UK_ACTIVE_ORDER_NO；VIRTUAL_COLUMN=YES。

-- ============ 还原 SQL（仅在需要回退时执行） ============
-- DROP INDEX QDITP.UK_ARL_ACTIVE_ORDER;
-- ALTER TABLE QDITP.ALIPAY_REFUND_LOG DROP COLUMN UK_ACTIVE_ORDER_NO;
-- COMMIT;

-- ============ 代码侧依赖 ============
-- RefundLogRepository.openRefund 的 catch 分支：命中唯一索引后回查 selectProcessingByOrderNo
-- 拿在途单号，记 ERROR 日志并抛业务异常，**绝不出网**。DDL 未执行前该 catch 只挡同一随机单号重复，
-- 挡不住同单并发；DDL 执行后才真正闭合。
