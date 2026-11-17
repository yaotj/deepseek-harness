package com.chinasofti.huateng.paysign.service.impl;

import com.chinasofti.huateng.model.domain.TerminationStatus;
import com.chinasofti.huateng.model.app.UnbindAgreementReqDTO;
import com.chinasofti.huateng.model.app.UnbindAgreementResult;
import com.chinasofti.huateng.model.paysign.ProcessTerminationReqDTO;
import com.chinasofti.huateng.model.paysign.ProcessTerminationRespDTO;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillError;
import static com.chinasofti.huateng.paysign.support.PaySignResponses.fillSuccess;

import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.domain.TerminationStatusTransition;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.exception.TerminationException;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.model.request.CheckFailedOrdersReqDTO;
import com.chinasofti.huateng.paysign.model.request.ExecuteTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.CheckFailedOrdersRespDTO;
import com.chinasofti.huateng.model.paysign.CompensateNotifyRespDTO;
import com.chinasofti.huateng.paysign.service.TerminationNotifyService;
import com.chinasofti.huateng.paysign.service.TerminationInternalService;
import com.chinasofti.huateng.paysign.port.UnsettledOrderAnswer;
import com.chinasofti.huateng.paysign.port.UnsettledOrderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class TerminationInternalServiceImpl implements TerminationInternalService {

    private static final Logger log = LoggerFactory.getLogger(TerminationInternalServiceImpl.class);

    private static final String STATUS_PENDING = TerminationStatus.PENDING.name();
    private static final String STATUS_FAILED = TerminationStatus.FAILED.name();

    private final TerminationCompensationService terminationCompensationService;

    private final TerminationExecutor terminationExecutor;

    private final AppTerminationRequestMapper terminationRequestMapper;

    private final UnsettledOrderPort unsettledOrderPort;

    private final TerminationNotifyService terminationNotifyService;

    public TerminationInternalServiceImpl(
            TerminationCompensationService terminationCompensationService,
            TerminationExecutor terminationExecutor,
            AppTerminationRequestMapper terminationRequestMapper,
            UnsettledOrderPort unsettledOrderPort,
            TerminationNotifyService terminationNotifyService) {
        this.terminationCompensationService = terminationCompensationService;
        this.terminationExecutor = terminationExecutor;
        this.terminationRequestMapper = terminationRequestMapper;
        this.unsettledOrderPort = unsettledOrderPort;
        this.terminationNotifyService = terminationNotifyService;
    }

    @Override
    public ProcessTerminationRespDTO processTermination(ProcessTerminationReqDTO request) {
        return terminationCompensationService.processTermination(request);
    }

    @Override
    public CompensateNotifyRespDTO compensateTerminationNotify() {
        return terminationCompensationService.compensateTerminationNotify();
    }

    @Override
    public CompensateNotifyRespDTO compensateChannelSync() {
        return terminationCompensationService.compensateChannelSync();
    }

    @Override
    public CheckFailedOrdersRespDTO checkFailedOrders(CheckFailedOrdersReqDTO request) {
        CheckFailedOrdersRespDTO response = new CheckFailedOrdersRespDTO();
        try {
            if (request == null || !StringUtils.hasText(request.getThirdUserId())
                    || !StringUtils.hasText(request.getPaymentVendor())
                    || request.getRequestTime() == null) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                return response;
            }

            UnsettledOrderAnswer answer = unsettledOrderPort.hasUnsettledOrder(
                    request.getThirdUserId(), request.getPaymentVendor(), request.getRequestTime());
            switch (answer) {
                case UnsettledOrderAnswer.Answered answered -> {
                    response.setHasFailedOrder(answered.hasUnsettledOrder());
                    fillSuccess(response);
                }
                case UnsettledOrderAnswer.Rejected rejected -> fillError(response,
                        PaySignErrorCodeEnum.SYSTEM_ERROR, "查询扣费订单失败");
                case UnsettledOrderAnswer.Unknown unknown -> fillError(response,
                        PaySignErrorCodeEnum.SYSTEM_ERROR, "查询扣费订单失败");
            }
            return response;
        } catch (Exception e) {
            log.error("查询扣费失败订单异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    public BaseRespDTO executeTermination(ExecuteTerminationReqDTO request) {
        return terminationExecutor.executeTermination(request);
    }

    @Override
    public UnbindAgreementResult unbindAgreement(UnbindAgreementReqDTO request) {
        return terminationExecutor.unbindAgreement(request);
    }

    @Override
    public BaseRespDTO notifyTerminationFailed(NotifyTerminationFailedReqDTO request) {
        BaseRespDTO response = new BaseRespDTO();
        try {
            if (request == null || !StringUtils.hasText(request.getRequestSignSeq())) {
                fillError(response, PaySignErrorCodeEnum.INVALID_PARAM, "请求报文不能为空");
                return response;
            }

            AppTerminationRequest terminationRequest = terminationRequestMapper
                    .selectByRequestSignSeq(request.getRequestSignSeq());
            if (terminationRequest == null) {
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请不存在");
                return response;
            }

            if (STATUS_FAILED.equals(terminationRequest.getTerminationStatus())) {
                fillSuccess(response);
                return response;
            }

            if (!STATUS_PENDING.equals(terminationRequest.getTerminationStatus())) {
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请状态不正确");
                return response;
            }

            String failReason = StringUtils.hasText(request.getFailReason())
                    ? request.getFailReason() : "存在扣费失败订单";

            TerminationStatusTransition.Result transit = TerminationStatusTransition.classify(
                    terminationRequestMapper.rejectPending(request.getRequestSignSeq(), failReason, LocalDateTime.now()),
                    TerminationStatus.FAILED,
                    () -> terminationRequestMapper.selectTerminationStatusBySeq(request.getRequestSignSeq()));

            if (!transit.isDone()) {
                log.warn("拒绝解约未命中 PENDING，已跳过失败通知, requestSignSeq={}, outcome={}, observedStatus={}",
                        request.getRequestSignSeq(), transit.outcome(), transit.observedStatus());
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请状态已变更，本次未处理");
                return response;
            }

            terminationNotifyService.asyncNotifyTerminationFailed(terminationRequest, request);

            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("通知APP解约失败异常", e);
            throw new TerminationException("通知APP解约失败异常，requestSignSeq="
                    + (request != null ? request.getRequestSignSeq() : null), e);
        }
    }

}
