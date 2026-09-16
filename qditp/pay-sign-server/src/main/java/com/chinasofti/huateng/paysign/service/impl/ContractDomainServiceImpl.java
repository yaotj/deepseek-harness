package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillContractResult;
import static com.chinasofti.huateng.paysign.support.TerminationRules.buildTerminationRequest;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateContractQuery;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateRequestSignInfo;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateRequestTermination;
import static com.chinasofti.huateng.paysign.support.PaySignValues.defaultString;
import static com.chinasofti.huateng.paysign.support.PaySignValues.normalizeVendor;
import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;
import static com.chinasofti.huateng.paysign.support.PaymentChannels.isWallet;
import static com.chinasofti.huateng.paysign.support.PaymentChannels.walletCode;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseResult;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.model.domain.SignStatus;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.domain.SignStatusTransition;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.paysign.port.AccountQuery;
import com.chinasofti.huateng.paysign.port.AccountUserView;
import com.chinasofti.huateng.paysign.service.ContractDomainService;
import com.chinasofti.huateng.paysign.port.ContractGatewayPort;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import com.chinasofti.huateng.paysign.support.PaymentChannel;
import com.chinasofti.huateng.paysign.support.PaymentChannels;
import com.chinasofti.huateng.paysign.support.ContractResultOutcome;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 签约 + 解约领域的真实现。
 *
 * <p>2026-09-15 由 {@code PaySignWorkflow} 整体搬迁而来（god class 拆分的最后一组），
 * 该类随之删除。搬迁为**纯移动**：方法体、日志措辞、判断顺序、异常类型与事务边界逐字保留。</p>
 */
@Service
public class ContractDomainServiceImpl implements ContractDomainService {

    private static final Logger log = LoggerFactory.getLogger(ContractDomainServiceImpl.class);
    private static final String STATUS_NOT_SIGNED = "NOT_SIGNED";
    private static final String STATUS_SIGNED = "SIGNED";
    private static final String STATUS_UNSIGNED = "UNSIGNED";
    /**
     * 解约申请状态，取值来自 {@link TerminationStatus}。
     *
     * <p><b>NEVER 用这个常量表达签约状态或流水日志的 SIGN_STATUS</b>：本类此前只有一个
     * {@code STATUS_FAILED}，同时被解约状态判断（{@code :571} / 幂等短路）和
     * {@code auditLogger.write(..., signStatus)} 两处使用，等于两台状态机共用一个常量 ——
     * 一旦某天要改其中一台的取值，另一台会被静默带走。日志用的那个已拆成独立常量，
     * 2026-09-15 随回调组搬到 {@code CallbackDomainServiceImpl.SIGN_LOG_STATUS_FAILED}。
     */
    private static final String STATUS_FAILED = TerminationStatus.FAILED.name();

    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();

    /*
     * 本类**不再持有** WALLET_PAYMENT_VENDOR 常量（2026-09-16，ADR-D108）。
     * 渠道判定一律走 support/PaymentChannels：判断用 isWallet(...)、要值用 walletCode()。
     * 原因：收口前这个常量在本模块有 5 份副本、「是不是钱包」的判断有 7 处且归一化时机三种写法并存，
     * 新增渠道要改 7 处、漏一处不编译失败也不告警。
     * NEVER 在本类重新声明该常量，也 NEVER 直接写 PaymentVendorEnum.WALLET.getCode() 或字面量 "0B"。
     */

    private final PaySignInfoMapper paySignInfoMapper;

    private final PaySignRequestMapper paySignRequestMapper;
    private final AppTerminationRequestMapper terminationRequestMapper;
    /**
     * 接口流水（{@code APP_PAY_SIGN_REQUEST}）的唯一写入点，2026-09-14 由本类的 {@code writeLog} 抽出。
     *
     * <p>本类 52 处调用它，另有 {@code TerminationProcessor} 与 {@code TerminationInternalServiceImpl}
     * 也各自直接注入 —— 那两处此前是**反向**调回本类的 {@code paySignWorkflow.writeLog(...)}，
     * 等于把业务类当公共工具库使唤。<b>NEVER 把审计流水的写入再搬回本类</b>。</p>
     */
    private final PaySignAuditLogger auditLogger;

    /**
     * 账户域出向调用的**唯一**出口（ADR-D46 写侧 / ADR-D94 续读侧）。
     *
     * <p>写方法返回 {@link RpcOutcome}、读方法返回 {@code AccountQuery}，两者都是三分类型。
     * <b>本类已不再注入 {@code AccountClient}</b>（2026-09-16 起最后一处读调用
     * {@code queryUserInfo} 也收进端口），<b>NEVER 把它加回来</b> —— 新增账户域调用一律加到端口上。</p>
     */
    private final AccountDomainPort accountDomainPort;

    /**
     * 支付中心**签约/解约方向**出向调用的唯一出口（2026-09-16，ADR-D112）。
     *
     * <p>本类此前直接注 {@code PaySignGateway} + {@code PaySignProperties}，于是
     * 「取哪个 URL、组哪份 bizData、怎么判成功」在本类里重复了 <b>5 次</b>；
     * 而 {@code PaySignProperties} 除了取 URL 与 {@code defaultNotifyUrl} 之外别无用处。
     * 现在这三件事收进 {@code ContractGatewayAdapter} 一处，本类协作者由 7 个降到 6 个。
     *
     * <p><b>NEVER 把 {@code PaySignGateway} / {@code PaySignProperties} 加回本类</b> ——
     * 那等于把 URL 知识重新散开（与账户方向的 {@code AccountDomainPort} 是同一条判据）。</p>
     */
    private final ContractGatewayPort contractGatewayPort;

    /**
     * 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备，
     * 且夹具漏注 / 多注一个协作者会**编译失败**，而不是运行时才报 {@code Could not find field}。
     * <b>NEVER 退回 {@code @Autowired} 字段注入。</b>
     */
    public ContractDomainServiceImpl(
            PaySignInfoMapper paySignInfoMapper,
            PaySignRequestMapper paySignRequestMapper,
            AppTerminationRequestMapper terminationRequestMapper,
            PaySignAuditLogger auditLogger,
            AccountDomainPort accountDomainPort,
            ContractGatewayPort contractGatewayPort) {
        this.paySignInfoMapper = paySignInfoMapper;
        this.paySignRequestMapper = paySignRequestMapper;
        this.terminationRequestMapper = terminationRequestMapper;
        this.auditLogger = auditLogger;
        this.accountDomainPort = accountDomainPort;
        this.contractGatewayPort = contractGatewayPort;
    }

