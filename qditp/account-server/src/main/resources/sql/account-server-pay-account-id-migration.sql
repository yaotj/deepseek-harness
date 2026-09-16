-- ADR-D30 的支付账号标识列。此前只改了 account-server-schema.sql、没有单独的迁移脚本，
-- 于是在 AFCITPDB 上一直没执行，而 UserPayChannelMapper.xml 的
-- selectByThirdUserIdAndCardTypeAndCardId 与 updatePayAccountIdByReqContractNo 已经在用它，
-- 运营页「支付账号」列与 IF8A-77 回写一跑就 ORA-00904。2026-09-14 已在 AFCITPDB 执行并回查。
-- 宽度取 64 CHAR 与来源列 APP_PAY_SIGN_INFO.PAY_ACCOUNT_ID 一致，NEVER 按 DATA_LENGTH 的 128 写。
ALTER TABLE APP_USER_PAY_CHANNEL ADD (PAY_ACCOUNT_ID VARCHAR2(64 CHAR));

COMMENT ON COLUMN APP_USER_PAY_CHANNEL.PAY_ACCOUNT_ID IS '支付账号标识，签约成功后由支付域经 IF8A-77 回推，宽度与 APP_PAY_SIGN_INFO.PAY_ACCOUNT_ID 一致';
