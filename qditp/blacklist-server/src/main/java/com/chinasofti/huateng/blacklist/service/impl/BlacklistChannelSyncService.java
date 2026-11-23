package com.chinasofti.huateng.blacklist.service.impl;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.mapper.BlacklistMapper;
import com.chinasofti.huateng.blacklist.port.AlipayBlacklistNotifyPort;
import com.chinasofti.huateng.model.domain.OutboxScan;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class BlacklistChannelSyncService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistChannelSyncService.class);

    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_REJECTED = "REJECTED";

    private static final String TYPE_ADD = "1";
    private static final String TYPE_RELEASE = "2";

    private static final String ROW_STATUS_RELEASING = "RELEASING";

    /** 地铁App渠道代码：解黑通过 HCE 文件同步，无需实时通知支付宝。 */
    private static final String CHANNEL_CODE_METRO_APP = "01";

    private static final int FAIL_REASON_MAX = 500;

    private final BlacklistMapper blacklistMapper;
    private final AlipayBlacklistNotifyPort alipayBlacklistNotifyPort;
    private final BlacklistReleaseFinalizer blacklistReleaseFinalizer;

    public BlacklistChannelSyncService(BlacklistMapper blacklistMapper,
                                       AlipayBlacklistNotifyPort alipayBlacklistNotifyPort,
                                       BlacklistReleaseFinalizer blacklistReleaseFinalizer) {
        this.blacklistMapper = blacklistMapper;
        this.alipayBlacklistNotifyPort = alipayBlacklistNotifyPort;
        this.blacklistReleaseFinalizer = blacklistReleaseFinalizer;
    }


    public void deliverAdd(Blacklist record) {
        if (record == null || record.getId() == null) {
            log.warn("加黑通知缺少主键，跳过快速路径、留给扫表补偿, cardId={}",
                    record == null ? null : record.getCardId());
            return;
        }
        try {
            deliverAddRow(record);
        } catch (RuntimeException e) {
            log.error("加黑通知快速路径异常，已留给扫表补偿, id={}, cardId={}", record.getId(), record.getCardId(), e);
        }
    }


    public void deliverReleaseByCardId(String cardId) {
        try {
            List<Blacklist> rows = blacklistMapper.selectByCardIds(List.of(cardId));
            if (rows.isEmpty()) {
                log.warn("解除提交后回查不到该卡，跳过通知快速路径, cardId={}", cardId);
                return;
            }
            Blacklist row = rows.get(0);
            if (!ROW_STATUS_RELEASING.equals(row.getStatus())) {
                log.warn("解除提交后该卡不在解除中状态，跳过通知快速路径, cardId={}, status={}",
                        cardId, row.getStatus());
                return;
            }
            deliverReleaseRow(row);
        } catch (RuntimeException e) {
            log.error("解黑通知快速路径异常，已留给扫表补偿, cardId={}", cardId, e);
        }
    }


    public OutboxScan.Result compensateAdd(int limit) {
        List<Blacklist> rows = blacklistMapper.selectPendingChannelSync(limit);
        OutboxScan.Result result = OutboxScan.run(rows,
                this::deliverAddRow,
                row -> log.warn("加黑通知本轮仍未成功，留待下一轮, id={}, cardId={}, retry={}",
                        row.getId(), row.getCardId(), row.getChannelSyncRetry()),
                (row, e) -> log.error("加黑通知投递抛异常，本行留在 FAILED 等下一轮, id={}, cardId={}",
                        row.getId(), row.getCardId(), e));
        log.info("加黑通知补偿完成, scanned={}, success={}, failed={}",
                result.scanned(), result.success(), result.failed());
        return result;
    }


    public OutboxScan.Result compensateRelease(int limit) {
        List<Blacklist> rows = blacklistMapper.selectPendingReleaseSync(limit);
        OutboxScan.Result result = OutboxScan.run(rows,
                this::deliverReleaseRow,
                row -> log.warn("解黑通知本轮仍未成功，该卡仍按拉黑处理、留待下一轮, id={}, cardId={}, retry={}",
                        row.getId(), row.getCardId(), row.getChannelSyncRetry()),
                (row, e) -> log.error("解黑通知投递抛异常，本行留在 RELEASING+FAILED 等下一轮, id={}, cardId={}",
                        row.getId(), row.getCardId(), e));
        log.info("解黑通知补偿完成, scanned={}, success={}, failed={}",
                result.scanned(), result.success(), result.failed());
        return result;
    }

    private boolean deliverAddRow(Blacklist row) {
        RpcOutcome outcome = alipayBlacklistNotifyPort.notifyBlackListChange(
                row.getCardId(), row.getThirdUserId(), row.getCardType(), TYPE_ADD, row.getReason());
        return applyOutcome("加黑", row.getId(), row.getCardId(), outcome,
                blacklistMapper::markChannelSyncSuccess, blacklistMapper::markChannelSyncFailed);
    }


    private boolean deliverReleaseRow(Blacklist row) {
        // 地铁App渠道（01）的黑名单通过 HCE 文件同步到闸机，无需实时通知支付宝；
        // 直接完成解黑收口，避免支付宝服务不可达时阻塞解黑流程。
        if (CHANNEL_CODE_METRO_APP.equals(row.getChannelCode())) {
            log.info("地铁App渠道解黑跳过支付宝通知，直接完成收口, id={}, cardId={}", row.getId(), row.getCardId());
            int affected = blacklistReleaseFinalizer.completeRelease(row) ? 1 : 0;
            if (affected == 0) {
                log.info("解黑收口已被别处处理，跳过, id={}, cardId={}", row.getId(), row.getCardId());
            }
            return true;
        }
        RpcOutcome outcome = alipayBlacklistNotifyPort.notifyBlackListChange(
                row.getCardId(), row.getThirdUserId(), row.getCardType(), TYPE_RELEASE, row.getReleaseReason());
        return applyOutcome("解黑", row.getId(), row.getCardId(), outcome,
                id -> blacklistReleaseFinalizer.completeRelease(row) ? 1 : 0,
                blacklistMapper::markChannelSyncFailed);
    }


    private boolean applyOutcome(String direction, Long id, String cardId, RpcOutcome outcome,
                                 SuccessWriter successWriter, FailureWriter failureWriter) {
        switch (outcome) {
            case RpcOutcome.Ok ok -> {
                int affected = successWriter.markSuccess(id);
                if (affected == 0) {
                    log.info("{}通知已被别处收口，跳过回写, id={}, cardId={}", direction, id, cardId);
                }
                return true;
            }
            case RpcOutcome.BizRejected rejected -> {
                failureWriter.markFailed(id, STATUS_REJECTED,
                        truncate("渠道业务拒绝 retCode=" + rejected.retCode() + ", retMsg=" + rejected.retMsg()));
                log.error("{}通知被渠道业务拒绝，置 REJECTED 终态、重推无意义、MUST 人工核对, id={}, cardId={}, retCode={}",
                        direction, id, cardId, rejected.retCode());
                return false;
            }
            case RpcOutcome.Unreachable unreachable -> {
                failureWriter.markFailed(id, STATUS_FAILED,
                        truncate("渠道不可达 " + unreachable.cause().getClass().getSimpleName()
                                + ": " + unreachable.cause().getMessage()));
                return false;
            }
        }
    }

    private String truncate(String reason) {
        if (reason == null || reason.length() <= FAIL_REASON_MAX) {
            return reason;
        }
        return reason.substring(0, FAIL_REASON_MAX);
    }

    @FunctionalInterface
    private interface SuccessWriter {
        int markSuccess(Long id);
    }

    @FunctionalInterface
    private interface FailureWriter {
        int markFailed(Long id, String status, String failReason);
    }
}
