package com.chinasofti.huateng.alipay.paysign.service.impl.termination;

import com.chinasofti.huateng.alipay.paysign.service.impl.notify.PaymentNotifyAdapter;

import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipayTerminationRequest;
import com.chinasofti.huateng.model.domain.AlipaySignStatus;
import com.chinasofti.huateng.model.domain.AlipayTerminationStatus;
import com.chinasofti.huateng.alipay.paysign.domain.AlipaySignStatusTransition;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayTerminationRequestMapper;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TerminationNotifier {

    private static final Logger log = LoggerFactory.getLogger(TerminationNotifier.class);

    private static final String SUCCESS_RET_CODE = FepAppErrorCodeEnum.SUCCESS.getCode();

    public enum Outcome {
        TERMINATED,
        FAILED,
        RETRY_LATER
    }

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    @Autowired
    private AlipayTerminationRequestMapper alipayTerminationRequestMapper;

    @Autowired
    private PaymentNotifyAdapter paymentNotifyAdapter;


    public Outcome execute(AlipayTerminationRequest terminationRequest) {
        String agreementCode = terminationRequest.getAgreementCode();
        String terminationSeq = terminationRequest.getTerminationSeq();
        try {
            AlipaySignInfo signInfo = alipaySignInfoMapper.selectByAgreementCode(agreementCode);
            if (signInfo == null) {
                log.warn("销卡失败，签约信息不存在, agreementCode={}, terminationSeq={}", agreementCode, terminationSeq);
                markStatus(terminationSeq, AlipayTerminationStatus.FAIL);
                return Outcome.FAILED;
            }

            if (!notifyCloseResult(signInfo)) {
                log.error("通知支付中心销卡结果未成功，保持 PENDING 等下轮重试, agreementCode={}, terminationSeq={}",
                        agreementCode, terminationSeq);
                return Outcome.RETRY_LATER;
            }

            if (!terminateSignRow(agreementCode, terminationSeq)) {
                return Outcome.RETRY_LATER;
            }

            int rows = alipayTerminationRequestMapper.updateStatusCas(terminationSeq,
                    AlipayTerminationStatus.PENDING.name(), AlipayTerminationStatus.COMPLETED.name(),
                    LocalDateTime.now());
            if (rows == 0) {
                log.warn("销卡登记状态已被并发改走，跳过收口, agreementCode={}, terminationSeq={}",
                        agreementCode, terminationSeq);
                return Outcome.RETRY_LATER;
            }

            log.info("销卡执行完成, agreementCode={}, terminationSeq={}", agreementCode, terminationSeq);
            return Outcome.TERMINATED;
        } catch (Exception e) {
            log.error("销卡执行异常，保持 PENDING, agreementCode={}, terminationSeq={}", agreementCode, terminationSeq, e);
            return Outcome.RETRY_LATER;
        }
    }


    private boolean terminateSignRow(String agreementCode, String terminationSeq) {
        int rows = alipaySignInfoMapper.markTerminated(agreementCode, LocalDateTime.now());
        AlipaySignStatusTransition.Result result = AlipaySignStatusTransition.classify(
                rows, AlipaySignStatus.TERMINATED,
                () -> alipaySignInfoMapper.selectSignStatusByAgreementCode(agreementCode));
        if (result.isConflict()) {
            log.error("签约状态非法，销卡无法收口，MUST 人工核对, agreementCode={}, terminationSeq={}, observedStatus={}",
                    agreementCode, terminationSeq, result.observedStatus());
            return false;
        }
        if (result.outcome() == AlipaySignStatusTransition.Outcome.IDEMPOTENT) {
            log.warn("签约状态已是 TERMINATED，按重复执行处理, agreementCode={}, terminationSeq={}",
                    agreementCode, terminationSeq);
        }
        return true;
    }

    private void markStatus(String terminationSeq, AlipayTerminationStatus toStatus) {
        try {
            int rows = alipayTerminationRequestMapper.updateStatusCas(terminationSeq,
                    AlipayTerminationStatus.PENDING.name(), toStatus.name(), LocalDateTime.now());
            if (rows == 0) {
                log.warn("更新销卡登记状态未命中，可能已被并发改走, terminationSeq={}, toStatus={}", terminationSeq, toStatus);
            }
        } catch (Exception e) {
            log.error("更新销卡登记状态失败, terminationSeq={}, toStatus={}", terminationSeq, toStatus, e);
        }
    }


    private boolean notifyCloseResult(AlipaySignInfo signInfo) {
        String agreementCode = signInfo.getAgreementCode();
        String channelAgreementCode = signInfo.getChannelAgreementCode();
        String notifyAgreementNo = channelAgreementCode;
        if (notifyAgreementNo == null || notifyAgreementNo.trim().isEmpty()) {
            log.warn("签约记录缺少 channelAgreementCode，退回使用我方 agreementCode 通知, agreementCode={}", agreementCode);
            notifyAgreementNo = agreementCode;
        }
        log.info("支付宝出行-销卡结果通知,调用支付中心, agreementCode={}, notifyAgreementNo={}",
                agreementCode, notifyAgreementNo);
        AlipayCommonResponse response = paymentNotifyAdapter.notifyCloseResult(notifyAgreementNo, true);
        boolean accepted = response != null && SUCCESS_RET_CODE.equals(response.getRetCode());
        if (!accepted) {
            log.error("支付宝出行-销卡结果通知未获成功应答, agreementCode={}, notifyAgreementNo={}, retCode={}, retMsg={}",
                    agreementCode, notifyAgreementNo,
                    response == null ? null : response.getRetCode(),
                    response == null ? null : response.getRetMsg());
        }
        return accepted;
    }
}
