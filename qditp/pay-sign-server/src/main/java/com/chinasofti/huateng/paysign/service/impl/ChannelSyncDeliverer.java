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

/**
 * 「解约成功后到账户域删支付通道」这一次投递的**唯一执行点**（ADR-D48）。
 *
 * <p>为什么要单独一个类：这段投递有两个调用方 —— 回调收口后的快速路径
 * （{@code PaySignWorkflow.receiveTerminationResult}）与扫表补偿
 * （{@code TerminationInternalServiceImpl.compensateChannelSync}）。ADR-D46 已经吃过一次
 * 「同一套模式在多处各写一遍、每遍都可能漏一条不变量」的亏，因此三分支处置 **MUST 只有一处实现**。
 *
 * <p><b>NEVER 让本类抛异常</b>：两个调用方都在「本地已提交」之后调它，抛出去只会让上游误判失败。
 */
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
     * <p>三分支处置**互不相同、NEVER 合并**（口径同 ADR-D45 与 {@code outbox.md} §四）：
     * <ul>
     *   <li>{@code Ok} → 落 {@code SUCCESS}，本笔结束。</li>
     *   <li>{@code BizRejected} → 账户域明确拒绝，**重推一万次也不会成功**：先 +1 把状态推到
     *       {@code FAILED}（{@code markChannelSyncManual} 的 CAS 要求前置态就是 {@code FAILED}），
     *       再一次性转 {@code MANUAL}。**NEVER 让它留在补偿队列里。**</li>
     *   <li>{@code Unreachable} → 只 +1（该语句自身会落 {@code FAILED}）并记原因，留给下一轮补偿。</li>
     * </ul>
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
            // NEVER 上抛：调用方都在本地已提交之后调本方法。滞留状态会被下一轮补偿捞走，不会丢。
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
