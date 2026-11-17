package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.mapper.QRCodeStatusMapper;
import org.springframework.stereotype.Component;

@Component
public class QRCodeStatusStore {

    private final QRCodeStatusMapper qrCodeStatusMapper;

    public QRCodeStatusStore(QRCodeStatusMapper qrCodeStatusMapper) {
        this.qrCodeStatusMapper = qrCodeStatusMapper;
    }

    public QRCodeStatus findByCardId(String cardId) {
        return qrCodeStatusMapper.selectByCardId(cardId);
    }

    public int upsert(QRCodeStatus record) {
        return qrCodeStatusMapper.upsert(record);
    }


    public int upsertWithCas(QRCodeStatus record, String expectedTxnSeq) {
        return qrCodeStatusMapper.upsertWithCas(record, expectedTxnSeq);
    }


    public int updateCodeStatus(String cardId, String codeStatus) {
        return qrCodeStatusMapper.updateCodeStatus(cardId, codeStatus);
    }
}
