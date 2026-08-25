package com.chinasofti.huateng.paysign.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.AppTerminationRequest;
import com.chinasofti.huateng.paysign.exception.TerminationException;
import com.chinasofti.huateng.paysign.mapper.AppTerminationRequestMapper;
import com.chinasofti.huateng.paysign.model.request.CheckFailedOrdersReqDTO;
import com.chinasofti.huateng.paysign.model.request.ExecuteTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.request.NotifyTerminationFailedReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.CheckFailedOrdersRespDTO;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
import com.chinasofti.huateng.paysign.service.AppNotifyService;
import com.chinasofti.huateng.paysign.service.TerminationInternalService;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class TerminationInternalServiceImpl implements TerminationInternalService {

    private static final Logger log = LoggerFactory.getLogger(TerminationInternalServiceImpl.class);

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_SCANNING = "SCANNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    @Autowired
    private AppTerminationRequestMapper terminationRequestMapper;

    @Autowired
    private PaySignWorkflow paySignWorkflow;

    @Autowired
    private GateTxnPayClient gateTxnPayClient;

    @Autowired
    private AppNotifyService appNotifyService;

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

            GateTxnPayFailedOrderReqDTO hasFailedOrderReq = new GateTxnPayFailedOrderReqDTO();
            hasFailedOrderReq.setThirdUserId(request.getThirdUserId());
            hasFailedOrderReq.setPaymentVendor(request.getPaymentVendor());
            hasFailedOrderReq.setRequestTime(request.getRequestTime());
            GateTxnPayFailedOrderRespDTO result = gateTxnPayClient.hasFailedOrder(hasFailedOrderReq);

            if (result == null) {
                fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, "查询扣费订单失败");
                return response;
            }

            response.setHasFailedOrder(result.isHasFailedOrder());
            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("查询扣费失败订单异常", e);
            fillError(response, PaySignErrorCodeEnum.SYSTEM_ERROR, PaySignErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseRespDTO executeTermination(ExecuteTerminationReqDTO request) {
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

            String status = terminationRequest.getTerminationStatus();
            if (STATUS_SCANNING.equals(status) || STATUS_SUCCESS.equals(status) || STATUS_FAILED.equals(status)) {
                fillSuccess(response);
                return response;
            }

            if (!STATUS_PENDING.equals(status)) {
                fillError(response, PaySignErrorCodeEnum.TERMINATION_REQUEST_NOT_FOUND, "解约申请状态不正确");
                return response;
            }

            terminationRequestMapper.updateStatus(request.getRequestSignSeq(), STATUS_SCANNING);
            terminationRequestMapper.updateScanTime(request.getRequestSignSeq(), LocalDateTime.now());

            PaySignGatewayResponse gatewayResponse = paySignWorkflow
                    .requestPayPlatformTermination(request.getRequestSignSeq());

            paySignWorkflow.writeLog("EXECUTE_TERMINATION", request.getThirdUserId(), request.getRequestSignSeq(),
                    request.getPaymentVendor(), null, request, gatewayResponse);

            if (!paySignWorkflow.isGatewaySuccess(gatewayResponse)) {
                throw new TerminationException("调用支付平台解约失败，requestSignSeq=" + request.getRequestSignSeq()
                        + ", gatewayResponse=" + JSON.toJSONString(gatewayResponse));
            }

            fillSuccess(response);
            return response;
        } catch (TerminationException e) {
            log.error("执行支付平台解约异常", e);
            throw e;
        } catch (Exception e) {
            log.error("执行支付平台解约异常", e);
            throw new TerminationException("执行支付平台解约异常，requestSignSeq="
                    + (request != null ? request.getRequestSignSeq() : null), e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
            terminationRequestMapper.updateFailReason(request.getRequestSignSeq(), STATUS_FAILED, failReason);
            terminationRequestMapper.updateCompleteTime(request.getRequestSignSeq(), LocalDateTime.now());
            terminationRequestMapper.updateNotifyStatus(request.getRequestSignSeq(), "PENDING", null, null);

            appNotifyService.asyncNotifyTerminationFailed(terminationRequest, request);

            fillSuccess(response);
            return response;
        } catch (Exception e) {
            log.error("通知APP解约失败异常", e);
            throw new TerminationException("通知APP解约失败异常，requestSignSeq="
                    + (request != null ? request.getRequestSignSeq() : null), e);
        }
    }

    private void fillError(CheckFailedOrdersRespDTO response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setResultCode(errorCode.getCode());
        response.setResultMsg(msg);
    }

    private void fillSuccess(CheckFailedOrdersRespDTO response) {
        response.setResultCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }

    private void fillError(BaseRespDTO response, PaySignErrorCodeEnum errorCode, String msg) {
        response.setCode(-1);
        response.setMsg(msg);
        response.setSuccess(Boolean.FALSE);
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
    }

    private void fillSuccess(BaseRespDTO response) {
        response.setCode(0);
        response.setMsg("成功");
        response.setSuccess(Boolean.TRUE);
        response.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
    }
}
