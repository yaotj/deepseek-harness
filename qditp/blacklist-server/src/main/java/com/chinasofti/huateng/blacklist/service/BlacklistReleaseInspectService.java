package com.chinasofti.huateng.blacklist.service;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.mapper.BlacklistMapper;
import com.chinasofti.huateng.model.app.BlacklistReleaseCandidateDTO;
import com.chinasofti.huateng.model.app.BlacklistReleaseInspectRespDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryReqDTO;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 黑名单「可解除性」只读盘点。
 *
 * <p>本类 NEVER 删除任何黑名单记录，也 NEVER 加 @Transactional（方法内有 RPC）。</p>
 */
@Service
public class BlacklistReleaseInspectService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistReleaseInspectService.class);

    private static final String RESULT_CODE_SUCCESS = "0000";
    private static final String DOWNSTREAM_SUCCESS = "0000";

    /** 两个欠费源都查成功且都无欠费。 */
    private static final String STATUS_SETTLED = "SETTLED";
    /** 至少一个欠费源仍有未结清订单。 */
    private static final String STATUS_UNSETTLED = "UNSETTLED";
    /** 至少一个欠费源查询失败，事实不明。 */
    private static final String STATUS_UNKNOWN = "UNKNOWN";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BlacklistMapper blacklistMapper;
    private final GateTxnPayClient gateTxnPayClient;
    private final AlipayPaySignClient alipayPaySignClient;

    /**
     * 单次盘点上限。
     */
    @Value("${blacklist.inspect.batch-size:200}")
    private int batchSize;

    public BlacklistReleaseInspectService(BlacklistMapper blacklistMapper,
                                          GateTxnPayClient gateTxnPayClient,
                                          AlipayPaySignClient alipayPaySignClient) {
        this.blacklistMapper = blacklistMapper;
        this.gateTxnPayClient = gateTxnPayClient;
        this.alipayPaySignClient = alipayPaySignClient;
    }

    /**
     * 盘点黑名单记录的欠费结清情况，只读。
     *
     * @return 计数与逐条明细
     */
    public BlacklistReleaseInspectRespDTO inspect() {
        BlacklistReleaseInspectRespDTO response = new BlacklistReleaseInspectRespDTO();
        List<BlacklistReleaseCandidateDTO> details = new ArrayList<>();
        response.setResultCode(RESULT_CODE_SUCCESS);
        response.setResultMsg("成功");
        response.setDetails(details);

        List<Blacklist> records = blacklistMapper.selectForInspect(batchSize > 0 ? batchSize : 200);
        if (records == null || records.isEmpty()) {
            log.info("黑名单可解除性盘点完成, 无待盘点记录");
            return response;
        }

        int settled = 0;
        int unsettled = 0;
        int unknown = 0;
        for (Blacklist record : records) {
            BlacklistReleaseCandidateDTO candidate = inspectOne(record);
            details.add(candidate);
            switch (candidate.getSettleStatus()) {
                case STATUS_SETTLED -> settled++;
                case STATUS_UNSETTLED -> unsettled++;
                default -> unknown++;
            }
        }

        response.setScanned(records.size());
        response.setSettled(settled);
        response.setUnsettled(unsettled);
        response.setUnknown(unknown);
        log.info("黑名单可解除性盘点完成, scanned={}, settled={}, unsettled={}, unknown={}",
                records.size(), settled, unsettled, unknown);
        return response;
    }

    /**
     * 盘点单条记录，任何异常都收敛为 UNKNOWN，不让一条坏数据中断整批。
     */
    private BlacklistReleaseCandidateDTO inspectOne(Blacklist record) {
        BlacklistReleaseCandidateDTO candidate = new BlacklistReleaseCandidateDTO();
        candidate.setCardId(record.getCardId());
        candidate.setThirdUserId(record.getThirdUserId());
        candidate.setReason(record.getReason());
        candidate.setCreateTime(record.getCreateTime() != null
                ? record.getCreateTime().format(TIME_FORMATTER) : null);

        Boolean gateUnsettled = queryGate(record.getCardId());
        Boolean alipayUnsettled = queryAlipay(record.getCardId());
        candidate.setGateUnsettled(gateUnsettled);
        candidate.setAlipayUnsettled(alipayUnsettled);

        if (gateUnsettled == null || alipayUnsettled == null) {
            candidate.setSettleStatus(STATUS_UNKNOWN);
            candidate.setFailReason(buildFailReason(gateUnsettled, alipayUnsettled));
            log.error("黑名单可解除性盘点存在查询失败, MUST 人工核对, cardId={}, gate={}, alipay={}",
                    record.getCardId(), gateUnsettled, alipayUnsettled);
            return candidate;
        }

        boolean stillOwing = gateUnsettled || alipayUnsettled;
        candidate.setSettleStatus(stillOwing ? STATUS_UNSETTLED : STATUS_SETTLED);
        log.info("黑名单可解除性盘点单条完成, cardId={}, gateUnsettled={}, alipayUnsettled={}, status={}, reason={}",
                record.getCardId(), gateUnsettled, alipayUnsettled, candidate.getSettleStatus(), record.getReason());
        return candidate;
    }

    /**
     * 查闸机出站扣费欠费，返回 null 表示查询未成功执行（事实不明）。
     */
    private Boolean queryGate(String cardId) {
        try {
            CardUnsettledQueryReqDTO request = new CardUnsettledQueryReqDTO();
            request.setCardId(cardId);
            CardUnsettledQueryRespDTO result = gateTxnPayClient.hasUnsettledOrderByCard(request);
            // 但那是「不明」不是「有欠费」，两者在报表上要区分开。
            if (result == null || !DOWNSTREAM_SUCCESS.equals(result.getResultCode())) {
                log.warn("查询闸机扣费欠费未成功, cardId={}, response={}", cardId, result);
                return null;
            }
            return result.isHasUnsettled();
        } catch (Exception e) {
            log.error("查询闸机扣费欠费异常, cardId={}", cardId, e);
            return null;
        }
    }

    /**
     * 查支付宝出行欠费，返回 null 表示查询未成功执行（事实不明）。
     */
    private Boolean queryAlipay(String cardId) {
        try {
            CardUnsettledQueryReqDTO request = new CardUnsettledQueryReqDTO();
            request.setCardId(cardId);
            CardUnsettledQueryRespDTO result = alipayPaySignClient.hasUnsettledOrderByCard(request);
            if (result == null || !DOWNSTREAM_SUCCESS.equals(result.getResultCode())) {
                log.warn("查询支付宝出行欠费未成功, cardId={}, response={}", cardId, result);
                return null;
            }
            return result.isHasUnsettled();
        } catch (Exception e) {
            log.error("查询支付宝出行欠费异常, cardId={}", cardId, e);
            return null;
        }
    }

    private String buildFailReason(Boolean gateUnsettled, Boolean alipayUnsettled) {
        if (gateUnsettled == null && alipayUnsettled == null) {
            return "闸机扣费与支付宝出行欠费查询均失败";
        }
        return gateUnsettled == null ? "闸机扣费欠费查询失败" : "支付宝出行欠费查询失败";
    }
}
