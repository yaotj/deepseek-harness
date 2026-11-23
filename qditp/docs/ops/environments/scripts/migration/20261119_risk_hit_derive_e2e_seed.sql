-- 20261119 风控命中 E2E 造数（IT-086，测试环境，用完即删）
-- 三条造数覆盖：000003 超时出站 / 000002 自然日重复出站 / 000003 滞留进站

-- 1. 超时出站（QRCODE_TXN_DETAIL，TRX_TYPE='02' 且 OVERTIME_AMOUNT>0）
insert into QRCODE_TXN_DETAIL (ID, CARD_ID, TRX_TYPE, CARD_TYPE, HANDLE_DATE_TIME, TXN_DATE,
                                DEVICE_ID, TICKET_TRANS_SEQ, OVERTIME_AMOUNT, TRX_AMOUNT,
                                LAST_TICKET_STATUS, HANDLE_RESULT_CODE, CREATE_TIME)
values (seq_qrcode_txn_detail.nextval, 'E2E_ONESIDE_001', '02', '0441',
        to_char(sysdate - 1, 'YYYYMMDD') || '183000',
        to_char(sysdate - 1, 'YYYYMMDD'),
        'E2E010101', 'E2E_ONESIDE_001_SEQ', 5, 3,
        '05', '0000', systimestamp);

-- 2. 自然日重复出站（QRCODE_TXN_DETAIL，同卡同日 3 次 TRX_TYPE='02'）
insert into QRCODE_TXN_DETAIL (ID, CARD_ID, TRX_TYPE, CARD_TYPE, HANDLE_DATE_TIME, TXN_DATE,
                                DEVICE_ID, TICKET_TRANS_SEQ, OVERTIME_AMOUNT, TRX_AMOUNT,
                                LAST_TICKET_STATUS, HANDLE_RESULT_CODE, CREATE_TIME)
values (seq_qrcode_txn_detail.nextval, 'E2E_REPEAT_001', '02', '0441',
        to_char(sysdate, 'YYYYMMDD') || '080000',
        to_char(sysdate, 'YYYYMMDD'),
        'E2E010102', 'E2E_REPEAT_001_SEQ1', 0, 3,
        '05', '0000', systimestamp);
insert into QRCODE_TXN_DETAIL (ID, CARD_ID, TRX_TYPE, CARD_TYPE, HANDLE_DATE_TIME, TXN_DATE,
                                DEVICE_ID, TICKET_TRANS_SEQ, OVERTIME_AMOUNT, TRX_AMOUNT,
                                LAST_TICKET_STATUS, HANDLE_RESULT_CODE, CREATE_TIME)
values (seq_qrcode_txn_detail.nextval, 'E2E_REPEAT_001', '02', '0441',
        to_char(sysdate, 'YYYYMMDD') || '120000',
        to_char(sysdate, 'YYYYMMDD'),
        'E2E010102', 'E2E_REPEAT_001_SEQ2', 0, 3,
        '05', '0000', systimestamp);
insert into QRCODE_TXN_DETAIL (ID, CARD_ID, TRX_TYPE, CARD_TYPE, HANDLE_DATE_TIME, TXN_DATE,
                                DEVICE_ID, TICKET_TRANS_SEQ, OVERTIME_AMOUNT, TRX_AMOUNT,
                                LAST_TICKET_STATUS, HANDLE_RESULT_CODE, CREATE_TIME)
values (seq_qrcode_txn_detail.nextval, 'E2E_REPEAT_001', '02', '0441',
        to_char(sysdate, 'YYYYMMDD') || '180000',
        to_char(sysdate, 'YYYYMMDD'),
        'E2E010102', 'E2E_REPEAT_001_SEQ3', 0, 3,
        '05', '0000', systimestamp);

-- 3. 滞留进站（QRCODE_STATUS，CODE_STATUS='04' 且 GATE_IN_TIME 超过 24h）
merge into QRCODE_STATUS S
using dual
on (S.CARD_ID = 'E2E_STUCK_001')
when not matched then
  insert (CARD_ID, CODE_STATUS, GATE_IN_TIME, GATE_IN_STATION, LAST_TXN_TIME, LAST_TXN_STATION,
          TXN_SEQ, CREATE_TIME, UPDATE_TIME)
  values ('E2E_STUCK_001', '04', to_char(sysdate - 2, 'YYYYMMDD') || '090000',
          '0101', to_char(sysdate - 2, 'YYYYMMDD') || '090000', '0101',
          'E2E_STUCK_001', systimestamp, systimestamp);
