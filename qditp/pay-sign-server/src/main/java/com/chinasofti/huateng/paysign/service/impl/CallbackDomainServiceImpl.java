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

/** 回调领域服务：签约结果回调（IPD02）与解约结果回调（IPD03）的真实现。 */
@Service
public class CallbackDomainServiceImpl implements CallbackDomainService {
    private static final Logger log = LoggerFactory.getLogger(CallbackDomainServiceImpl.class);

    private static final String STATUS_SIGNED = "SIGNED";
    private static final String STATUS_UNSIGNED = "UNSIGNED";

    /** 解约申请状态，取值来自 {@link TerminationStatus}。 */
    private static final String STATUS_FAILED = TerminationStatus.FAILED.name();

    private static final String STATUS_SCANNING = TerminationStatus.SCANNING.name();
    private static final String STATUS_SUCCESS = TerminationStatus.SUCCESS.name();

    /** 写入 {@code APP_PAY_SIGN_LOG.SIGN_STATUS} 的失败态，取值来自 {@link SignStatus}。 */
    private static final String SIGN_LOG_STATUS_FAILED = SignStatus.FAILED.name();

    /** 本类**不再持有** WALLET_PAYMENT_VENDOR 常量（2026-09-16，ADR-D108）。 */

    private final PaySignInfoMapper paySignInfoMapper;
    private final PaySignRequestMapper paySignRequestMapper;
    private final AppTerminationRequestMapper terminationRequestMapper;
    private final AppNotifyService appNotifyService;
    /** 接口流水（{@code APP_PAY_SIGN_REQUEST}）的唯一写入点，NEVER 在本类重建一份 writeLog。 */
    private final PaySignAuditLogger auditLogger;
    /** 只用于在 @Transactional 方法内发布「已提交」事件，让通知投递落到 afterCommit。 */
    private final ApplicationEventPublisher eventPublisher;

    /** 解约回调的本地写入用它显式开短事务（ADR-D8 第一处）。 */
    private final TransactionTemplate transactionTemplate;

    /** 通道清理的唯一投递点，快速路径与扫表补偿共用，NEVER 在本类复制其三分支处置（ADR-D48）。 */
    private final ChannelSyncDeliverer channelSyncDeliverer;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
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

