package com.chinasofti.huateng.paysign.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.UnbindAgreementReqDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;

import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.exception.TerminationException;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.model.request.ExecuteTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.paysign.port.AccountPayChannelView;
import com.chinasofti.huateng.paysign.port.AccountQuery;
import com.chinasofti.huateng.paysign.service.ContractDomainService;
import com.chinasofti.huateng.paysign.support.PaySignGateway;
import com.chinasofti.huateng.paysign.support.PaymentChannel;
import com.chinasofti.huateng.paysign.support.PaymentChannels;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 解约域的**执行**能力：内部执行解约（`/internal/termination/execute`）与 IF8A-75 直接解绑。
 *
 * <p><b>为什么从 {@code TerminationInternalServiceImpl} 拆出来</b>（2026-09-16，ADR-D95，接着补偿那一刀）：
 * 门面剩下的四个入口里，这两个共用一整套东西 —— 状态白名单、`markScanning` /
 * `revertScanningToPending` 两条 CAS、支付中心调用、审计流水、补建申请、唯一索引竞态兜底；
 * 而另外两个（拒绝解约、失败订单核对）一条都不碰。把这簇搬出来后门面只剩「参数进 → 委托 → 应答出」。
 *
 * <p><b>这是纯搬迁</b>：方法体、日志文案、CAS 顺序、异常类型逐字不变，连
 * {@code unbindAgreement → executeTermination} 的同类内部调用也保持原样（两者都不带
 * {@code @Transactional}，不存在绕过事务代理的问题，理由见 {@code executeTermination} 上方注释）。
 *
 * <p><b>本类整体 NEVER 加 {@code @Transactional}</b> —— 逐条理由写在两个方法上方的注释里，
 * 那是 2026-08-26 生产事故换来的，NEVER 因为「看起来该有事务」加回去。
 */
@Service
public class TerminationExecutor {

    private static final Logger log = LoggerFactory.getLogger(TerminationExecutor.class);

    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();
    private static final String STATUS_SCANNING = TerminationStatus.SCANNING.name();
    private static final String STATUS_SUCCESS = TerminationStatus.SUCCESS.name();
    private static final String STATUS_FAILED = TerminationStatus.FAILED.name();

    /*
     * 本类**不再持有** WALLET_PAYMENT_VENDOR 常量（2026-09-16，ADR-D108）。
     * 钱包开户后直接绑定支付通道、不生成签约流水，因此没有可向支付中心解约的协议 ——
     * 这条业务事实不变，只是「是不是钱包」的判定统一走 support/PaymentChannels.isWallet(...)。
     * NEVER 在本类重新声明该常量，也 NEVER 直接写 PaymentVendorEnum.WALLET.getCode() 或字面量 "0B"。
     */

    private final AppTerminationRequestMapper terminationRequestMapper;

    private final PaySignInfoMapper paySignInfoMapper;

    private final ContractDomainService contractDomainService;

    /** 网关应答判读的收口点，NEVER 在本类自己判 {@code code}。 */
    private final PaySignGateway paySignGateway;

    /** 审计流水的唯一写入点。 */
    private final PaySignAuditLogger auditLogger;

    /**
     * 账户域出向调用的唯一出口（ADR-D94 续）。
     *
     * <p>IF8A-75 反查票卡信息用（`APP_PAY_SIGN_INFO` 的 `CARD_ID` / `CARD_TYPE` 全库为 NULL）。
     * <b>NEVER 在本类注入 {@code AccountClient}</b>，新增账户域调用一律加到端口上。</p>
     */
    private final AccountDomainPort accountDomainPort;

    /**
     * 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备，
     * 且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 {@code Could not find field}。
     * <b>NEVER 退回 {@code @Autowired} 字段注入。</b>
     */
    public TerminationExecutor(
            AppTerminationRequestMapper terminationRequestMapper,
            PaySignInfoMapper paySignInfoMapper,
            ContractDomainService contractDomainService,
            PaySignGateway paySignGateway,
            PaySignAuditLogger auditLogger,
            AccountDomainPort accountDomainPort) {
        this.terminationRequestMapper = terminationRequestMapper;
        this.paySignInfoMapper = paySignInfoMapper;
        this.contractDomainService = contractDomainService;
        this.paySignGateway = paySignGateway;
        this.auditLogger = auditLogger;
        this.accountDomainPort = accountDomainPort;
    }

