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

/** 支付宝出行销卡执行器：把一条 PENDING 的解约登记记录真正执行掉并收口状态。 */
@Service
public class TerminationNotifier {

    private static final Logger log = LoggerFactory.getLogger(TerminationNotifier.class);

    /** 通知被支付中心确认的应答码。判定本身在 {@link PaymentNotifyAdapter}，本类只解读它的结论。 */
    private static final String SUCCESS_RET_CODE = FepAppErrorCodeEnum.SUCCESS.getCode();

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
    private PaymentNotifyAdapter paymentNotifyAdapter;

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
                markStatus(terminationSeq, AlipayTerminationStatus.FAIL);
                return Outcome.FAILED;
            }

            // 先调远端：通知支付中心业务关闭。失败即放弃本轮，不动任何本地状态。
            if (!notifyCloseResult(signInfo)) {
                log.error("通知支付中心销卡结果未成功，保持 PENDING 等下轮重试, agreementCode={}, terminationSeq={}",
                        agreementCode, terminationSeq);
                return Outcome.RETRY_LATER;
            }

            // 远端已确认，再改本地：签约 SIGNED -> TERMINATED 走 CAS（ADR-D130）。
            if (!terminateSignRow(agreementCode, terminationSeq)) {
                return Outcome.RETRY_LATER;
            }

            int rows = alipayTerminationRequestMapper.updateStatusCas(terminationSeq,
                    AlipayTerminationStatus.PENDING.name(), AlipayTerminationStatus.COMPLETED.name(),
                    LocalDateTime.now());
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

    /**
     * 把签约行 CAS 推进到 TERMINATED。
     *
     * <p>{@code IDEMPOTENT}（库里已是 TERMINATED）与 {@code DONE} 一样放行 —— 远端此刻已确认解约，
     * 登记表该继续收口成 COMPLETED，否则这条会永远重推。</p>
     *
     * <p>{@code CONFLICT} 表示签约状态既不是 SIGNED 也不是 TERMINATED（NULL 或历史脏值）：
     * 打 ERROR 交人工，并让本轮返回 {@code RETRY_LATER} 把登记留在 PENDING —— <b>NEVER 静默放行</b>，
     * 否则「签约行状态不明、登记已 COMPLETED」这条不一致就再没有人看得见了。</p>
     *
     * @return true 表示目标态已达成、可以继续收口登记表
     */
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

    /** 把登记记录改成指定状态，CAS 未命中只告警，不抛异常。 */
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

    /**
     * 通知支付中心业务关闭结果。
     *
     * <p>出网收口到 {@link PaymentNotifyAdapter}（ADR-D131）：此前本类自己直调
     * {@code payCenterClient.closeResultNotify} 并自带一套 {@code code==200} 判定，与那个 adapter
     * 逐字重复 —— 两份同形副本意味着支付中心一改判据就得改两处、且极可能只改一处。
     * **NEVER 再在本类里直调 `PayCenterClient`。**</p>
     *
     * <p>唯一的行为差异：`agreementNo` 为空时 adapter 直接返 `8001` 不发请求，而旧实现会发一次
     * 注定失败的请求。两者对本方法的返回值都是 false，收口结论不变。</p>
     *
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
