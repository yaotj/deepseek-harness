-- ============================================================
-- 表名: TBL_NOTICE_APP_FAILURE_RECORD
-- 用途: APP通知失败记录表（取票/退款失败）
-- 所属服务: collect-pay-server
-- 创建日期: 2026-08-16
-- 数据库: Oracle
-- ============================================================

-- 删除表（可选）
-- DROP TABLE TBL_NOTICE_APP_FAILURE_RECORD;

-- 建表语句
CREATE TABLE TBL_NOTICE_APP_FAILURE_RECORD (
    ORDER_NO VARCHAR2(64) NOT NULL,
    orderTicketNum VARCHAR2(32),
    actualTakeTicketNum VARCHAR2(32),
    takeTickeDate VARCHAR2(32),
    takeTiketFaultReason VARCHAR2(256),
    refundAmount VARCHAR2(32),
    status VARCHAR2(16),
    retryTimes VARCHAR2(16),
    CREATE_TIME VARCHAR2(32),
    UPDATE_TIME VARCHAR2(32),
    CONSTRAINT PK_NOTICE_APP_FAILURE PRIMARY KEY (ORDER_NO)
) TABLESPACE USERS
PCTFREE 10
PCTUSED 40
INITRANS 1
MAXTRANS 255
STORAGE (
    INITIAL 64K
    NEXT 1M
    MINEXTENTS 1
    MAXEXTENTS UNLIMITED
);

-- 表注释
COMMENT ON TABLE TBL_NOTICE_APP_FAILURE_RECORD IS 'APP通知失败记录表';

-- 字段注释
COMMENT ON COLUMN TBL_NOTICE_APP_FAILURE_RECORD.ORDER_NO IS '订单号';
COMMENT ON COLUMN TBL_NOTICE_APP_FAILURE_RECORD.orderTicketNum IS '订单票数';
COMMENT ON COLUMN TBL_NOTICE_APP_FAILURE_RECORD.actualTakeTicketNum IS '实际取票数量';
COMMENT ON COLUMN TBL_NOTICE_APP_FAILURE_RECORD.takeTickeDate IS '取票日期';
COMMENT ON COLUMN TBL_NOTICE_APP_FAILURE_RECORD.takeTiketFaultReason IS '取票故障原因';
COMMENT ON COLUMN TBL_NOTICE_APP_FAILURE_RECORD.refundAmount IS '退款金额';
COMMENT ON COLUMN TBL_NOTICE_APP_FAILURE_RECORD.status IS '状态: SUCCESS-成功, FAIL-失败, INIT-初始';
COMMENT ON COLUMN TBL_NOTICE_APP_FAILURE_RECORD.retryTimes IS '重试次数';
COMMENT ON COLUMN TBL_NOTICE_APP_FAILURE_RECORD.CREATE_TIME IS '创建时间';
COMMENT ON COLUMN TBL_NOTICE_APP_FAILURE_RECORD.UPDATE_TIME IS '更新时间';

-- 创建索引
CREATE INDEX IDX_FAILURE_RECORD_ORDER_NO ON TBL_NOTICE_APP_FAILURE_RECORD(ORDER_NO)
    TABLESPACE USERS
    PCTFREE 10
    INITRANS 2
    MAXTRANS 255
    STORAGE (
        INITIAL 64K
        NEXT 1M
        MINEXTENTS 1
        MAXEXTENTS UNLIMITED
    );

CREATE INDEX IDX_FAILURE_RECORD_STATUS ON TBL_NOTICE_APP_FAILURE_RECORD(status)
    TABLESPACE USERS
    PCTFREE 10
    INITRANS 2
    MAXTRANS 255
    STORAGE (
        INITIAL 64K
        NEXT 1M
        MINEXTENTS 1
        MAXEXTENTS UNLIMITED
    );

CREATE INDEX IDX_FAILURE_RECORD_CREATE_TIME ON TBL_NOTICE_APP_FAILURE_RECORD(CREATE_TIME)
    TABLESPACE USERS
    PCTFREE 10
    INITRANS 2
    MAXTRANS 255
    STORAGE (
        INITIAL 64K
        NEXT 1M
        MINEXTENTS 1
        MAXEXTENTS UNLIMITED
    );

-- ============================================================
-- 使用说明
-- ============================================================
-- 1. 该表用于记录APP通知失败的交易（取票/退款）
-- 2. 通过ORDER_NO作为主键，避免重复记录
-- 3. status字段标识通知状态：INIT-初始，FAIL-失败，SUCCESS-成功
-- 4. retryTimes字段记录重试次数，用于控制重试策略
-- 5. 定时任务会扫描status='FAIL'且retryTimes<最大重试次数的记录进行重试
-- ============================================================
