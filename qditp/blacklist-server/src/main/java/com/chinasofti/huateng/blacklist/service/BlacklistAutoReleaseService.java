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
