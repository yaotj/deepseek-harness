-- ============================================
-- ALIPAY_PAY_LOG 表结构更新
-- 新增字段：进站交易id、出站交易id
-- 生成时间：2026-07-21
-- ============================================

-- 添加进站交易id字段
ALTER TABLE ALIPAY_PAY_LOG ADD (
    ENTRY_ID VARCHAR2(128)
);

-- 添加出站交易id字段
ALTER TABLE ALIPAY_PAY_LOG ADD (
    EXIT_ID VARCHAR2(128)
);

-- 添加字段注释
COMMENT ON COLUMN ALIPAY_PAY_LOG.ENTRY_ID IS '进站交易id';
COMMENT ON COLUMN ALIPAY_PAY_LOG.EXIT_ID IS '出站交易id';
