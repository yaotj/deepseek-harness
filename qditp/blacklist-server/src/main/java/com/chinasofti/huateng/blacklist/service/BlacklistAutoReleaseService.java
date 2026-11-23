package com.chinasofti.huateng.blacklist.service;

import com.chinasofti.huateng.blacklist.constant.BlacklistErrorCodeEnum;
import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.mapper.BlacklistMapper;
import com.chinasofti.huateng.model.app.BlackListOperateResult;
import com.chinasofti.huateng.model.app.BlacklistAutoReleaseRespDTO;
import com.chinasofti.huateng.model.app.DeleteBlackListReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class BlacklistAutoReleaseService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistAutoReleaseService.class);

    private static final String CHANNEL_CODE_METRO_APP = "01";
    private static final String CHANNEL_CODE_ALIPAY = "02";

    private static final String RELEASE_REASON = "欠费已结清自动解除";
    private static final String RELEASE_BY = "auto-release-job";

    /** 欠费类黑名单原因码，与 selectForAutoRelease 的 BLACK_CAUSE='01' 同口径。 */
    static final String BLACK_CAUSE_UNPAID = "01";
    static final String STATUS_ACTIVE = "ACTIVE";

    private final BlacklistMapper blacklistMapper;
    private final BlacklistService blacklistService;
    private final CardUnsettledQuery cardUnsettledQuery;

    @Value("${blacklist.auto-release.batch-size:200}")
    private int batchSize;

    public BlacklistAutoReleaseService(BlacklistMapper blacklistMapper,
                                       BlacklistService blacklistService,
                                       CardUnsettledQuery cardUnsettledQuery) {
        this.blacklistMapper = blacklistMapper;
        this.blacklistService = blacklistService;
        this.cardUnsettledQuery = cardUnsettledQuery;
    }


    public BlacklistAutoReleaseRespDTO autoRelease(Integer limit) {
        BlacklistAutoReleaseRespDTO response = new BlacklistAutoReleaseRespDTO();
        response.setResultCode(BlacklistErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg("成功");

        int effective = limit == null || limit <= 0 ? (batchSize > 0 ? batchSize : 200) : limit;
        List<Blacklist> records = blacklistMapper.selectForAutoRelease(effective);
        if (records == null || records.isEmpty()) {
            log.info("黑名单自动解除完成, 无欠费类候选记录, limit={}", effective);
            return response;
        }

        int released = 0;
        int unsettled = 0;
        int unknown = 0;
        int skipped = 0;
        int failed = 0;
        for (Blacklist record : records) {
            switch (handleOne(record)) {
                case RELEASED -> released++;
                case UNSETTLED -> unsettled++;
                case UNKNOWN -> unknown++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }

        response.setScanned(records.size());
        response.setReleased(released);
        response.setUnsettled(unsettled);
        response.setUnknown(unknown);
        response.setSkipped(skipped);
        response.setFailed(failed);
        log.info("黑名单自动解除完成, scanned={}, released={}, unsettled={}, unknown={}, skipped={}, failed={}",
                records.size(), released, unsettled, unknown, skipped, failed);
        return response;
    }


    /**
     * 单卡实时解黑：补款付清后由 face-pay 调用。
     *
     * <p>只处理该卡「ACTIVE + 欠费类（BLACK_CAUSE=01）」的黑名单记录，逐条复用 {@link #handleOne} 的判据
     * （按记录的 CHANNEL_CODE 查对应欠费源，无欠费才解）——与全表 autoRelease 完全同源，
     * NEVER 跳过欠费判据直接解黑：补款可能只付了部分欠费，直接解会放跑欠费乘客。
     *
     * <p>本方法是「尽早解」的加速路径，失败/异常由调用方仅记日志不阻断补款收敛；
     * 兜底仍是每天两次的全表 {@link #autoRelease(Integer)}，两者并存不替代。
     */
    public BlacklistAutoReleaseRespDTO releaseCardIfSettled(String cardId) {
        BlacklistAutoReleaseRespDTO response = new BlacklistAutoReleaseRespDTO();
        response.setResultCode(BlacklistErrorCodeEnum.SUCCESS.getCode());
        response.setResultMsg("成功");
        if (cardId == null || cardId.isBlank()) {
            log.warn("单卡实时解黑跳过: cardId 为空");
            return response;
        }

        List<Blacklist> records = blacklistMapper.selectByCardIds(List.of(cardId));
        if (records == null || records.isEmpty()) {
            return response;
        }

        int released = 0;
        int unsettled = 0;
        int unknown = 0;
        int skipped = 0;
        int failed = 0;
        for (Blacklist record : records) {
            // 只认 ACTIVE + 欠费类；解除中(RELEASING)/已解除(表里不会有)及其它原因码一律不碰。
            if (!isRealtimeEligible(record)) {
                skipped++;
                continue;
            }
            switch (handleOne(record)) {
                case RELEASED -> released++;
                case UNSETTLED -> unsettled++;
                case UNKNOWN -> unknown++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }
        response.setScanned(records.size());
        response.setReleased(released);
        response.setUnsettled(unsettled);
        response.setUnknown(unknown);
        response.setSkipped(skipped);
        response.setFailed(failed);
        log.info("单卡实时解黑完成, cardId={}, scanned={}, released={}, unsettled={}, unknown={}, skipped={}, failed={}",
                cardId, records.size(), released, unsettled, unknown, skipped, failed);
        return response;
    }

    /** 实时解黑的候选判据：仅 ACTIVE + 欠费类（01）；与全表 selectForAutoRelease 的 WHERE 同口径。 */
    static boolean isRealtimeEligible(Blacklist record) {
        return record != null
                && STATUS_ACTIVE.equals(record.getStatus())
                && BLACK_CAUSE_UNPAID.equals(record.getBlackCause());
    }

    private Outcome handleOne(Blacklist record) {
        String cardId = record.getCardId();
        String channelCode = record.getChannelCode();
        Boolean stillOwing;
        if (CHANNEL_CODE_METRO_APP.equals(channelCode)) {
            stillOwing = cardUnsettledQuery.gateUnsettled(cardId);
        } else if (CHANNEL_CODE_ALIPAY.equals(channelCode)) {
            stillOwing = cardUnsettledQuery.alipayUnsettled(cardId);
        } else {
            log.warn("黑名单自动解除跳过: 渠道未知、无法定位欠费源, MUST 人工处理, cardId={}, channelCode={}",
                    cardId, channelCode);
            return Outcome.SKIPPED;
        }

        if (stillOwing == null) {
            log.error("黑名单自动解除跳过: 欠费查询未成功、事实不明, MUST 人工核对, cardId={}, channelCode={}",
                    cardId, channelCode);
            return Outcome.UNKNOWN;
        }
        if (stillOwing) {
            log.info("黑名单自动解除跳过: 仍有未结清欠费, cardId={}, channelCode={}", cardId, channelCode);
            return Outcome.UNSETTLED;
        }

        return release(cardId, channelCode);
    }


    private Outcome release(String cardId, String channelCode) {
        try {
            DeleteBlackListReqDTO request = new DeleteBlackListReqDTO();
            request.setCardId(cardId);
            request.setReleaseReason(RELEASE_REASON);
            request.setReleaseBy(RELEASE_BY);
            BlackListOperateResult result = blacklistService.deleteBlackList(request);
            if (result == null || !BlacklistErrorCodeEnum.SUCCESS.getCode().equals(result.getRetCode())) {
                log.error("黑名单自动解除失败, MUST 人工核对, cardId={}, channelCode={}, response={}",
                        cardId, channelCode, result);
                return Outcome.FAILED;
            }
            log.info("黑名单自动解除已发起, cardId={}, channelCode={}, releaseReason={}",
                    cardId, channelCode, RELEASE_REASON);
            return Outcome.RELEASED;
        } catch (Exception e) {
            log.error("黑名单自动解除异常, MUST 人工核对, cardId={}, channelCode={}", cardId, channelCode, e);
            return Outcome.FAILED;
        }
    }

    private enum Outcome {
        RELEASED,
        UNSETTLED,
        UNKNOWN,
        SKIPPED,
        FAILED
    }
}
