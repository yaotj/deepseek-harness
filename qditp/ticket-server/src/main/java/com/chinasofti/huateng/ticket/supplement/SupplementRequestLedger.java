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

/**
 * IF5A-03 补站请求台账的读写收口。
 *
 * <p>审查项 L001 —— <b>幂等</b>：{@link #claim} 靠唯一索引
 * {@code UK_QSR_CARD_SEQ_ADVICE} 保证同一 {@code (cardId, txnSeq, adviceOpt)}
 * 只有一条请求能下发闸机。原实现是「下发前再 select 一次比对快照」，即典型 TOCTOU：
 * 187 行读、236/263 行调 RPC、277 行再读、309 行下发 —— 两条并发的同卡请求各读到相同快照、
 * 各自比对通过、各下发一次，付费更新分支下就是两次扣费。</p>
 *
 * <p>审查项 L002 —— <b>结果未知留证据</b>：闸机超时 / 无响应时闸机侧可能已推进
 * {@code QRCODE_STATUS}，原实现只 {@code log.error} 一行，库里零证据、事后无从对账。
 * 现在该行留在 {@code UNKNOWN}，{@link SupplementRequestMapper#selectUnknown} 可捞出。</p>
 *
 * <p><b>本类的每个方法都不带事务、各自独立提交</b>，这是刻意的：AGENTS.md §5.2 记着一次生产事故
 * —— 事务内调 RPC，连接被 Druid 强杀后 commit 抛错，「留证据」的 INSERT 连同事务一起丢弃。
 * 声明与收口必须在 RPC 之外各自落地。<b>NEVER 给它们加 {@code @Transactional}。</b></p>
 *
 * <p><b>收口失败一律只记日志、NEVER 抛出</b>：闸机侧已经动过了，为了一行台账写不进去就对 BOM 报错，
 * 只会引来重推、把「一次未知」放大成「多次未知」。</p>
 */
@Component
class SupplementRequestLedger {

    private static final Logger log = LoggerFactory.getLogger(SupplementRequestLedger.class);

    /** {@code TXN_SEQ} 为空时的兜底值。<b>MUST 有</b>：Oracle 唯一索引对 NULL 不去重。 */
    private static final String TXN_SEQ_FALLBACK = "0";

    @Autowired
    private SupplementRequestMapper supplementRequestMapper;

    /**
     * 声明结果。
     *
     * @param acquired true 表示本请求取得下发权；false 表示同键请求正在处理或已成终态
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
            // 同键已存在。只有「上一次被闸机明确拒绝」才允许 BOM 重新发起；
            // PENDING（并发在飞）/ SUCCESS / UNKNOWN 一律拒绝，由 CAS 的 0 行体现。
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

    /**
     * 沿 {@code getCause()} 链判定唯一索引冲突。
     *
     * <p><b>NEVER 只 catch {@code DuplicateKeyException}</b>：`resource/micro/web` 的观测切面
     * 会在打开 tracing 的模块里把异常重新包一层（AGENTS.md §5.2 / ADR-D53）。ticket-server
     * 当前没开 tracing，但**一旦有人给它开，裸 catch 就会静默失效**，而编译与单测都发现不了。</p>
     */
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
