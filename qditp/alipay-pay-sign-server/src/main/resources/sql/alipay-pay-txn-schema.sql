-- 支付宝出行（小程序）渠道的三张表：支付交易明细 / 退款明细 / 出网报文留痕。
--
-- 【本文件的定位】只服务「新建这三张表」，不是迁移脚本。对已存在的库，每次结构变动 MUST 另出
-- *-migration.sql：ALIPAY_PAY_TXN_DETAIL 的重建在 alipay-pay-txn-detail-rebuild-migration.sql，
-- ALIPAY_PAY_CENTER_MSG_LOG 的新建在 alipay-pay-center-msg-log-migration.sql。
--
-- ============================================================================
-- 三张表的分工（改任何一张之前 MUST 先读懂这一段）
-- ============================================================================
--
--   GATE_TXN_PAY                  订单主表（不在本模块）。ISSUE_CHANNEL_CODE='07' 那些行就是
--                                 支付宝出行的订单，由 fep-dev-server 的 GateTransactionHandler
--                                 分流后落单。「订单是否成立、扣费到底成没成」权威只在
--                                 GATE_TXN_PAY.DEBIT_STATUS。
--
--   ALIPAY_PAY_TXN_DETAIL         支付侧**当前态**，一单一行，有唯一索引，19 列、无 CLOB。
--                                 只回答「这笔在支付中心侧现在是什么状态、已退多少」。
--                                 列数以本文件的 CREATE TABLE 为准：2026-09-20 按 AFCITPDB 实测复核，
--                                 库内就是 19 列、与本文件逐列一致。**本处与下面「删除清单」此前都写
--                                 「21 列」，那是错的**（alipay-pay-txn-detail-rebuild-migration.sql:9
--                                 与 alipay-pay-center-msg-log-migration.sql:24 里那两处「21 列」同样
--                                 不准，属当时的意图记述，NEVER 拿它们当列数依据）。
--
--   ALIPAY_PAY_CENTER_MSG_LOG     **我方出网**报文流水，一次调用一行，无唯一索引。
--                                 requestPay / payQuery / requestRefund / refundQuery 四个接口
--                                 的请求与应答原文都在这里。
--
--   ALIPAY_PAY_CALLBACK_LOG       **对方入向**回调流水，一次推送一行，无唯一索引（本文件不含它，
--                                 DDL 在 alipay-pay-sign-callback-log-migration.sql）。
--
-- 「当前态」与「流水」分开的收益不是洁癖：出网报文原来是 UPDATE 覆盖式写在明细表的
-- RESPONSE_BODY 上，**重试三次只剩最后一次的报文**，前两次的证据永久丢失；拆成一次一行后，
-- 每次调用都留得下来，且明细表不再带 CLOB。
--
-- ============================================================================
-- ALIPAY_PAY_TXN_DETAIL 的边界：主体已有的、无入向来源的，一概不放
-- ============================================================================
--
-- 【查询编排】行程列表与详情统一是「① 先查 GATE_TXN_PAY 拿主体信息（分页 / 过滤 / 排序全在主表侧，
-- 走 POST /ci/gateTxnPay/app/requestTransList + /countTransList，单笔走 queryByOrderNo）
-- → ② 按拿到的 ORDER_NO 批量来本表取支付侧详情」。本表因此只有 selectByOrderNo /
-- selectByOrderNos 两条读语句，**没有任何分页查询，也没有按用户 / 卡号 / 进出站的过滤能力**，
-- NEVER 加回 —— 那些维度全在主表上。
--
-- 【删除清单，NEVER 加回】从最初 52 列一路收窄到 19 列，分三类：
--
--   A 主体已有（去 GATE_TXN_PAY 拿）：
--     THIRD_USER_ID / CARD_ID / CARD_TYPE / PAYMENT_VENDOR
--     PAY_CHANNEL_CODE（出行恒 '07'，等于主表 ISSUE_CHANNEL_CODE）
--     INDUSTRY_DETAIL（主表那列是权威）
--     ENTRY_ID / EXIT_ID（值就在主表 INDUSTRY_DETAIL 的 JSON 里）
--
--   B **无入向来源**（2026-09-18 逐字段核对代码与供方文档后确认，这批是重点）：
--     CASH_AMOUNT / COUPON_AMOUNT / DISCOUNT_FEE / DISCOUNT_INFO / PAY_USER_ID / MERCHANT_ORDER_NO
--     判据：支付宝出行的支付结果回调 DTO（model 的 AlipayTripPayNotifyReqDTO）**只有 6 个字段**
--     —— orderNo / channelVoucherId / transAmount / transTime / transStatus / cardNo，这批字段一个
--     都不在里面；requestPay 的同步应答代码只取 channelOrderNo（且不落库）；payQuery 的同步应答
--     代码只取 4 个键。也就是说这批列**建了也永远是 NULL**。
--     ⚠️ 这与 pay-sign 不同：pay-sign 的 ReceivePayResultReqDTO 有 12 个字段（含 cashAmount /
--     couponAmount / payUserId / discountInfo），那是支付中心的另一条回调契约。
--     **NEVER 因为 pay-sign 有就照抄过来** —— 支付宝出行这条链路的入向契约窄得多。
--     哪天支付中心把回调契约扩了，MUST 按那时的**真实报文**加列，而不是照文档预置。
--     TOTAL_AMOUNT 是这批里唯一有过取值点的（payQuery 应答，PaymentQueryService 取 "totalAmount"
--     这个键），已挪进 ALIPAY_PAY_CALLBACK_LOG，本表不留。
--
--   C 报文与请求参数留痕（挪进 ALIPAY_PAY_CENTER_MSG_LOG）：
--     REQUEST_BODY / RESPONSE_BODY / RESULT_CODE / RESULT_MSG
--     SCENE / INDUSTRY_TYPE / IP_ADDRESS / REMARK
--     PAY_TYPE（退款有独立表，本表恒 'PAY'）
--     SUBJECT / BODY / AUTH_CODE / NOTIFY_URL / RETURN_URL / ORDER_TIME_OUT
--
-- 【本表 NEVER 承载非过闸场景】支付宝小程序的口径就是出行渠道（免密扣款 + 碰一下），每一行都
-- MUST 有对应的 GATE_TXN_PAY 行。出现「小程序内主动购票 / 充值」这类主表里没有订单行的支付时，
-- MUST 另议归属。
--
-- 【为什么不继续用 ALIPAY_PAY_LOG】① 旧表 31 列全 VARCHAR2，金额与时间都是字符串，查询侧只能
-- TO_NUMBER / TO_DATE(TRANS_TIME,...)，走不到索引且存量格式不统一（既有 '2026-07-28 16:59:55'
-- 也有毫秒时间戳）；② 旧表没有 CREATE_TIME / UPDATE_TIME，排序只能落到 UUID 主键 PAY_SEQ；
-- ③ 旧表没有任何唯一索引，项目主幂等写法无处落地；④ 没有 REQUEST_COUNT / NEXT_REQUEST_TIME，
-- 扫表补偿无列可依。
--
-- 【形态对齐 pay-sign 的 pay-txn-schema.sql】金额一律 NUMBER 且单位为分，时间一律 TIMESTAMP，
-- TXN_DATE 为 yyyyMMdd 字符串并作月分区键，主键取序列，业务唯一键是 (ORDER_NO, TXN_DATE) 的
-- LOCAL 唯一索引（Oracle LOCAL 唯一索引必须含分区键）。状态词表与 pay-sign 逐字一致，
-- NEVER 换成 ALIPAY_*_LOG 旧表那套。
--
-- 【最早分区 NEVER 改成 P202609】三张表都从 P202606 起共 8 个分区，与库内 PAY_TXN_DETAIL /
-- PAY_REFUND_DETAIL 实测布局逐个对齐。存量 ALIPAY_PAY_LOG 的 TRANS_TIME 最大值是 2026-07-28，
-- 若后续裁决迁移存量，TXN_DATE=202607 的行会全部挤进九月分区，而建表时看不出任何异常。

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

    TRANS_TIME           VARCHAR2(32 CHAR),

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

