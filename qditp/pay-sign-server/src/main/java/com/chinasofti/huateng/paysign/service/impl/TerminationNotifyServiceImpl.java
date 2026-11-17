package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;

import com.chinasofti.huateng.model.app.AppTerminationResultNotifyReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.paysign.domain.TerminationFailReason;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.port.AppNotifyPort;
import com.chinasofti.huateng.paysign.port.NotifyDelivery;
import com.chinasofti.huateng.paysign.service.TerminationNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executor;


@Service("paySignTerminationNotifyServiceImpl")
public class TerminationNotifyServiceImpl implements TerminationNotifyService {
    private static final Logger log = LoggerFactory.getLogger(TerminationNotifyServiceImpl.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String TERMINATION_STATUS_SUCCESS = TerminationStatus.SUCCESS.name();
    private static final String TERMINATION_STATUS_FAILED = TerminationStatus.FAILED.name();

    private final AppTerminationRequestMapper terminationRequestMapper;

    private final AppNotifyPort appNotifyPort;

    private final Executor notifyExecutor;

    public TerminationNotifyServiceImpl(
            AppTerminationRequestMapper terminationRequestMapper,
            AppNotifyPort appNotifyPort,
            @Qualifier("notifyExecutor") Executor notifyExecutor) {
        this.terminationRequestMapper = terminationRequestMapper;
        this.appNotifyPort = appNotifyPort;
        this.notifyExecutor = notifyExecutor;
    }

    @Override
    public void asyncNotifyTerminationResult(AppTerminationRequest terminationRequest, PaySignInfo signInfo,
                                            ReceiveTerminationResultReqDTO receiveRequest) {
        submitNotifyTask(terminationRequest, TERMINATION_STATUS_SUCCESS, () -> {
            try {
                doNotifyTerminationResult(terminationRequest, signInfo, receiveRequest);
            } catch (Exception e) {
                log.error("异步通知App解约结果异常, requestSignSeq={}", terminationRequest.getRequestSignSeq(), e);
                updateNotifyStatus(terminationRequest, TERMINATION_STATUS_SUCCESS, false, "异步异常:" + e.getMessage());
            }
        });
    }

    @Override
    public void asyncNotifyTerminationFailed(AppTerminationRequest terminationRequest,
                                             NotifyTerminationFailedReqDTO receiveRequest) {
        submitNotifyTask(terminationRequest, TERMINATION_STATUS_FAILED, () -> {
            try {
                doNotifyTerminationFailed(terminationRequest, receiveRequest);
            } catch (Exception e) {
                log.error("异步通知App解约失败异常, requestSignSeq={}", terminationRequest.getRequestSignSeq(), e);
                updateNotifyStatus(terminationRequest, TERMINATION_STATUS_FAILED, false, "异步异常:" + e.getMessage());
            }
        });
    }

    @Override
    public void asyncRetryTerminationNotify(AppTerminationRequest terminationRequest) {
        String expectedStatus = TERMINATION_STATUS_FAILED.equals(terminationRequest.getTerminationStatus())
                ? TERMINATION_STATUS_FAILED : TERMINATION_STATUS_SUCCESS;
        submitNotifyTask(terminationRequest, expectedStatus, () -> {
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
                updateNotifyStatus(terminationRequest, expectedStatus, false, "补偿异常:" + e.getMessage());
            }
        });
    }

    private void doNotifyTerminationResult(AppTerminationRequest terminationRequest, PaySignInfo signInfo, ReceiveTerminationResultReqDTO receiveRequest) {
        try {
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

            updateNotifyStatus(terminationRequest, TERMINATION_STATUS_SUCCESS,
                    appNotifyPort.pushTerminationResult(bizData));
        } catch (Exception e) {
            log.error("通知App解约结果异常, requestSignSeq={}", terminationRequest.getRequestSignSeq(), e);
            updateNotifyStatus(terminationRequest, TERMINATION_STATUS_SUCCESS, false, "异常:" + e.getMessage());
        }
    }

    private void doNotifyTerminationFailed(AppTerminationRequest terminationRequest, NotifyTerminationFailedReqDTO receiveRequest) {
        try {
            AppTerminationResultNotifyReqDTO bizData = new AppTerminationResultNotifyReqDTO();
            bizData.setThirdUserId(terminationRequest.getThirdUserId());
            bizData.setRequestSignSeq(terminationRequest.getRequestSignSeq());
            bizData.setCardId(terminationRequest.getCardId());
            bizData.setCardType(terminationRequest.getCardType());
            bizData.setTerminationResult("FAIL");
            bizData.setTerminationResultMsg(StringUtils.hasText(receiveRequest.getFailReason()) ? receiveRequest.getFailReason() : "存在扣费失败订单");
            bizData.setTerminationTime(LocalDateTime.now().format(DATETIME_FORMATTER));

            updateNotifyStatus(terminationRequest, TERMINATION_STATUS_FAILED,
                    appNotifyPort.pushTerminationResult(bizData));
        } catch (Exception e) {
            log.error("通知App解约失败异常, requestSignSeq={}", terminationRequest.getRequestSignSeq(), e);
            updateNotifyStatus(terminationRequest, TERMINATION_STATUS_FAILED, false, "异常:" + e.getMessage());
        }
    }

    private void submitNotifyTask(AppTerminationRequest request, String expectedTerminationStatus, Runnable task) {
        try {
            notifyExecutor.execute(task);
        } catch (TaskRejectedException e) {
            log.error("通知线程池拒绝任务, requestSignSeq={}", request.getRequestSignSeq(), e);
            updateNotifyStatus(request, expectedTerminationStatus, false, "线程池拒绝:" + e.getMessage());
        }
    }


    private void updateNotifyStatus(AppTerminationRequest request, String expectedTerminationStatus,
                                    boolean success, String result) {
        int affected = terminationRequestMapper.updateNotifyStatus(request.getRequestSignSeq(),
                expectedTerminationStatus, success ? "SUCCESS" : "FAILED", LocalDateTime.now(), result);
        if (affected == 0) {
            log.warn("解约通知结果回写未命中本轮，已被新一轮解约申请取代，丢弃本次回写, requestSignSeq={}, "
                            + "expectedTerminationStatus={}, success={}",
                    request.getRequestSignSeq(), expectedTerminationStatus, success);
        }
    }

    private void updateNotifyStatus(AppTerminationRequest request, String expectedTerminationStatus,
                                    NotifyDelivery delivery) {
        updateNotifyStatus(request, expectedTerminationStatus, delivery.delivered(), delivery.message());
    }

}
