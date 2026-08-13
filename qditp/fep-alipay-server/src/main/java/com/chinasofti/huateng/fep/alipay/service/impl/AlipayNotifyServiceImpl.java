package com.chinasofti.huateng.fep.alipay.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultRespDTO;
import com.chinasofti.huateng.fep.alipay.service.AlipayNotifyService;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 支付宝出行通知服务实现。
 */
@Service
public class AlipayNotifyServiceImpl implements AlipayNotifyService {
    private static final Logger log = LoggerFactory.getLogger(AlipayNotifyServiceImpl.class);

    private final AlipayPaySignClient alipayPaySignClient;

    @Autowired
    public AlipayNotifyServiceImpl(AlipayPaySignClient alipayPaySignClient) {
        this.alipayPaySignClient = alipayPaySignClient;
    }

    @Override
    public AlipayTripPayNotifyRespDTO handlePaymentNotify(AlipayTripPayNotifyReqDTO request) {
        log.info("支付中心-支付结果回调,请求参数：{}", JSON.toJSONString(request));
        AlipayTripPayNotifyRespDTO response = new AlipayTripPayNotifyRespDTO();
        try {
            if (request == null || !StringUtils.hasText(request.getOrderNo())) {
                response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("参数异常：orderNo不能为空");
                return response;
            }

            com.chinasofti.huateng.common.response.AlipayCommonResponse paySignResponse = alipayPaySignClient.handlePayNotify(request);
            log.info("alipay-pay-sign-server支付结果回调响应, response={}", JSON.toJSONString(paySignResponse));

            if (paySignResponse != null && "0000".equals(paySignResponse.getRetCode())) {
                response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg(FepAppErrorCodeEnum.SUCCESS.getMsg());
            } else {
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg(paySignResponse != null ? paySignResponse.getRetMsg() : "系统内部错误");
            }
        } catch (Exception e) {
            log.error("支付中心-支付结果回调 异常", e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误");
        }
        log.info("支付中心-支付结果回调,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public AlipayTripCloseResultRespDTO closeResult(AlipayTripCloseResultReqDTO request) {
        log.info("支付宝出行-业务关闭结果通知,请求参数：{}", JSON.toJSONString(request));
        AlipayTripCloseResultRespDTO response = new AlipayTripCloseResultRespDTO();
        if (request == null || !StringUtils.hasText(request.getAgreementNo())) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("无效的参数");
            log.warn("支付宝出行-业务关闭结果通知,参数校验失败");
            return response;
        }
        try {
            com.chinasofti.huateng.common.response.AlipayCommonResponse notifyResponse = alipayPaySignClient.notifyCloseResult(
                    request.getAgreementNo(),
                    request.getResult() != null && request.getResult()
            );
            if (notifyResponse != null && "0000".equals(notifyResponse.getRetCode())) {
                response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg("成功");
            } else {
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg(notifyResponse != null ? notifyResponse.getRetMsg() : "系统内部错误");
            }
        } catch (Exception e) {
            log.error("支付宝出行-业务关闭结果通知 异常", e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-业务关闭结果通知,响应结果：{}", JSON.toJSONString(response));
        return response;
    }
}
