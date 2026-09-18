package com.chinasofti.huateng.blacklist.service.impl;

import com.chinasofti.huateng.blacklist.entity.Blacklist;
import com.chinasofti.huateng.blacklist.entity.BlacklistReleased;
import com.chinasofti.huateng.blacklist.mapper.BlacklistMapper;
import com.chinasofti.huateng.blacklist.mapper.BlacklistReleasedMapper;
import com.chinasofti.huateng.blacklist.port.AlipayBlacklistNotifyPort;
import com.chinasofti.huateng.model.domain.OutboxScan;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 黑名单渠道同步的投递与补偿（`CHANNEL_SYNC_*` outbox 的唯一驱动方）。
 *
 * <p><b>为什么要有它</b>：此前加黑 / 解黑都是在 afterCommit 里直接发一次 HTTP，失败只打一行日志 ——
 * 于是**最后一次推失败即永久丢失**，后果是「我方库里已拉黑、支付宝渠道侧不知道」（欠费卡在该渠道照样
 * 发起乘车）或反向的「我方已解黑、渠道仍按拉黑拦着」（用户过不了闸），两种都无人推进、无告警。
 * 四列 `CHANNEL_SYNC_*` 本来就在库里，但**零 UPDATE、零扫表、零调度**，是个完整的 outbox 空壳。
 *
 * <p><b>两条路径，职责不同、NEVER 互相替代</b>：
 * <ul>
 *   <li><b>快速路径</b>（{@code deliverAdd} / {@code deliverRelease}）由业务事务的 afterCommit 调用，
 *       让绝大多数通知在一秒内到达。它是**进程内非持久**的，JVM 崩溃即丢。</li>
 *   <li><b>扫表补偿</b>（{@code compensateAdd} / {@code compensateRelease}）由 web-admin 的
 *       {@code sys_job} 定时触发内部端点驱动，是**唯一的兜底**。
 *       <b>NEVER 在本模块加 `@Scheduled`</b> —— 调度一律在 web-admin，见 AGENTS.md §2.2.1。</li>
 * </ul>
 *
 * <p><b>NEVER 给本类或其方法加 {@code @Transactional}</b>：方法内有出网 HTTP。事务包住它会让行锁持有
 * 时长等于对端响应时长，上游重推全部堆在同一行上串行等待，超过 Druid {@code remove-abandoned-timeout}
 * 后连接被强杀、连状态回写一起回滚 —— pay-sign 侧 2026-08-26 已因此出过单请求 287 秒的生产事故。
 * 这里每条 SQL 自动提交才是要的语义：**先发、再按结果回写一行**。
 *
 * <p><b>三分支处置 MUST 保持不同</b>（穷尽 switch，少一支编译失败）：
 * {@code Ok} → SUCCESS；{@code BizRejected} → REJECTED（终态，重推无意义，留人工）；
 * {@code Unreachable} → FAILED（留在扫描白名单里等下一轮）。
 * <b>NEVER 把 BizRejected 也写成 FAILED</b> —— 那会让一个永远不会成功的通知无限重推。
 */
