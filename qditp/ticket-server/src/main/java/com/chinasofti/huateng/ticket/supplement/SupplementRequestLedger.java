package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.ticket.entity.SupplementRequest;
import com.chinasofti.huateng.ticket.mapper.SupplementRequestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** IF5A-03 补站请求台账的读写收口。 */
@Component
class SupplementRequestLedger {

    private static final Logger log = LoggerFactory.getLogger(SupplementRequestLedger.class);

    /** {@code TXN_SEQ} 为空时的兜底值。 */
    private static final String TXN_SEQ_FALLBACK = "0";

    @Autowired
    private SupplementRequestMapper supplementRequestMapper;

    /**
     * 声明结果。
     *
     * @param acquired true 表示本请求取得下发权；
     */
    record Claim(String cardId, String txnSeq, String adviceOpt, boolean acquired) {
    }

    Claim claim(String cardId, String rawTxnSeq, String adviceOpt, String codeStatusSnapshot,
                String handleStationCode, String handleDateTime, String trxAmount) {
        String txnSeq = StringUtils.hasText(rawTxnSeq) ? rawTxnSeq : TXN_SEQ_FALLBACK;

        SupplementRequest record = new SupplementRequest();
        record.setCardId(cardId);
        record.setTxnSeq(txnSeq);
        record.setAdviceOpt(adviceOpt);
        record.setCodeStatusSnapshot(codeStatusSnapshot);
        record.setHandleStationCode(handleStationCode);
        record.setHandleDateTime(handleDateTime);
        record.setTrxAmount(trxAmount);
        record.setHandleStatus(SupplementRequest.STATUS_PENDING);

        try {
            supplementRequestMapper.insertClaim(record);
            return new Claim(cardId, txnSeq, adviceOpt, true);
        } catch (Exception e) {
            if (!isIntegrityViolation(e)) {
                throw e;
            }
            int reclaimed = supplementRequestMapper.reclaimRejected(cardId, txnSeq, adviceOpt,
                    codeStatusSnapshot, handleStationCode, handleDateTime, trxAmount);
            if (reclaimed > 0) {
                log.info("IF5A-03 上次被闸机拒绝，本次重新声明, cardId={}, txnSeq={}, adviceOpt={}",
                        cardId, txnSeq, adviceOpt);
                return new Claim(cardId, txnSeq, adviceOpt, true);
            }
            SupplementRequest existing = supplementRequestMapper.selectByKey(cardId, txnSeq, adviceOpt);
            log.warn("IF5A-03 同键补站请求已存在且不可重发, cardId={}, txnSeq={}, adviceOpt={}, 现状态={}",
                    cardId, txnSeq, adviceOpt, existing == null ? "null" : existing.getHandleStatus());
            return new Claim(cardId, txnSeq, adviceOpt, false);
        }
    }

    void markSuccess(Claim claim) {
        finish(claim, SupplementRequest.STATUS_SUCCESS, null);
    }

    void markRejected(Claim claim, String gateRetCode, String gateRetMsg) {
        finish(claim, SupplementRequest.STATUS_REJECTED, "闸机拒绝 retCode=" + gateRetCode + ", retMsg=" + gateRetMsg);
    }

    void markUnknown(Claim claim, String reason) {
        finish(claim, SupplementRequest.STATUS_UNKNOWN, reason);
        log.error("IF5A-03 闸机结果未知，已留台账等待处置, cardId={}, txnSeq={}, adviceOpt={}, reason={}",
                claim.cardId(), claim.txnSeq(), claim.adviceOpt(), reason);
    }

    private void finish(Claim claim, String handleStatus, String failReason) {
        try {
            int updated = supplementRequestMapper.finishClaim(claim.cardId(), claim.txnSeq(),
                    claim.adviceOpt(), handleStatus, truncate(failReason));
            if (updated == 0) {
                log.warn("IF5A-03 补站台账收口命中 0 行（已被收口过）, cardId={}, txnSeq={}, adviceOpt={}, 目标状态={}",
                        claim.cardId(), claim.txnSeq(), claim.adviceOpt(), handleStatus);
            }
        } catch (Exception e) {
            log.error("IF5A-03 补站台账收口失败，仅记日志不打断链路, cardId={}, txnSeq={}, adviceOpt={}, 目标状态={}",
                    claim.cardId(), claim.txnSeq(), claim.adviceOpt(), handleStatus, e);
        }
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 256 ? reason : reason.substring(0, 256);
    }

    /** 沿 {@code getCause()} 链判定唯一索引冲突。 */
    private boolean isIntegrityViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof DuplicateKeyException || current instanceof DataIntegrityViolationException) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }
}
