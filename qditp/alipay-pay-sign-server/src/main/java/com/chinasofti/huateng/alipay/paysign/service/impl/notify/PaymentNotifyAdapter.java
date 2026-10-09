package com.chinasofti.huateng.alipay.paysign.service.impl.notify;

import com.chinasofti.huateng.model.alipaytrip.AlipayBlackListNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterNotifyPort;
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

/**
 * 支付中心**通知方向**的出向门面：黑名单状态变更、业务关闭（销卡）结果。
 *
 * <p>本类是这两条通知的**唯一**发起处，三个调用方（支付申请加黑后通知、解约执行器、
 * {@code controller/legacy/AlipayNotifyController} 的两条入向 handler）都经它出网。
 * 最后那个调用方 2026-09-21 起**直连本类**，此前是经 {@code AlipayTripPaymentService} 那个
 * 跨聚合门面转一跳（拆门面第 1 步），<b>NEVER 回退成经门面</b>。
 *
 * <p><b>两条的成功判据刻意不同、也刻意留在本类内</b>（ADR-D131）：黑名单变更认
 * {@code retCode=0000} / {@code success=true} / {@code code=200} 三者任一（支付中心对通知类接口
 * 与支付类接口的应答形态不一样，2026-09-11 曾因只认 {@code success} 打出假告警）；
 * 销卡结果只认 {@code code=200}。**NEVER 把这两套判据合并、也 NEVER 挪进
 * {@link PayCenterNotifyPort}** —— 端口只负责传输。
 */
@Service
public class PaymentNotifyAdapter {
    private static final Logger log = LoggerFactory.getLogger(PaymentNotifyAdapter.class);

    @Autowired
    private PayCenterNotifyPort payCenterNotifyPort;

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
            PayCenterResponse payCenterResponse = payCenterNotifyPort.blacklistNotify(bizDataMap);
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

    /** 判定支付中心通知类接口是否成功。 */
    private boolean isPayCenterNotifySuccess(PayCenterResponse payCenterResponse) {
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
    private String payCenterFailMsg(PayCenterResponse payCenterResponse) {
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

            PayCenterResponse payCenterResponse = payCenterNotifyPort.closeResultNotify(bizDataMap);
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