@Service
public class BlacklistChannelSyncService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistChannelSyncService.class);

    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_REJECTED = "REJECTED";

    private static final String TYPE_ADD = "1";
    private static final String TYPE_RELEASE = "2";

    /** CHANNEL_SYNC_FAIL_REASON 的列长，落库前 MUST 截断到它。 */
    private static final int FAIL_REASON_MAX = 500;

    private final BlacklistMapper blacklistMapper;
    private final BlacklistReleasedMapper blacklistReleasedMapper;
    private final AlipayBlacklistNotifyPort alipayBlacklistNotifyPort;

    public BlacklistChannelSyncService(BlacklistMapper blacklistMapper,
                                       BlacklistReleasedMapper blacklistReleasedMapper,
                                       AlipayBlacklistNotifyPort alipayBlacklistNotifyPort) {
        this.blacklistMapper = blacklistMapper;
        this.blacklistReleasedMapper = blacklistReleasedMapper;
        this.alipayBlacklistNotifyPort = alipayBlacklistNotifyPort;
    }

    /**
     * 加黑方向的快速路径，由 afterCommit 调用。
     *
     * <p>内部吞掉全部异常：afterCommit 里抛异常回滚不了已提交的事务，只会让本已成功的加黑对上游报错、
     * 引来重推。推不动没关系，行还在白名单里、下一轮扫表会捞到。
     */
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

    /**
     * 解黑方向的快速路径，由 afterCommit 调用。异常处置同 {@link #deliverAdd}。
     */
    public void deliverRelease(BlacklistReleased record) {
        if (record == null || record.getId() == null) {
            log.warn("解黑通知缺少主键，跳过快速路径、留给扫表补偿, cardId={}",
                    record == null ? null : record.getCardId());
            return;
        }
        try {
            deliverReleaseRow(record);
        } catch (RuntimeException e) {
            log.error("解黑通知快速路径异常，已留给扫表补偿, id={}, cardId={}", record.getId(), record.getCardId(), e);
        }
    }

    /**
     * 加黑方向的扫表补偿，由 web-admin 定时任务经内部端点驱动。
     *
     * @param limit 单轮最多处理多少行
     * @return 本轮扫描结果
     */
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

    /**
     * 解黑方向的扫表补偿，由 web-admin 定时任务经内部端点驱动。
     *
     * @param limit 单轮最多处理多少行
     * @return 本轮扫描结果
     */
    public OutboxScan.Result compensateRelease(int limit) {
        List<BlacklistReleased> rows = blacklistReleasedMapper.selectPendingChannelSync(limit);
        OutboxScan.Result result = OutboxScan.run(rows,
                this::deliverReleaseRow,
                row -> log.warn("解黑通知本轮仍未成功，留待下一轮, id={}, cardId={}, retry={}",
                        row.getId(), row.getCardId(), row.getChannelSyncRetry()),
                (row, e) -> log.error("解黑通知投递抛异常，本行留在 FAILED 等下一轮, id={}, cardId={}",
                        row.getId(), row.getCardId(), e));
        log.info("解黑通知补偿完成, scanned={}, success={}, failed={}",
                result.scanned(), result.success(), result.failed());
        return result;
    }

    /** 投递一行加黑通知并回写状态，返回 true 表示已收口 SUCCESS。 */
    private boolean deliverAddRow(Blacklist row) {
        RpcOutcome outcome = alipayBlacklistNotifyPort.notifyBlackListChange(
                row.getCardId(), row.getThirdUserId(), row.getCardType(), TYPE_ADD, row.getReason());
        return applyOutcome("加黑", row.getId(), row.getCardId(), outcome,
                blacklistMapper::markChannelSyncSuccess, blacklistMapper::markChannelSyncFailed);
    }

    /** 投递一行解黑通知并回写状态，返回 true 表示已收口 SUCCESS。 */
    private boolean deliverReleaseRow(BlacklistReleased row) {
        RpcOutcome outcome = alipayBlacklistNotifyPort.notifyBlackListChange(
                row.getCardId(), row.getThirdUserId(), row.getCardType(), TYPE_RELEASE, row.getReleaseReason());
        return applyOutcome("解黑", row.getId(), row.getCardId(), outcome,
                blacklistReleasedMapper::markChannelSyncSuccess, blacklistReleasedMapper::markChannelSyncFailed);
    }

    /**
     * 按三分支回写状态，两个方向共用同一套判据。
     *
     * <p>回写影响 0 行不是错误：说明该行已被别处（快速路径与补偿撞上了）收口，只打 INFO。
     */
    private boolean applyOutcome(String direction, Long id, String cardId, RpcOutcome outcome,
                                 SuccessWriter successWriter, FailureWriter failureWriter) {
        switch (outcome) {
            case RpcOutcome.Ok ok -> {
                int affected = successWriter.markSuccess(id);
                if (affected == 0) {
                    log.info("{}通知已被别处收口，跳过回写 SUCCESS, id={}, cardId={}", direction, id, cardId);
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

    /** 截断到 CHANNEL_SYNC_FAIL_REASON 的列长，避免落库时 ORA-12899。 */
    private String truncate(String reason) {
        if (reason == null || reason.length() <= FAIL_REASON_MAX) {
            return reason;
        }
        return reason.substring(0, FAIL_REASON_MAX);
    }

    /** 成功回写的写法，两个 mapper 各自的 markChannelSyncSuccess。 */
    @FunctionalInterface
    private interface SuccessWriter {
        int markSuccess(Long id);
    }

    /** 失败回写的写法，两个 mapper 各自的 markChannelSyncFailed。 */
    @FunctionalInterface
    private interface FailureWriter {
        int markFailed(Long id, String status, String failReason);
    }
}
