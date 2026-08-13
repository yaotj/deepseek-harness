-- HCE 卡数据存储。已执行 account-server-card-type-migration.sql 的环境也必须执行本脚本。
ALTER TABLE USER_ITP_REG_INFO ADD (HCE_DATA VARCHAR2(512 CHAR));

COMMENT ON COLUMN USER_ITP_REG_INFO.HCE_DATA IS 'HCE卡数据，开户时生成并由闸机交易reserve1更新';
