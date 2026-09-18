-- 新建出网报文留痕表 ALIPAY_PAY_CENTER_MSG_LOG，并把两张明细表里的报文 CLOB 摘掉（2026-09-18）。
--
-- 【为什么单独一张表】原本 requestPay / payQuery / requestRefund / refundQuery 的请求与应答原文是
-- 写在明细表的 REQUEST_BODY / RESPONSE_BODY 两个 CLOB 上，且是 **UPDATE 覆盖式**写入 ——
-- **重试三次只剩最后一次报文**，前两次的证据永久丢失，而这类列存在的唯一意义就是留证据。
-- 改成「一次调用一行」后：① 重试链路每一步都可举证；② 一单一行的当前态表不再带 CLOB；
-- ③ 报文可按账期分区归档 / 清理，不牵动业务主表。
--
-- 【四张表的分工，NEVER 混】
--   GATE_TXN_PAY                订单主表，扣费成没成的唯一权威（不在本模块）
--   ALIPAY_PAY_TXN_DETAIL       支付侧当前态，一单一行，有唯一索引
--   ALIPAY_PAY_CENTER_MSG_LOG   我方 -> 支付中心，一次调用一行，无唯一索引（本文件新建）
--   ALIPAY_PAY_CALLBACK_LOG     支付中心 -> 我方，一次推送一行，无唯一索引
--
-- 【本表刻意没有唯一索引】同一个 ORDER_NO 会有多行（首次 + 每次重试 + 每次回查）。
-- 判断「这一单当前什么状态」MUST 查 ALIPAY_PAY_TXN_DETAIL，**NEVER 在本表上按时间取最后一行
-- 推断状态** —— 最后一行可能是一次超时的 payQuery，与订单实际状态无关。
-- 【NEVER 让业务逻辑读本表的列】它只服务排查与对账举证；任何被业务判断依赖的值 MUST 落到
-- 当前态表的列上，否则就会出现「靠翻流水拼状态」这种无法维护的读法。
--
-- 【执行注意】
-- 1. 建表语句含 PARTITION BY RANGE，经 mcp_database_qd 执行没问题（jsqlparser 5.4 起支持）。
-- 2. 下面那条 DROP COLUMN 只针对 ALIPAY_REFUND_TXN_DETAIL —— 支付明细表已由
--    alipay-pay-txn-detail-rebuild-migration.sql 整表重建成 21 列、本来就不含这两列。
--    执行前 MUST 先确认 ALIPAY_REFUND_TXN_DETAIL 为 0 行（该表建成后从未接线）。
-- 3. 执行后 MUST 回查 USER_TABLES / USER_TAB_COLS / USER_INDEXES / USER_TAB_PARTITIONS，
--    并把结果写进 ADR —— 只写了 DDL 不算完成。

CREATE TABLE ALIPAY_PAY_CENTER_MSG_LOG (
    ID              NUMBER(22) NOT NULL,

    ORDER_NO        VARCHAR2(128 CHAR) NOT NULL,
    TXN_DATE        VARCHAR2(8 CHAR) NOT NULL,
    API_NAME        VARCHAR2(32 CHAR) NOT NULL,
    REQUEST_NO      VARCHAR2(128 CHAR),

    SCENE           VARCHAR2(32 CHAR),
    INDUSTRY_TYPE   VARCHAR2(32 CHAR),
    IP_ADDRESS      VARCHAR2(64 CHAR),

    RET_CODE        VARCHAR2(32 CHAR),
    RET_MSG         VARCHAR2(512 CHAR),
    ELAPSED_MS      NUMBER(22),

    REQUEST_BODY    CLOB,
    RESPONSE_BODY   CLOB,
    REMARK          VARCHAR2(512 CHAR),

    CREATE_TIME     TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_ALIPAY_PAY_CENTER_MSG_LOG PRIMARY KEY (ID)
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

CREATE SEQUENCE SEQ_ALIPAY_PAY_CENTER_MSG_LOG
    START WITH 1
    INCREMENT BY 1
    CACHE 1000
    NOCYCLE;

CREATE INDEX IDX_APCML_ORDER
ON ALIPAY_PAY_CENTER_MSG_LOG (ORDER_NO, TXN_DATE) LOCAL;

CREATE INDEX IDX_APCML_API_DATE
ON ALIPAY_PAY_CENTER_MSG_LOG (API_NAME, TXN_DATE) LOCAL;

COMMENT ON TABLE  ALIPAY_PAY_CENTER_MSG_LOG IS '支付宝出行渠道我方出网报文留痕表；一次调用一行，追加型流水，无唯一索引，NEVER 拿它推断订单当前状态';
COMMENT ON COLUMN ALIPAY_PAY_CENTER_MSG_LOG.ORDER_NO IS '地铁侧订单号，等于 GATE_TXN_PAY.ORDER_NO 与 ALIPAY_PAY_TXN_DETAIL.ORDER_NO';
COMMENT ON COLUMN ALIPAY_PAY_CENTER_MSG_LOG.TXN_DATE IS '订单日期 yyyyMMdd，月分区键；取自主表，NEVER 用本地当天日期另算';
COMMENT ON COLUMN ALIPAY_PAY_CENTER_MSG_LOG.API_NAME IS '支付中心接口名：requestPay 扣款，payQuery 支付结果查询，requestRefund 退款申请，refundQuery 退款结果查询';
COMMENT ON COLUMN ALIPAY_PAY_CENTER_MSG_LOG.REQUEST_NO IS '本次请求的从键：退款类接口填退款单号，支付类接口留空';
COMMENT ON COLUMN ALIPAY_PAY_CENTER_MSG_LOG.SCENE IS '支付场景，我方送出的请求参数原值';
COMMENT ON COLUMN ALIPAY_PAY_CENTER_MSG_LOG.INDUSTRY_TYPE IS '行业类型：1地铁，2公交，3打车，4购物';
COMMENT ON COLUMN ALIPAY_PAY_CENTER_MSG_LOG.IP_ADDRESS IS '发起支付的用户 IP，审计用';
COMMENT ON COLUMN ALIPAY_PAY_CENTER_MSG_LOG.RET_CODE IS '支付中心本次同步应答的业务码';
COMMENT ON COLUMN ALIPAY_PAY_CENTER_MSG_LOG.RET_MSG IS '支付中心本次同步应答文案；NEVER 把它当 debitRequestResult 直接对外返回';
COMMENT ON COLUMN ALIPAY_PAY_CENTER_MSG_LOG.ELAPSED_MS IS '本次调用耗时毫秒，用于定位支付中心慢响应';
COMMENT ON COLUMN ALIPAY_PAY_CENTER_MSG_LOG.REQUEST_BODY IS '我方送出的请求报文原文，MUST 写；被删掉的 subject / body / authCode / notifyUrl / returnUrl / orderTimeOut 都在这里';
COMMENT ON COLUMN ALIPAY_PAY_CENTER_MSG_LOG.RESPONSE_BODY IS '支付中心同步应答报文原文；异步回调的原文在 ALIPAY_PAY_CALLBACK_LOG.RAW_BODY，两者不是一回事';

ALTER TABLE ALIPAY_REFUND_TXN_DETAIL DROP (REQUEST_BODY, RESPONSE_BODY);
