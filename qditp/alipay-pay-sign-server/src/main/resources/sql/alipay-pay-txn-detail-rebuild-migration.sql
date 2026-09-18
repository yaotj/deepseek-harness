-- ALIPAY_PAY_TXN_DETAIL 重新设计后的重建脚本（2026-09-18 最终版，18 列）。
--
-- 【四轮收窄的过程，留档防回退】
--   原始（2026-09-16 建成，从未接线）：52 列
--   第一轮 41 列：删主体已有的 CARD_TYPE / PAYMENT_VENDOR / PAY_CHANNEL_CODE / INDUSTRY_DETAIL、
--                 恒 'PAY' 的 PAY_TYPE、6 个请求参数列
--   第二轮 29 列：按「行程列表与详情统一先查 GATE_TXN_PAY 拿主体、再按 ORDER_NO 取各业务详情」，
--                 删 THIRD_USER_ID / CARD_ID / ENTRY_ID / EXIT_ID 与 8 个对端回传字段
--   第三轮 21 列：报文与请求参数留痕整组挪进 ALIPAY_PAY_CENTER_MSG_LOG
--   第四轮（本文件现版）**18 列**：删掉最后三个冗余列
--
-- 【第四轮删掉的 3 列，NEVER 加回】
--   DEBIT_REQUEST_RESULT VARCHAR2(16 CHAR)
--     它是 PAY_STATUS 的派生值，而对外契约那个 debitRequestResult（0/1）实际由主表
--     GATE_TXN_PAY.DEBIT_STATUS 映射。两个状态列并存只带来「MUST 同步回写、漏写就静默不一致」
--     这条本不必存在的护栏 —— pay-sign 侧 2026-08-26 正是漏写它导致「状态已 SUCCESS、扣款结果
--     还停在 PROCESSING」。删掉列，护栏一并消失。
--   RESPONSE_TIME TIMESTAMP(6)
--     存在价值只是算耗时，而 ALIPAY_PAY_CENTER_MSG_LOG.ELAPSED_MS 已逐次记录，更准。
--   FIRST_REQUEST_TIME TIMESTAMP(6)
--     与 CREATE_TIME 几乎恒等（落单后立即出网），看 CREATE_TIME 即可。
--
-- 【前三轮删掉的清单同样 NEVER 加回】见 alipay-pay-txn-schema.sql 头注释，其中最重要的一条是
-- **那 8 个「对端回传字段」在支付宝出行链路里没有入向来源**：支付结果回调 DTO
-- （model 的 AlipayTripPayNotifyReqDTO）只有 6 个业务字段 —— orderNo / channelVoucherId /
-- transAmount / transTime / transStatus / cardNo；requestPay 同步应答代码只取 channelOrderNo、
-- payQuery 只取 4 个键。**NEVER 因为 pay-sign 的 ReceivePayResultReqDTO 有那些字段就照抄过来。**
--
-- 【索引 3 条 + PK】历史上有过、已随列删除的：IDX_APTD_USER_DATE / IDX_APTD_CARD_DATE /
-- IDX_APTD_CARD_STATUS / IDX_APTD_ENTRY_ID / IDX_APTD_EXIT_ID。按用户 / 卡号 / 进出站维度检索
-- 一律去主表（`/ci/gateTxnPay/app/requestTransList` 已有这些过滤 + 分页），NEVER 在本表重建。
--
-- 【为什么每轮都是 DROP 重建而不是 ALTER】该表建成后**从未接线**（mapper 零调用方），每轮执行前都
-- 实测 SELECT COUNT(*) = 0，没有数据可丢。要回退到任一历史结构，取 SVN 上对应日期那版
-- alipay-pay-txn-schema.sql 整段重跑即可。
--
-- 【执行注意】
-- 1. 序列 SEQ_ALIPAY_PAY_TXN_DETAIL **不 DROP、不重建**；重复 CREATE SEQUENCE 会报对象已存在。
-- 2. 经 mcp_database_qd 执行时 **MUST 去掉下面那句的 PURGE**（校验器不认，实测
--    `Encountered: <K_PURGE>`）。sqlplus 下保留 PURGE 才能不进回收站。
-- 3. 逐条执行并逐条看结果：executeDdlBatch **遇错即停、且 DDL 隐式提交、失败前的语句不回滚**，
--    报文里的 appliedBeforeFailure 就是「已永久生效、需手工反做」的清单。

DROP TABLE ALIPAY_PAY_TXN_DETAIL PURGE;

