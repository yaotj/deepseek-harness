package com.chinasofti.huateng.fep.alipay.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.model.app.AddBlackListReqDTO;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.alipaytrip.TerminationExecuteResult;
import com.chinasofti.huateng.fep.alipay.service.AlipayPaymentService;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.blacklist.BlacklistClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 支付宝出行支付/黑名单/解约执行服务实现。
 */
@Service
public class AlipayPaymentServiceImpl implements AlipayPaymentService {
    private static final Logger log = LoggerFactory.getLogger(AlipayPaymentServiceImpl.class);

    private final AlipayPaySignClient alipayPaySignClient;
    private final BlacklistClient blacklistClient;

    @Autowired
    public AlipayPaymentServiceImpl(AlipayPaySignClient alipayPaySignClient, BlacklistClient blacklistClient) {
        this.alipayPaySignClient = alipayPaySignClient;
        this.blacklistClient = blacklistClient;
    }

    @Override
    public AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request) {
        log.info("支付宝出行-退款申请,请求参数：{}", JSON.toJSONString(request));
        AlipayTripRequestRefundRespDTO response = new AlipayTripRequestRefundRespDTO();

        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("无效的参数：orderNo不能为空");
            log.warn("支付宝出行-退款申请,参数校验失败");
            return response;
        }

        try {
            AlipayTripRequestRefundRespDTO rpcResponse = alipayPaySignClient.alipayTripRequestRefund(request);
            if (rpcResponse == null) {
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("系统内部错误：支付签约服务返回空响应");
                log.error("支付宝出行-退款申请,alipay-pay-sign-server 返回空响应");
                return response;
            }

            response.setRetCode(rpcResponse.getRetCode());
            response.setRetMsg(rpcResponse.getRetMsg());
            log.info("支付宝出行-退款申请,alipay-pay-sign-server 响应：{}", JSON.toJSONString(rpcResponse));
        } catch (Exception e) {
            log.error("支付宝出行-退款申请 异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误：" + e.getMessage());
        }

        log.info("支付宝出行-退款申请,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public BlackListOperateResult addBlackListForAlipay(AddBlackListReqDTO request) {
        log.info("支付宝出行-添加黑名单,请求参数：{}", JSON.toJSONString(request));
        BlackListOperateResult response = new BlackListOperateResult();

        if (request == null || !StringUtils.hasText(request.getCardId())) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("无效的参数：cardId不能为空");
            log.warn("支付宝出行-添加黑名单,参数校验失败");
            return response;
        }

        try {
            BlackListOperateResult rpcResponse = blacklistClient.addBlackList(request);
            if (rpcResponse == null) {
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("系统内部错误：黑名单服务返回空响应");
                log.error("支付宝出行-添加黑名单,blacklist-server 返回空响应");
                return response;
            }

            if (!FepAppErrorCodeEnum.SUCCESS.getCode().equals(rpcResponse.getRetCode())) {
                response.setRetCode(rpcResponse.getRetCode());
                response.setRetMsg(rpcResponse.getRetMsg());
                log.warn("支付宝出行-添加黑名单,blacklist-server 返回失败, retCode={}, retMsg={}",
                        rpcResponse.getRetCode(), rpcResponse.getRetMsg());
                return response;
            }

            response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("成功");
            log.info("支付宝出行-添加黑名单,blacklist-server 响应：{}", JSON.toJSONString(rpcResponse));
        } catch (Exception e) {
            log.error("支付宝出行-添加黑名单 异常, request={}", JSON.toJSONString(request), e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误：" + e.getMessage());
        }

        log.info("支付宝出行-添加黑名单,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public TerminationExecuteResult executeTermination(String agreementCode) {
        log.info("支付宝出行-执行解约, agreementCode={}", agreementCode);
        TerminationExecuteResult result = new TerminationExecuteResult();
        result.setAgreementCode(agreementCode);

        if (!StringUtils.hasText(agreementCode)) {
            result.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("无效的参数：agreementCode不能为空");
            result.setStatus("FAIL");
            log.warn("支付宝出行-执行解约,参数校验失败");
            return result;
        }

        try {
            com.chinasofti.huateng.common.response.AlipayCommonResponse rpcResponse = alipayPaySignClient.executeTermination(agreementCode);
            if (rpcResponse == null) {
                result.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                result.setRetMsg("系统内部错误：支付签约服务返回空响应");
                result.setStatus("FAIL");
                log.error("支付宝出行-执行解约,alipay-pay-sign-server 返回空响应");
                return result;
            }

            result.setRetCode(rpcResponse.getRetCode());
            result.setRetMsg(rpcResponse.getRetMsg());
            result.setStatus("0000".equals(rpcResponse.getRetCode()) ? "COMPLETED" : "FAIL");
            log.info("支付宝出行-执行解约,alipay-pay-sign-server 响应：{}", JSON.toJSONString(rpcResponse));
        } catch (Exception e) {
            log.error("支付宝出行-执行解约 异常, agreementCode={}", agreementCode, e);
            result.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            result.setRetMsg("系统内部错误：" + e.getMessage());
            result.setStatus("FAIL");
        }

        log.info("支付宝出行-执行解约,响应结果：{}", JSON.toJSONString(result));
        return result;
    }
}
