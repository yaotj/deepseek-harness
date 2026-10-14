-- ITP 与第三方支付对账：渠道日账单 + 差异明细 落地表
-- 关联：docs/business/recon-thirdparty.md
-- 注意：本文件仅建表，外部契约（账单投递方式、渠道枚举映射、ITP 侧对账源表、关联键）确认后再启用加载与比对代码。

-- 渠道日账单落地（reconciliation-bill-date.csv 解析后入库）
CREATE TABLE RECON_THIRD_BILL (
    ID                  NUMBER(19)      NOT NULL,
    BILL_DATE           VARCHAR2(10),                -- yyyy-MM-dd 账单所属日期
    PAYMENT_CHANNEL     VARCHAR2(16),                -- 支付渠道枚举(ALIPAY...)
    PAYMENT_VENDOR      VARCHAR2(16),                -- 渠道编码(panchan)
    CHANNEL_NAME        VARCHAR2(64),
    CHANNEL_MERCHANT_NO VARCHAR2(64),
    TERMINAL_NO         VARCHAR2(32),
    BUSINESS_TYPE       VARCHAR2(16),                -- PAY/REFUND/REVERSAL/OTHER
    TRADE_STATUS        VARCHAR2(16),                -- SUCCESS/FAIL/CLOSED/UNKNOWN
    TRADE_TIME          VARCHAR2(32),                -- yyyy-MM-dd HH:mm:ss
    CLEARING_DATE       VARCHAR2(10),
    MERCHANT_ORDER_NO   VARCHAR2(64),                -- 关联键
    CHANNEL_TRADE_NO    VARCHAR2(64),                -- 关联键
    MERCHANT_REFUND_NO  VARCHAR2(64),
    CHANNEL_REFUND_NO   VARCHAR2(64),
    CURRENCY            VARCHAR2(8),                 -- ISO 4217
    ORDER_AMOUNT        NUMBER(18,2),                -- 元
    TRANSACTION_AMOUNT  NUMBER(18,2),                -- 元，正数
    FEE_AMOUNT          NUMBER(18,2),                -- 元
    SETTLEMENT_AMOUNT   NUMBER(18,2),                -- 元
    DISCOUNT_AMOUNT     NUMBER(18,2),                -- 元
    PRODUCT_NAME        VARCHAR2(128),
    REMARK              VARCHAR2(256),
    BATCH_NO            VARCHAR2(32),                -- 本次入账批次
    CREATE_TIME         TIMESTAMP,
    CONSTRAINT PK_RECON_THIRD_BILL PRIMARY KEY (ID)
);
CREATE INDEX IDX_RECON_THIRD_BILL_KEY ON RECON_THIRD_BILL (BILL_DATE, PAYMENT_CHANNEL, MERCHANT_ORDER_NO, CHANNEL_TRADE_NO);

-- 对账差异明细落地（生成 ITP.TXNDIFF.YYYYMMDD 的数据源）
CREATE TABLE RECON_TXN_DIFF (
    ID                  NUMBER(19)      NOT NULL,
    BILL_DATE           VARCHAR2(10),                -- 自然日 yyyy-MM-dd
    TXN_TYPE            VARCHAR2(16),                -- 交易类型(发售/出站...)
    LOGIC_CARD_NO       VARCHAR2(64),                -- 逻辑卡号
    TXN_DATE            VARCHAR2(32),                -- 交易日期时间
    ORIGINAL_FARE       NUMBER(18),                  -- 交易原价(分)
    CUR_STATION_NAME    VARCHAR2(64),                -- 当前车站名称
    DEVICE_NO           VARCHAR2(64),                -- 设备编码
    TICKET_TYPE         VARCHAR2(16),                -- 票卡类型
    TICKET_SUB_TYPE     VARCHAR2(16),                -- 票卡子类型
    TIMES               NUMBER(10),                  -- 次数
    PAY_CHANNEL         VARCHAR2(16),                -- 支付渠道
    ACTIVATE_TIME       VARCHAR2(32),                -- 激活时间
    QUANTITY            NUMBER(10),                  -- 数量
    TICKET_TRANS_SEQ    VARCHAR2(64),                -- 交易流水号(二维码里程序列号)
    ORDER_NO            VARCHAR2(64),                -- 交易订单号
    ORDER_AMOUNT        NUMBER(18),                  -- 订单金额(分)
    ACTUAL_AMOUNT       NUMBER(18),                  -- 实际扣款金额(分)
    DISCOUNT_CUMULATIVE_AMOUNT NUMBER(18),           -- 优惠累计消费金额(分)
    OVERTIME_AMOUNT     NUMBER(18),                  -- 超时金额(分)
    DIFF_SIDE           VARCHAR2(16),                -- 差异方: ITP_ONLY / THIRD_ONLY / AMOUNT_MISMATCH / STATUS_MISMATCH
    FAIL_REASON         VARCHAR2(256),               -- 扣款失败原因(平台返回)
    BATCH_NO            VARCHAR2(32),
    CREATE_TIME         TIMESTAMP,
    CONSTRAINT PK_RECON_TXN_DIFF PRIMARY KEY (ID)
);
CREATE INDEX IDX_RECON_TXN_DIFF_BILL ON RECON_TXN_DIFF (BILL_DATE, PAY_CHANNEL, DIFF_SIDE);

-- 自增主键序列（若不使用 IDENTITY）
CREATE SEQUENCE SEQ_RECON_THIRD_BILL START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE SEQ_RECON_TXN_DIFF START WITH 1 INCREMENT BY 1;
