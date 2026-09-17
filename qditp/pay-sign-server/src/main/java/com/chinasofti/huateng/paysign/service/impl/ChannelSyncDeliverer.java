package com.chinasofti.huateng.paysign.service.impl;

import com.chinasofti.huateng.model.domain.SyncStatus;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 「解约成功后到账户域删支付通道」这一次投递的**唯一执行点**（ADR-D48）。 */
@Component
public class ChannelSyncDeliverer {

    private static final Logger log = LoggerFactory.getLogger(ChannelSyncDeliverer.class);

    /** {@code CHANNEL_SYNC_RESULT} 是 VARCHAR2(1024 CHAR)，超长直接抛 ORA-12899。 */
    private static final int RESULT_MAX_LENGTH = 1024;

    private final AccountDomainPort accountDomainPort;
    private final AppTerminationRequestMapper terminationRequestMapper;

    public ChannelSyncDeliverer(AccountDomainPort accountDomainPort,
                               AppTerminationRequestMapper terminationRequestMapper) {
        this.accountDomainPort = accountDomainPort;
        this.terminationRequestMapper = terminationRequestMapper;
    }

    /** 补偿侧入口：行是扫表快照，四要素直接取自 {@code APP_TERMINATION_REQUEST}。 */
    public boolean deliver(AppTerminationRequest row) {
        return deliver(row.getRequestSignSeq(), row.getThirdUserId(), row.getPaymentVendor(),
                row.getCardId(), row.getCardType());
    }

    /**
     * 投递一次并把结果落进 {@code CHANNEL_SYNC_*}。
     *
     * @return 是否投递成功；{@code false} 含「业务拒绝」与「不可达」两种，调用方 NEVER 据此判断可否重试
     */
    public boolean deliver(String requestSignSeq, String thirdUserId, String paymentVendor,
                           String cardId, String cardType) {
        try {
            RpcOutcome outcome = accountDomainPort.removeChannel(thirdUserId, paymentVendor, cardId, cardType);
            switch (outcome) {
                case RpcOutcome.Ok ignored -> {
                    int rows = terminationRequestMapper.updateChannelSyncStatus(requestSignSeq,
                            SyncStatus.SUCCESS.name(), LocalDateTime.now(), "账户支付通道已清理");
                    log.info("账户支付通道已清理, requestSignSeq={}, rows={}", requestSignSeq, rows);
                    return true;
                }
                case RpcOutcome.BizRejected rejected -> {
                    terminationRequestMapper.increaseChannelSyncRetryCount(requestSignSeq);
                    int manual = terminationRequestMapper.markChannelSyncManual(requestSignSeq, LocalDateTime.now(),
                            truncate("账户域拒绝清理:" + rejected.retCode() + "/" + rejected.retMsg()));
                    log.error("账户域拒绝清理支付通道（不可重试），已转人工, requestSignSeq={}, retCode={}, retMsg={}, manualRows={}",
                            requestSignSeq, rejected.retCode(), rejected.retMsg(), manual);
                    return false;
                }
                case RpcOutcome.Unreachable unreachable -> {
                    terminationRequestMapper.increaseChannelSyncRetryCount(requestSignSeq);
                    terminationRequestMapper.updateChannelSyncStatus(requestSignSeq, SyncStatus.FAILED.name(),
                            LocalDateTime.now(), truncate("未获答复:" + unreachable.cause()));
                    log.warn("清理账户支付通道未获答复（可重试，已交补偿）, requestSignSeq={}", requestSignSeq,
                            unreachable.cause());
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("落 CHANNEL_SYNC 状态自身异常，已交补偿, requestSignSeq={}", requestSignSeq, e);
        }
        return false;
    }

    private String truncate(String result) {
        if (result == null) {
            return null;
        }
        return result.length() <= RESULT_MAX_LENGTH ? result : result.substring(0, RESULT_MAX_LENGTH);
    }
}