CREATE SEQUENCE SEQ_ALIPAY_PAY_TXN_DETAIL
    START WITH 1
    INCREMENT BY 1
    CACHE 1000
    NOCYCLE;

-- 只有 4 条索引。按用户 / 卡号 / 进出站维度检索一律去主表，NEVER 在本表重建那类索引
-- （历史上有过 IDX_APTD_USER_DATE / IDX_APTD_CARD_DATE / IDX_APTD_CARD_STATUS /
--   IDX_APTD_ENTRY_ID / IDX_APTD_EXIT_ID，已随对应列一起删除）。

-- 业务唯一键，也是幂等地基：并发 requestPay 时两条请求可能都看不到已存在的行，
-- 第二条 INSERT 撞索引即被 catch 成「重推」（沿 getCause() 链判完整性冲突）。
CREATE UNIQUE INDEX UK_APTD_ORDER
ON ALIPAY_PAY_TXN_DETAIL (ORDER_NO, TXN_DATE) LOCAL;

-- 扫表补偿：按状态 + 交易日 + 下次重试时刻取待重试单。
CREATE INDEX IDX_APTD_STATUS
ON ALIPAY_PAY_TXN_DETAIL (PAY_STATUS, TXN_DATE, NEXT_REQUEST_TIME) LOCAL;

