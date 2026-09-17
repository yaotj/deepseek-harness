package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.mapper.QRCodeStatusMapper;
import org.springframework.stereotype.Component;

/** {@code QRCODE_STATUS} 的唯一访问出口（owner = gate）。 */
@Component
public class QRCodeStatusStore {

    private final QRCodeStatusMapper qrCodeStatusMapper;

    public QRCodeStatusStore(QRCodeStatusMapper qrCodeStatusMapper) {
        this.qrCodeStatusMapper = qrCodeStatusMapper;
    }

    /** 按卡号读当前票卡状态，不存在返回 {@code null}。 */
    public QRCodeStatus findByCardId(String cardId) {
        return qrCodeStatusMapper.selectByCardId(cardId);
    }

    /** 无条件 upsert（无并发保护）。 */
    public int upsert(QRCodeStatus record) {
        return qrCodeStatusMapper.upsert(record);
    }

    /**
     * CAS upsert：仅当库里 {@code TXN_SEQ} 仍等于 {@code expectedTxnSeq} 时才写入。
     *
     * @return 1 = 写入成功；
     */
    public int upsertWithCas(QRCodeStatus record, String expectedTxnSeq) {
        return qrCodeStatusMapper.upsertWithCas(record, expectedTxnSeq);
    }

    /**
     * 运营端只改 {@code CODE_STATUS}，保留进出站与末次交易等行程字段。
     *
     * @return 影响行数，0 表示该卡号在表里不存在
     */
    public int updateCodeStatus(String cardId, String codeStatus) {
        return qrCodeStatusMapper.updateCodeStatus(cardId, codeStatus);
    }
}
