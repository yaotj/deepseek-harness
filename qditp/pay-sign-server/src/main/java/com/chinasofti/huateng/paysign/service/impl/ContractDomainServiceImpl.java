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
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
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

/** 签约 + 解约领域的真实现。 */
@Service
public class ContractDomainServiceImpl implements ContractDomainService {

    private static final Logger log = LoggerFactory.getLogger(ContractDomainServiceImpl.class);
    private static final String STATUS_NOT_SIGNED = SignStatus.NOT_SIGNED.name();
    private static final String STATUS_SIGNED = SignStatus.SIGNED.name();
    private static final String STATUS_UNSIGNED = SignStatus.UNSIGNED.name();
    /** 解约申请状态，取值来自 {@link TerminationStatus}。 */
    private static final String STATUS_FAILED = TerminationStatus.FAILED.name();

    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();

    /** 本类**不再持有** WALLET_PAYMENT_VENDOR 常量（2026-09-16，ADR-D108）。 */

    private final PaySignInfoMapper paySignInfoMapper;

    /** 本类**不再持有** PaySignRequestMapper（2026-09-16，ADR-D115 续（二））：协作者 6 -> 5。 */
    private final AppTerminationRequestMapper terminationRequestMapper;
    /** 接口流水（{@code APP_PAY_SIGN_REQUEST}）的唯一写入点，2026-09-14 由本类的 {@code writeLog} 抽出。 */
    private final PaySignAuditLogger auditLogger;

    /** 账户域出向调用的**唯一**出口（ADR-D46 写侧 / ADR-D94 续读侧）。 */
    private final AccountDomainPort accountDomainPort;

    /** 支付中心**签约/解约方向**出向调用的唯一出口（2026-09-16，ADR-D112）。 */
    private final ContractGatewayPort contractGatewayPort;

    /**
     * 支付平台签约状态 → 本地落库动作。**表里没有的取值即「未知状态」，一律只告警不落库。**
     *
     * <p>2026-09-17 由 {@code applyGatewayStatus} 的 if-else 链改成表驱动（ADR-D122）：
     * 新增一个状态取值时**只能**往这张表里加一行，NEVER 再在方法体里插分支 ——
     * 那种写法把「状态集合」与「每个状态怎么落库」两件事糅在一处，漏一个分支只表现为静默不落库。
     */
    private final Map<String, GatewayStatusMigrator> gatewayStatusMigrators;

    /** 一次状态落库动作，返回受影响行数（0 即交给 {@link SignStatusTransition} 判幂等还是冲突）。 */
    @FunctionalInterface
    private interface GatewayStatusMigrator {
        int migrate(String requestSignSeq, PaySignInfo signInfo);
    }

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public ContractDomainServiceImpl(
            PaySignInfoMapper paySignInfoMapper,
            AppTerminationRequestMapper terminationRequestMapper,
            PaySignAuditLogger auditLogger,
            AccountDomainPort accountDomainPort,
            ContractGatewayPort contractGatewayPort) {
        this.paySignInfoMapper = paySignInfoMapper;
        this.terminationRequestMapper = terminationRequestMapper;
        this.auditLogger = auditLogger;
        this.accountDomainPort = accountDomainPort;
        this.contractGatewayPort = contractGatewayPort;
        this.gatewayStatusMigrators = Map.of(
                STATUS_SIGNED, (seq, info) -> paySignInfoMapper.markSigned(seq, info.getPayAccountId(),
                        info.getPayAgreementNo(),
                        info.getSignTime() != null ? info.getSignTime() : LocalDateTime.now()),
                STATUS_UNSIGNED, (seq, info) -> paySignInfoMapper.markUnsigned(seq, LocalDateTime.now()),
                STATUS_NOT_SIGNED, (seq, info) -> paySignInfoMapper.reactivateForResign(seq));
    }

