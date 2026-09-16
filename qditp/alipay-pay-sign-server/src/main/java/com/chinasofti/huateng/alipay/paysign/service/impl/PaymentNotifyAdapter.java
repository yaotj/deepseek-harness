package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.model.alipaytrip.AlipayBlackListNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.alipay.paysign.util.PayCenterClient;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PaymentNotifyAdapter {
    private static final Logger log = LoggerFactory.getLogger(PaymentNotifyAdapter.class);

    @Autowired
    private PayCenterClient payCenterClient;

    @Autowired
    private PayCenterProperties payCenterProperties;

    public AlipayCommonResponse notifyBlackListChange(AlipayBlackListNotifyReqDTO request) {
        AlipayCommonResponse response = new AlipayCommonResponse();
        try {
            if (request == null || !org.springframework.util.StringUtils.hasText(request.getCardId())) {
                response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("卡号不能为空");
                return response;
            }
            log.info("接收到支付宝出行-黑名单状态变更通知: cardId={}, blackListType={}, reason={}",
                    request.getCardId(), request.getBlackListType(), request.getReason());

            Map<String, Object> bizDataMap = new LinkedHashMap<>();
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("thirdUserId", request.getThirdUserId());
            record.put("cardId", request.getCardId());
            record.put("cardType", request.getCardType());
            record.put("blackListType", request.getBlackListType());
            record.put("optionDate", request.getOptionDate());
            if ("1".equals(request.getBlackListType()) && org.springframework.util.StringUtils.hasText(request.getExpireTime())) {
                record.put("expireTime", request.getExpireTime());
            }
            bizDataMap.put("blackList", Collections.singletonList(record));

            log.info("支付宝出行-黑名单状态变更通知,调用支付中心通知接口,请求参数: {}", JSON.toJSONString(bizDataMap));
            com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse payCenterResponse = payCenterClient.blacklistNotify(bizDataMap);
            log.info("支付宝出行-黑名单状态变更通知,支付中心响应结果: retCode={}, retMsg={}, success={}",
                    payCenterResponse != null ? payCenterResponse.getRetCode() : "null",
                    payCenterResponse != null ? payCenterResponse.getRetMsg() : "null",
                    payCenterResponse != null ? payCenterResponse.getSuccess() : "null");

            if (payCenterResponse != null && isPayCenterNotifySuccess(payCenterResponse)) {
                response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg("黑名单变更通知成功");
            } else if (payCenterResponse != null) {
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg(payCenterFailMsg(payCenterResponse));
            } else {
                response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
                response.setRetMsg("调用支付中心黑名单通知接口失败");
            }
            log.info("支付宝出行-黑名单状态变更通知完成, cardId={}, retCode={}", request.getCardId(), response.getRetCode());
            return response;
        } catch (Exception e) {
            log.error("支付宝出行-黑名单状态变更通知异常, cardId={}", request != null ? request.getCardId() : null, e);
            response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg("系统内部错误");
            return response;
        }
    }

    /**
     * 判定支付中心通知类接口是否成功。
     *
     * <p>通知类接口（receiveBlackListFromItp）实测返回 {@code retCode=0000}，不带 success；
     * 支付类接口返回 {@code success=true} / {@code code=200}。两种形态都要认，
     * 否则成功应答会被判成失败并触发无意义的重试与告警。
     */
    private boolean isPayCenterNotifySuccess(com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse payCenterResponse) {
        if (Boolean.TRUE.equals(payCenterResponse.getSuccess())) {
            return true;
        }
        if (FepAppErrorCodeEnum.SUCCESS.getCode().equals(payCenterResponse.getRetCode())) {
            return true;
        }
        return Integer.valueOf(200).equals(payCenterResponse.getCode());
    }

    /**
     * 组装支付中心失败原因，优先取通知类接口的 retMsg。
     */
    private String payCenterFailMsg(com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse payCenterResponse) {
        if (org.springframework.util.StringUtils.hasText(payCenterResponse.getRetMsg())) {
            return payCenterResponse.getRetMsg();
        }
        if (org.springframework.util.StringUtils.hasText(payCenterResponse.getMsg())) {
            return payCenterResponse.getMsg();
        }
        return "黑名单变更通知失败";
    }

    public AlipayCommonResponse notifyCloseResult(String agreementCode, boolean result) {
        AlipayCommonResponse response = new AlipayCommonResponse();
        try {
            log.info("支付宝出行-业务关闭结果通知, agreementCode={}, result={}", agreementCode, result);

            if (!org.springframework.util.StringUtils.hasText(agreementCode)) {
                response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("agreementCode不能为空");
                return response;
            }

            Map<String, Object> bizDataMap = new LinkedHashMap<>();
            bizDataMap.put("agreementNo", agreementCode);
            bizDataMap.put("result", result);

            com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse payCenterResponse = payCenterClient.closeResultNotify(bizDataMap);
            log.info("支付宝出行-业务关闭结果通知,支付中心响应结果: code={}, msg={}",
                    payCenterResponse != null ? payCenterResponse.getCode() : "null",
                    payCenterResponse != null ? payCenterResponse.getMsg() : "null");
            if (payCenterResponse != null && Integer.valueOf(200).equals(payCenterResponse.getCode())) {
                response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg("成功");
            } else if (payCenterResponse != null) {
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("通知推送失败, 支付中心code=" + payCenterResponse.getCode() + ", msg=" + payCenterResponse.getMsg());
            } else {
                response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
                response.setRetMsg("调用支付中心通知接口失败");
            }
        } catch (Exception e) {
            log.error("支付宝出行-业务关闭结果通知 异常, agreementCode={}, result={}", agreementCode, result, e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误");
        }
        return response;
    }
}
