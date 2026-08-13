package com.chinasofti.huateng.fep.alipay.service.impl;

import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.fep.alipay.service.AlipayContractService;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 支付宝出行合约服务实现。
 */
@Service
public class AlipayContractServiceImpl implements AlipayContractService {
    private static final Logger log = LoggerFactory.getLogger(AlipayContractServiceImpl.class);

    private final AlipayPaySignClient alipayPaySignClient;

    @Autowired
    public AlipayContractServiceImpl(AlipayPaySignClient alipayPaySignClient) {
        this.alipayPaySignClient = alipayPaySignClient;
    }

    @Override
    public AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request) {
        log.info("支付宝出行-添加签约信息,请求参数：{}", JSON.toJSONString(request));
        AlipayTripAddContractRespDTO response = new AlipayTripAddContractRespDTO();
        if (request == null || !StringUtils.hasText(request.getThirdUserId()) || !StringUtils.hasText(request.getAgreementCode())) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("无效的参数");
            log.warn("支付宝出行-添加签约信息,参数校验失败");
            return response;
        }
        try {
            response = alipayPaySignClient.alipayTripAddContract(request);
            if (response == null) {
                response = new AlipayTripAddContractRespDTO();
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("系统内部错误");
                log.error("支付宝出行-添加签约信息,alipay-pay-sign-server 返回空响应");
                return response;
            }
        } catch (Exception e) {
            log.error("支付宝出行-添加签约信息 异常", e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-添加签约信息,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request) {
        log.info("支付宝出行-解约登记,请求参数：{}", JSON.toJSONString(request));
        AlipayTripTerminateContractRespDTO response = new AlipayTripTerminateContractRespDTO();
        if (request == null || !StringUtils.hasText(request.getAgreementCode())) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("无效的参数");
            log.warn("支付宝出行-解约登记,参数校验失败");
            return response;
        }
        try {
            response = alipayPaySignClient.alipayTripTerminateContract(request);
            if (response == null) {
                response = new AlipayTripTerminateContractRespDTO();
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("系统内部错误");
                log.error("支付宝出行-解约登记,alipay-pay-sign-server 返回空响应");
                return response;
            }
        } catch (Exception e) {
            log.error("支付宝出行-解约登记 异常", e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-解约登记,响应结果：{}", JSON.toJSONString(response));
        return response;
    }
}
