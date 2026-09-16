package com.chinasofti.huateng.paysign.event;

import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

/**
 * {@link SignResultCommittedEvent} 的<b>唯一</b>监听方：签约结果落库提交后做两件派生动作 ——
 * ① 触发一次 APP 通知；② 把 {@code PAY_ACCOUNT_ID} 推给账户域（ADR-D32）。
 *
 * <p>两件事共享同一个前提：都**必须在事务提交之后**发 HTTP（AGENTS.md §5.2），所以放在同一个
 * {@code AFTER_COMMIT} 回调里、而不是各起一个监听器 —— 本项目规定<b>一个事件类只挂一个监听器</b>。
 * 但两者**可靠性等级不同**：① 有 {@code NOTIFY_STATUS='PENDING'} + 扫表补偿兜底，
 * ② 是允许丢的展示值同步（理由见 {@link #syncPayAccountIdQuietly}）。
 * <b>NEVER 因为写在一起就给 ② 也加补偿、或让 ② 的失败影响 ①</b>。</p>
 *
 * <h2>注解的三个参数都是有意选的，NEVER 改</h2>
 * <ol>
 *   <li>{@code phase = AFTER_COMMIT}：这是本类存在的全部理由——把投递挪到事务提交之后，
 *       满足 AGENTS.md §5.2。</li>
 *   <li>{@code fallbackExecution = false}（默认值，此处显式写出以防被误改）：<b>没有事务就不执行</b>。
 *       发布点 {@code PaySignWorkflow.receiveSignResult} 带 {@code @Transactional}，所以这里必然触发；
 *       但若哪天那个注解被摘掉，本监听器会**静默不执行**、通知只能等扫表补偿。
 *       改成 {@code true} 更糟——那等于「无事务时立刻同步发 HTTP」，退回本次改造要消除的行为。
 *       摘 {@code @Transactional} 的人 MUST 同步处理这里。</li>
 *   <li><b>不加 {@code @Async}</b>：{@link AppNotifyService#asyncNotifySignResult} 自身已是异步投递，
 *       再套一层线程池只会让异常彻底无声，且默认执行器在
 *       {@code spring.threads.virtual.enabled=true} 下有 pin 载体线程的风险（§5.2）。</li>
 * </ol>
 *
 * <h2>为什么在这里回查而不是把实体放进事件</h2>
 * <p>{@code AFTER_COMMIT} 时数据已提交，回查拿到的是权威值；把事务内构造的实体带过来则可能是脏快照。
 * 回查 {@code APP_PAY_SIGN_REQUEST} <b>MUST 用 {@link PaySignRequestMapper#selectLatestSignResultBySeq}</b>，
 * NEVER 用 {@code selectByRequestSignSeq}：后者不带 {@code OPERATION_TYPE} 过滤，同一流水号下还有
 * {@code OPERATION_TYPE='SIGN'} 的记录，取错会拿到 {@code SIGN_STATUS} 为空、且不在通知队列里的那行
 * （该 mapper 方法的 Javadoc 已警告过）。</p>
 *
 * <h2>异常与兜底</h2>
 * <p>本方法内 {@code catch} 全部异常并只记日志：{@code AFTER_COMMIT} 回调里抛异常无法回滚已提交的事务，
 * 却会污染调用栈、让本已成功的回调对支付平台报错、引来重推。流水行插入时
 * {@code NOTIFY_STATUS='PENDING'}，{@code POST /internal/paySign/compensateNotify} 的扫表补偿会捞到它，
 * 因此<b>这里丢一次通知不会造成永久缺失</b>。多副本下事件是进程内的、各自只处理自己那次请求，
 * 不会重复投递。</p>
 */
@Component
public class SignResultCommittedListener {

    private static final Logger log = LoggerFactory.getLogger(SignResultCommittedListener.class);

    private final PaySignRequestMapper paySignRequestMapper;

    private final PaySignInfoMapper paySignInfoMapper;

    private final AppNotifyService appNotifyService;

    /** ADR-D32：把 {@code PAY_ACCOUNT_ID} 推给账户域。**允许失败**，见 {@link #syncPayAccountIdQuietly}。 */
    private final AccountDomainPort accountDomainPort;

    public SignResultCommittedListener(PaySignRequestMapper paySignRequestMapper,
                                       PaySignInfoMapper paySignInfoMapper,
                                       AppNotifyService appNotifyService,
                                       AccountDomainPort accountDomainPort) {
        this.paySignRequestMapper = paySignRequestMapper;
        this.paySignInfoMapper = paySignInfoMapper;
        this.appNotifyService = appNotifyService;
        this.accountDomainPort = accountDomainPort;
    }

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
            appNotifyService.asyncNotifySignResult(logRecord, signInfo, event.callback());
            log.info("签约结果通知已在事务提交后提交投递, requestSignSeq={}", requestSignSeq);
            syncPayAccountIdQuietly(requestSignSeq, signInfo.getPayAccountId());
        } catch (RuntimeException e) {
            log.error("签约结果通知提交失败，等补偿任务处理, requestSignSeq={}", requestSignSeq, e);
        }
    }

    /**
     * 把支付账号推给账户域，让 {@code APP_USER_PAY_CHANNEL.PAY_ACCOUNT_ID} 在签约这一刻就完整（ADR-D32）。
     *
     * <p><b>为什么放在这里</b>：这是「已提交的签约结果」的派生动作，和 APP 通知同一时机、同一约束
     * （不能在事务内发 HTTP）。放在 {@code AFTER_COMMIT} 里，回滚时根本不会执行。</p>
     *
     * <p><b>为什么允许失败、不做补偿</b>：账户域那一列是**展示值**，权威值一直在本域
     * {@code APP_PAY_SIGN_INFO.PAY_ACCOUNT_ID}。丢一次的后果仅是运营页面那一格显示 {@code -}，
     * 且账户域 IF8A-77 仍会主动向本域取值。<b>NEVER 为它加落库状态 + 扫表补偿</b>——
     * 那是给「必须最终一致的业务写」用的，不该为一列展示值付这个复杂度。</p>
     *
     * <p><b>NEVER 让它抛异常</b>：本方法在 {@code AFTER_COMMIT} 里跑，抛出去回滚不了已提交的签约，
     * 只会让本已成功的支付平台回调对上游报错、引来重推（与本类顶部的异常约定一致）。</p>
     *
     * <p>按 AGENTS.md §5.2 <b>显式判 {@link RpcOutcome} 的每个分支</b>，不假定「没抛异常就是写成功」：
     * 账户域未命中通道行时返 {@code 8004}，那是「签约先于加通道」的合法时序，记 info 即可。
     * 这里两个失败分支的日志级别<b>刻意不同</b>（业务拒绝 info / 不可达 warn），但<b>处置相同 —— 都是放弃</b>：
     * 本方法整体是「允许丢」的展示值同步，<b>NEVER 因为能区分出 {@code Unreachable} 就给它加重试或补偿</b>。</p>
     */
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
