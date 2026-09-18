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
import com.chinasofti.huateng.paysign.port.AccountDomainPort;
import com.chinasofti.huateng.paysign.port.AccountPayChannelView;
import com.chinasofti.huateng.paysign.port.AccountQuery;
import com.chinasofti.huateng.paysign.port.ContractGatewayPort;
import com.chinasofti.huateng.paysign.port.GatewayReply;
import com.chinasofti.huateng.paysign.support.PaymentChannel;
import com.chinasofti.huateng.paysign.support.PaymentChannels;
import com.chinasofti.huateng.paysign.domain.PaySignDuplicateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** 解约域的**执行**能力：内部执行解约（`/internal/termination/execute`）与 IF8A-75 直接解绑。 */
@Service
public class TerminationExecutor {

    private static final Logger log = LoggerFactory.getLogger(TerminationExecutor.class);

    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();
    private static final String STATUS_SCANNING = TerminationStatus.SCANNING.name();
    private static final String STATUS_SUCCESS = TerminationStatus.SUCCESS.name();
    private static final String STATUS_FAILED = TerminationStatus.FAILED.name();

    /** 本类**不再持有** WALLET_PAYMENT_VENDOR 常量（2026-09-16，ADR-D108）。 */

    private final AppTerminationRequestMapper terminationRequestMapper;

    private final PaySignInfoMapper paySignInfoMapper;

    /** 支付中心签约/解约方向的出向端口（2026-09-16，ADR-D115）。 */
    private final ContractGatewayPort contractGatewayPort;

    /** 审计流水的唯一写入点。 */
    private final PaySignAuditLogger auditLogger;

    /** 账户域出向调用的唯一出口（ADR-D94 续）。 */
    private final AccountDomainPort accountDomainPort;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public TerminationExecutor(
            AppTerminationRequestMapper terminationRequestMapper,
            PaySignInfoMapper paySignInfoMapper,
            ContractGatewayPort contractGatewayPort,
            PaySignAuditLogger auditLogger,
            AccountDomainPort accountDomainPort) {
        this.terminationRequestMapper = terminationRequestMapper;
        this.paySignInfoMapper = paySignInfoMapper;
        this.contractGatewayPort = contractGatewayPort;
        this.auditLogger = auditLogger;
        this.accountDomainPort = accountDomainPort;
    }

    /** 本方法 NEVER 加 @Transactional：方法体内要调支付中心（contractGatewayPort.requestDismissal）。 */
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

        GatewayReply reply;
        try {
            reply = contractGatewayPort.requestDismissal(requestSignSeq);
        } catch (Exception e) {
            log.error("调用支付平台解约异常，保持 SCANNING 待主动查询收口, requestSignSeq={}", requestSignSeq, e);
            auditLogger.write("EXECUTE_TERMINATION", terminationRequest.getThirdUserId(),
                    requestSignSeq, terminationRequest.getPaymentVendor(), null, request, null);
            throw new TerminationException("执行支付平台解约异常，requestSignSeq=" + requestSignSeq, e);
        }

        auditLogger.write("EXECUTE_TERMINATION", terminationRequest.getThirdUserId(),
                requestSignSeq, terminationRequest.getPaymentVendor(), null, request, reply.raw());

        if (!(reply instanceof GatewayReply.Accepted)) {
            if (terminationRequestMapper.revertScanningToPending(requestSignSeq) == 0) {
                log.warn("支付平台解约失败但状态已被改走，放弃回退 PENDING, requestSignSeq={}", requestSignSeq);
            }
            throw new TerminationException("调用支付平台解约失败，requestSignSeq=" + requestSignSeq
                    + ", gatewayResponse=" + JSON.toJSONString(reply.raw()));
        }

        fillSuccess(response);
        return response;
    }

    /** IF8A-75 直接解绑支付方式。 */
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
        switch (PaymentChannels.classify(paymentVendor)) {
            case PaymentChannel.Wallet ignored -> {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM,
                        "钱包支付无签约协议，解绑请使用支付通道解绑接口");
                return response;
            }
            case PaymentChannel.Contracted ignored -> {
            }
        }

        ExecuteTerminationReqDTO executeRequest = new ExecuteTerminationReqDTO();
        executeRequest.setThirdUserId(request.getThirdUserId());
        executeRequest.setPaymentVendor(paymentVendor);
        executeRequest.setRequestSignSeq(requestSignSeq);
        fillCardInfoFromAccount(executeRequest, requestSignSeq);

        try {
            BaseRespDTO executeResponse = executeTermination(executeRequest);
            response.setRetCode(executeResponse.getRetCode());
            response.setRetMsg(executeResponse.getRetMsg());
        } catch (Exception e) {
            log.error("直接解绑支付方式失败, requestSignSeq={}", requestSignSeq, e);
            fillError(response, PaySignErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE,
                    PaySignErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE.getMsg());
        }
        return response;
    }

    /** 从 account-server 反查该签约流水对应的 CARD_ID / CARD_TYPE 并写入执行解约入参。 */
    private void fillCardInfoFromAccount(ExecuteTerminationReqDTO executeRequest, String requestSignSeq) {
        try {
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
            if (!PaySignDuplicateKey.isConflict(e)) {
                throw e;
            }
            log.info("解约申请已被并发插入，改用库中记录继续, requestSignSeq={}", request.getRequestSignSeq());
            return terminationRequestMapper.selectByRequestSignSeq(request.getRequestSignSeq());
        }
        log.info("解约申请不存在，已按签约记录补建 PENDING 申请, requestSignSeq={}, thirdUserId={}",
                record.getRequestSignSeq(), record.getThirdUserId());
        return record;
    }
}
