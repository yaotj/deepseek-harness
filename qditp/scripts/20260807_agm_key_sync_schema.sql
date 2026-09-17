-- AGM key synchronization initialization script for Oracle.
-- Run once before deploying AGM sync, then run 20260807_agm_key_sync_initial_data.sql. Execution status NOT recorded -- verify via USER_TABLES first. See docs/ops/生产环境清单.md 附.二.2

create table METRO_AGM_KEY_VERSION
(
    ID                        NUMBER(20) not null,
    PROVIDER_ID               VARCHAR2(10) not null,
    KEY_BATH_NUMBER           NUMBER not null,
    PUBLIC_KEY_VERSION_STATUS VARCHAR2(10) not null,
    PUBLIC_KEY_VERSION_DESC   VARCHAR2(200),
    MANAGER_ID                VARCHAR2(20),
    RESERVE                   VARCHAR2(100),
    REMARK                    VARCHAR2(100),
    UPDATE_DATE               DATE,
    REG_DATE                  DATE default sysdate not null,
    constraint PK_METRO_AGM_KEY_VERSION primary key (ID),
    constraint UK_METRO_AGM_KEY_VERSION unique (PROVIDER_ID, KEY_BATH_NUMBER)
);

comment on table METRO_AGM_KEY_VERSION is 'AGM渠道密钥批次及审批状态';
comment on column METRO_AGM_KEY_VERSION.PROVIDER_ID is '渠道编码，例如01';
comment on column METRO_AGM_KEY_VERSION.KEY_BATH_NUMBER is '密钥批次号';
comment on column METRO_AGM_KEY_VERSION.PUBLIC_KEY_VERSION_STATUS is '20010初始化，20020审批通过，20030拒绝';

-- The bundled legacy data has a maximum ID of 10001.
create sequence SEQ_METRO_AGM_KEY_VERSION start with 10002 increment by 1 nocache;

create index IDX_AGM_KEY_VERSION_APPROVED
    on METRO_AGM_KEY_VERSION (PROVIDER_ID, PUBLIC_KEY_VERSION_STATUS, KEY_BATH_NUMBER);

create table METRO_AGM_KEY_POOL
(
    ID                 NUMBER(20) not null,
    PROVIDER_ID        VARCHAR2(10) not null,
    KEY_BATH_NUMBER    NUMBER not null,
    KEY_IDX            VARCHAR2(10) not null,
    KEY_VALUE          VARCHAR2(256) not null,
    KEY_EFFECTIVE_DATE VARCHAR2(8),
    DOWNLOAD_DATE      DATE,
    MANAGER_ID         VARCHAR2(20),
    RESERVE            VARCHAR2(100),
    REMARK             VARCHAR2(100),
    KEY_TYPE           NUMBER,
    UPDATE_DATE        DATE,
    REG_DATE           DATE default sysdate not null,
    constraint PK_METRO_AGM_KEY_POOL primary key (ID),
    constraint UK_METRO_AGM_KEY_POOL unique (PROVIDER_ID, KEY_BATH_NUMBER, KEY_IDX)
);

comment on table METRO_AGM_KEY_POOL is 'AGM渠道密钥池';
comment on column METRO_AGM_KEY_POOL.KEY_IDX is '密钥索引';
comment on column METRO_AGM_KEY_POOL.KEY_VALUE is '密钥值';
comment on column METRO_AGM_KEY_POOL.KEY_TYPE is '0非对称密钥，1对称密钥';

-- The bundled legacy data has a maximum ID of 128.
create sequence SEQ_METRO_AGM_KEY_POOL start with 129 increment by 1 nocache;

create index IDX_AGM_KEY_POOL_PROVIDER_BATCH
    on METRO_AGM_KEY_POOL (PROVIDER_ID, KEY_BATH_NUMBER, KEY_IDX);

create table COM_DEVICE_SYN_KEY
(
    DEVICE_ID           VARCHAR2(20) not null,
    AGM_KEY_CURVER_LIST CLOB,
    REQ_SYN_KEY_DATE    DATE,
    STATION_CODE        CHAR(4),
    STATION_NAME        VARCHAR2(60),
    REG_DATE            DATE default sysdate not null,
    constraint PK_COM_DEVICE_SYN_KEY primary key (DEVICE_ID)
);

comment on table COM_DEVICE_SYN_KEY is 'AGM设备最后一次密钥同步上报记录';
comment on column COM_DEVICE_SYN_KEY.DEVICE_ID is '设备编码';
comment on column COM_DEVICE_SYN_KEY.AGM_KEY_CURVER_LIST is '设备上报的当前密钥版本原始报文';
comment on column COM_DEVICE_SYN_KEY.REQ_SYN_KEY_DATE is '设备请求密钥同步时间';

-- Next step: run 20260807_agm_key_sync_initial_data.sql in the same schema.

