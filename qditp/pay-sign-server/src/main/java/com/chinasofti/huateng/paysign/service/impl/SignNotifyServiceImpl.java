package com.chinasofti.huateng.paysign.service.impl;

import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;
import static com.chinasofti.huateng.paysign.support.PaySignValues.stringValue;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.app.AppSignResultNotifyReqDTO;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.domain.SignStatus;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.model.paysign.ResendSignNotifyRespDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PaySignRequest;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PaySignRequestMapper;
import com.chinasofti.huateng.paysign.port.AppNotifyPort;
import com.chinasofti.huateng.paysign.port.NotifyDelivery;
import com.chinasofti.huateng.paysign.service.SignNotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * {@link SignNotifyService} 的唯一实现，**只碰签约聚合的两张表**。
 *
 * <p>2026-09-17（ADR-D127）由 {@code AppNotifyServiceImpl} 按聚合拆出。原类 381 行、注了 3 个 mapper、
 * 同时承担签约与解约两条通知链，解约那半已迁到 {@code TerminationNotifyServiceImpl}。
 * 两半没有共享的私有方法：{@code submitNotifyTask} 与 {@code updateNotifyStatus} 本来就按实体类型重载分家，
 * {@code parseRequestBody} 只有签约侧用。**NEVER 合回一个类。**
 */
@Service("paySignSignNotifyServiceImpl")
public class SignNotifyServiceImpl implements SignNotifyService {
    private static final Logger log = LoggerFactory.getLogger(SignNotifyServiceImpl.class);

    /**
     * {@code APP_PAY_SIGN_INFO.SIGN_STATUS} 的已签约取值，**从 {@link SignStatus} 取**。
     *
     * <p>2026-09-17（ADR-D127）由裸字面量 {@code "SIGNED"} 改为枚举派生。此前的注释写着
     * 「与 {@code PaySignWorkflow.STATUS_SIGNED} 同值」，而那个类已于 ADR-D87 删除，
     * 等于把一致性挂在一个不存在的锚点上。**NEVER 退回裸字面量或引用已删除的类。**
     */
    private static final String SIGN_STATUS_SIGNED = SignStatus.SIGNED.name();

    /** PENDING 滞留多久（分钟）视为「状态回写丢了」，纳入补偿。 */
    private static final int PENDING_STALE_MINUTES = 10;

    /** 单次补偿取多少条，与 TerminationCompensationService 保持一致。 */
    private static final int BATCH_SIZE = 200;

    private final PaySignRequestMapper paySignRequestMapper;

    private final PaySignInfoMapper paySignInfoMapper;

    private final AppNotifyPort appNotifyPort;

    private final Executor notifyExecutor;

    /** 通知重试上限，超过后该记录不再被补偿扫到，只能人工介入。 */
    @Value("${app.notify.max-retry-count:10}")
    private int maxNotifyRetryCount;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public SignNotifyServiceImpl(
            PaySignRequestMapper paySignRequestMapper,
            PaySignInfoMapper paySignInfoMapper,
            AppNotifyPort appNotifyPort,
            @Qualifier("notifyExecutor") Executor notifyExecutor) {
        this.paySignRequestMapper = paySignRequestMapper;
        this.paySignInfoMapper = paySignInfoMapper;
        this.appNotifyPort = appNotifyPort;
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

    /** 组装并投递签约结果通知，返回本次投递结果。 */
    private NotifyDelivery doNotifySignResult(PaySignRequest request, PaySignInfo signInfo, ReceiveSignResultReqDTO receiveRequest) {
        try {
            AppSignResultNotifyReqDTO bizData = new AppSignResultNotifyReqDTO();
            bizData.setThirdUserId(signInfo != null && StringUtils.hasText(signInfo.getThirdUserId())
                    ? signInfo.getThirdUserId() : request.getThirdUserId());
            bizData.setRequestSignSeq(request.getRequestSignSeq());
            bizData.setPaymentVendor(stringValue(receiveRequest != null ? receiveRequest.getPaymentVendor() : null, signInfo != null ? signInfo.getPaymentVendor() : request.getPaymentVendor()));
            bizData.setPayAccountId(stringValue(receiveRequest != null ? receiveRequest.getPayUserId() : null, request.getPayAccountId()));
            bizData.setPayAgreementNo(stringValue(receiveRequest != null ? receiveRequest.getPayAgreementNo() : null, request.getPayAgreementNo()));
            bizData.setSignResult(receiveRequest != null ? receiveRequest.getStatus() : request.getSignStatus());
            bizData.setRealNameAuthResult(receiveRequest != null ? receiveRequest.getStatus() : request.getSignStatus());

            NotifyDelivery delivery = appNotifyPort.pushSignResult(bizData);
            updateNotifyStatus(request, delivery);
            return delivery;
        } catch (Exception e) {
            log.error("通知App签约结果异常, requestSignSeq={}", request.getRequestSignSeq(), e);
            updateNotifyStatus(request, false, "异常:" + e.getMessage());
            return NotifyDelivery.failed("异常:" + e.getMessage());
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
            NotifyDelivery delivery = doNotifySignResult(request, signInfo, receiveRequest);
            response.setNotified(delivery.delivered());
            response.setNotifyResult(delivery.message());
            log.info("单条重发签约结果通知完成, requestSignSeq={}, notified={}, result={}",
                    requestSignSeq, delivery.delivered(), delivery.message());
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

    private void updateNotifyStatus(PaySignRequest request, boolean success, String result) {
        PaySignRequest update = new PaySignRequest();
        update.setId(request.getId());
        update.setNotifyStatus(success ? "SUCCESS" : "FAILED");
        update.setNotifyTime(LocalDateTime.now());
        update.setNotifyResult(result);
        paySignRequestMapper.updateNotifyStatus(update);
    }

    /** 统一落库本次投递的成功状态或失败原因。 */
    private void updateNotifyStatus(PaySignRequest request, NotifyDelivery delivery) {
        updateNotifyStatus(request, delivery.delivered(), delivery.message());
    }

}
