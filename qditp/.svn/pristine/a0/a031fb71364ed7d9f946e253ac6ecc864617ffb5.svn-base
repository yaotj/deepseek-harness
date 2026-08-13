package com.chinasofti.huateng.fep.app.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.app.service.PaySignAppService;
import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationResult;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaySignAppServiceImpl implements PaySignAppService {
    private static final Logger log = LoggerFactory.getLogger(PaySignAppServiceImpl.class);

    private final PaySignClient paySignClient;

    public PaySignAppServiceImpl(PaySignClient paySignClient) {
        this.paySignClient = paySignClient;
    }

    @Override
    public RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request) {
        log.info("call pay-sign requestSignInfo request={}", JSON.toJSONString(request));
        RequestSignInfoResult result = paySignClient.requestSignInfo(request);
        log.info("call pay-sign requestSignInfo response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public RequestTerminationResult requestTermination(RequestTerminationReqDTO request) {
        log.info("call pay-sign requestTermination request={}", JSON.toJSONString(request));
        RequestTerminationResult result = paySignClient.requestTermination(request);
        log.info("call pay-sign requestTermination response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public RequestContractResultResult requestContractResult(RequestContractResultReqDTO request) {
        log.info("call pay-sign requestContractResult request={}", JSON.toJSONString(request));
        RequestContractResultResult result = paySignClient.requestContractResult(request);
        log.info("call pay-sign requestContractResult response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request) {
        log.info("call pay-sign receiveSignResult request={}", JSON.toJSONString(request));
        PaySignCallbackResult result = paySignClient.receiveSignResult(request);
        log.info("call pay-sign receiveSignResult response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request) {
        log.info("call pay-sign receivePayResult request={}", JSON.toJSONString(request));
        PaySignCallbackResult result = paySignClient.receivePayResult(request);
        log.info("call pay-sign receivePayResult response={}", JSON.toJSONString(result));
        return result;
    }

    @Override
    public PaySignCallbackResult receiveTerminationResult(ReceiveTerminationResultReqDTO request) {
        log.info("call pay-sign receiveTerminationResult request={}", JSON.toJSONString(request));
        PaySignCallbackResult result = paySignClient.receiveTerminationResult(request);
        log.info("call pay-sign receiveTerminationResult response={}", JSON.toJSONString(result));
        return result;
    }
}