    /** IPD02 签约结果回调。 */
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
            String validMsg = validateReceiveSignResult(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                auditLogger.write("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            String thirdUserId = resolveThirdUserId(request.getRequestSignSeq(), request.getThirdUserId());
            if (!StringUtils.hasText(thirdUserId)) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "thirdUserId不能为空");
                auditLogger.write("RECEIVE_SIGN_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }
            request.setThirdUserId(thirdUserId);

            String displayAccount = resolveDisplayAccount(request.getRequestSignSeq(), request.getDisplayAccount());
            request.setDisplayAccount(displayAccount);

            boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getStatus());

            log.info("签约成功，准备通知app--- {} ., displayAccount: {}",request,displayAccount);
            if (isSuccess) {
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

                eventPublisher.publishEvent(new SignResultCommittedEvent(
                        request.getRequestSignSeq(), request.getPaymentVendor(), request));
            } else {
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

    /** IPD03 解约结果回调。 */
    /** 本方法 NEVER 加 @Transactional（2026-09-12 / ADR-D48 摘掉，此前一直带着）。 */
    @Override
    public BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel) {
        BaseRespDTO response = new BaseRespDTO();
        try {
            if (request == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                auditLogger.write("RECEIVE_TERMINATION_RESULT", null, null, null, null, null, response);
                return response;
            }
            switch (PaymentChannels.classify(request.getPaymentVendor())) {
                case PaymentChannel.Wallet ignored -> {
                    fillSuccess(response);
                    response.setMsg("钱包支付不需要解约回调");
                    response.setRetMsg("钱包支付不需要解约回调");
                    auditLogger.write("RECEIVE_TERMINATION_RESULT_WALLET_IGNORED", request.getThirdUserId(),
                            request.getRequestSignSeq(), PaymentChannels.walletCode(),
                            signChannel, request, response);
                    return response;
                }
                case PaymentChannel.Contracted ignored -> {
                }
            }
            String validMsg = validateReceiveTerminationResult(request);
            if (validMsg != null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, validMsg);
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            String thirdUserId = resolveThirdUserId(request.getRequestSignSeq(), request.getThirdUserId());
            if (!StringUtils.hasText(thirdUserId)) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "thirdUserId不能为空");
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }
            request.setThirdUserId(thirdUserId);

            resolveCardInfoFromSignInfo(request);

            AppTerminationRequest terminationRequest = terminationRequestMapper.selectByRequestSignSeq(request.getRequestSignSeq());
            if (terminationRequest == null) {
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请不存在");
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            if (STATUS_SUCCESS.equals(terminationRequest.getTerminationStatus()) || STATUS_FAILED.equals(terminationRequest.getTerminationStatus())) {
                fillSuccess(response);
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            resolveCardInfoFromTerminationRequest(request, terminationRequest);

            if (!STATUS_SCANNING.equals(terminationRequest.getTerminationStatus())) {
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请不存在或状态不正确");
                auditLogger.write("RECEIVE_TERMINATION_RESULT", request.getThirdUserId(), request.getRequestSignSeq(), request.getPaymentVendor(), signChannel, request, response);
                return response;
            }

            boolean isSuccess = "SUCCESS".equalsIgnoreCase(request.getStatus());

            if (isSuccess) {
                record LocalClosure(PaySignInfo signInfo, TerminationStatusTransition.Result transit) {
                }
                LocalClosure closure = transactionTemplate.execute(txStatus -> {
                    PaySignInfo local = paySignInfoMapper.selectByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());
                    paySignInfoMapper.deleteByUserAndVendor(request.getThirdUserId(), request.getPaymentVendor());

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

                    TerminationStatusTransition.Result t = TerminationStatusTransition.classify(
                            terminationRequestMapper.markSuccess(request.getRequestSignSeq(), LocalDateTime.now()),
                            TerminationStatus.SUCCESS,
                            () -> terminationRequestMapper.selectTerminationStatusBySeq(request.getRequestSignSeq()));
                    if (t.isDone()) {
                        terminationRequestMapper.initChannelSyncPending(request.getRequestSignSeq());
                    }
                    return new LocalClosure(local, t);
                });

                TerminationStatusTransition.Result transit = closure.transit();
                if (transit.isDone()) {
                    appNotifyService.asyncNotifyTerminationResult(terminationRequest, closure.signInfo(), request);
                    syncChannelRemovalAfterCommit(request);
                } else if (transit.isConflict()) {
                    log.error("解约成功收口未命中 SCANNING，已跳过成功通知，MUST 人工核对, "
                                    + "requestSignSeq={}, outcome={}, observedStatus={}",
                            request.getRequestSignSeq(), transit.outcome(), transit.observedStatus());

                    if (TerminationStatus.FAILED.name().equals(transit.observedStatus())) {
                        int marked = terminationRequestMapper.markConflictForManualReview(
                                request.getRequestSignSeq(),
                                TerminationFailReason.successConflictNote(),
                                TerminationFailReason.MANUAL_REVIEW_MARK);
                        log.warn("解约结果矛盾已留痕, requestSignSeq={}, markedRows={}",
                                request.getRequestSignSeq(), marked);
                    }
                } else {
                    log.info("解约成功收口已由另一路完成，本次按幂等跳过, requestSignSeq={}, observedStatus={}",
                            request.getRequestSignSeq(), transit.observedStatus());
                }
            } else {
                String failReason = StringUtils.hasText(request.getStatus()) ? request.getStatus() : "支付平台解约失败";
                TerminationStatusTransition.Result rejectTransit = TerminationStatusTransition.classify(
                        terminationRequestMapper.rejectScanning(request.getRequestSignSeq(), failReason, LocalDateTime.now()),
                        TerminationStatus.FAILED,
                        () -> terminationRequestMapper.selectTerminationStatusBySeq(request.getRequestSignSeq()));

                if (rejectTransit.isDone()) {
                    NotifyTerminationFailedReqDTO failedNotifyRequest = new NotifyTerminationFailedReqDTO();
                    failedNotifyRequest.setThirdUserId(request.getThirdUserId());
                    failedNotifyRequest.setRequestSignSeq(request.getRequestSignSeq());
                    failedNotifyRequest.setPaymentVendor(request.getPaymentVendor());
                    failedNotifyRequest.setCardId(request.getCardId());
                    failedNotifyRequest.setCardType(request.getCardType());
                    failedNotifyRequest.setFailReason(failReason);
                    appNotifyService.asyncNotifyTerminationFailed(terminationRequest, failedNotifyRequest);
                } else {
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

    /** 支付平台回调可能不带 thirdUserId，尝试从流水表补充。 */
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

    /** 支付平台回调可能不带 displayAccount，尝试从流水表补充。 */
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

    /** 支付平台回调可能不带 cardId/cardType，尝试从签约主表补充。 */
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

    /** 从解约申请记录补齐 cardId/cardType。 */
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

    /** 解约本地事务**提交之后**去账户域删支付通道（ADR-D48）。 */
    private void syncChannelRemovalAfterCommit(ReceiveTerminationResultReqDTO request) {
        channelSyncDeliverer.deliver(request.getRequestSignSeq(), request.getThirdUserId(),
                request.getPaymentVendor(), request.getCardId(), request.getCardType());
    }
}
