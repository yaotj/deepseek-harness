package com.chinasofti.huateng.paysign.event;

import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.paysign.service.SignNotifyService;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/** {@link SignResultCommittedEvent} 的唯一监听方：签约结果落库提交后做两件派生动作。 */
@Component
public class SignResultCommittedListener {

    private static final Logger log = LoggerFactory.getLogger(SignResultCommittedListener.class);

    private final PaySignRequestMapper paySignRequestMapper;

    private final PaySignInfoMapper paySignInfoMapper;

    private final SignNotifyService signNotifyService;

    /** ADR-D32：把 {@code PAY_ACCOUNT_ID} 推给账户域。**允许失败**，见 {@link #syncPayAccountIdQuietly}。 */
    private final AccountDomainPort accountDomainPort;

    public SignResultCommittedListener(PaySignRequestMapper paySignRequestMapper,
                                       PaySignInfoMapper paySignInfoMapper,
                                       SignNotifyService signNotifyService,
                                       AccountDomainPort accountDomainPort) {
        this.paySignRequestMapper = paySignRequestMapper;
        this.paySignInfoMapper = paySignInfoMapper;
        this.signNotifyService = signNotifyService;
        this.accountDomainPort = accountDomainPort;
    }

    // AFTER_COMMIT + fallbackExecution=false；NEVER 加 @Async，内部 MUST catch 全部异常只记日志。
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onSignResultCommitted(SignResultCommittedEvent event) {
        String requestSignSeq = event.requestSignSeq();
        try {
            PaySignRequest logRecord = paySignRequestMapper.selectLatestSignResultBySeq(requestSignSeq);
            if (logRecord == null) {
                log.error("签约结果流水回查为空，本次不通知，等补偿任务处理, requestSignSeq={}", requestSignSeq);
                return;
            }
            PaySignInfo signInfo = paySignInfoMapper.selectBySeq(requestSignSeq, event.paymentVendor());
            if (signInfo == null) {
                log.error("签约信息回查为空，本次不通知，等补偿任务处理, requestSignSeq={}, paymentVendor={}",
                        requestSignSeq, event.paymentVendor());
                return;
            }
            signNotifyService.asyncNotifySignResult(logRecord, signInfo, event.callback());
            log.info("签约结果通知已在事务提交后提交投递, requestSignSeq={}", requestSignSeq);
            syncPayAccountIdQuietly(requestSignSeq, signInfo.getPayAccountId());
        } catch (RuntimeException e) {
            log.error("签约结果通知提交失败，等补偿任务处理, requestSignSeq={}", requestSignSeq, e);
        }
    }

    /** 把支付账号推给账户域，让 {@code APP_USER_PAY_CHANNEL.PAY_ACCOUNT_ID} 在签约这一刻就完整（ADR-D32）。 */
    private void syncPayAccountIdQuietly(String requestSignSeq, String payAccountId) {
        if (!StringUtils.hasText(payAccountId)) {
            log.info("签约信息未带支付账号，跳过向账户域回写, requestSignSeq={}", requestSignSeq);
            return;
        }
        switch (accountDomainPort.syncPayAccountId(requestSignSeq, payAccountId)) {
            case RpcOutcome.Ok ignored ->
                    log.info("向账户域回写支付账号成功, requestSignSeq={}", requestSignSeq);
            case RpcOutcome.BizRejected rejected ->
                    log.info("向账户域回写支付账号未成功（不影响签约结果）, requestSignSeq={}, result={}/{}",
                            requestSignSeq, rejected.retCode(), rejected.retMsg());
            case RpcOutcome.Unreachable unreachable ->
                    log.warn("向账户域回写支付账号异常（不影响签约结果）, requestSignSeq={}",
                            requestSignSeq, unreachable.cause());
        }
    }
}
