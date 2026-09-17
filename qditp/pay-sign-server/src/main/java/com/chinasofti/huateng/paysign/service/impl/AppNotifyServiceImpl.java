package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.AppNotifySigner.buildItpSign;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;
import static com.chinasofti.huateng.paysign.support.PaySignValues.defaultString;
import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.AppSignResultNotifyReqDTO;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.model.app.AppTerminationResultNotifyReqDTO;
import com.chinasofti.huateng.model.app.ItpCommonRequest;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.paysign.ResendSignNotifyRespDTO;
import com.chinasofti.huateng.paysign.client.AppNotificationClient;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.domain.TerminationFailReason;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executor;

@Service("paySignAppNotifyServiceImpl")
public class AppNotifyServiceImpl implements AppNotifyService {
    private static final Logger log = LoggerFactory.getLogger(AppNotifyServiceImpl.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** APP_TERMINATION_REQUEST.TERMINATION_STATUS 终态取值，取值来自 TerminationStatus。 */
    private static final String TERMINATION_STATUS_SUCCESS = TerminationStatus.SUCCESS.name();
    private static final String TERMINATION_STATUS_FAILED = TerminationStatus.FAILED.name();

    /** APP_PAY_SIGN_INFO.SIGN_STATUS 的已签约取值，与 {@code PaySignWorkflow.STATUS_SIGNED} 同值。 */
    private static final String SIGN_STATUS_SIGNED = "SIGNED";

    /** PENDING 滞留多久（分钟）视为「状态回写丢了」，纳入补偿。 */
    private static final int PENDING_STALE_MINUTES = 10;

    /** 单次补偿取多少条，与 TerminationInternalServiceImpl 保持一致。 */
    private static final int BATCH_SIZE = 200;

    private final PaySignRequestMapper paySignRequestMapper;

    private final PaySignInfoMapper paySignInfoMapper;

    private final AppTerminationRequestMapper terminationRequestMapper;

    private final AppNotificationClient appNotificationClient;

    private final Executor notifyExecutor;

    @Value("${app.notify.sign-result-url:http://dtcustomer.bestonepay.com/testngback/ci/app/receiveSignResult}")
    private String appNotifySignResultUrl;

    @Value("${app.notify.termination-result-url:http://127.0.0.1:8080/ci/app/receiveTerminationResultFromItp}")
    private String appNotifyTerminationResultUrl;

    @Value("${itp.providerId:06}")
    private String itpProviderId;

    /** 通知重试上限，超过后该记录不再被补偿扫到，只能人工介入。 */
    @Value("${app.notify.max-retry-count:10}")
    private int maxNotifyRetryCount;

    @Value("${itp.charset:UTF-8}")
    private String itpCharset;

    @Value("${itp.format:json}")
    private String itpFormat;

    @Value("${itp.deviceId:ITP-PAY-SIGN}")
    private String itpDeviceId;

    @Value("${itp.signType:null}")
    private String itpSignType;

    @Value("${itp.signKey:}")
    private String itpSignKey;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public AppNotifyServiceImpl(
            PaySignRequestMapper paySignRequestMapper,
            PaySignInfoMapper paySignInfoMapper,
            AppTerminationRequestMapper terminationRequestMapper,
            AppNotificationClient appNotificationClient,
            @Qualifier("notifyExecutor") Executor notifyExecutor) {
        this.paySignRequestMapper = paySignRequestMapper;
        this.paySignInfoMapper = paySignInfoMapper;
        this.terminationRequestMapper = terminationRequestMapper;
        this.appNotificationClient = appNotificationClient;
        this.notifyExecutor = notifyExecutor;
    }

    @Override
    public void asyncNotifySignResult(PaySignRequest request, PaySignInfo signInfo, ReceiveSignResultReqDTO receiveRequest) {
        submitNotifyTask(request, () -> {
            try {
                log.info("ready to exec 异步通知App签约结果, requestSignSeq={}", request.getRequestSignSeq());
                doNotifySignResult(request, signInfo, receiveRequest);
            } catch (Exception e) {
                log.error("异步通知App签约结果异常, requestSignSeq={}", request.getRequestSignSeq(), e);
                updateNotifyStatus(request, false, "异步异常:" + e.getMessage());
            }
        });
    }

    @Override
    public void asyncNotifyTerminationResult(AppTerminationRequest terminationRequest, PaySignInfo signInfo, ReceiveTerminationResultReqDTO receiveRequest) {
        submitNotifyTask(terminationRequest, () -> {
            try {
                doNotifyTerminationResult(terminationRequest, signInfo, receiveRequest);
            } catch (Exception e) {
                log.error("异步通知App解约结果异常, requestSignSeq={}", terminationRequest.getRequestSignSeq(), e);
                updateNotifyStatus(terminationRequest, false, "异步异常:" + e.getMessage());
            }
        });
    }

    @Override
    public void asyncNotifyTerminationFailed(AppTerminationRequest terminationRequest, NotifyTerminationFailedReqDTO receiveRequest) {
        submitNotifyTask(terminationRequest, () -> {
            try {
                doNotifyTerminationFailed(terminationRequest, receiveRequest);
            } catch (Exception e) {
                log.error("异步通知App解约失败异常, requestSignSeq={}", terminationRequest.getRequestSignSeq(), e);
                updateNotifyStatus(terminationRequest, false, "异步异常:" + e.getMessage());
            }
        });
    }

    /** 重发一条解约结果通知：按解约申请的终态决定重发成功通知还是失败通知。 */
    @Override
    public void asyncRetryTerminationNotify(AppTerminationRequest terminationRequest) {
        submitNotifyTask(terminationRequest, () -> {
            try {
                if (TERMINATION_STATUS_FAILED.equals(terminationRequest.getTerminationStatus())) {
                    NotifyTerminationFailedReqDTO failedRequest = new NotifyTerminationFailedReqDTO();
                    failedRequest.setThirdUserId(terminationRequest.getThirdUserId());
                    failedRequest.setRequestSignSeq(terminationRequest.getRequestSignSeq());
                    failedRequest.setPaymentVendor(terminationRequest.getPaymentVendor());
                    failedRequest.setCardId(terminationRequest.getCardId());
                    failedRequest.setCardType(terminationRequest.getCardType());
                    failedRequest.setFailReason(
                            TerminationFailReason.stripManualMark(terminationRequest.getFailReason()));
                    doNotifyTerminationFailed(terminationRequest, failedRequest);
                    return;
                }
                ReceiveTerminationResultReqDTO receiveRequest = new ReceiveTerminationResultReqDTO();
                receiveRequest.setThirdUserId(terminationRequest.getThirdUserId());
                receiveRequest.setRequestSignSeq(terminationRequest.getRequestSignSeq());
                receiveRequest.setPaymentVendor(terminationRequest.getPaymentVendor());
                receiveRequest.setCardId(terminationRequest.getCardId());
                receiveRequest.setCardType(terminationRequest.getCardType());
                receiveRequest.setStatus(TERMINATION_STATUS_SUCCESS);
                if (terminationRequest.getCompleteTime() != null) {
                    receiveRequest.setDismissalTime(terminationRequest.getCompleteTime().format(DATETIME_FORMATTER));
                }
                doNotifyTerminationResult(terminationRequest, null, receiveRequest);
            } catch (Exception e) {
                log.error("重发解约结果通知异常, requestSignSeq={}", terminationRequest.getRequestSignSeq(), e);
                updateNotifyStatus(terminationRequest, false, "补偿异常:" + e.getMessage());
            }
        });
    }

    /** 解约成功通知：以 APP_TERMINATION_REQUEST 为主记录更新通知状态。 */
    private void doNotifyTerminationResult(AppTerminationRequest terminationRequest, PaySignInfo signInfo, ReceiveTerminationResultReqDTO receiveRequest) {
        try {
            ItpCommonRequest<AppTerminationResultNotifyReqDTO> notifyRequest = new ItpCommonRequest<>();
            notifyRequest.setProviderId(itpProviderId);
            notifyRequest.setCharset(itpCharset);
            notifyRequest.setFormat(itpFormat);
            notifyRequest.setTimestamp(LocalDateTime.now().format(DATETIME_FORMATTER));
            notifyRequest.setDeviceId(itpDeviceId);
            notifyRequest.setSignType(itpSignType);

            AppTerminationResultNotifyReqDTO bizData = new AppTerminationResultNotifyReqDTO();
            bizData.setThirdUserId(signInfo != null && StringUtils.hasText(signInfo.getThirdUserId())
                    ? signInfo.getThirdUserId() : terminationRequest.getThirdUserId());
            bizData.setRequestSignSeq(terminationRequest.getRequestSignSeq());
            bizData.setCardId(stringValue(receiveRequest != null ? receiveRequest.getCardId() : null,
                    signInfo != null ? signInfo.getCardId() : terminationRequest.getCardId()));
            bizData.setCardType(stringValue(receiveRequest != null ? receiveRequest.getCardType() : null,
                    signInfo != null ? signInfo.getCardType() : terminationRequest.getCardType()));
            bizData.setTerminationResult(receiveRequest != null ? receiveRequest.getStatus() : "SUCCESS");
            bizData.setTerminationResultMsg("");
            bizData.setTerminationTime(receiveRequest != null && StringUtils.hasText(receiveRequest.getDismissalTime())
                    ? receiveRequest.getDismissalTime() : LocalDateTime.now().format(DATETIME_FORMATTER));

            notifyRequest.setBizData(bizData);
            notifyRequest.setSign(buildItpSign(notifyRequest, itpSignKey));

            updateNotifyStatus(terminationRequest, appNotificationClient.notify(appNotifyTerminationResultUrl, toClientRequest(notifyRequest)));
        } catch (Exception e) {
            log.error("通知App解约结果异常, requestSignSeq={}", terminationRequest.getRequestSignSeq(), e);
            updateNotifyStatus(terminationRequest, false, "异常:" + e.getMessage());
        }
    }

    private void doNotifyTerminationFailed(AppTerminationRequest terminationRequest, NotifyTerminationFailedReqDTO receiveRequest) {
        try {
            ItpCommonRequest<AppTerminationResultNotifyReqDTO> notifyRequest = new ItpCommonRequest<>();
            notifyRequest.setProviderId(itpProviderId);
            notifyRequest.setCharset(itpCharset);
            notifyRequest.setFormat(itpFormat);
            notifyRequest.setTimestamp(LocalDateTime.now().format(DATETIME_FORMATTER));
            notifyRequest.setDeviceId(itpDeviceId);
            notifyRequest.setSignType(itpSignType);

            AppTerminationResultNotifyReqDTO bizData = new AppTerminationResultNotifyReqDTO();
            bizData.setThirdUserId(terminationRequest.getThirdUserId());
            bizData.setRequestSignSeq(terminationRequest.getRequestSignSeq());
            bizData.setCardId(terminationRequest.getCardId());
            bizData.setCardType(terminationRequest.getCardType());
            bizData.setTerminationResult("FAIL");
            bizData.setTerminationResultMsg(StringUtils.hasText(receiveRequest.getFailReason()) ? receiveRequest.getFailReason() : "存在扣费失败订单");
            bizData.setTerminationTime(LocalDateTime.now().format(DATETIME_FORMATTER));

            notifyRequest.setBizData(bizData);
            notifyRequest.setSign(buildItpSign(notifyRequest, itpSignKey));

            updateNotifyStatus(terminationRequest, appNotificationClient.notify(appNotifyTerminationResultUrl, toClientRequest(notifyRequest)));
        } catch (Exception e) {
            log.error("通知App解约失败异常, requestSignSeq={}", terminationRequest.getRequestSignSeq(), e);
            updateNotifyStatus(terminationRequest, false, "异常:" + e.getMessage());
        }
    }

    /** 组装并投递签约结果通知，返回本次投递结果。 */
    private AppNotificationClient.NotificationResult doNotifySignResult(PaySignRequest request, PaySignInfo signInfo, ReceiveSignResultReqDTO receiveRequest) {
        try {
            ItpCommonRequest<AppSignResultNotifyReqDTO> notifyRequest = new ItpCommonRequest<>();
            notifyRequest.setProviderId(itpProviderId);
            notifyRequest.setCharset(itpCharset);
            notifyRequest.setFormat(itpFormat);
            notifyRequest.setTimestamp(LocalDateTime.now().format(DATETIME_FORMATTER));
            notifyRequest.setDeviceId(itpDeviceId);
            notifyRequest.setSignType(itpSignType);

            AppSignResultNotifyReqDTO bizData = new AppSignResultNotifyReqDTO();
            bizData.setThirdUserId(signInfo != null && StringUtils.hasText(signInfo.getThirdUserId())
                    ? signInfo.getThirdUserId() : request.getThirdUserId());
            bizData.setRequestSignSeq(request.getRequestSignSeq());
            bizData.setPaymentVendor(stringValue(receiveRequest != null ? receiveRequest.getPaymentVendor() : null, signInfo != null ? signInfo.getPaymentVendor() : request.getPaymentVendor()));
            bizData.setPayAccountId(stringValue(receiveRequest != null ? receiveRequest.getPayUserId() : null, request.getPayAccountId()));
            bizData.setPayAgreementNo(stringValue(receiveRequest != null ? receiveRequest.getPayAgreementNo() : null, request.getPayAgreementNo()));
            bizData.setSignResult(receiveRequest != null ? receiveRequest.getStatus() : request.getSignStatus());
            bizData.setRealNameAuthResult(receiveRequest != null ? receiveRequest.getStatus() : request.getSignStatus());

            notifyRequest.setBizData(bizData);
            notifyRequest.setSign(buildItpSign(notifyRequest, itpSignKey));

            AppNotificationClient.NotificationResult result =
                    appNotificationClient.notify(appNotifySignResultUrl, toClientRequest(notifyRequest));
            updateNotifyStatus(request, result);
            return result;
        } catch (Exception e) {
            log.error("通知App签约结果异常, requestSignSeq={}", request.getRequestSignSeq(), e);
            updateNotifyStatus(request, false, "异常:" + e.getMessage());
            return AppNotificationClient.NotificationResult.failure("异常:" + e.getMessage());
        }
    }

    @Override
    public CompensateNotifyRespDTO compensateSignNotify() {
        CompensateNotifyRespDTO response = new CompensateNotifyRespDTO();
        try {
            List<PaySignRequest> failedList = paySignRequestMapper
                    .selectCompensableNotify(maxNotifyRetryCount, PENDING_STALE_MINUTES, BATCH_SIZE);
            if (failedList == null || failedList.isEmpty()) {
                fillSuccess(response);
                return response;
            }
            response.setScanned(failedList.size());
            for (PaySignRequest request : failedList) {
                try {
                    log.info("补偿通知, requestSignSeq={}, operationType={}, notifyStatus={}, retryCount={}",
                            request.getRequestSignSeq(), request.getOperationType(),
                            request.getNotifyStatus(), request.getNotifyRetryCount());
                    paySignRequestMapper.increaseRetryCount(request.getId());
                    PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getRequestSignSeq(), request.getPaymentVendor());
                    ReceiveSignResultReqDTO receiveRequest = parseRequestBody(request.getRequestBody(), ReceiveSignResultReqDTO.class);
                    submitNotifyTask(request, () -> doNotifySignResult(request, signInfo, receiveRequest));
                    response.setSubmitted(response.getSubmitted() + 1);
                } catch (Exception e) {
                    log.error("提交签约流水通知重发异常, requestSignSeq={}", request.getRequestSignSeq(), e);
                    response.setSkipped(response.getSkipped() + 1);
                }
            }
            log.info("签约流水通知补偿完成, scanned={}, submitted={}, skipped={}",
                    response.getScanned(), response.getSubmitted(), response.getSkipped());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("签约流水通知补偿异常", e);
            response.setResultCode(PaySignErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setResultMsg(PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    public ResendSignNotifyRespDTO resendSignNotify(String requestSignSeq) {
        ResendSignNotifyRespDTO response = new ResendSignNotifyRespDTO();
        response.setRequestSignSeq(requestSignSeq);
        if (!StringUtils.hasText(requestSignSeq)) {
            return fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "requestSignSeq不能为空");
        }
        try {
            PaySignInfo signInfo = paySignInfoMapper.selectBySeq(requestSignSeq, null);
            if (signInfo == null) {
                log.warn("单条通知重发被拒：签约记录不存在, requestSignSeq={}", requestSignSeq);
                return fillError(response, PaySignErrorCodeEnum.RECORD_NOT_EXIST, null);
            }
            if (!SIGN_STATUS_SIGNED.equals(signInfo.getSignStatus())) {
                log.warn("单条通知重发被拒：签约状态非 SIGNED, requestSignSeq={}, signStatus={}",
                        requestSignSeq, signInfo.getSignStatus());
                return fillError(response, PaySignErrorCodeEnum.USER_NOT_SIGNED,
                        "签约状态为" + signInfo.getSignStatus() + "，只允许重发 SIGNED 的签约结果通知");
            }
            PaySignRequest request = paySignRequestMapper.selectLatestSignResultBySeq(requestSignSeq);
            if (request == null) {
                log.warn("单条通知重发被拒：无 RECEIVE_SIGN_RESULT 流水, requestSignSeq={}", requestSignSeq);
                return fillError(response, PaySignErrorCodeEnum.RECORD_NOT_EXIST, "无签约结果流水，不存在可重发的通知");
            }

            ReceiveSignResultReqDTO receiveRequest = parseRequestBody(request.getRequestBody(), ReceiveSignResultReqDTO.class);
            log.info("单条重发签约结果通知, requestSignSeq={}, id={}, 原通知状态={}, 重试次数={}",
                    requestSignSeq, request.getId(), request.getNotifyStatus(), request.getNotifyRetryCount());
            AppNotificationClient.NotificationResult result = doNotifySignResult(request, signInfo, receiveRequest);
            response.setNotified(result.success());
            response.setNotifyResult(result.message());
            log.info("单条重发签约结果通知完成, requestSignSeq={}, notified={}, result={}",
                    requestSignSeq, result.success(), result.message());
            return fillSuccess(response);
        } catch (Exception e) {
            log.error("单条重发签约结果通知异常, requestSignSeq={}", requestSignSeq, e);
            return fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, null);
        }
    }

    private <T> T parseRequestBody(String requestBody, Class<T> clazz) {
        if (!StringUtils.hasText(requestBody)) {
            return null;
        }
        try {
            return JSON.parseObject(requestBody, clazz);
        } catch (Exception e) {
            log.warn("解析请求体失败, requestBody={}", requestBody, e);
            return null;
        }
    }

    private void submitNotifyTask(PaySignRequest request, Runnable task) {
        try {
            notifyExecutor.execute(task);
        } catch (TaskRejectedException e) {
            log.error("通知线程池拒绝任务, requestSignSeq={}", request.getRequestSignSeq(), e);
            updateNotifyStatus(request, false, "线程池拒绝:" + e.getMessage());
        }
    }

    private void submitNotifyTask(AppTerminationRequest request, Runnable task) {
        try {
            notifyExecutor.execute(task);
        } catch (TaskRejectedException e) {
            log.error("通知线程池拒绝任务, requestSignSeq={}", request.getRequestSignSeq(), e);
            updateNotifyStatus(request, false, "线程池拒绝:" + e.getMessage());
        }
    }

    private void updateNotifyStatus(PaySignRequest request, boolean success, String result) {
        PaySignRequest update = new PaySignRequest();
        update.setId(request.getId());
        update.setNotifyStatus(success ? "SUCCESS" : "FAILED");
        update.setNotifyTime(LocalDateTime.now());
        update.setNotifyResult(result);
        paySignRequestMapper.updateNotifyStatus(update);
    }

    private void updateNotifyStatus(AppTerminationRequest request, boolean success, String result) {
        terminationRequestMapper.updateNotifyStatus(request.getRequestSignSeq(),
                success ? "SUCCESS" : "FAILED", LocalDateTime.now(), result);
    }

    /** 将领域通知模型转换为客户端只关心的 ITP 表单参数。 */
    private AppNotificationClient.NotificationRequest toClientRequest(ItpCommonRequest<?> request) {
        return new AppNotificationClient.NotificationRequest(
                request.getProviderId(), request.getCharset(), request.getFormat(), request.getTimestamp(),
                request.getDeviceId(), request.getSignType(), request.getSign(), JSON.toJSONString(request.getBizData()));
    }

    /** 统一落库通知客户端的成功状态或失败原因。 */
    private void updateNotifyStatus(PaySignRequest request, AppNotificationClient.NotificationResult result) {
        updateNotifyStatus(request, result.success(), result.message());
    }

    private void updateNotifyStatus(AppTerminationRequest request, AppNotificationClient.NotificationResult result) {
        updateNotifyStatus(request, result.success(), result.message());
    }

}
