package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateReceiveSignResult;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateReceiveTerminationResult;
import static com.chinasofti.huateng.paysign.support.PaySignValues.normalizeVendor;
import static com.chinasofti.huateng.paysign.support.PaySignValues.parseDateTime;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.domain.SignStatus;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.support.PaymentChannel;
import com.chinasofti.huateng.paysign.support.PaymentChannels;
import com.chinasofti.huateng.paysign.constant.PaymentVendorEnum;
import com.chinasofti.huateng.paysign.domain.TerminationFailReason;
import com.chinasofti.huateng.paysign.domain.TerminationStatusTransition;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.event.SignResultCommittedEvent;
import com.chinasofti.huateng.paysign.exception.TerminationException;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import com.chinasofti.huateng.paysign.service.CallbackDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 回调领域服务：签约结果回调（IPD02）与解约结果回调（IPD03）的真实现。
 *
 * <p>2026-09-15 由 {@code PaySignWorkflow} **纯搬迁**而来（god class 按业务组拆分的第二组，
 * 紧随支付组）。方法体、日志措辞、判断顺序、字段赋值顺序、异常类型、事务注解与注释全部逐字保留，
 * <b>NEVER 在搬迁批次里顺手改逻辑</b>。等价性由 {@code PaySignWorkflowFixture} 经
 * {@code PaySignServiceImpl} 门面下钻的特征测试守着。</p>
 *
 * <p><b>两个入口的事务边界刻意不同，NEVER 对齐它们</b>：{@link #receiveSignResult} 带
 * {@code @Transactional} 并靠 {@code AFTER_COMMIT} 事件投递通知；
 * {@link #receiveTerminationResult} <b>刻意不带</b>（ADR-D48），自己用
 * {@link #transactionTemplate} 开短事务收口，事务外才出网。两者的理由分别写在各自方法头，
 * 都对应过生产事故。</p>
 *
 * <p><b>字段注入而非构造器注入是刻意的</b>：与 {@code PaySignWorkflow} /
 * {@code PaymentDomainServiceImpl} 保持同一种装配风格，测试夹具靠 {@code ReflectionTestUtils}
 * 按字段名注入 mock，改成构造器注入会让夹具整批失效。</p>
 *
 * <p><b>{@code receivePayResult} 不在本类</b>：支付结果回调的真实现随支付组一起搬进了
 * {@code PaymentDomainServiceImpl}，{@code PaySignServiceImpl} 现在直接路由到支付领域。
 * <b>NEVER 在这里加回一层转发</b> —— 那正是「回调组依赖支付组」这条横向依赖的来源。</p>
 */
@Service
public class CallbackDomainServiceImpl implements CallbackDomainService {
    private static final Logger log = LoggerFactory.getLogger(CallbackDomainServiceImpl.class);

    private static final String STATUS_SIGNED = "SIGNED";
    private static final String STATUS_UNSIGNED = "UNSIGNED";

    /**
     * 解约申请状态，取值来自 {@link TerminationStatus}。
     *
     * <p><b>NEVER 用这几个常量表达签约状态或流水日志的 SIGN_STATUS</b>：日志用的失败态是
     * {@link #SIGN_LOG_STATUS_FAILED}，与本常量字面量相同、语义无关，<b>NEVER 合并</b>。
     */
    private static final String STATUS_FAILED = TerminationStatus.FAILED.name();

    private static final String STATUS_SCANNING = TerminationStatus.SCANNING.name();
    private static final String STATUS_SUCCESS = TerminationStatus.SUCCESS.name();

    /**
     * 写入 {@code APP_PAY_SIGN_LOG.SIGN_STATUS} 的失败态，取值来自 {@link SignStatus}。
     * 与 {@link #STATUS_FAILED} 字面量相同、语义无关，<b>NEVER 合并</b>。
     */
    private static final String SIGN_LOG_STATUS_FAILED = SignStatus.FAILED.name();

    /*
     * 本类**不再持有** WALLET_PAYMENT_VENDOR 常量（2026-09-16，ADR-D108）。
     * 渠道判定一律走 support/PaymentChannels.isWallet(...)。原因：收口前这个常量在本模块有 5 份副本、
     * 判断有 7 处且归一化时机三种写法并存，新增渠道要改 7 处、漏一处不编译失败也不告警
     * （只会让钱包用户在其中一条链路上被当成普通签约渠道处理）。
     * NEVER 在本类重新声明该常量，也 NEVER 直接写 PaymentVendorEnum.WALLET.getCode() 或字面量 "0B"。
     */

    private final PaySignInfoMapper paySignInfoMapper;
    private final PaySignRequestMapper paySignRequestMapper;
    private final AppTerminationRequestMapper terminationRequestMapper;
    private final AppNotifyService appNotifyService;
    /** 接口流水（{@code APP_PAY_SIGN_REQUEST}）的唯一写入点，<b>NEVER 在本类重建一份 writeLog</b>。 */
    private final PaySignAuditLogger auditLogger;
    /*
     * 只用于在 @Transactional 方法内发布「已提交」事件，让通知投递落到 afterCommit。
     * publishEvent 本身是纯内存操作，不碰连接、不出网，放在事务内是安全的。
     * NEVER 用它替代 appNotifyService 的直接调用——只有事务内的通知才需要绕这一层。
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 解约回调的本地写入用它显式开短事务（ADR-D8 第一处）。
     *
     * <p>Spring Boot 的 {@code TransactionAutoConfiguration} 在只有一个 {@code PlatformTransactionManager}
     * 时会自动注册这个 Bean，**不需要自己 {@code @Bean}**；account-server 的
     * {@code PhoneChangeServiceImpl} 用的是同一个来源。</p>
     *
     * <p><b>NEVER 给 {@link #receiveTerminationResult} 加回 {@code @Transactional}</b>：
     * 它尾部要调 account-server 删支付通道（一次出网 HTTP）。</p>
     */
    private final TransactionTemplate transactionTemplate;

    /** 通道清理的唯一投递点，快速路径与扫表补偿共用，NEVER 在本类复制其三分支处置（ADR-D48）。 */
    private final ChannelSyncDeliverer channelSyncDeliverer;

    /**
     * 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备，
     * 且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 {@code Could not find field}。
     * <b>NEVER 退回 {@code @Autowired} 字段注入。</b>
     */
    public CallbackDomainServiceImpl(
            PaySignInfoMapper paySignInfoMapper,
            PaySignRequestMapper paySignRequestMapper,
            AppTerminationRequestMapper terminationRequestMapper,
            AppNotifyService appNotifyService,
            PaySignAuditLogger auditLogger,
            ApplicationEventPublisher eventPublisher,
            TransactionTemplate transactionTemplate,
            ChannelSyncDeliverer channelSyncDeliverer) {
        this.paySignInfoMapper = paySignInfoMapper;
        this.paySignRequestMapper = paySignRequestMapper;
        this.terminationRequestMapper = terminationRequestMapper;
        this.appNotifyService = appNotifyService;
        this.auditLogger = auditLogger;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = transactionTemplate;
        this.channelSyncDeliverer = channelSyncDeliverer;
    }

    /**
     * IPD02 签约结果回调。
     * 支付平台异步通知签约结果时，以回调报文为准刷新本地签约主表。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel) {
        PaySignCallbackResult response = new PaySignCallbackResult();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                auditLogger.write("RECEIVE_SIGN_RESULT", null, null, null, null, null, response);
                return response;
            }
            // 钱包渠道（0B）自 2026-09-15 起也走支付中心签约，因此**必须**接受它的签约结果回调。
            //
            // 原实现在这里对 0B 直接返 INVALID_PARAM「钱包支付不支持签约结果回调」。那与
            // ContractDomainServiceImpl.requestSignInfo 的钱包短路是同一个前提的两半：
            // 「钱包不签约、只靠 thirdPayId 扣款」。该前提已被支付中心实测推翻（withholding 强制要
            // requestSignSeq，见 requestSignInfo 方法内注释与 PAY_TXN_DETAIL 里 0B 渠道零条 SUCCESS）。
            // 放开短路的直接目的：让 0B 的签约成功回调能落 APP_PAY_SIGN_INFO ——
            // 那张表是 requestPay 取 requestSignSeq 的权威来源，缺这一行钱包扣款永远拿不到有效协议。
            //
            // NEVER 只放开 requestSignInfo 而把这里留着：那样签约请求发得出去、结果却永远进不了库，
            // 表现为「支付中心侧已签约、我方 APP_PAY_SIGN_INFO 仍零行」的静默不一致。
            String validMsg = validateReceiveSignResult(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                auditLogger.write("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            // 支付平台回调可能不带 thirdUserId，尝试从流水表补充
            String thirdUserId = resolveThirdUserId(request.getRequestSignSeq(), request.getThirdUserId());
            if (!StringUtils.hasText(thirdUserId)) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "thirdUserId不能为空");
                auditLogger.write("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }
            request.setThirdUserId(thirdUserId);

            // 支付平台回调可能不带 displayAccount，尝试从流水表补充
            String displayAccount = resolveDisplayAccount(request.getRequestSignSeq(), request.getDisplayAccount());
            request.setDisplayAccount(displayAccount);

            boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getStatus());

            log.info("签约成功，准备通知app--- {} ., displayAccount: {}",request,displayAccount);
            if (isSuccess) {
                // 1. INSERT 签约成功记录
                PaySignInfo signInfo = new PaySignInfo();
                signInfo.setRequestSignSeq(request.getRequestSignSeq());
                signInfo.setThirdUserId(request.getThirdUserId());
                signInfo.setPaymentVendor(request.getPaymentVendor());
                signInfo.setSignChannel(signChannel);
                signInfo.setDisplayAccount(request.getDisplayAccount());
                signInfo.setPayAccountId(request.getPayUserId());
                signInfo.setPayAgreementNo(request.getPayAgreementNo());
                signInfo.setContractStatus(STATUS_SIGNED);
                signInfo.setSignTime(parseDateTime(request.getSignTime(), null));
                paySignInfoMapper.insert(signInfo);

                // 2. 写入流水表
                PaySignRequest logRecord = new PaySignRequest();
                logRecord.setRequestSignSeq(request.getRequestSignSeq());
                logRecord.setThirdUserId(request.getThirdUserId());
                logRecord.setPaymentVendor(request.getPaymentVendor());
                logRecord.setSignChannel(signChannel);
                logRecord.setOperationType("RECEIVE_SIGN_RESULT");
                logRecord.setSignStatus(STATUS_SIGNED);
                logRecord.setPayAccountId(request.getPayUserId());
                logRecord.setPayAgreementNo(request.getPayAgreementNo());
                logRecord.setCreateTms(LocalDateTime.now());
                logRecord.setNotifyStatus("PENDING");
                logRecord.setNotifyRetryCount(0);
                paySignRequestMapper.insert(logRecord);

                // 3. 发布「已提交」事件，由 SignResultCommittedListener 在 afterCommit 投递 APP 通知。
                //    NEVER 在这里直接调 appNotifyService：本方法带 @Transactional，事务若在提交阶段
                //    失败回滚，APP 已收到「签约成功」而 APP_PAY_SIGN_INFO 并没有这行（AGENTS.md §5.2）。
                //    原先此处还有一次 selectByRequestSignSeq 回填自增主键，已随通知一起挪进监听器，
                //    并按 PaySignRequestMapper 的要求换成带 OPERATION_TYPE 过滤的
                //    selectLatestSignResultBySeq——不带过滤会取到 OPERATION_TYPE='SIGN' 那行。
                eventPublisher.publishEvent(new SignResultCommittedEvent(
                        request.getRequestSignSeq(), request.getPaymentVendor(), request));
            } else {
                // 签约失败，记录流水但不通知
                auditLogger.write("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(),
                         request.getPaymentVendor(), signChannel, request, response, SIGN_LOG_STATUS_FAILED);
            }

            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("处理签约结果通知异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            auditLogger.write("RECEIVE_SIGN_RESULT", request != null ? request.getThirdUserId() : null, request != null ? request.getRequestSignSeq() : null, request != null ? request.getPaymentVendor() : null, signChannel, request, response);
            return response;
        }
    }

    /**
     * IPD03 解约结果回调。
     * 支付平台异步通知解约结果时，更新解约申请状态并执行后续清理。
     */
    /*
     * 本方法 NEVER 加 @Transactional（2026-09-12 / ADR-D48 摘掉，此前一直带着）。
     *
     * 原因：成功分支要调 account-server 删支付通道。包在事务里，APP_TERMINATION_REQUEST 那一行的
     * 排他锁就持满整个 RPC 往返，支付中心对同一笔的重推全部堆在同一行上串行等待；等待超过 Druid
     * remove-abandoned-timeout（sql.properties:44）后连接被强杀、commit 抛 connection closed，
     * 整个事务连同「留证据」的流水一起丢弃——这正是 2026-08-26 生产事故（订单
     * GT20260826210647653586419 循环重推 8 分钟）的形态，孪生方法 receivePayResult 早已按此改造。
     *
     * 摘掉注解的前提是 CHANNEL_SYNC_* 已接线：本地事务先把 TERMINATION_STATUS 收成 SUCCESS 并同时
     * 置 CHANNEL_SYNC_STATUS='PENDING'（两者 MUST 同事务），提交后再出网删通道、按 RpcOutcome 落
     * CHANNEL_SYNC_*，失败留给 /internal/termination/compensateChannelSync 重推。
     * NEVER 在没有这套落库状态的前提下摘注解——那样本地会先提交成 SUCCESS，重推撞上幂等短路，
     * 账户域的通道就永远删不掉，比「粗暴回滚 + 靠上游重推」更糟。
     *
     * 早期返回路径上的 writeLog 现在各自自动提交，这是想要的：回滚不再吞掉证据。
     */
    @Override
    public BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel) {
        BaseRespDTO response = new BaseRespDTO();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                auditLogger.write("RECEIVE_TERMINATION_RESULT", null, null, null, null, null, response);
                return response;
            }
            // 入口级路径选择 MUST 穷尽（ADR-D109）：新增渠道类别时编译器会在这里报错 ——
            // pattern switch 语句与表达式**都**校验穷尽性（2026-09-16 实测）。
            // NEVER 退回 if (isWallet(...))：那个 else 隐含「其余一切按传统签约处理」，漏改不报错。
            switch (PaymentChannels.classify(request.getPaymentVendor())) {
                case PaymentChannel.Wallet ignored -> {
                    // 钱包解绑由 requestAgreeRelease 同步完成，不依赖支付平台解约回调。
                    // 这里仅记录审计并返回幂等成功，避免误删 APP_PAY_SIGN_INFO 或传统签约数据。
                    fillSuccess(response);
                    response.setMsg("钱包支付不需要解约回调");
                    response.setRetMsg("钱包支付不需要解约回调");
                    auditLogger.write("RECEIVE_TERMINATION_RESULT_WALLET_IGNORED", request.getThirdUserId(),
                            request.getRequestSignSeq(), PaymentChannels.walletCode(),
                            signChannel, request, response);
                    return response;
                }
                // 传统签约渠道无需前置处理，继续走下面的主干。
                // 空分支是刻意的：它是「这个类别已被考虑过」的证据，NEVER 删。
                case PaymentChannel.Contracted ignored -> {
                }
            }
            String validMsg = validateReceiveTerminationResult(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            // 支付平台回调可能不带 thirdUserId，尝试从流水表补充
            String thirdUserId = resolveThirdUserId(request.getRequestSignSeq(), request.getThirdUserId());
            if (!StringUtils.hasText(thirdUserId)) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "thirdUserId不能为空");
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }
            request.setThirdUserId(thirdUserId);

            // 支付平台回调可能不带 cardId/cardType，尝试从签约主表补充
            resolveCardInfoFromSignInfo(request);

            // 1. 查询解约申请记录
            AppTerminationRequest terminationRequest = terminationRequestMapper.selectByRequestSignSeq(request.getRequestSignSeq());
            if (terminationRequest == null) {
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请不存在");
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            // 2. 幂等校验：已处理过的直接返回
            if (STATUS_SUCCESS.equals(terminationRequest.getTerminationStatus()) || STATUS_FAILED.equals(terminationRequest.getTerminationStatus())) {
                fillSuccess(response);
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            // 2.1 cardId/cardType 兜底：APP_PAY_SIGN_INFO 的这两列全库为 NULL，
            // 上面的 resolveCardInfoFromSignInfo 补不出来，而 APP_TERMINATION_REQUEST 落库时带了真值。
            // 不补齐会让 removeAccountPayChannel 带 null 调 account-server，参数校验直接失败，
            // 进而抛 TerminationException 回滚——支付渠道已解约、本地全部回退，且重试永远在同一处失败。
            // 已发生事故：2026-09-09 requestSignSeq=0052294801523908。
            resolveCardInfoFromTerminationRequest(request, terminationRequest);

            // 3. 校验解约申请状态必须为 SCANNING（已触发 T+4 日扫描并调用支付平台解约）
            if (!STATUS_SCANNING.equals(terminationRequest.getTerminationStatus())) {
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请不存在或状态不正确");
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getStatus());

            if (isSuccess) {
                // 阶段一：本地事务收口。事务内 NEVER 出网，理由见方法头「NEVER 加 @Transactional」。
                //
                // ADR-D48 之前这里是「事务内先调 account-server 删通道，失败即抛异常整单回滚」。
                // 那个形态同时违反 AGENTS.md §5.2 的两条（事务内 RPC、事务内提交异步通知），
                // 且失败只能靠支付中心重推自愈。现在改成 outbox：本地收口 + 落 PENDING，提交后再出网。
                //
                // markSuccess 与 initChannelSyncPending MUST 在同一个事务里：两者之间崩掉会留下
                // TERMINATION_STATUS='SUCCESS' 而 CHANNEL_SYNC_STATUS=NULL，而补偿扫表**刻意不捞 NULL**
                // （那是改造前的历史行，见 outbox.md 的第三个坑），这一笔的通道清理就永久丢失。
                record LocalClosure(PaySignInfo signInfo, TerminationStatusTransition.Result transit) {
                }
                LocalClosure closure = transactionTemplate.execute(txStatus -> {
                    // 删除前先查，通知报文要用
                    PaySignInfo local = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());
                    paySignInfoMapper.deleteByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());

                    // 写入签约流水日志
                    PaySignRequest logRecord = new PaySignRequest();
                    logRecord.setRequestSignSeq(request.getRequestSignSeq());
                    logRecord.setThirdUserId(request.getThirdUserId());
                    logRecord.setPaymentVendor(request.getPaymentVendor());
                    logRecord.setSignChannel(signChannel);
                    logRecord.setOperationType("RECEIVE_TERMINATION_RESULT");
                    logRecord.setSignStatus(STATUS_UNSIGNED);
                    logRecord.setCardId(request.getCardId());
                    logRecord.setCardType(request.getCardType());
                    logRecord.setTerminationTime(request.getDismissalTime());
                    logRecord.setCreateTms(LocalDateTime.now());
                    logRecord.setNotifyStatus("PENDING");
                    logRecord.setNotifyRetryCount(0);
                    paySignRequestMapper.insert(logRecord);

                    // 收口成 SUCCESS（单条 CAS，WHERE 带 TERMINATION_STATUS = 'SCANNING'）。
                    // 2026-09-12：原先是 updateStatus + updateCompleteTime + updateNotifyStatus 三条
                    // 无 CAS 语句，而 updateStatus 的 WHERE 只有 REQUEST_SIGN_SEQ —— expireScanning 的
                    // Quartz 任务与本回调并发时，已被打成 FAILED 的申请会被无条件改回 SUCCESS，
                    // 而 APP 已经收到过失败通知。NEVER 退回那种写法。
                    TerminationStatusTransition.Result t = TerminationStatusTransition.classify(
                            terminationRequestMapper.markSuccess(request.getRequestSignSeq(), LocalDateTime.now()),
                            TerminationStatus.SUCCESS,
                            () -> terminationRequestMapper.selectTerminationStatusBySeq(request.getRequestSignSeq()));
                    if (t.isDone()) {
                        // 只在确实由本次调用收口时才置「通道清理待投递」。
                        // IDEMPOTENT 说明另一路已经收口过、那一路自己会置；这里再置一次会把可能已经
                        // 落成 SUCCESS 的 CHANNEL_SYNC_STATUS 打回 PENDING，导致通道被重复删一次。
                        terminationRequestMapper.initChannelSyncPending(request.getRequestSignSeq());
                    }
                    return new LocalClosure(local, t);
                });

                TerminationStatusTransition.Result transit = closure.transit();
                if (transit.isDone()) {
                    // 阶段二：先提交 APP 通知（异步、立即返回），再走删通道的快速路径。
                    // NEVER 反过来：account-server 慢或不可达时会把 APP 通知一起拖住，而通道清理
                    // 已经落成 PENDING、补偿一定会重推，这条通知不该等它。
                    appNotifyService.asyncNotifyTerminationResult(terminationRequest, closure.signInfo(), request);
                    syncChannelRemovalAfterCommit(request);
                } else if (transit.isConflict()) {
                    // CAS 未命中、且回查到的不是目标态：状态在 :1176 的 select 与此处之间被并发改走。
                    // NEVER 在这里补发成功通知：CONFLICT 多半是 expireScanning 抢先打成 FAILED、
                    // 失败通知已发出，再发一条成功通知就是给 APP 两条相反结果。
                    // （IDEMPOTENT 是另一路已正常收口，走下面那个 else，NEVER 与本分支合并。）
                    //
                    // NEVER 改成抛异常回滚：账户域通道的 RPC 已经出网、回滚不掉，而本地一回滚
                    // 签约记录就留着，下一轮重推在 :1184 的幂等短路直接返回成功，通道永远清不掉。
                    //
                    // 2026-09-12：CONFLICT 落 FAILED 时，把「需人工核对」写进 FAIL_REASON。
                    // 此前这条路径只有下面这行 ERROR 日志，日志滚掉就彻底失联；现在运维能用
                    // FAIL_REASON LIKE '%需人工核对:解约结果矛盾%' 把这些行捞出来。
                    // 仍 NEVER 自动订正状态：FAILED -> SUCCESS 已被明确禁止（见 TerminationStatus），
                    // 且 APP 已收到失败通知，要不要让用户「反悔」是业务裁决，不是并发处置。
                    // 人工恢复口径：先查支付中心 /api/v1/contract/queryResult，status=UNSIGNED
                    // 即确认协议真已注销，再决定订正库内状态还是引导用户重新签约
                    // （此时 :403 查不到签约记录会返 8011，用户自己走不通）。
                    // 这与 docs/business/pay-sign.md §SCANNING NEVER 无上限重试 里
                    // 「expired 非 0 即需人工核对」是同一个出口。
                    log.error("解约成功收口未命中 SCANNING，已跳过成功通知，MUST 人工核对, "
                                    + "requestSignSeq={}, outcome={}, observedStatus={}",
                            request.getRequestSignSeq(), transit.outcome(), transit.observedStatus());

                    if (TerminationStatus.FAILED.name().equals(transit.observedStatus())) {
                        int marked = terminationRequestMapper.markConflictForManualReview(
                                request.getRequestSignSeq(),
                                TerminationFailReason.successConflictNote(),
                                TerminationFailReason.MANUAL_REVIEW_MARK);
                        // 0 行的两个正常原因：本行已标记过（重推）、状态又被改走。两者都不是失败。
                        log.warn("解约结果矛盾已留痕, requestSignSeq={}, markedRows={}",
                                request.getRequestSignSeq(), marked);
                    }
                } else {
                    // IDEMPOTENT：CAS 返 0 行但回查已是 SUCCESS —— 另一路（批处理的同步 confirm，
                    // 或另一次回调重放）先收口了，APP 通知与通道清理都由那一路负责，这里再做任何事
                    // 都是重复投递。
                    //
                    // NEVER 把这条打成 ERROR / 「MUST 人工核对」：解约收口本就是「主动查 queryResult
                    // 为准 + 回调作快速路径」双路驱动（AGENTS.md §8），两路撞同一个 CAS 是设计内的
                    // 常态而非异常。2026-09-14 实测 requestSignSeq=0052294901523920，两路相差 46ms，
                    // 库内三态全 SUCCESS、通道行也已删除，却报出一条要求人工核对的 ERROR。
                    log.info("解约成功收口已由另一路完成，本次按幂等跳过, requestSignSeq={}, observedStatus={}",
                            request.getRequestSignSeq(), transit.observedStatus());
                }
            } else {
                // 9. 解约失败，更新解约申请表为最终失败状态（单条 CAS，同上）
                String failReason = StringUtils.hasText(request.getStatus()) ? request.getStatus() : "支付平台解约失败";
                TerminationStatusTransition.Result rejectTransit = TerminationStatusTransition.classify(
                        terminationRequestMapper.rejectScanning(request.getRequestSignSeq(), failReason, LocalDateTime.now()),
                        TerminationStatus.FAILED,
                        () -> terminationRequestMapper.selectTerminationStatusBySeq(request.getRequestSignSeq()));

                if (rejectTransit.isDone()) {
                    // 10. 异步通知App解约失败
                    NotifyTerminationFailedReqDTO failedNotifyRequest = new NotifyTerminationFailedReqDTO();
                    failedNotifyRequest.setThirdUserId(request.getThirdUserId());
                    failedNotifyRequest.setRequestSignSeq(request.getRequestSignSeq());
                    failedNotifyRequest.setPaymentVendor(request.getPaymentVendor());
                    failedNotifyRequest.setCardId(request.getCardId());
                    failedNotifyRequest.setCardType(request.getCardType());
                    failedNotifyRequest.setFailReason(failReason);
                    appNotifyService.asyncNotifyTerminationFailed(terminationRequest, failedNotifyRequest);
                } else {
                    // 同成功分支：不补发通知。CONFLICT 在这里的典型成因是另一路回调已收口成 SUCCESS，
                    // 此时发失败通知会把「已解约」说成「解约失败」，比不发更糟。
                    //
                    // 2026-09-12：观察到 SUCCESS 时同样落库留痕。两条回调结论相反是必须有人看见的事，
                    // 而「以哪条为准」是业务裁决 —— 留痕只陈述事实、NEVER 自动订正状态。
                    // 这一条不会泄漏给 APP：SUCCESS 行走成功通知分支，
                    // doNotifyTerminationResult 把 terminationResultMsg 恒置空串、不读 FAIL_REASON。
                    log.error("解约失败收口未命中 SCANNING，已跳过失败通知, "
                                    + "requestSignSeq={}, outcome={}, observedStatus={}",
                            request.getRequestSignSeq(), rejectTransit.outcome(), rejectTransit.observedStatus());

                    if (TerminationStatus.SUCCESS.name().equals(rejectTransit.observedStatus())) {
                        int marked = terminationRequestMapper.markFailureConflictForManualReview(
                                request.getRequestSignSeq(),
                                TerminationFailReason.failureConflictNote(),
                                TerminationFailReason.MANUAL_REVIEW_MARK);
                        log.warn("解约结果矛盾已留痕（本次答复失败、库内已成功）, requestSignSeq={}, markedRows={}",
                                request.getRequestSignSeq(), marked);
                    }
                }

                // 记录流水
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(),
                         request.getPaymentVendor(), signChannel, request, response, SIGN_LOG_STATUS_FAILED);
            }

            fillSuccess(response);
            return response;
        } catch (TerminationException e) {
            log.error("处理解约结果通知异常", e);
            throw e;
        } catch (Exception e) {
            log.error("处理解约结果通知异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            auditLogger.write("RECEIVE_TERMINATION_RESULT", request != null ? request.getThirdUserId() : null, request != null ? request.getRequestSignSeq() : null, request != null ? request.getPaymentVendor() : null, signChannel, request, response);
            return response;
        }
    }

    /**
     * 支付平台回调可能不带 thirdUserId，尝试从流水表补充。
     */
    private String resolveThirdUserId(String requestSignSeq, String thirdUserId) {
        if (StringUtils.hasText(thirdUserId)) {
            return thirdUserId;
        }
        if (!StringUtils.hasText(requestSignSeq)) {
            return null;
        }
        try {
            PaySignRequest record = paySignRequestMapper.selectByRequestSignSeq(requestSignSeq);
            if (record != null && StringUtils.hasText(record.getThirdUserId())) {
                log.info("从流水表补充 thirdUserId, requestSignSeq={}, thirdUserId={}", requestSignSeq, record.getThirdUserId());
                return record.getThirdUserId();
            }
        } catch (Exception e) {
            log.warn("查询流水表补充 thirdUserId 异常, requestSignSeq={}", requestSignSeq, e);
        }
        return null;
    }

    /**
     * 支付平台回调可能不带 displayAccount，尝试从流水表补充。
     */
    private String resolveDisplayAccount(String requestSignSeq, String displayAccount) {
        if (StringUtils.hasText(displayAccount)) {
            return displayAccount;
        }
        if (!StringUtils.hasText(requestSignSeq)) {
            return null;
        }
        try {
            PaySignRequest record = paySignRequestMapper.selectByRequestSignSeq(requestSignSeq);
            if (record != null && StringUtils.hasText(record.getDisplayAccount())) {
                log.info("从流水表补充 displayAccount, requestSignSeq={}, displayAccount={}", requestSignSeq, record.getDisplayAccount());
                return record.getDisplayAccount();
            }
        } catch (Exception e) {
            log.warn("查询流水表补充 displayAccount 异常, requestSignSeq={}", requestSignSeq, e);
        }
        return null;
    }

    /**
     * 支付平台回调可能不带 cardId/cardType，尝试从签约主表补充。
     * 解约回调时，签约记录还存在（尚未 DELETE），可反查补齐。
     */
    private void resolveCardInfoFromSignInfo(ReceiveTerminationResultReqDTO request) {
        if (StringUtils.hasText(request.getCardId()) && StringUtils.hasText(request.getCardType())) {
            return;
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return;
        }
        try {
            PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getRequestSignSeq(), request.getPaymentVendor());
            if (signInfo != null) {
                if (!StringUtils.hasText(request.getCardId()) && StringUtils.hasText(signInfo.getCardId())) {
                    request.setCardId(signInfo.getCardId());
                    log.info("从签约主表补充 cardId, requestSignSeq={}, cardId={}", request.getRequestSignSeq(), signInfo.getCardId());
                }
                if (!StringUtils.hasText(request.getCardType()) && StringUtils.hasText(signInfo.getCardType())) {
                    request.setCardType(signInfo.getCardType());
                    log.info("从签约主表补充 cardType, requestSignSeq={}, cardType={}", request.getRequestSignSeq(), signInfo.getCardType());
                }
            }
        } catch (Exception e) {
            log.warn("查询签约主表补充 cardId/cardType 异常, requestSignSeq={}", request.getRequestSignSeq(), e);
        }
    }

    /**
     * 从解约申请记录补齐 cardId/cardType。
     *
     * <p>为什么不能只依赖 {@link #resolveCardInfoFromSignInfo}：`APP_PAY_SIGN_INFO.CARD_ID` /
     * `CARD_TYPE` 全库为 NULL（`docs/business/pay-sign.md` 已记载），真值只在
     * `APP_TERMINATION_REQUEST` 里。**NEVER** 删掉本兜底。</p>
     */
    private void resolveCardInfoFromTerminationRequest(ReceiveTerminationResultReqDTO request,
                                                       AppTerminationRequest terminationRequest) {
        if (terminationRequest == null) {
            return;
        }
        if (!StringUtils.hasText(request.getCardId()) && StringUtils.hasText(terminationRequest.getCardId())) {
            request.setCardId(terminationRequest.getCardId());
            log.info("从解约申请表补充 cardId, requestSignSeq={}, cardId={}",
                    request.getRequestSignSeq(), terminationRequest.getCardId());
        }
        if (!StringUtils.hasText(request.getCardType()) && StringUtils.hasText(terminationRequest.getCardType())) {
            request.setCardType(terminationRequest.getCardType());
            log.info("从解约申请表补充 cardType, requestSignSeq={}, cardType={}",
                    request.getRequestSignSeq(), terminationRequest.getCardType());
        }
    }

    /**
     * 解约本地事务**提交之后**去账户域删支付通道（ADR-D48）。
     *
     * <p>这是 outbox 的**快速路径**，不是唯一路径：本地事务已经把 {@code CHANNEL_SYNC_STATUS} 置成
     * {@code PENDING}，本方法无论成败，滞留的 {@code PENDING} 与 {@code FAILED} 都会被
     * {@code POST /internal/termination/compensateChannelSync} 重推。
     *
     * <p>三分支处置在 {@link ChannelSyncDeliverer} 里**只有一处实现**，补偿侧走的是同一个方法
     * —— **NEVER 在这里复制一份**，那正是 ADR-D46 已经吃过一次亏的形态。
     * {@code deliver} 自己兜住全部异常，因此本方法也不会因它抛出而让支付中心误判回调失败。
     */
    private void syncChannelRemovalAfterCommit(ReceiveTerminationResultReqDTO request) {
        channelSyncDeliverer.deliver(request.getRequestSignSeq(), request.getThirdUserId(),
                request.getPaymentVendor(), request.getCardId(), request.getCardType());
    }
}
