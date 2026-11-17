package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;
import static com.chinasofti.huateng.paysign.support.PaySignValidators.validateReceiveSignResult;
import static com.chinasofti.huateng.paysign.support.PaySignValues.parseDateTime;

import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.domain.SignStatus;
import com.chinasofti.huateng.paysign.audit.PaySignAuditLogger;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.support.CallbackLookups;
import com.chinasofti.huateng.paysign.domain.PaySignDuplicateKey;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.event.SignResultCommittedEvent;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service
public class SignResultCallbackHandler {
    private static final Logger log = LoggerFactory.getLogger(SignResultCallbackHandler.class);


    private static final String STATUS_SIGNED = SignStatus.SIGNED.name();
    private static final String SIGN_LOG_STATUS_FAILED = SignStatus.FAILED.name();

    private final PaySignInfoMapper paySignInfoMapper;
    private final PaySignRequestMapper paySignRequestMapper;
    private final PaySignAuditLogger auditLogger;
    private final ApplicationEventPublisher eventPublisher;

    public SignResultCallbackHandler(
            PaySignInfoMapper paySignInfoMapper,
            PaySignRequestMapper paySignRequestMapper,
            PaySignAuditLogger auditLogger,
            ApplicationEventPublisher eventPublisher) {
        this.paySignInfoMapper = paySignInfoMapper;
        this.paySignRequestMapper = paySignRequestMapper;
        this.auditLogger = auditLogger;
        this.eventPublisher = eventPublisher;
    }

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

            String thirdUserId = CallbackLookups.resolveThirdUserId(paySignRequestMapper, request.getRequestSignSeq(), request.getThirdUserId());
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
                try {
                    paySignInfoMapper.insert(signInfo);
                } catch (RuntimeException e) {
                    if (!PaySignDuplicateKey.isConflict(e)) {
                        throw e;
                    }
                    return replaySignResult(request, signChannel, response);
                }

                auditLogger.writeSignResultNotifyPending(request, signChannel, STATUS_SIGNED);

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

    private PaySignCallbackResult replaySignResult(ReceiveSignResultReqDTO request, String signChannel,
                                                   PaySignCallbackResult response) {
        String observedStatus = paySignInfoMapper.selectSignStatusBySeq(request.getRequestSignSeq());
        if (!STATUS_SIGNED.equals(observedStatus)) {
            log.warn("签约结果回调重放，但库内状态不是 SIGNED，按幂等返成功且不复活, requestSignSeq={}, observedStatus={}, signChannel={}",
                    request.getRequestSignSeq(), observedStatus, signChannel);
        } else {
            log.warn("签约结果回调幂等重放（已是SIGNED，不重复通知）, requestSignSeq={}, signChannel={}",
                    request.getRequestSignSeq(), signChannel);
        }
        fillSuccess(response);
        auditLogger.write("RECEIVE_SIGN_RESULT_REPLAY", request.getThirdUserId(), request.getRequestSignSeq(),
                request.getPaymentVendor(), signChannel, request, response);
        return response;
    }

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
}