-- 拿支付宝交易号反查我方订单（客服与对账排查用，不是业务主路径）。
CREATE INDEX IDX_APTD_CHANNEL_ORDER
ON ALIPAY_PAY_TXN_DETAIL (CHANNEL_ORDER_NO, TXN_DATE) LOCAL;

COMMENT ON TABLE  ALIPAY_PAY_TXN_DETAIL IS '支付宝出行（小程序）渠道支付交易明细表；订单主表是 GATE_TXN_PAY，本表按 ORDER_NO 一对一挂在其上，只装支付侧当前态';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.ORDER_NO IS '地铁侧订单号，等于 GATE_TXN_PAY.ORDER_NO，同时作为支付中心接口的 orderNo；本表唯一的对外查询键';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.TXN_DATE IS '订单日期 yyyyMMdd，月分区键 + 唯一键第二列；取 GATE_TXN_PAY.TXN_DATE，NEVER 用本地当天日期另算';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.PAY_STATUS IS '本笔在支付中心侧的状态：INIT初始化，PROCESSING处理中，SUCCESS成功，FAIL失败，RETRY待重试，CLOSED关闭；订单整体是否扣费成功以 GATE_TXN_PAY.DEBIT_STATUS 为准';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.AMOUNT IS '我方送出的请求支付金额（分），恒有值；本表唯一的金额列，退款可退上限只取它。对端回传的金额在 ALIPAY_PAY_CALLBACK_LOG';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.REFUND_STATUS IS '退款状态：NONE未退款，PROCESSING退款中，PARTIAL部分退款，SUCCESS已全额退款，FAIL退款失败';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.REFUND_AMOUNT IS '已退款总金额（分），由 ALIPAY_REFUND_TXN_DETAIL 重算得出，NEVER 累加写入';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.PAY_CENTER_ORDER_NO IS '支付中心支付订单号，来自 requestPay 的同步应答；退款报文的「原支付订单号」取此列，缺它退款必失败';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.CHANNEL_ORDER_NO IS '渠道订单号，即支付宝交易号（旧表 TRADE_NO）；属当前态关键标识，故留在本表';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.TRANS_TIME IS '支付时刻，统一存14位yyyyMMddHHmmss（2026-09-20起，归一在PayTxnCallbackWriter.normalizeTransTime做，NEVER回退成原文直存）；报文原文留在ALIPAY_PAY_CALLBACK_LOG的TRANS_TIME与RAW_BODY；列仍是VARCHAR2因归一失败时原样入库，NEVER建成DATE也NEVER用TO_DATE查询；支付宝出行记录应答的payOrderNoDate取此列；只由支付回调写入且套NVL';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.REQUEST_SIGN_SEQ IS '我方签约流水号，取 ALIPAY_SIGN_INFO.AGREEMENT_CODE';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.CHANNEL_AGREEMENT_NO IS '渠道协议号，取 ALIPAY_SIGN_INFO.CHANNEL_AGREEMENT_CODE；与上一列不是同一个号，销卡通知与退款只认这个';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.REQUEST_COUNT IS '已发起支付请求次数；每次出网的请求与应答原文在 ALIPAY_PAY_CENTER_MSG_LOG，一次一行';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.NEXT_REQUEST_TIME IS '下次允许重试支付时间';
COMMENT ON COLUMN ALIPAY_PAY_TXN_DETAIL.INVOICE IS '发票状态；旧表 33 行实测全为 NULL、从未被写过，本表同样没有写入方，属占位列。接线前 MUST 先确认发票状态归谁维护，NEVER 假定它有值';