    /**
     * IF8A-16 请求签约信息。
     * 按支付平台 2.2 contract 接口组装签约参数并发起请求，
     * 将返回的业务数据 JSON 字符串放入 requestStartSdkInfo 中返回。
     *
     * <p><b>本方法没有 {@code @Transactional}，2026-09-15 摘掉，NEVER 加回</b>
     * （批次 5A；先例与逐条论证见 {@code PaymentDomainServiceImpl.requestPay} 方法头注释）。
     * 摘掉的理由是那个注解的净效果为负：</p>
     * <ol>
     *   <li><b>它包住的只有一次写</b> —— 每条返回路径末尾的 {@code auditLogger.write}，即一条
     *       {@code APP_PAY_SIGN_REQUEST} INSERT。本方法刻意不动 {@code APP_PAY_SIGN_INFO}
     *       （见第 3 步注释），所以事务里从来没有第二条写需要与它保持原子。</li>
     *   <li><b>它永远不会因业务失败回滚</b> —— 方法体末尾的 {@code catch (Exception e)} 吞掉一切并
     *       {@code return response}，没有异常能逃出方法，Spring 因此永远看不到异常、永远走 commit。</li>
     *   <li><b>它反而把出网包了进来</b>，这才是真正的代价：第 2 步 {@code requestGatewaySignInfo}
     *       要等支付中心一个完整往返。等待超过 Druid {@code remove-abandoned-timeout}
     *       （{@code resource/micro/sql-datasource/src/main/resources/sql.properties:44}）后连接被强杀，
     *       {@code commit} 抛 {@code connection closed}，<b>整个事务连那条「留证据」的审计 INSERT
     *       一起被丢弃</b> —— 上游只看到全局异常处理器的 UUID {@code retCode}，库里一条痕迹都没有。
     *       2026-08-26 生产事故（订单 {@code GT20260826210647653586419} 循环重推 8 分钟、
     *       {@code PAY_CALLBACK_LOG} 零条落库）就是这个机理。</li>
     * </ol>
     * <p>去掉后每条 SQL 自动提交，形状回到「留痕 → 出网 → 留痕」：审计流水一写即落，出网无锁无事务。
     * NEVER 用「保持原子」换「可能整段丢失」。</p>
     */
    @Override
    public RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request, String signChannel) {
        RequestSignInfoResult response = new RequestSignInfoResult();
        try {
            // 钱包渠道（0B）自 2026-09-15 起也要在支付中心建代扣签约，原先这里的短路已删除。
            //
            // 原实现对 0B 直接返回「钱包支付不走 requestSignInfo，请先开户后调用 requestAddPayChannel」，
            // 前提是「钱包扣款只靠 payUserId(thirdPayId)、不需要签约流水号」。该前提已被实测推翻：
            // 支付中心 §1.1 requestPay 的 withholding 场景**强制要求 requestSignSeq** ——
            // 2026-09-15 用真实钱包用户（thirdUserId=00522943 / 0B / thirdPayId=2095397359025590272）
            // 打 /ci/app/requestPay，只送 payUserId 时网关返 code=9999「代扣签约请求流水号不能为空」；
            // 补一个流水号后返 code=9999 且无 msg（该号在支付中心侧不是有效协议）。
            // 对应后果：PAY_TXN_DETAIL 里 0B 渠道 16 笔全部 FAIL/RETRY、**零条 SUCCESS**，
            // 而同期 03/04 渠道分别有 36/7 条 SUCCESS。
            //
            // NEVER 退回短路：短路等于让钱包渠道在支付中心侧永远没有可用的代扣协议，扣款必然失败。
            String paymentVendor = request == null ? null : normalizeVendor(request.getPayChannelCode());

            String validMsg = validateRequestSignInfo(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                auditSignInfo(request, response, signChannel);
                return response;
            }

            paymentVendor = normalizeVendor(request.getPayChannelCode());

            // 1. 校验是否已签约
            PaySignInfo existingSign = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), paymentVendor);
            if (existingSign != null) {
                fillError(response, PaySignErrorCodeEnum.ALREADY_SIGNED, "该用户已签约此支付渠道");
                auditSignInfo(request, response, signChannel);
                return response;
            }

            // 2. 调用支付平台获取SDK参数
            String sdkInfo = requestGatewaySignInfo(request, paymentVendor);
            if (!StringUtils.hasText(sdkInfo)) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "请求支付签约接口失败");
                auditSignInfo(request, response, signChannel);
                return response;
            }

            // 3. 不操作 APP_PAY_SIGN_INFO，只记录流水
            fillSuccess(response);
            response.setRequestStartSdkInfo(sdkInfo);
            auditSignInfo(request, response, signChannel);
            return response;
        } catch (Exception e) {
            log.error("处理请求签约信息异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            auditSignInfo(request, response, signChannel);
            return response;
        }
    }

    /**
     * 支付宝出行-添加签约信息。
     * <p>
     * 支付宝渠道完全独立，不需要对接支付平台的签约接口。
     * 接收支付宝 DTO，直接写入 APP_PAY_SIGN_INFO 表和流水表，同步确认签约成功。
     * </p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestSignInfoResult alipayTripRequestSignInfo(AlipayTripAddContractReqDTO request) {
        RequestSignInfoResult response = new RequestSignInfoResult();
        try {
            if (request == null || !StringUtils.hasText(request.getThirdUserId())
                    || !StringUtils.hasText(request.getChannel())
                    || !StringUtils.hasText(request.getAgreementCode())
                    || !StringUtils.hasText(request.getChannelUserAccount())) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "thirdUserId/channel/agreementCode/channelUserAccount不能为空");
                auditAlipayTripSignInfo(request, response, request != null ? request.getChannel() : null);
                return response;
            }

            String paymentVendor = normalizeVendor(request.getChannel());

            // 1. 校验是否已签约（支付宝渠道）
            PaySignInfo existingSign = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), paymentVendor);
            if (existingSign != null) {
                fillError(response, PaySignErrorCodeEnum.ALREADY_SIGNED, "该用户已签约此支付渠道");
                auditAlipayTripSignInfo(request, response, paymentVendor);
                return response;
            }

            // 2. 同步确认：直接写入签约主表，不调用支付平台
            PaySignInfo signInfo = new PaySignInfo();
            signInfo.setRequestSignSeq(request.getAgreementCode());
            signInfo.setThirdUserId(request.getThirdUserId());
            signInfo.setPaymentVendor(paymentVendor);
            signInfo.setSignChannel(SignChannelEnum.ALIPAY.getCode());
            signInfo.setDisplayAccount(request.getChannelUserAccount());
            signInfo.setContractStatus(STATUS_SIGNED);
            signInfo.setSignTime(LocalDateTime.now());
            paySignInfoMapper.insert(signInfo);

            // 3. 写入流水表
            PaySignRequest logRecord = new PaySignRequest();
            logRecord.setRequestSignSeq(request.getAgreementCode());
            logRecord.setThirdUserId(request.getThirdUserId());
            logRecord.setPaymentVendor(paymentVendor);
            logRecord.setSignChannel(SignChannelEnum.ALIPAY.getCode());
            logRecord.setOperationType("ALIPAY_TRIP_REQUEST_SIGN_INFO");
            logRecord.setSignStatus(STATUS_SIGNED);
            logRecord.setCreateTms(LocalDateTime.now());
            // NEVER 写 NOTIFY_STATUS / NOTIFY_RETRY_COUNT：本接口是「渠道侧已签好、我方同步确认」，
            // 整条链路没有 APP 通知环节，而那两列的唯一消费方是签约结果通知的补偿扫表
            // （AppNotifyServiceImpl.compensateSignNotify，且它只捞 RECEIVE_SIGN_RESULT）。
            // 写了就是一对永远没人回写的僵尸字段。
            //
            // 2026-09-16 更正：此处原注释讲的是「解约通知的状态记在 APP_TERMINATION_REQUEST（下面第 7 步）」
            // —— 那是从 requestTermination 复制过来的，本方法既没有第 7 步也不碰解约申请表，已删除。
            // NEVER 再把别的方法的论证复制到这里：注释写错会被后人当判据。
            //
            // 这一行也是 paySignRequestMapper 在本类里**仅存的引用**，且与方法收尾的
            // auditAlipayTripSignInfo 一起构成「一次成功写两行流水」的现状（ADR-D111）：
            // 本行 OPERATION_TYPE 是原样字面量 + SIGN_STATUS='SIGNED'，审计那行被归并成 SIGN、带报文。
            // 要不要合并成一行属于**改审计口径**，MUST 由人裁决；现状已被
            // AlipayTripSignCharacterizationTest 钉住，合并那天它会变红。
            paySignRequestMapper.insert(logRecord);

            // 4. 返回成功响应，不返回 SDK 参数
            fillSuccess(response);
            response.setRequestStartSdkInfo(null);
            auditAlipayTripSignInfo(request, response, paymentVendor);
            return response;
        } catch (Exception e) {
            log.error("支付宝出行-添加签约信息异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            auditAlipayTripSignInfo(request, response, request != null ? request.getChannel() : null);
            return response;
        }
    }

    /**
     * IF8A-21 信用能力咨询。
     * 实际映射支付平台 creditQuery 接口，用于判断当前用户是否具备签约/代扣能力。
     *
     * <p><b>本方法没有 {@code @Transactional}，2026-09-15 摘掉，NEVER 加回</b>（批次 5A，
     * 与 {@code requestSignInfo} 同型；论证先例见 {@code PaymentDomainServiceImpl.requestPay} 方法头）。
     * 原注解包住的写只有一次 {@code auditLogger.write}（{@code APP_PAY_SIGN_REQUEST} 一条 INSERT），
     * 本方法是纯查询、不碰任何业务表，因此事务里没有第二条写需要与它保持原子；而方法末尾的
     * {@code catch (Exception e)} 吞掉一切并返回响应，异常从不逃出方法，事务也就从来不会因业务失败回滚。
     * 留下的唯一效果是把 {@code paySignGateway.request}（支付中心一个完整往返）包进未提交的事务：
     * 等待超过 Druid {@code remove-abandoned-timeout}
     * （{@code resource/micro/sql-datasource/src/main/resources/sql.properties:44}）后连接被强杀，
     * {@code commit} 抛 {@code connection closed}，<b>那条「留证据」的审计 INSERT 会连同事务一起被丢弃</b>
     * （2026-08-26 生产事故的同一机理：循环重推 8 分钟、{@code PAY_CALLBACK_LOG} 零条落库）。
     * NEVER 用「保持原子」换「可能整段丢失」。</p>
     */
    @Override
    public RequestContractAdvisoryRespDTO requestContractAdvisory(RequestContractAdvisoryReqDTO request, String signChannel) {
        RequestContractAdvisoryRespDTO response = new RequestContractAdvisoryRespDTO();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                auditContractAdvisory(request, response, signChannel);
                return response;
            }
            String paymentVendor = normalizeVendor(request.getPaymentVendor());
            // 入口级路径选择 MUST 穷尽（ADR-D109）：新增渠道类别时编译器会在这里报错 ——
            // pattern switch 语句与表达式**都**校验穷尽性（2026-09-16 实测）。
            // NEVER 退回 if (isWallet(...))：那个 else 隐含「其余一切按传统签约处理」，漏改不报错。
            switch (PaymentChannels.classify(paymentVendor)) {
                case PaymentChannel.Wallet ignored -> {
                    // 钱包没有签约能力咨询；返回成功兼容旧客户端，避免其把钱包误判为支付失败。
                    fillSuccess(response);
                    response.setMsg("钱包支付无需签约咨询");
                    response.setRetMsg("钱包支付无需签约咨询");
                    auditLogger.write("REQUEST_CONTRACT_ADVISORY_WALLET_NOT_APPLICABLE",
                            request.getThirdUserId(), request.getRequestSignSeq(), paymentVendor,
                            signChannel, request, response);
                    return response;
                }
                // 传统签约渠道无需前置处理，继续走下面的主干。
                // 空分支是刻意的：它是「这个类别已被考虑过」的证据，NEVER 删。
                case PaymentChannel.Contracted ignored -> {
                }
            }
            String validMsg = validateContractQuery(request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor());
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                auditContractAdvisory(request, response, signChannel);
                return response;
            }

            GatewayReply reply = contractGatewayPort.creditQuery(request.getThirdUserId(),
                    request.getRequestSignSeq(), request.getPaymentVendor());
            PaySignGatewayResponse gatewayResponse = reply.raw();
            if (reply instanceof GatewayReply.Rejected rejected) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, rejected.messageOr("请求支付签约咨询接口失败"));
                auditContractAdvisory(request, response, signChannel);
                return response;
            }

            fillSuccess(response);
            auditContractAdvisory(request, response, signChannel);
            return response;
        } catch (Exception e) {
            log.error("处理信用能力咨询异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            auditContractAdvisory(request, response, signChannel);
            return response;
        }
    }

    /**
     * IF8A-22 签约结果查询。
     * 以 requestSignSeq 为主键向支付平台查询最新签约状态，并回填本地签约主表。
     *
     * <p><b>本方法没有 {@code @Transactional}，2026-09-15 摘掉，NEVER 加回</b>（批次 5A；
     * 论证先例见 {@code PaymentDomainServiceImpl.requestPay} 方法头）。三点理由：</p>
     * <ol>
     *   <li><b>它从来不会因业务失败回滚</b>：末尾 {@code catch (Exception e)} 吞掉一切并返回响应，
     *       没有异常逃出方法，Spring 永远走 commit。</li>
     *   <li><b>它保护不了什么</b>：钱包分支（{@code queryWalletBindingResult}）事务内只有一次
     *       {@code auditLogger.write}；主分支多出的那条写是 {@code applyGatewayStatus} 的
     *       {@code APP_PAY_SIGN_INFO} CAS，而它与审计 INSERT 的不一致<b>已被现有设计接纳</b> ——
     *       CAS 影响 0 行时只告警不落库，并把内存状态回写成库里真值（见该方法内注释），
     *       本就不依赖事务兜。</li>
     *   <li><b>它把出网包了进来</b>：{@code paySignGateway.request} 要等支付中心一个完整往返，
     *       等待超过 Druid {@code remove-abandoned-timeout}
     *       （{@code resource/micro/sql-datasource/src/main/resources/sql.properties:44}）后连接被强杀，
     *       {@code commit} 抛 {@code connection closed}，<b>连「留证据」的审计 INSERT 一起丢弃</b>；
     *       钱包分支的 {@code accountClient.queryUserInfo} 同理。2026-08-26 生产事故
     *       （循环重推 8 分钟、{@code PAY_CALLBACK_LOG} 零条落库）即此机理。</li>
     * </ol>
     * <p>NEVER 用「保持原子」换「可能整段丢失」。</p>
     */
    @Override
    public RequestContractResultRespDTO requestContractResult(RequestContractResultReqDTO request, String signChannel) {
        RequestContractResultRespDTO response = new RequestContractResultRespDTO();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                auditContractResult(request, response, signChannel);
                return response;
            }
            String paymentVendor = normalizeVendor(request.getPaymentVendor());
            // 入口级路径选择 MUST 穷尽（ADR-D109）：新增渠道类别时编译器会在这里报错 ——
            // pattern switch 语句与表达式**都**校验穷尽性（2026-09-16 实测）。
            // NEVER 退回 if (isWallet(...))：那个 else 隐含「其余一切按传统签约处理」，漏改不报错。
            switch (PaymentChannels.classify(paymentVendor)) {
                case PaymentChannel.Wallet ignored -> {
                    return queryWalletBindingResult(request, signChannel, response);
                }
                // 传统签约渠道无需前置处理，继续走下面的主干。
                // 空分支是刻意的：它是「这个类别已被考虑过」的证据，NEVER 删。
                case PaymentChannel.Contracted ignored -> {
                }
            }
            String validMsg = validateContractQuery(request.getThirdUserId(), request.getRequestSignSeq(), paymentVendor);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                auditContractResult(request, response, signChannel);
                return response;
            }

            PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getRequestSignSeq(), null);
            boolean existed = signInfo != null;

            // 归属校验：REQUEST_SIGN_SEQ 是 APP_PAY_SIGN_INFO 的主键，只按它查等于任何网络可达方
            // 带一个流水号就能读到别人的 payAccountId / payAgreementNo。命中的记录 MUST 属于报文里的
            // thirdUserId，不匹配一律按「签约记录不存在」回绝，NEVER 回具体原因（会变成存在性探测）。
            if (existed && !request.getThirdUserId().equals(signInfo.getThirdUserId())) {
                log.warn("签约结果查询归属校验失败, requestSignSeq={}, requestThirdUserId={}, ownerThirdUserId={}",
                        request.getRequestSignSeq(), request.getThirdUserId(), signInfo.getThirdUserId());
                fillError(response, PaySignErrorCodeEnum.RECORD_NOT_EXIST, PaySignErrorCodeEnum.RECORD_NOT_EXIST.getMsg());
                auditContractResult(request, response, signChannel);
                return response;
            }

            // 本地已有签约记录，且状态为已签约、数据完整时，直接返回，不调用支付平台。
            boolean needCallGateway = !existed
                    || !STATUS_SIGNED.equals(signInfo.getContractStatus())
                    || !StringUtils.hasText(signInfo.getPayAccountId())
                    || !StringUtils.hasText(signInfo.getPayAgreementNo());

            if (!needCallGateway) {
                fillContractResult(response, signInfo, STATUS_NOT_SIGNED);
                auditContractResult(request, response, signChannel);
                return response;
            }

            GatewayReply reply = contractGatewayPort.queryContractResult(request.getRequestSignSeq());
            PaySignGatewayResponse gatewayResponse = reply.raw();

            if (reply instanceof GatewayReply.Rejected rejected) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, rejected.messageOr("request pay contract result failed"));
                auditContractResult(request, response, signChannel);
                return response;
            }

            ContractResultOutcome outcome =
                    ContractResultOutcome.decide(request, signChannel, signInfo, gatewayResponse.getData());
            signInfo = outcome.signInfo();
            switch (outcome) {
                case ContractResultOutcome.RefreshExisting refresh ->
                        applyGatewayStatus(request.getRequestSignSeq(), refresh.previousStatus(), signInfo);
                case ContractResultOutcome.OrphanSigned orphan -> {
                    // 支付平台说已签约、本地却没有签约记录，是「孤儿协议」，不是本接口该修的。
                    // NEVER 在这里 insert APP_PAY_SIGN_INFO：本接口是 IF8A-22 查询接口，
                    // 报文里拿不到 CARD_ID / CARD_TYPE，只能写 NULL，而解约链路要靠这两个字段
                    // 调 account requestRemovePayChannel 做卡信息比对；更重要的是这等于允许调用方
                    // 凭一个 requestSignSeq 就给任意 thirdUserId 凭空造出一条 SIGNED 记录。
                    // 签约记录只由 receiveSignResult 与支付宝出行 requestSignInfo 创建。
                    // 已发生：生产库 00522908 / 00522914 共 3 条 SIGNED 孤儿签约，对应
                    // USER_ITP_REG_INFO 一行都没有，解约必然拿 NO_ACCOUNT_CARD 卡死在
                    // SCANNING，只能改库清理（2026-08-26 修复）。
                    log.warn("支付平台已签约但本地无签约记录，只返回不落库, requestSignSeq={}, thirdUserId={}, payAgreementNo={}",
                            request.getRequestSignSeq(), request.getThirdUserId(), signInfo.getPayAgreementNo());
                }
                case ContractResultOutcome.NoLocalChange ignored -> {
                    // 无需对本地做任何写入：平台没给 data，或本地无记录且平台未签约。
                }
            }

            fillContractResult(response, signInfo, STATUS_NOT_SIGNED);
            auditContractResult(request, response, signChannel);
            return response;
        } catch (Exception e) {
            log.error("处理签约结果查询异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            auditContractResult(request, response, signChannel);
            return response;
        }
    }

    /**
     * IF8A-06 请求解约。
     * 对外仍保留 ITP 侧报文风格；T+4 日前仅记录解约申请，不调用支付平台。
     *
     * <p><b>本方法没有 {@code @Transactional}，2026-09-15 摘掉，NEVER 加回</b>（批次 5A；
     * 论证先例见 {@code PaymentDomainServiceImpl.requestPay} 方法头）。理由：</p>
     * <ol>
     *   <li><b>它从来不会因业务失败回滚</b>：末尾 {@code catch (Exception e)} 吞掉一切并返回响应，
     *       异常不逃出方法，Spring 永远走 commit。</li>
     *   <li><b>钱包分支事务内零 DB 写</b>：{@code :449} 判定为钱包即 {@code return releaseWalletBinding(...)}，
     *       与传统分支互斥；该私有方法通读只有一次 {@code accountDomainPort.agreeRelease}，连审计都不写 ——
     *       所以「远端已 {@code agreeRelease} 成功、本地却没落库」这个中间态<b>结构上不可能出现</b>。</li>
     *   <li><b>它把这次出网包了进来</b>：{@code agreeRelease} 是一次账户域 RPC。等待超过 Druid
     *       {@code remove-abandoned-timeout}
     *       （{@code resource/micro/sql-datasource/src/main/resources/sql.properties:44}）后连接被强杀、
     *       {@code commit} 抛 {@code connection closed}，事务里已有的「留证据」INSERT 会被整段丢弃 ——
     *       2026-08-26 生产事故（循环重推 8 分钟、{@code PAY_CALLBACK_LOG} 零条落库）就是这个机理。</li>
     *   <li><b>传统分支的写序本来就是安全方向</b>：先业务表（{@code :530} insert / {@code :506}
     *       {@code reactivateFailed}）、后审计表（{@code :536}）。摘事务后顺序不变，有害的那个方向
     *       （审计已落、业务表未落）不会出现。</li>
     * </ol>
     * <p>NEVER 用「保持原子」换「可能整段丢失」。</p>
     */
    @Override
    public RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request, String signChannel) {
        RequestTerminationRespDTO response = new RequestTerminationRespDTO();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                auditTermination(request, response, signChannel);
                return response;
            }
            // 入口级路径选择 MUST 穷尽（ADR-D109）：新增渠道类别时编译器会在这里报错 ——
            // pattern switch 语句与表达式**都**校验穷尽性（2026-09-16 实测）。
            // NEVER 退回 if (isWallet(...))：那个 else 隐含「其余一切按传统签约处理」，漏改不报错。
            switch (PaymentChannels.classify(request.getPaymentVendor())) {
                case PaymentChannel.Wallet ignored -> {
                    return releaseWalletBinding(request, response);
                }
                // 传统签约渠道无需前置处理，继续走下面的主干。
                // 空分支是刻意的：它是「这个类别已被考虑过」的证据，NEVER 删。
                case PaymentChannel.Contracted ignored -> {
                }
            }
            String validMsg = validateRequestTermination(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                auditTermination(request, response, signChannel);
                return response;
            }

            // 1. 查询是否已签约（只做校验，不修改）
            // 按 requestSignSeq 查询：paymentVendor 非必填（见 validateRequestTermination），
            // 用它做无条件等值条件会拼出 PAYMENT_VENDOR = null 而永不匹配，误判为用户未签约。
            // selectBySeq 对 paymentVendor 做了空值判断，为空时退化为只按流水号查询。
            PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getRequestSignSeq(), request.getPaymentVendor());
            if (signInfo == null) {
                fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED, PaySignErrorCodeEnum.USER_NOT_SIGNED.getMsg());
                auditTermination(request, response, signChannel);
                return response;
            }

            // 2. 校验是否已存在待处理解约申请
            AppTerminationRequest existRequest = terminationRequestMapper.selectPendingByUserId(request.getThirdUserId());
            if (existRequest != null) {
                fillError(response, PaySignErrorCodeEnum.ALREADY_TERMINATING, "用户正在解约中");
                auditTermination(request, response, signChannel);
                return response;
            }

            // cardId/cardType/paymentVendor 在 APP 报文中非必填（见 validateRequestTermination），
            // 而 APP_TERMINATION_REQUEST 中三列均为 NOT NULL，请求缺字段时用签约记录回填，否则插入抛 ORA-01400。
            String cardId = StringUtils.hasText(request.getCardId()) ? request.getCardId() : signInfo.getCardId();
            String cardType = StringUtils.hasText(request.getCardType()) ? request.getCardType() : signInfo.getCardType();
            String paymentVendor = StringUtils.hasText(request.getPaymentVendor())
                    ? request.getPaymentVendor() : signInfo.getPaymentVendor();
            LocalDateTime now = LocalDateTime.now();

            // 3. 同一签约流水是否已有解约申请。
            // REQUEST_SIGN_SEQ 上有唯一索引 UK_ATR_REQUEST_SIGN_SEQ，无条件 insert 会抛
            // DuplicateKeyException 并被本方法外层 catch 成 SYSTEM_ERROR —— 一次 FAILED 的解约
            // （比如被未结清欠费拦住）就让这条流水**永久无法重新申请**，用户只看到系统错误，
            // 只能改库才能解开。状态机白名单：只有 FAILED 允许复活，其余状态一律拒绝。
            AppTerminationRequest existBySeq = terminationRequestMapper
                    .selectByRequestSignSeq(request.getRequestSignSeq());
            if (existBySeq != null && !STATUS_FAILED.equals(existBySeq.getTerminationStatus())) {
                // PENDING / SCANNING 正常已被上一步按用户维度拦住；能走到这里的是 SUCCESS 这类终态，
                // 或上一步的边界漏网（selectPendingByUserId 带 ROWNUM = 1，多渠道时取到的是任意一条）。
                fillError(response, PaySignErrorCodeEnum.ALREADY_TERMINATING,
                        "该签约流水已有解约申请，当前状态=" + existBySeq.getTerminationStatus());
                auditTermination(request, response, signChannel);
                return response;
            }

            if (existBySeq != null) {
                // FAILED 复活：清掉上一轮的失败痕迹退回 PENDING，交给扫表任务重跑。
                // reactivateFailed 的 WHERE 带 TERMINATION_STATUS = 'FAILED'，是 CAS；
                // 影响 0 行说明并发下状态已被改走，MUST 拒绝，NEVER 当成功继续。
                int revived = terminationRequestMapper.reactivateFailed(
                        request.getRequestSignSeq(), cardId, cardType, paymentVendor, now);
                if (revived == 0) {
                    fillError(response, PaySignErrorCodeEnum.ALREADY_TERMINATING, "解约申请状态已变更，请稍后重试");
                    auditTermination(request, response, signChannel);
                    return response;
                }
                log.info("解约申请由 FAILED 复活为 PENDING, requestSignSeq={}, 上一轮失败原因={}",
                        request.getRequestSignSeq(), existBySeq.getFailReason());
            } else {
                // 4. 插入解约申请记录（状态=PENDING，通知状态=PENDING）
                AppTerminationRequest terminationRequest =
                        buildTerminationRequest(request, cardId, cardType, paymentVendor, now);
                terminationRequestMapper.insert(terminationRequest);
            }

            // T+4 日前不调用支付平台，不操作 APP_PAY_SIGN_INFO 与 USER_ITP_REG_INFO
            fillSuccess(response);
            response.setRequestSignSeq(request.getRequestSignSeq());
            auditTermination(request, response, signChannel);
            return response;
        } catch (Exception e) {
            log.error("处理请求解约异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            auditTermination(request, response, signChannel);
            return response;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestAgreeReleaseResult removeSignAgreement(RequestAgreeReleaseReqDTO request, String signChannel) {
        RequestAgreeReleaseResult result = new RequestAgreeReleaseResult();
        if (!StringUtils.hasText(request.getAgreementCode())) {
            result.setRetCode(PaySignErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("agreementCode不能为空");
            result.setCode(400);
            result.setMsg("agreementCode不能为空");
            result.setSuccess(false);
            return result;
        }
        PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getAgreementCode(), null);
        if (signInfo == null) {
            result.setRetCode(PaySignErrorCodeEnum.RECORD_NOT_EXIST.getCode());
            result.setRetMsg("签约记录不存在");
            result.setCode(404);
            result.setMsg("签约记录不存在");
            result.setSuccess(false);
            return result;
        }
        signInfo.setSignStatus(STATUS_UNSIGNED);
        signInfo.setTerminationTime(LocalDateTime.now());
        // 状态迁移走 CAS（SIGNED -> UNSIGNED），NEVER 退回 updateBySeq：
        // 后者 WHERE 只有 REQUEST_SIGN_SEQ，与签约回调并发时会互相覆盖。
        int updated = paySignInfoMapper.markUnsigned(request.getAgreementCode(), signInfo.getTerminationTime());
        // CAS 返 0 行的解读统一走 SignStatusTransition（ADR-D40），NEVER 在这里重写判定：
        // 与本类的 applyGatewayStatus 共用同一条规则，两处曾各写一遍、正反判断不一致。
        SignStatusTransition.Result transit = SignStatusTransition.classify(updated, SignStatus.UNSIGNED,
                () -> paySignInfoMapper.selectSignStatusBySeq(request.getAgreementCode()));
        if (transit.isConflict()) {
            // 本入口是状态变更型接口：冲突 MUST 拒绝并返 409，NEVER 降级成只告警放行。
            log.warn("移除签约被状态机拒绝, agreementCode={}, currentStatus={}, signChannel={}",
                    request.getAgreementCode(), transit.observedStatus(), signChannel);
            result.setRetCode(PaySignErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("当前签约状态不允许解约:" + transit.observedStatus());
            result.setCode(409);
            result.setMsg("当前签约状态不允许解约:" + transit.observedStatus());
            result.setSuccess(false);
            return result;
        }
        if (transit.outcome() == SignStatusTransition.Outcome.IDEMPOTENT) {
            log.info("移除签约幂等重放（已是UNSIGNED）, agreementCode={}, signChannel={}",
                    request.getAgreementCode(), signChannel);
        }
        log.info("移除签约成功, agreementCode={}, signChannel={}", request.getAgreementCode(), signChannel);
        result.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
        result.setCode(0);
        result.setMsg("成功");
        result.setSuccess(true);
        return result;
    }

    /**
     * 供内部解约流程调用，向支付平台发起解约请求。
     *
     * @param requestSignSeq 签约流水号
     * @return 支付平台网关响应
     */
    @Override
    public PaySignGatewayResponse requestPayPlatformTermination(String requestSignSeq) {
        // 契约刻意不变（仍交出原始应答体）：调用方 TerminationExecutor / TerminationProcessor
        // 自己判读，改它们的判读方式属另一批次（ADR-D112 已列为遗留项）。
        return contractGatewayPort.requestDismissal(requestSignSeq).raw();
    }

    /**
     * 供内部解约流程调用，向支付平台查询协议状态。
     *
     * <p>支付中心没有独立的「解约结果查询」接口：网关文档 §2.4 查询签约结果的 status
     * 同时承载签约态与解约态，{@code status=UNSIGNED} 即该协议已解约。解约回调地址只能由
     * 支付中心在商户侧配置（§2.3 请求解约的 bizData 没有 notifyUrl 字段），我方无法保证
     * 一定收到回调，因此解约收口 MUST 以主动查询为准，回调只作为快速路径。</p>
     *
     * @param requestSignSeq 签约流水号
     * @return 支付平台网关响应
     */
    @Override
    public PaySignGatewayResponse queryPayPlatformContractStatus(String requestSignSeq) {
        // 契约同上刻意不变。
        return contractGatewayPort.queryContractResult(requestSignSeq).raw();
    }

    /**
     * 把支付平台返回的签约状态落到 APP_PAY_SIGN_INFO。
     * <p>
     * 状态迁移一律走 CAS（{@code markSigned} / {@code markUnsigned} / {@code reactivateForResign}），
     * **NEVER** 用 {@code updateBySeq} 改 SIGN_STATUS —— 那条语句 WHERE 只有 REQUEST_SIGN_SEQ，
     * 而本方法所在的 IF8A-22 是可被反复调用的查询接口，与签约回调 / 解约回调并发时会互相覆盖，
     * 典型后果是迟到的一次查询把 UNSIGNED 改回 SIGNED（已解约通道在 APP 上显示有效）。
     * 白名单不允许的迁移**只告警不落库**，留给对账与异常工单，**NEVER** 强改。
     * </p>
     */
    private void applyGatewayStatus(String requestSignSeq, String previousStatus, PaySignInfo signInfo) {
        String targetStatus = signInfo.getContractStatus();
        if (targetStatus == null || targetStatus.equals(previousStatus)) {
            PaySignInfo patch = new PaySignInfo();
            patch.setRequestSignSeq(requestSignSeq);
            patch.setPayAccountId(signInfo.getPayAccountId());
            patch.setPayAgreementNo(signInfo.getPayAgreementNo());
            paySignInfoMapper.updateBySeq(patch);
            return;
        }
        int updated;
        if (STATUS_SIGNED.equals(targetStatus)) {
            LocalDateTime signTime = signInfo.getSignTime() != null ? signInfo.getSignTime() : LocalDateTime.now();
            updated = paySignInfoMapper.markSigned(requestSignSeq, signInfo.getPayAccountId(),
                    signInfo.getPayAgreementNo(), signTime);
        } else if (STATUS_UNSIGNED.equals(targetStatus)) {
            updated = paySignInfoMapper.markUnsigned(requestSignSeq, LocalDateTime.now());
        } else if (STATUS_NOT_SIGNED.equals(targetStatus)) {
            updated = paySignInfoMapper.reactivateForResign(requestSignSeq);
        } else {
            log.warn("支付平台返回未知签约状态，不落库, requestSignSeq={}, gatewayStatus={}", requestSignSeq, targetStatus);
            return;
        }
        if (updated == 0) {
            // CAS 返 0 行的解读统一走 SignStatusTransition（ADR-D40），与
            // ContractDomainServiceImpl.removeSignAgreement 共用同一条规则，NEVER 在这里重写判定。
            SignStatusTransition.Result transit = SignStatusTransition.classify(0,
                    SignStatus.parseOrNull(targetStatus),
                    () -> paySignInfoMapper.selectSignStatusBySeq(requestSignSeq));
            String current = transit.observedStatus();
            if (transit.isConflict()) {
                // 本方法在 IF8A-22 查询接口内：冲突 MUST 只告警不落库、也不对上游报错，
                // NEVER 照抄解约入口的 409 —— 那会让一次只读查询因状态不一致而失败。
                log.warn("签约状态迁移被白名单拒绝，只告警不落库, requestSignSeq={}, current={}, gatewayStatus={}",
                        requestSignSeq, current, targetStatus);
            } else {
                log.info("本地签约状态与支付平台已一致, requestSignSeq={}, status={}", requestSignSeq, current);
            }
            signInfo.setContractStatus(current);
        }
    }

    /** 钱包没有签约主表，签约结果查询兼容为支付通道绑定状态查询。 */
    private RequestContractResultRespDTO queryWalletBindingResult(RequestContractResultReqDTO request,
                                                                  String signChannel,
                                                                  RequestContractResultRespDTO response) {
        if (!StringUtils.hasText(request.getThirdUserId())
                || !StringUtils.hasText(request.getCardId())
                || !StringUtils.hasText(request.getCardType())) {
            fillError(response, PaySignErrorCodeEnum.INVALID_PARAM,
                    "钱包绑定状态查询需要thirdUserId/cardId/cardType");
            auditWalletBindingResult(request, response, signChannel);
            return response;
        }
        try {
            // 2026-09-16 起走 AccountDomainPort（ADR-D94 续）。原先在这里 new QueryUserInfoReqDTO
            // 并自行判 retCode，三处读调用各写一遍、且判法不一致。现在「答成功」由 Found 分支表达，
            // 本方法只保留业务判定（通道是钱包 + 有 thirdPayId）。
            // NotFound 与 Unreachable 的映射刻意不同：前者是「查不到」，对 APP 就是未绑定（0000）；
            // 后者是「没问到」，MUST 报 9001 让调用方重试。NEVER 把两者合并。
            switch (accountDomainPort.queryUser(
                    request.getThirdUserId(), request.getCardId(), request.getCardType())) {
                case AccountQuery.Found<AccountUserView> found -> {
                    AccountUserView view = found.value();
                    boolean active = isWallet(view.channel())
                            && StringUtils.hasText(view.thirdPayId());
                    fillSuccess(response);
                    response.setStatus(active ? STATUS_SIGNED : STATUS_NOT_SIGNED);
                    if (active) {
                        response.setPayUserId(view.thirdPayId());
                        response.setPayAccountId(view.thirdPayId());
                    }
                }
                case AccountQuery.NotFound<AccountUserView> notFound -> {
                    log.info("账户域未命中钱包绑定, thirdUserId={}, retCode={}, retMsg={}",
                            request.getThirdUserId(), notFound.retCode(), notFound.retMsg());
                    fillSuccess(response);
                    response.setStatus(STATUS_NOT_SIGNED);
                }
                case AccountQuery.Unreachable<AccountUserView> unreachable -> {
                    log.error("查询钱包绑定状态未获账户域答复, request={}", JSON.toJSONString(request),
                            unreachable.cause());
                    fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "查询钱包绑定状态失败");
                }
            }
            auditWalletBindingResult(request, response, signChannel);
            return response;
        } catch (Exception e) {
            log.error("查询钱包绑定状态异常, request={}", JSON.toJSONString(request), e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "查询钱包绑定状态失败");
            auditWalletBindingResult(request, response, signChannel);
            return response;
        }
    }

    /**
     * 兼容旧客户端误用 requestTermination 的场景，钱包只执行本地支付通道移除。
     *
     * <p><b>2026-09-12（ADR-D46）走 {@link AccountDomainPort#agreeRelease}</b>：三个分支的对外行为
     * 与改造前<b>逐字一致</b>（业务拒绝回 {@code retMsg}、不可达回「钱包解绑失败」），
     * 差别只在「不可达」不再靠 {@code catch (Exception)} 兜、而是编译期就必须写出来。
     * 这里 <b>NEVER 把 {@code BizRejected} 的 retMsg 换成固定文案</b> —— 旧客户端依赖它区分
     * 「没有这张卡」与「系统故障」。</p>
     */
    private RequestTerminationRespDTO releaseWalletBinding(RequestTerminationReqDTO request,
                                                           RequestTerminationRespDTO response) {
        if (!StringUtils.hasText(request.getThirdUserId())
                || !StringUtils.hasText(request.getCardId())
                || !StringUtils.hasText(request.getCardType())) {
            fillError(response, PaySignErrorCodeEnum.INVALID_PARAM,
                    "钱包解绑需要thirdUserId/cardId/cardType");
            return response;
        }
        RpcOutcome outcome = accountDomainPort.agreeRelease(request.getThirdUserId(),
                walletCode(), request.getCardId(), request.getCardType());
        switch (outcome) {
            case RpcOutcome.Ok ignored -> fillSuccess(response);
            case RpcOutcome.BizRejected rejected -> fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR,
                    StringUtils.hasText(rejected.retMsg()) ? rejected.retMsg() : "钱包解绑失败");
            case RpcOutcome.Unreachable unreachable -> {
                log.error("兼容处理钱包解绑异常, request={}", JSON.toJSONString(request), unreachable.cause());
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "钱包解绑失败");
            }
        }
        return response;
    }

    /**
     * 调用支付平台 contract 接口发起正式签约，取出 SDK 参数。
     * 返回值为支付平台 data 节点的 JSON 字符串；取不到一律返回 {@code null}，由调用点收成 9001。
     *
     * <p><b>URL、bizData 装配与 notifyUrl 三级回落已搬进 {@code ContractGatewayAdapter}</b>
     * （2026-09-16，ADR-D112）。本方法只剩「把应答翻译成 SDK 参数」这一件事 ——
     * 那才是签约用例自己的知识，NEVER 把它也搬进端口（端口不认识业务语义）。</p>
     */
    private String requestGatewaySignInfo(RequestSignInfoReqDTO request, String paymentVendor) {
        GatewayReply reply = contractGatewayPort.requestContract(request, paymentVendor);
        if (!(reply instanceof GatewayReply.Accepted accepted)) {
            return null;
        }
        if (accepted.data() == null) {
            // 成功码但无 data：支付中心真实出现过的形态，与「失败」同样取不到 SDK 参数。
            log.error("支付签约接口返回失败, code={}, msg={}",
                    accepted.raw() == null ? null : accepted.raw().getCode(),
                    accepted.raw() == null ? null : accepted.raw().getMsg());
            return null;
        }
        return stringValue(accepted.data().get("data"), JSON.toJSONString(accepted.data()));
    }

    /*
     * ===== 审计流水的按接口收口（2026-09-16，ADR-D107）=====
     *
     * 下面六个薄壳只做一件事：把 auditLogger.write 的前四个「按接口恒定」的实参固定下来，
     * 让调用点只剩 (request, response) + signChannel。原先本类有 37 处裸调用，其中
     * REQUEST_TERMINATION 6 处、REQUEST_CONTRACT_RESULT 5 处、REQUEST_SIGN_INFO 3 处
     * 的 7 个实参**逐字相同** —— 那是「多处参数列表必须保持一致」的隐患：漏改一处不会编译失败、
     * 不会告警，只会让审计流水少一个字段，而 APP_PAY_SIGN_REQUEST 是这条链路唯一的证据。
     *
     * NEVER 把这六个合并成一个「通用 writeAudit(action, ...)」：它们的第 4 个实参取法各不相同 ——
     * REQUEST_SIGN_INFO 送 normalizeVendor 归一后的值，ALIPAY_TRIP 的正常分支送归一值、
     * catch 分支刻意送原始 channel，其余三个一律送报文原值 request.getPaymentVendor()。
     * 归一与不归一混在一个入口里，改一次就会静默改掉某条链路的流水口径。
     *
     * 每个薄壳内部都做 request == null 的判空，因此**校验失败分支与 catch 分支可以共用同一个调用**，
     * 这也是原先那 5 处「三元表达式版」与「直取版」之所以能合并的原因。
     */

    private void auditSignInfo(RequestSignInfoReqDTO request, RequestSignInfoResult response, String signChannel) {
        auditLogger.write("REQUEST_SIGN_INFO",
                request != null ? request.getThirdUserId() : null,
                request != null ? request.getRequestSignSeq() : null,
                request != null ? normalizeVendor(request.getPayChannelCode()) : null,
                signChannel, request, response);
    }

    /**
     * 支付宝出行签约的流水。
     *
     * <p><b>paymentVendor MUST 由调用方传入</b>：正常分支送 {@code normalizeVendor(request.getChannel())}，
     * 而 {@code catch} 分支历来送**未归一的** {@code request.getChannel()}。两者是否等价取决于
     * {@code normalizeVendor} 的实现，本次收口不改这个既有差异，因此不在本方法内自行取值。</p>
     */
    private void auditAlipayTripSignInfo(AlipayTripAddContractReqDTO request, RequestSignInfoResult response,
                                         String paymentVendor) {
        auditLogger.write("ALIPAY_TRIP_REQUEST_SIGN_INFO",
                request != null ? request.getThirdUserId() : null,
                request != null ? request.getAgreementCode() : null,
                paymentVendor, SignChannelEnum.ALIPAY.getCode(), request, response);
    }

    private void auditContractAdvisory(RequestContractAdvisoryReqDTO request, RequestContractAdvisoryRespDTO response,
                                       String signChannel) {
        auditLogger.write("REQUEST_CONTRACT_ADVISORY",
                request != null ? request.getThirdUserId() : null,
                request != null ? request.getRequestSignSeq() : null,
                request != null ? request.getPaymentVendor() : null,
                signChannel, request, response);
    }

    private void auditContractResult(RequestContractResultReqDTO request, RequestContractResultRespDTO response,
                                     String signChannel) {
        auditLogger.write("REQUEST_CONTRACT_RESULT",
                request != null ? request.getThirdUserId() : null,
                request != null ? request.getRequestSignSeq() : null,
                request != null ? request.getPaymentVendor() : null,
                signChannel, request, response);
    }

    private void auditTermination(RequestTerminationReqDTO request, RequestTerminationRespDTO response,
                                  String signChannel) {
        auditLogger.write("REQUEST_TERMINATION",
                request != null ? request.getThirdUserId() : null,
                request != null ? request.getRequestSignSeq() : null,
                request != null ? request.getPaymentVendor() : null,
                signChannel, request, response);
    }

    /**
     * 钱包绑定状态查询的流水。
     *
     * <p>第 3 个实参恒为 {@code PaymentChannels.walletCode()}、第 2 个恒为 {@code null}（钱包没有签约流水号），
     * 三处调用逐字相同。<b>NEVER 在这里改成送 request.getPaymentVendor()</b> —— 旧客户端可能送空或送错，
     * 而这条流水的语义就是「本次被判定为钱包渠道」。</p>
     */
    private void auditWalletBindingResult(RequestContractResultReqDTO request, RequestContractResultRespDTO response,
                                          String signChannel) {
        auditLogger.write("REQUEST_WALLET_BINDING_RESULT",
                request != null ? request.getThirdUserId() : null, null,
                walletCode(), signChannel, request, response);
    }
}
