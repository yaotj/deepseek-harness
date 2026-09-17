package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipayTerminationRequest;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayTerminationRequestMapper;
import com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse;
import com.chinasofti.huateng.alipay.paysign.util.PayCenterClient;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 支付宝出行销卡执行器：把一条 PENDING 的解约登记记录真正执行掉并收口状态。 */
@Service
public class TerminationNotifier {

    private static final Logger log = LoggerFactory.getLogger(TerminationNotifier.class);

    private static final String SIGN_STATUS_TERMINATED = "TERMINATED";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAIL = "FAIL";

    /** 支付中心通知成功的 code。与 PaymentNotifyAdapter 保持一致，勿再引入第二套判定。 */
    private static final String PAY_CENTER_SUCCESS_CODE = "200";

    /** 单条登记记录的执行结果。 */
    public enum Outcome {
        /** 销卡完成，登记表已置 COMPLETED。 */
        TERMINATED,
        /** 明确失败且不再重试，登记表已置 FAIL。 */
        FAILED,
        /** 本轮未能完成，状态保持 PENDING，等下一轮重试。 */
        RETRY_LATER
    }

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    @Autowired
    private AlipayTerminationRequestMapper alipayTerminationRequestMapper;

    @Autowired
    private PayCenterClient payCenterClient;

    /**
     * 执行一条销卡登记。
     * @param terminationRequest 状态为 PENDING 的登记记录
     * @return 执行结果，调用方按此计数，NEVER 假定「没抛异常就是成功」
     */
    public Outcome execute(AlipayTerminationRequest terminationRequest) {
        String agreementCode = terminationRequest.getAgreementCode();
        String terminationSeq = terminationRequest.getTerminationSeq();
        try {
            AlipaySignInfo signInfo = alipaySignInfoMapper.selectByAgreementCode(agreementCode);
            if (signInfo == null) {
                // 签约信息不存在是明确的终态失败：重试多少次都不会变出一条签约记录。
                log.warn("销卡失败，签约信息不存在, agreementCode={}, terminationSeq={}", agreementCode, terminationSeq);
                markStatus(terminationSeq, STATUS_FAIL);
                return Outcome.FAILED;
            }

            // 先调远端：通知支付中心业务关闭。失败即放弃本轮，不动任何本地状态。
            if (!notifyCloseResult(signInfo)) {
                log.error("通知支付中心销卡结果未成功，保持 PENDING 等下轮重试, agreementCode={}, terminationSeq={}",
                        agreementCode, terminationSeq);
                return Outcome.RETRY_LATER;
            }

            // 远端已确认，再改本地：签约置 TERMINATED，登记表 PENDING -> COMPLETED（CAS）。
            alipaySignInfoMapper.updateStatus(signInfo.getAgreementCode(), SIGN_STATUS_TERMINATED, LocalDateTime.now());
            int rows = alipayTerminationRequestMapper.updateStatusCas(
                    terminationSeq, STATUS_PENDING, STATUS_COMPLETED, LocalDateTime.now());
            if (rows == 0) {
                // CAS 未命中说明这条已被别的执行流收口，本轮不重复计数。
                log.warn("销卡登记状态已被并发改走，跳过收口, agreementCode={}, terminationSeq={}",
                        agreementCode, terminationSeq);
                return Outcome.RETRY_LATER;
            }

            log.info("销卡执行完成, agreementCode={}, terminationSeq={}", agreementCode, terminationSeq);
            return Outcome.TERMINATED;
        } catch (Exception e) {
            // 异常原因未知（可能是网络抖动），**NEVER 置 FAIL**：保持 PENDING 让下一轮自愈。
            log.error("销卡执行异常，保持 PENDING, agreementCode={}, terminationSeq={}", agreementCode, terminationSeq, e);
            return Outcome.RETRY_LATER;
        }
    }

    /** 把登记记录改成指定状态，CAS 未命中只告警，不抛异常。 */
    private void markStatus(String terminationSeq, String toStatus) {
        try {
            int rows = alipayTerminationRequestMapper.updateStatusCas(
                    terminationSeq, STATUS_PENDING, toStatus, LocalDateTime.now());
            if (rows == 0) {
                log.warn("更新销卡登记状态未命中，可能已被并发改走, terminationSeq={}, toStatus={}", terminationSeq, toStatus);
            }
        } catch (Exception e) {
            log.error("更新销卡登记状态失败, terminationSeq={}, toStatus={}", terminationSeq, toStatus, e);
        }
    }

    /**
     * 通知支付中心业务关闭结果。
     * @return true 表示支付中心明确返回成功；网络异常、响应为空、code 非成功一律 false
     */
    private boolean notifyCloseResult(AlipaySignInfo signInfo) {
        String agreementCode = signInfo.getAgreementCode();
        String channelAgreementCode = signInfo.getChannelAgreementCode();
        String notifyAgreementNo = channelAgreementCode;
        if (notifyAgreementNo == null || notifyAgreementNo.trim().isEmpty()) {
            // 签约记录没存渠道号属于数据缺陷，退回我方号只为保留旧行为，支付中心大概率仍查不到。
            log.warn("签约记录缺少 channelAgreementCode，退回使用我方 agreementCode 通知, agreementCode={}", agreementCode);
            notifyAgreementNo = agreementCode;
        }
        try {
            Map<String, Object> bizDataMap = new LinkedHashMap<>();
            bizDataMap.put("agreementNo", notifyAgreementNo);
            bizDataMap.put("result", true);

            log.info("支付宝出行-销卡结果通知,调用支付中心,agreementCode={}, 请求参数: {}",
                    agreementCode, JSON.toJSONString(bizDataMap));
            PayCenterResponse payCenterResponse = payCenterClient.closeResultNotify(bizDataMap);
            log.info("支付宝出行-销卡结果通知,支付中心响应: code={}, success={}, msg={}",
                    payCenterResponse == null ? null : payCenterResponse.getCode(),
                    payCenterResponse == null ? null : payCenterResponse.getSuccess(),
                    payCenterResponse == null ? null : payCenterResponse.getMsg());
            return payCenterResponse != null
                    && payCenterResponse.getCode() != null
                    && PAY_CENTER_SUCCESS_CODE.equals(String.valueOf(payCenterResponse.getCode()));
        } catch (Exception e) {
            log.error("支付宝出行-销卡结果通知异常, agreementCode={}, notifyAgreementNo={}",
                    agreementCode, notifyAgreementNo, e);
            return false;
        }
    }
}