-- ============================================================================
-- 退款明细表（18 列，与 AFCITPDB 实测逐列一致）
-- ============================================================================
-- 报文原文同样不在本表：退款的 requestRefund / refundQuery 请求与应答落
-- ALIPAY_PAY_CENTER_MSG_LOG（API_NAME 区分，REQUEST_NO 填退款单号）。
--
-- 【删除清单，NEVER 加回】以下 10 列在本表 DDL 里从来没有过，而 2026-09-20 之前
-- entity/AlipayRefundTxnDetail 与 mapper/AlipayRefundTxnDetailMapper.xml 却按 28 列在写 ——
-- insert / updateRequestResult / 三条 select 一旦被调用就是 ORA-00904。当时没炸只是因为
-- 这张表零业务调用方（退款链路仍走旧表 ALIPAY_REFUND_LOG），属「代码在用、库里没有」的潜伏形态。
-- 同批已把实体与 mapper 收窄回 18 列：
--   A 主体已有（按 ORDER_NO 去 GATE_TXN_PAY 回查）：THIRD_USER_ID / CARD_ID / CARD_ISSUE_CODE
--   B 报文与应答码留痕（归 ALIPAY_PAY_CENTER_MSG_LOG，一次调用一行）：
--     RET_CODE / RET_MSG / PAY_CENTER_CODE / PAY_CENTER_MSG / IP_ADDRESS / REQUEST_BODY / RESPONSE_BODY
-- 注意 B 组与 pay-sign-server 的 PAY_REFUND_DETAIL 不同：那张表确实有这几列，
-- **NEVER 因为两张表「形态对齐」就照它补列**。
-- 另注：支付中心应答码在本域落 ALIPAY_PAY_CENTER_MSG_LOG.RET_CODE（实测取值即对端的
-- SUCCESS / 20000 之类），本域不再分「我方码 / 对端码」两列。

