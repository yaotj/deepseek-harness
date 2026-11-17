package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class GateTicketWriter {

    private static final Logger log = LoggerFactory.getLogger(GateTicketWriter.class);

    private final QRCodeTxnDetailMapper qrCodeTxnDetailMapper;
    private final QRCodeStatusStore qrCodeStatusStore;

    public GateTicketWriter(QRCodeTxnDetailMapper qrCodeTxnDetailMapper,
                            QRCodeStatusStore qrCodeStatusStore) {
        this.qrCodeTxnDetailMapper = qrCodeTxnDetailMapper;
        this.qrCodeStatusStore = qrCodeStatusStore;
    }


    @Transactional(rollbackFor = Exception.class)
    public WriteResult saveTxnAndAdvanceStatus(QRCodeTxnDetail detail, QRCodeStatus nextStatus,
                                                String expectedTxnSeq) {
        boolean duplicate = false;
        try {
            qrCodeTxnDetailMapper.insert(detail);
        } catch (Exception e) {
            if (!isDuplicateKeyViolation(e)) throw e;
            duplicate = true;
        }
        if (duplicate) {
            QRCodeStatus persisted = qrCodeStatusStore.findByCardId(nextStatus.getCardId());
            if (persisted != null) {
                return new WriteResult(true, 0, persisted);
            }
        }

        int updateCount = qrCodeStatusStore.upsertWithCas(nextStatus, expectedTxnSeq);
        if (updateCount > 0) {
            return new WriteResult(duplicate, updateCount, nextStatus);
        }

        QRCodeStatus actual = qrCodeStatusStore.findByCardId(nextStatus.getCardId());
        if (actual != null) {
            log.warn("IF1A-01 CAS upsert 未命中: cardId={}, expectedTxnSeq={}, actualTxnSeq={}, actualCodeStatus={}. "
                            + "序号已被其他请求推进（AGM 超时重发或并发），降级使用库内状态。",
                    nextStatus.getCardId(), expectedTxnSeq, actual.getTxnSeq(), actual.getCodeStatus());
            return new WriteResult(duplicate, 0, actual);
        }

        log.warn("IF1A-01 CAS upsert 未命中且回查为空, cardId={}, expectedTxnSeq={}. 降级走无条件 upsert。",
                nextStatus.getCardId(), expectedTxnSeq);
        updateCount = qrCodeStatusStore.upsert(nextStatus);
        return new WriteResult(duplicate, updateCount, nextStatus);
    }

    private boolean isDuplicateKeyViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }


    public record WriteResult(boolean duplicate, int updateCount, QRCodeStatus status) {
    }
}