    /*
     * 本方法 NEVER 加 @Transactional：方法体内要调支付中心（requestPayPlatformTermination）。
     * 事务包住网络调用会让 APP_TERMINATION_REQUEST 这一行的排他锁持满整个往返，
     * 同一笔的重推全部堆在该行串行等待，超过 Druid remove-abandoned-timeout 后连接被强杀、
     * commit 抛 connection closed，连「留证据」的流水日志 INSERT 一起被丢弃
     * （与 2026-08-26 receivePayResult 生产事故同型）。
     *
     * 去事务后每条 SQL 自动提交，代价是没有回滚可用，因此状态流转全部改为 CAS：
     *   markScanning            PENDING → SCANNING，抢执行权，影响 0 行即放弃；
     *   revertScanningToPending  仅在支付平台**明确**答复失败时交还执行权。
     * 结果未知（超时 / 连接异常）时刻意保持 SCANNING，由 processTermination 的 SCANNING 分支
     * 主动查协议状态收口，NEVER 退回 PENDING —— 支付平台可能已受理，退回会重复发解约。
     */
    public BaseRespDTO executeTermination(ExecuteTerminationReqDTO request) {
        BaseRespDTO response = new BaseRespDTO();
        if (request == null || !StringUtils.hasText(request.getRequestSignSeq())) {
            fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
            return response;
        }
        String requestSignSeq = request.getRequestSignSeq();

        AppTerminationRequest terminationRequest = terminationRequestMapper
                .selectByRequestSignSeq(requestSignSeq);
        if (terminationRequest == null) {
            terminationRequest = createTerminationRequest(request);
            if (terminationRequest == null) {
                fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED,
                        PaySignErrorCodeEnum.USER_NOT_SIGNED.getMsg());
                return response;
            }
        }