CREATE TABLE ALIPAY_REFUND_TXN_DETAIL (
    ID                  NUMBER(22) NOT NULL,

    REFUND_ORDER_NO     VARCHAR2(128 CHAR) NOT NULL,
    ORDER_NO            VARCHAR2(128 CHAR) NOT NULL,
    REFUND_STATUS       VARCHAR2(32 CHAR) DEFAULT 'INIT' NOT NULL,

    REFUND_AMOUNT       NUMBER(22) NOT NULL,
    REFUND_REASON       VARCHAR2(512 CHAR),

    MERCHANT_REFUND_NO  VARCHAR2(128 CHAR),
    REFUND_NO           VARCHAR2(128 CHAR),
    CHANNEL_REFUND_NO   VARCHAR2(128 CHAR),

    REQUEST_COUNT       NUMBER(22) DEFAULT 0 NOT NULL,
    NEXT_REQUEST_TIME   TIMESTAMP(6),
    LAST_REQUEST_TIME   TIMESTAMP(6),

    REFUND_TIME         VARCHAR2(28 CHAR),
    TXN_DATE            VARCHAR2(8 CHAR) NOT NULL,

    OPERATOR            VARCHAR2(64 CHAR),
    REMARK              VARCHAR2(512 CHAR),

    CREATE_TIME         TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    UPDATE_TIME         TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,

    CONSTRAINT PK_ALIPAY_REFUND_TXN_DETAIL PRIMARY KEY (ID)
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

CREATE SEQUENCE SEQ_ALIPAY_REFUND_TXN_DETAIL
    START WITH 1
    INCREMENT BY 1
    CACHE 1000
    NOCYCLE;

CREATE UNIQUE INDEX UK_ARTD_REFUND_ORDER
ON ALIPAY_REFUND_TXN_DETAIL (REFUND_ORDER_NO, TXN_DATE) LOCAL;

-- updateRefundSummary 的相关子查询按 (ORDER_NO, REFUND_STATUS) 求和，两列都进索引。
CREATE INDEX IDX_ARTD_ORDER_STATUS
ON ALIPAY_REFUND_TXN_DETAIL (ORDER_NO, REFUND_STATUS) LOCAL;

CREATE INDEX IDX_ARTD_STATUS
ON ALIPAY_REFUND_TXN_DETAIL (REFUND_STATUS, TXN_DATE, NEXT_REQUEST_TIME) LOCAL;

COMMENT ON TABLE  ALIPAY_REFUND_TXN_DETAIL IS '支付宝出行渠道退款明细表；报文原文在 ALIPAY_PAY_CENTER_MSG_LOG';
COMMENT ON COLUMN ALIPAY_REFUND_TXN_DETAIL.REFUND_ORDER_NO IS '内部退款单号，由本模块生成';
COMMENT ON COLUMN ALIPAY_REFUND_TXN_DETAIL.ORDER_NO IS '原支付订单号，对应 ALIPAY_PAY_TXN_DETAIL.ORDER_NO';
COMMENT ON COLUMN ALIPAY_REFUND_TXN_DETAIL.REFUND_STATUS IS '退款状态：INIT初始化，PROCESSING处理中，SUCCESS成功，FAIL失败，RETRY待重试，CLOSED关闭；拿不到业务应答时 MUST 保持 PROCESSING，NEVER 置 FAIL';
COMMENT ON COLUMN ALIPAY_REFUND_TXN_DETAIL.REFUND_AMOUNT IS '退款金额，单位分';
COMMENT ON COLUMN ALIPAY_REFUND_TXN_DETAIL.MERCHANT_REFUND_NO IS '商户退款流水号；支付中心退款查询实测只认这个键，NEVER 只送 refundOrderNo';
COMMENT ON COLUMN ALIPAY_REFUND_TXN_DETAIL.TXN_DATE IS '退款发起日期，格式YYYYMMDD，用于月分区与唯一键；与原支付单的 TXN_DATE 各自独立，NEVER 复用原单日期';
COMMENT ON COLUMN ALIPAY_REFUND_TXN_DETAIL.REMARK IS '人工备注与收口说明；支付中心应答码不落本表，去 ALIPAY_PAY_CENTER_MSG_LOG 的 RET_CODE / RET_MSG 看';

-- ============================================================================
-- 出网报文留痕表（我方 -> 支付中心，一次调用一行）
-- ============================================================================
-- 【为什么单独一张】① 两个 CLOB 留在当前态表里会让一单一行的主表变重；② 更要紧的是原来
-- RESPONSE_BODY 是 UPDATE 覆盖式写入，**重试三次只剩最后一次报文**，前两次的证据永久丢失 ——
-- 而这类表存在的唯一意义就是留证据。改成一次调用一行后，重试链路的每一步都可举证。
-- 【刻意没有唯一索引】它是追加型流水，同一个 ORDER_NO 会有多行（首次 + 每次重试 + 每次回查）。
-- 判断「这一单当前什么状态」MUST 查 ALIPAY_PAY_TXN_DETAIL，NEVER 在本表上按时间取最后一行推断。
-- 【NEVER 在本表放业务判断依赖的字段】它只服务排查与对账举证；任何被业务逻辑读取的值都
-- MUST 落到当前态表的列上。

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

-- 按单号取这一单的全部出网记录（排查主路径）。
CREATE INDEX IDX_APCML_ORDER
ON ALIPAY_PAY_CENTER_MSG_LOG (ORDER_NO, TXN_DATE) LOCAL;

-- 按接口 + 账期做批量核对（例如「昨天 requestPay 打了多少次、非 0000 有几条」）。
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

-- 后续月分区维护形态（三张表各一条）：
-- ALTER TABLE ALIPAY_PAY_TXN_DETAIL SPLIT PARTITION P_MAX AT ('20270201') INTO (PARTITION P202701, PARTITION P_MAX);