CREATE TABLE ALIPAY_PAY_TXN_DETAIL (
    ID                   NUMBER(22) NOT NULL,

    ORDER_NO             VARCHAR2(128 CHAR) NOT NULL,
    TXN_DATE             VARCHAR2(8 CHAR) NOT NULL,

    PAY_STATUS           VARCHAR2(32 CHAR) DEFAULT 'INIT' NOT NULL,

    AMOUNT               NUMBER(22) DEFAULT 0 NOT NULL,

    REFUND_STATUS        VARCHAR2(32 CHAR) DEFAULT 'NONE' NOT NULL,
    REFUND_AMOUNT        NUMBER(22) DEFAULT 0 NOT NULL,
    LAST_REFUND_TIME     TIMESTAMP(6),

    PAY_CENTER_ORDER_NO  VARCHAR2(128 CHAR),
    CHANNEL_ORDER_NO     VARCHAR2(256 CHAR),

    REQUEST_SIGN_SEQ     VARCHAR2(256 CHAR),
    CHANNEL_AGREEMENT_NO VARCHAR2(256 CHAR),

    REQUEST_COUNT        NUMBER(22) DEFAULT 0 NOT NULL,
    LAST_REQUEST_TIME    TIMESTAMP(6),
    NEXT_REQUEST_TIME    TIMESTAMP(6),

    INVOICE              VARCHAR2(32 CHAR),

    CREATE_TIME          TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATE_TIME          TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_ALIPAY_PAY_TXN_DETAIL PRIMARY KEY (ID)
)
PARTITION BY RANGE (TXN_DATE) (
    PARTITION P202606 VALUES LESS THAN ('20260701'),
    PARTITION P202607 VALUES LESS THAN ('20260801'),
    PARTITION P202608 VALUES LESS THAN ('20260901'),
    PARTITION P202609 VALUES LESS THAN ('20261001'),
    PARTITION P202610 VALUES LESS THAN ('20261101'),
    PARTITION P202611 VALUES LESS THAN ('20261201'),
    PARTITION P202612 VALUES LESS THAN ('20270101'),
    PARTITION P_MAX   VALUES LESS THAN (MAXVALUE)
);

CREATE UNIQUE INDEX UK_APTD_ORDER
ON ALIPAY_PAY_TXN_DETAIL (ORDER_NO, TXN_DATE) LOCAL;

CREATE INDEX IDX_APTD_STATUS
ON ALIPAY_PAY_TXN_DETAIL (PAY_STATUS, TXN_DATE, NEXT_REQUEST_TIME) LOCAL;

CREATE INDEX IDX_APTD_CHANNEL_ORDER
ON ALIPAY_PAY_TXN_DETAIL (CHANNEL_ORDER_NO, TXN_DATE) LOCAL;

COMMENT ON TABLE  ALIPAY_PAY_TXN_DETAIL IS '支付宝出行（小程序）渠道支付交易明细表；订单主表是 GATE_TXN_PAY，本表按 ORDER_NO 一对一挂在其上，只装支付侧当前态。报文原文在 ALIPAY_PAY_CENTER_MSG_LOG，对端回调在 ALIPAY_PAY_CALLBACK_LOG';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.ORDER_NO IS '地铁侧订单号，等于 GATE_TXN_PAY.ORDER_NO，同时作为支付中心接口的 orderNo；本表唯一的对外查询键';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.TXN_DATE IS '订单日期 yyyyMMdd，月分区键 + 唯一键第二列；取 GATE_TXN_PAY.TXN_DATE，NEVER 用本地当天日期另算';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.PAY_STATUS IS '本笔在支付中心侧的状态：INIT初始化，PROCESSING处理中，SUCCESS成功，FAIL失败，RETRY待重试，CLOSED关闭。本表唯一的状态列，原 DEBIT_REQUEST_RESULT 已删除、NEVER 加回；订单整体是否扣费成功以 GATE_TXN_PAY.DEBIT_STATUS 为准';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.AMOUNT IS '我方送出的请求支付金额（分），恒有值；本表唯一的金额列，退款可退上限只取它。对端回传的金额在 ALIPAY_PAY_CALLBACK_LOG';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.REFUND_STATUS IS '退款状态：NONE未退款，PROCESSING退款中，PARTIAL部分退款，SUCCESS已全额退款，FAIL退款失败';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.REFUND_AMOUNT IS '已退款总金额（分），由 ALIPAY_REFUND_TXN_DETAIL 重算得出，NEVER 累加写入';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.PAY_CENTER_ORDER_NO IS '支付中心支付订单号，来自 requestPay 的同步应答；退款报文的原支付订单号取此列，缺它退款必失败';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.CHANNEL_ORDER_NO IS '渠道订单号，即支付宝交易号（旧表 TRADE_NO）；属当前态关键标识，故留在本表';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.REQUEST_SIGN_SEQ IS '我方签约流水号，取 ALIPAY_SIGN_INFO.AGREEMENT_CODE';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.CHANNEL_AGREEMENT_NO IS '渠道协议号，取 ALIPAY_SIGN_INFO.CHANNEL_AGREEMENT_CODE；与上一列不是同一个号，销卡通知与退款只认这个';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.REQUEST_COUNT IS '已发起支付请求次数；每次出网的请求与应答原文、应答码、耗时都在 ALIPAY_PAY_CENTER_MSG_LOG，一次一行';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.LAST_REQUEST_TIME IS '最近一次发起支付的时间；首次时刻看 CREATE_TIME，原 FIRST_REQUEST_TIME 已删除';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.NEXT_REQUEST_TIME IS '下次允许重试支付时间';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.INVOICE IS '发票状态；旧表 33 行实测全为 NULL、从未被写过，本表同样没有写入方，属占位列。接线前 MUST 先确认发票状态归谁维护，NEVER 假定它有值';