        String status = terminationRequest.getTerminationStatus();
        if (STATUS_SCANNING.equals(status) || STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status)) {
            fillSuccess(response);
            return response;
        }
        if (!STATUS_PENDING.equals(status)) {
            fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请状态不正确");
            return response;
        }

        if (terminationRequestMapper.markScanning(requestSignSeq, LocalDateTime.now()) == 0) {
            log.info("解约申请已被并发接手（未命中 PENDING），本次不再发起, requestSignSeq={}", requestSignSeq);
            fillSuccess(response);
            return response;
        }

        PaySignGatewayResponse gatewayResponse;
        try {
            gatewayResponse = contractDomainService.requestPayPlatformTermination(requestSignSeq);
        } catch (Exception e) {
            // 结果未知：MUST 保持 SCANNING 等 processTermination 主动查询收口，NEVER 退回 PENDING。
            log.error("调用支付平台解约异常，保持 SCANNING 待主动查询收口, requestSignSeq={}", requestSignSeq, e);
            auditLogger.write("EXECUTE_TERMINATION", terminationRequest.getThirdUserId(),
                    requestSignSeq, terminationRequest.getPaymentVendor(), null, request, null);
            throw new TerminationException("执行支付平台解约异常，requestSignSeq=" + requestSignSeq, e);
        }

        // 无事务，本条日志立即提交，无论后续成败都留得下请求响应快照（这正是去掉事务要换来的东西）。
        // THIRD_USER_ID / PAYMENT_VENDOR 是 NOT NULL 而本接口非必填，MUST 取自解约申请记录，否则 ORA-01400。
        auditLogger.write("EXECUTE_TERMINATION", terminationRequest.getThirdUserId(),
                requestSignSeq, terminationRequest.getPaymentVendor(), null, request, gatewayResponse);

        if (!paySignGateway.isSuccess(gatewayResponse)) {
            // 明确失败：把执行权交还扫表任务。CAS 影响 0 行说明已被回调收口，不再干预。
            if (terminationRequestMapper.revertScanningToPending(requestSignSeq) == 0) {
                log.warn("支付平台解约失败但状态已被改走，放弃回退 PENDING, requestSignSeq={}", requestSignSeq);
            }
            throw new TerminationException("调用支付平台解约失败，requestSignSeq=" + requestSignSeq
                    + ", gatewayResponse=" + JSON.toJSONString(gatewayResponse));
        }

        fillSuccess(response);
        return response;
    }

    /*
     * IF8A-75 直接解绑支付方式。
     *
     * 本方法只做「参数收敛 + 渠道短路 + 异常翻译」，解约动作整体委托 executeTermination：
     * 状态机、CAS 抢执行权、支付中心调用、日志落库、失败回退全在那边，NEVER 在此重写一份。
     *
     * 同类内部调用不经 Spring 代理，但 executeTermination 本身就不带 @Transactional
     * （见其上方注释），因此不存在「绕过事务代理」或「把 RPC 包进事务」的问题。
     * 本方法同样 NEVER 加 @Transactional。
     *
     * 不校验未结清欠费（用户 2026-09-08 裁决）：75 的语义是强制解绑，欠费由后续催收流程处理。
     */
    public UnbindAgreementResult unbindAgreement(UnbindAgreementReqDTO request) {
        UnbindAgreementResult response = new UnbindAgreementResult();
        String requestSignSeq = request == null ? null : request.getRequestSignSeq();
        if (!StringUtils.hasText(requestSignSeq)) {
            fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "requestSignSeq不能为空");
            return response;
        }
        requestSignSeq = requestSignSeq.trim();

        String paymentVendor = request.getPaymentVendor();
        if (paymentVendor != null) {
            paymentVendor = paymentVendor.trim();
        }
        // 入口级路径选择 MUST 穷尽（ADR-D109）：新增渠道类别时编译器会在这里报错。
        switch (PaymentChannels.classify(paymentVendor)) {
            case PaymentChannel.Wallet ignored -> {
                // 钱包渠道没有签约协议，走 executeTermination 会在 createTerminationRequest 处以
                // USER_NOT_SIGNED 收场，语义含糊；这里直接短路给出可操作的提示。
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM,
                        "钱包支付无签约协议，解绑请使用支付通道解绑接口");
                return response;
            }
            // 传统签约渠道继续走下面的解约申请流程。空分支是刻意的，NEVER 删。
            case PaymentChannel.Contracted ignored -> {
            }
        }

        ExecuteTerminationReqDTO executeRequest = new ExecuteTerminationReqDTO();
        executeRequest.setThirdUserId(request.getThirdUserId());
        executeRequest.setPaymentVendor(paymentVendor);
        executeRequest.setRequestSignSeq(requestSignSeq);
        // APP_TERMINATION_REQUEST.CARD_ID / CARD_TYPE 是 NOT NULL，而 APP_PAY_SIGN_INFO 的同名两列
        // 全库为 NULL（签约链路从不写它们），因此 createTerminationRequest 从签约记录回填必然 ORA-01400
        // （2026-09-08 实测）。票卡信息的真实来源是 account 侧 APP_USER_PAY_CHANNEL，
        // 其 REQ_CONTRACT_NO 即 requestSignSeq，MUST 先取回来塞进入参。
        fillCardInfoFromAccount(executeRequest, requestSignSeq);

        try {
            BaseRespDTO executeResponse = executeTermination(executeRequest);
            response.setRetCode(executeResponse.getRetCode());
            response.setRetMsg(executeResponse.getRetMsg());
        } catch (Exception e) {
            // executeTermination 在「网关异常」与「网关明确失败」两种情形下抛 TerminationException，
            // 状态已由它自己 CAS 收口（保持 SCANNING 或退回 PENDING），此处只翻译成应答码。
            log.error("直接解绑支付方式失败, requestSignSeq={}", requestSignSeq, e);
            fillError(response, PaySignErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE,
                    PaySignErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE.getMsg());
        }
        return response;
    }

    /**
     * 从 account-server 反查该签约流水对应的 CARD_ID / CARD_TYPE 并写入执行解约入参。
     *
     * <p>查不到或调用异常时<b>不中断</b>：入参维持为空，交由 {@code executeTermination} 按原有口径处理
     * ——若申请已存在则本来就不需要这两个字段；若申请不存在，补建会因 NOT NULL 失败并回落到 8007，
     * 与不调此方法的行为一致。这里刻意不抛异常，避免把「取不到辅助信息」放大成整个接口不可用。</p>
     */
    private void fillCardInfoFromAccount(ExecuteTerminationReqDTO executeRequest, String requestSignSeq) {
        try {
            // 2026-09-16 起走 AccountDomainPort（ADR-D94 续）。「必须答成功才采信」已上移到端口，
            // 本方法只做「把取到的票卡信息落到入参上」。
            // NotFound 与 Unreachable 都**不中断**、只记日志 —— 这是刻意的：取不到辅助信息
            // NEVER 放大成整个接口不可用，交由 executeTermination 按原口径处理
            // （申请已存在则本来不需要这两个字段；不存在则补建因 NOT NULL 失败并回落 8007）。
            switch (accountDomainPort.queryPayChannelByContract(requestSignSeq)) {
                case AccountQuery.Found<AccountPayChannelView> found -> {
                    AccountPayChannelView view = found.value();
                    executeRequest.setCardId(view.cardId());
                    executeRequest.setCardType(view.cardType());
                    if (!StringUtils.hasText(executeRequest.getThirdUserId())) {
                        executeRequest.setThirdUserId(view.thirdUserId());
                    }
                }
                case AccountQuery.NotFound<AccountPayChannelView> notFound ->
                        log.warn("未从 account 取到支付通道票卡信息, requestSignSeq={}, retCode={}, retMsg={}",
                                requestSignSeq, notFound.retCode(), notFound.retMsg());
                case AccountQuery.Unreachable<AccountPayChannelView> unreachable ->
                        log.error("从 account 查询支付通道票卡信息未获答复，继续按原入参执行, requestSignSeq={}",
                                requestSignSeq, unreachable.cause());
            }
        } catch (Exception e) {
            log.error("从 account 查询支付通道票卡信息异常，继续按原入参执行, requestSignSeq={}", requestSignSeq, e);
        }
    }

    /**
     * 解约申请缺失时按 requestTermination 的口径补建一条 PENDING 申请，再交回主流程执行。
     *
     * <p>字段口径与 {@code PaySignWorkflow.requestTermination} 完全一致：THIRD_USER_ID / CARD_ID /
     * CARD_TYPE / PAYMENT_VENDOR 四列在 APP_TERMINATION_REQUEST 中均为 NOT NULL，本接口除
     * requestSignSeq 外都非必填，缺字段时 MUST 用签约记录回填，否则 INSERT 抛 ORA-01400。
     * CREATE_TIME / UPDATE_TIME 由 insert 语句显式传入，为 null 同样触发 ORA-01400。</p>
     *
     * <p>签约记录不存在即拒绝：解约的前提是这条流水确实签过约，**NEVER** 凭一个流水号凭空造申请，
     * 否则等于允许任意可达方在库里写入无主记录并驱动支付平台解约。</p>
     *
     * <p>本方法在无事务上下文中执行，insert 立即提交：后续网关失败时这条 PENDING 申请**留在库里**，
     * 由扫表任务接着重试，不会像事务版那样连补建痕迹一起回滚掉。</p>
     *
     * @return 补建（或并发下由他人插入）的解约申请；签约记录不存在时返回 null
     */
    private AppTerminationRequest createTerminationRequest(ExecuteTerminationReqDTO request) {
        PaySignInfo signInfo = paySignInfoMapper.selectBySeq(
                request.getRequestSignSeq(), request.getPaymentVendor());
        if (signInfo == null) {
            log.warn("解约申请不存在且未查到签约记录，拒绝补建, requestSignSeq={}, paymentVendor={}",
                    request.getRequestSignSeq(), request.getPaymentVendor());
            return null;
        }
        AppTerminationRequest record = new AppTerminationRequest();
        record.setRequestSignSeq(request.getRequestSignSeq());
        record.setThirdUserId(StringUtils.hasText(request.getThirdUserId())
                ? request.getThirdUserId() : signInfo.getThirdUserId());
        record.setCardId(StringUtils.hasText(request.getCardId())
                ? request.getCardId() : signInfo.getCardId());
        record.setCardType(StringUtils.hasText(request.getCardType())
                ? request.getCardType() : signInfo.getCardType());
        record.setPaymentVendor(StringUtils.hasText(request.getPaymentVendor())
                ? request.getPaymentVendor() : signInfo.getPaymentVendor());
        record.setTerminationStatus(STATUS_PENDING);
        record.setNotifyStatus(STATUS_PENDING);
        record.setNotifyRetryCount(0);
        LocalDateTime now = LocalDateTime.now();
        record.setRequestTime(now);
        record.setCreateTime(now);
        record.setUpdateTime(now);
        try {
            terminationRequestMapper.insert(record);
        } catch (RuntimeException e) {
            if (!isDuplicateKeyViolation(e)) {
                throw e;
            }
            log.info("解约申请已被并发插入，改用库中记录继续, requestSignSeq={}", request.getRequestSignSeq());
            return terminationRequestMapper.selectByRequestSignSeq(request.getRequestSignSeq());
        }
        log.info("解约申请不存在，已按签约记录补建 PENDING 申请, requestSignSeq={}, thirdUserId={}",
                record.getRequestSignSeq(), record.getThirdUserId());
        return record;
    }

    /**
     * 异常链上是否有唯一键冲突。
     *
     * <p>{@code REQUEST_SIGN_SEQ} 上有唯一索引 {@code UK_ATR_REQUEST_SIGN_SEQ}：并发下别人刚插入同一条，
     * 回查以对方的记录继续走状态判定，<b>NEVER 把它当失败</b>——否则运维重试与批处理撞车时会假失败。
     *
     * <p><b>MUST 逐层遍历 cause，NEVER 直接 {@code catch (DuplicateKeyException)}</b>（ADR-D53）：
     * 本模块打开了 tracing，{@code MapperAspectToTrace} 会切到所有 {@code @Mapper} 方法上；它此前把异常包成
     * {@code new RuntimeException(e)}，单层类型判断在本项目里捕不到，冲突会直接冒到全局处理器。
     * 切面已改成原样抛出，这层按 cause 链判定作为第二道防线保留。<b>非唯一键冲突 MUST 原样上抛。</b></p>
     *
     * @param e 捕获到的异常
     * @return 链上出现过唯一键冲突即 true
     */
    private boolean isDuplicateKeyViolation(Throwable e) {
        for (Throwable cause = e; cause != null && cause != cause.getCause(); cause = cause.getCause()) {
            if (cause instanceof DuplicateKeyException) {
                return true;
            }
        }
        return false;
    }
}
