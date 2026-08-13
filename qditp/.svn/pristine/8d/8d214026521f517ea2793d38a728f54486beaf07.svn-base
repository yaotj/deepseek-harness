package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.alipay.paysign.entity.AlipaySignLog;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignLogMapper;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class SignLogRecorder {
    private static final Logger log = LoggerFactory.getLogger(SignLogRecorder.class);
    private static final String CHANNEL_ALIPAY = "ALIPAY";

    @Autowired
    private AlipaySignLogMapper alipaySignLogMapper;

    public void recordSignSuccess(AlipaySignInfo signInfo, Object request, Object response, String agreementCode, String thirdUserId) {
        try {
            AlipaySignLog signLog = new AlipaySignLog();
            signLog.setRequestSeq(agreementCode);
            signLog.setThirdUserId(thirdUserId);
            signLog.setCardId(signInfo.getCardId());
            signLog.setCardType(signInfo.getCardType());
            signLog.setChannel(CHANNEL_ALIPAY);
            signLog.setAgreementCode(agreementCode);
            signLog.setOperationType("SIGN");
            signLog.setRequestBody(JSON.toJSONString(request));
            signLog.setResponseBody(JSON.toJSONString(response));
            if (response instanceof AlipayCommonResponse) {
                signLog.setResultCode(((AlipayCommonResponse) response).getRetCode());
                signLog.setResultMsg(((AlipayCommonResponse) response).getRetMsg());
            } else if (response instanceof AlipayTripAddContractRespDTO) {
                signLog.setResultCode(((AlipayTripAddContractRespDTO) response).getRetCode());
                signLog.setResultMsg(((AlipayTripAddContractRespDTO) response).getRetMsg());
            }
            signLog.setDeleteFlag("0");
            signLog.setVersion("1");
            signLog.setCreateTime(LocalDateTime.now());
            signLog.setUpdateTime(LocalDateTime.now());
            alipaySignLogMapper.insert(signLog);
            log.info("签约日志记录成功, thirdUserId={}, agreementCode={}", thirdUserId, agreementCode);
        } catch (Exception e) {
            log.error("签约日志记录异常, thirdUserId={}, agreementCode={}", thirdUserId, agreementCode, e);
        }
    }
}