    /** IF8A-16 请求签约信息。 */
    @Override
    public RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request, String signChannel) {
        RequestSignInfoResult response = new RequestSignInfoResult();
        try {
            String paymentVendor = request == null ? null : normalizeVendor(request.getPayChannelCode());

            String validMsg = validateRequestSignInfo(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                auditSignInfo(request, response, signChannel);
                return response;
            }

            paymentVendor = normalizeVendor(request.getPayChannelCode());

            PaySignInfo existingSign = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), paymentVendor);
            if (existingSign != null) {
                fillError(response, PaySignErrorCodeEnum.ALREADY_SIGNED, "该用户已签约此支付渠道");
                auditSignInfo(request, response, signChannel);
                return response;
            }

            String sdkInfo = requestGatewaySignInfo(request, paymentVendor);
            if (!StringUtils.hasText(sdkInfo)) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "请求支付签约接口失败");
                auditSignInfo(request, response, signChannel);
                return response;
            }

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

    /** 支付宝出行-添加签约信息。 */
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
                auditAlipayTripSignInfo(request, response, request != null ? request.getChannel() : null, null);
                return response;
            }

            String paymentVendor = normalizeVendor(request.getChannel());

            PaySignInfo existingSign = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), paymentVendor);
            if (existingSign != null) {
                fillError(response, PaySignErrorCodeEnum.ALREADY_SIGNED, "该用户已签约此支付渠道");
                auditAlipayTripSignInfo(request, response, paymentVendor, null);
                return response;
            }

            PaySignInfo signInfo = new PaySignInfo();
            signInfo.setRequestSignSeq(request.getAgreementCode());
            signInfo.setThirdUserId(request.getThirdUserId());
            signInfo.setPaymentVendor(paymentVendor);
            signInfo.setSignChannel(SignChannelEnum.ALIPAY.getCode());
            signInfo.setDisplayAccount(request.getChannelUserAccount());
            signInfo.setContractStatus(STATUS_SIGNED);
            signInfo.setSignTime(LocalDateTime.now());
            paySignInfoMapper.insert(signInfo);

            fillSuccess(response);
            response.setRequestStartSdkInfo(null);
            auditAlipayTripSignInfo(request, response, paymentVendor, STATUS_SIGNED);
            return response;
        } catch (Exception e) {
            log.error("支付宝出行-添加签约信息异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            auditAlipayTripSignInfo(request, response, request != null ? request.getChannel() : null, null);
            return response;
        }
    }

    /** IF8A-21 信用能力咨询。 */
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
            switch (PaymentChannels.classify(paymentVendor)) {
                case PaymentChannel.Wallet ignored -> {
                    fillSuccess(response);
                    response.setMsg("钱包支付无需签约咨询");
                    response.setRetMsg("钱包支付无需签约咨询");
                    auditLogger.write("REQUEST_CONTRACT_ADVISORY_WALLET_NOT_APPLICABLE",
                            request.getThirdUserId(), request.getRequestSignSeq(), paymentVendor,
                            signChannel, request, response);
                    return response;
                }
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

    /** IF8A-22 签约结果查询。 */
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
            switch (PaymentChannels.classify(paymentVendor)) {
                case PaymentChannel.Wallet ignored -> {
                    return queryWalletBindingResult(request, signChannel, response);
                }
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

            if (existed && !request.getThirdUserId().equals(signInfo.getThirdUserId())) {
                log.warn("签约结果查询归属校验失败, requestSignSeq={}, requestThirdUserId={}, ownerThirdUserId={}",
                        request.getRequestSignSeq(), request.getThirdUserId(), signInfo.getThirdUserId());
                fillError(response, PaySignErrorCodeEnum.RECORD_NOT_EXIST, PaySignErrorCodeEnum.RECORD_NOT_EXIST.getMsg());
                auditContractResult(request, response, signChannel);
                return response;
            }

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
                    log.warn("支付平台已签约但本地无签约记录，只返回不落库, requestSignSeq={}, thirdUserId={}, payAgreementNo={}",
                            request.getRequestSignSeq(), request.getThirdUserId(), signInfo.getPayAgreementNo());
                }
                case ContractResultOutcome.NoLocalChange ignored -> {
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

    /** IF8A-06 请求解约。 */
    @Override
    public RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request, String signChannel) {
        RequestTerminationRespDTO response = new RequestTerminationRespDTO();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                auditTermination(request, response, signChannel);
                return response;
            }
            switch (PaymentChannels.classify(request.getPaymentVendor())) {
                case PaymentChannel.Wallet ignored -> {
                    return releaseWalletBinding(request, response);
                }
                case PaymentChannel.Contracted ignored -> {
                }
            }
            String validMsg = validateRequestTermination(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                auditTermination(request, response, signChannel);
                return response;
            }

            PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getRequestSignSeq(), request.getPaymentVendor());
            if (signInfo == null) {
                fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED, PaySignErrorCodeEnum.USER_NOT_SIGNED.getMsg());
                auditTermination(request, response, signChannel);
                return response;
            }

            AppTerminationRequest existRequest = terminationRequestMapper.selectPendingByUserId(request.getThirdUserId());
            if (existRequest != null) {
                fillError(response, PaySignErrorCodeEnum.ALREADY_TERMINATING, "用户正在解约中");
                auditTermination(request, response, signChannel);
                return response;
            }

            String cardId = StringUtils.hasText(request.getCardId()) ? request.getCardId() : signInfo.getCardId();
            String cardType = StringUtils.hasText(request.getCardType()) ? request.getCardType() : signInfo.getCardType();
            String paymentVendor = StringUtils.hasText(request.getPaymentVendor())
                    ? request.getPaymentVendor() : signInfo.getPaymentVendor();
            LocalDateTime now = LocalDateTime.now();

            AppTerminationRequest existBySeq = terminationRequestMapper
                    .selectByRequestSignSeq(request.getRequestSignSeq());
            if (existBySeq != null && !STATUS_FAILED.equals(existBySeq.getTerminationStatus())) {
                fillError(response, PaySignErrorCodeEnum.ALREADY_TERMINATING,
                        "该签约流水已有解约申请，当前状态=" + existBySeq.getTerminationStatus());
                auditTermination(request, response, signChannel);
                return response;
            }

            if (existBySeq != null) {
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
                AppTerminationRequest terminationRequest =
                        buildTerminationRequest(request, cardId, cardType, paymentVendor, now);
                terminationRequestMapper.insert(terminationRequest);
            }

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
        int updated = paySignInfoMapper.markUnsigned(request.getAgreementCode(), signInfo.getTerminationTime());
        SignStatusTransition.Result transit = SignStatusTransition.classify(updated, SignStatus.UNSIGNED,
                () -> paySignInfoMapper.selectSignStatusBySeq(request.getAgreementCode()));
        if (transit.isConflict()) {
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

    /** requestPayPlatformTermination / queryPayPlatformContractStatus 两个转发壳已于 2026-09-16 删除。 */

    /** 把支付平台返回的签约状态落到 APP_PAY_SIGN_INFO。 */
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
        GatewayStatusMigrator migrator = gatewayStatusMigrators.get(targetStatus);
        if (migrator == null) {
            log.warn("支付平台返回未知签约状态，不落库, requestSignSeq={}, gatewayStatus={}", requestSignSeq, targetStatus);
            return;
        }
        if (migrator.migrate(requestSignSeq, signInfo) == 0) {
            SignStatusTransition.Result transit = SignStatusTransition.classify(0,
                    SignStatus.parseOrNull(targetStatus),
                    () -> paySignInfoMapper.selectSignStatusBySeq(requestSignSeq));
            String current = transit.observedStatus();
            if (transit.isConflict()) {
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

    /** 兼容旧客户端误用 requestTermination 的场景，钱包只执行本地支付通道移除。 */
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

    /** 调用支付平台 contract 接口发起正式签约，取出 SDK 参数。 */
    private String requestGatewaySignInfo(RequestSignInfoReqDTO request, String paymentVendor) {
        GatewayReply reply = contractGatewayPort.requestContract(request, paymentVendor);
        if (!(reply instanceof GatewayReply.Accepted accepted)) {
            return null;
        }
        if (accepted.data() == null) {
            log.error("支付签约接口返回失败, code={}, msg={}",
                    accepted.raw() == null ? null : accepted.raw().getCode(),
                    accepted.raw() == null ? null : accepted.raw().getMsg());
            return null;
        }
        return stringValue(accepted.data().get("data"), JSON.toJSONString(accepted.data()));
    }

    /** ===== 审计流水的按接口收口（2026-09-16，ADR-D107）=====。 */

    private void auditSignInfo(RequestSignInfoReqDTO request, RequestSignInfoResult response, String signChannel) {
        auditLogger.write("REQUEST_SIGN_INFO",
                request != null ? request.getThirdUserId() : null,
                request != null ? request.getRequestSignSeq() : null,
                request != null ? normalizeVendor(request.getPayChannelCode()) : null,
                signChannel, request, response);
    }

    /** 支付宝出行签约的流水。 */
    /**
     * 支付宝出行签约的流水行。
     *
     * @param signStatus 成功分支传 {@code STATUS_SIGNED}，其余分支传 {@code null} ——
     */
    private void auditAlipayTripSignInfo(AlipayTripAddContractReqDTO request, RequestSignInfoResult response,
                                         String paymentVendor, String signStatus) {
        auditLogger.write("ALIPAY_TRIP_REQUEST_SIGN_INFO",
                request != null ? request.getThirdUserId() : null,
                request != null ? request.getAgreementCode() : null,
                paymentVendor, SignChannelEnum.ALIPAY.getCode(), request, response, signStatus);
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

    /** 钱包绑定状态查询的流水。 */
    private void auditWalletBindingResult(RequestContractResultReqDTO request, RequestContractResultRespDTO response,
                                          String signChannel) {
        auditLogger.write("REQUEST_WALLET_BINDING_RESULT",
                request != null ? request.getThirdUserId() : null, null,
                walletCode(), signChannel, request, response);
    }
}
