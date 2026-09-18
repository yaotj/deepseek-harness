package com.chinasofti.huateng.alipay.paysign.service.impl.sign;

import com.chinasofti.huateng.alipay.paysign.entity.AlipaySignLog;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignLogMapper;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 新签约服务的签约流水写入器（{@code ALIPAY_SIGN_LOG}）。
 *
 * <p><b>为什么不叫 {@code SignLogRecorder}</b>：旧实现 {@code impl/contract/SignLogRecorder} 在迁移期
 * 仍在跑，两个 {@code @Component} 的**默认 bean 名会撞**（都是 {@code signLogRecorder}），
 * 启动即 {@code ConflictingBeanDefinitionException}。新旧并存期间**同名类 NEVER 放两个包**，
 * 要么改类名、要么显式指定 bean 名 —— 这里选改类名，因为读代码时能一眼看出是新链路那份。
 *
 * <p>行为与旧实现逐字一致（含整体 {@code catch} 只记 ERROR：流水记不上不该让已成立的签约报错）。
 * 待旧实现删除后，可考虑把两者合回一个类；<b>在那之前 NEVER 让旧实现改注本类</b> ——
 * 迁移期两侧各用自己那份，才能在切流量时逐条对比行为。
 */
@Component
public class SignLogWriter {

    private static final Logger log = LoggerFactory.getLogger(SignLogWriter.class);
    private static final String CHANNEL_ALIPAY = "ALIPAY";

    @Autowired
    private AlipaySignLogMapper alipaySignLogMapper;

    /**
     * 记一条签约成功流水。
     *
     * <p>协议号与 thirdUserId 都从 {@code signInfo} 取，**NEVER 再从调用点额外传一遍**（ADR-D135）：
     * 那两个入参此前与 signInfo 内的值恒等，多传一份只是给「两处不一致」留了口子。
     */
    public void recordSignSuccess(AlipaySignInfo signInfo, Object request, AlipayTripAddContractRespDTO response) {
        String agreementCode = signInfo.getAgreementCode();
        String thirdUserId = signInfo.getThirdUserId();
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
            signLog.setResultCode(response.getRetCode());
            signLog.setResultMsg(response.getRetMsg());
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
