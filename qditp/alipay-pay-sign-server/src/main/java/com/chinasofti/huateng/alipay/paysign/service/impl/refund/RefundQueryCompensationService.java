package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.domain.PayCenterTradeStatus;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundLog;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.model.domain.OutboxScan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 退款回查补偿：把停在 {@code PROCESSING} 的退款明细主动去支付中心问一次并收口。
 *
 * <p><b>补的是哪个缺口</b>：退款申请出网后只有「拿到业务应答」那一支会落终态；
 * {@code Rejected} / {@code NoAnswer} 两支刻意保持 {@code PROCESSING}（钱可能已退，落 FAIL 会被再退一次，
 * 见 {@link AlipayPayRefundServiceImpl}）。此前**没有任何人再看这些行一眼** ——
 * {@code pay.center.refund-query-url} 与 {@code PayCenterClient.refundQuery} 都在、却零调用方，
 * 于是只能人工核。退款回调（{@code /api/payment/refundNotify}）只是快速路径，本类是兜底路径，
 * <b>NEVER 设计成「只等回调」</b>（AGENTS.md §8 已有同款教训）。
 *
 * <p><b>本类刻意没有 {@code @Scheduled}</b>：本项目补偿一律由 web-admin 的 Quartz 打 {@code /internal/**}
 * 驱动（AGENTS.md §2.2.1）。加了就是「同一份补偿两个驱动源」，而本模块多副本时没有分布式锁。
 *
 * <p><b>本类也刻意不带 {@code @Transactional}</b>：方法体是「扫一批 → 逐条出网 → 逐条 CAS 回写」，
 * 出网 NEVER 被事务包住（§5.2 那条 2026-08-26 生产事故）。每条回写各自自动提交。
 *
 * <p><b>与 pay-sign 侧退款回查的一处刻意偏差，NEVER 当成漏实现</b>：pay-sign 的
 * {@code PAY_REFUND_DETAIL} 有 {@code NEXT_REQUEST_TIME} / {@code LAST_REQUEST_TIME} 退避列，
 * 未得终态时只推时间；而 {@code ALIPAY_REFUND_LOG} <b>没有任何退避列</b>，本类因此改用
 * 「{@code UPDATE_TIME} 静默期闸门 + {@code CREATE_TIME} 天窗口」——回查本身不写库，所以卡住的行
 * 每轮都会被重新扫到，直到滚出 {@value #SCAN_DAYS} 天窗口。这是**有意选择**：给本表加退避列要出一次
 * DDL 迁移并在库里执行，而回查是只读出网、重复问一次的代价远小于加列。
 * 真要加列 MUST 先出 {@code *-migration.sql} 并当场执行 + 回查（AGENTS.md §8）。
 */
@Service
public class RefundQueryCompensationService {

    private static final Logger log = LoggerFactory.getLogger(RefundQueryCompensationService.class);

    /** 支付中心业务成功的 {@code retCode}，与退款申请侧同一个判据。 */
    private static final String PAY_CENTER_SUCCESS = "SUCCESS";

    /** 只回查最近 N 天创建的退款：更早的行已过人工核对窗口，继续每轮打对端没有意义。 */
    private static final int SCAN_DAYS = 7;

    /**
     * 距上次更新至少多少分钟才回查。
     *
     * <p><b>驱动侧 cron 的间隔 MUST 大于这个值</b>，否则同一行会在正常回调还没到达时就被反复回查。
     */
    private static final int STALE_MINUTES = 5;

    private final RefundLogRepository refundLogRepository;
    private final PayCenterPort payCenterPort;

    /** 单轮取多少行。上限在 SQL 的 {@code ROWNUM} 里，不是在 Java 里截断。 */
    @Value("${alipay.refund-query.batch-size:200}")
    private int batchSize;

    public RefundQueryCompensationService(RefundLogRepository refundLogRepository, PayCenterPort payCenterPort) {
        this.refundLogRepository = refundLogRepository;
        this.payCenterPort = payCenterPort;
    }

    /**
     * 扫一批未收口的退款明细并逐条回查。
     *
     * @return 本轮扫描结果，不变量 {@code scanned == success + failed}
     */
    public OutboxScan.Result compensate() {
        List<AlipayRefundLog> pending = refundLogRepository.scanCompensable(SCAN_DAYS, STALE_MINUTES, batchSize);
        if (pending == null || pending.isEmpty()) {
            log.info("退款回查补偿：本轮无待处理行, scanDays={}, staleMinutes={}, batchSize={}",
                    SCAN_DAYS, STALE_MINUTES, batchSize);
            return new OutboxScan.Result(0, 0, 0);
        }
        OutboxScan.Result scan = OutboxScan.run(pending,
                this::settleByQuery,
                row -> log.warn("退款回查本轮未收口，等下次重扫, orderNo={}, refundOrderNo={}",
                        row.getOrderNo(), row.getRefundOrderNo()),
                (row, e) -> log.error("单条退款回查异常，NEVER 因此中断整批, orderNo={}, refundOrderNo={}",
                        row.getOrderNo(), row.getRefundOrderNo(), e));
        log.info("退款回查补偿完成, scanned={}, settled={}, pendingAgain={}",
                scan.scanned(), scan.success(), scan.failed());
        return scan;
    }

    /**
     * 回查一笔并尝试收口。
     *
     * @return 仅当「支付中心给出终态」且「CAS 真的推进了这一行」才 {@code true}
     */
    private boolean settleByQuery(AlipayRefundLog row) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        // 两个键都送：ADR-D92 实测只送 refundOrderNo 时网关返 9999「退款流水号或商户退款流水号必填」，
        // 表现为退款单永久空转而端点每轮返 0000、调度日志一片绿。NEVER 只送一个。
        bizData.put("refundOrderNo", row.getRefundOrderNo());
        bizData.put("merchantRefundNo", row.getRefundOrderNo());

        PayCenterReply reply = payCenterPort.refundQuery(bizData);
        log.info("退款回查支付中心返回, orderNo={}, refundOrderNo={}, code={}, success={}, msg={}",
                row.getOrderNo(), row.getRefundOrderNo(), reply.code(), reply.success(), reply.msg());

        String settledStatus = resolveSettledStatus(row, reply);
        if (settledStatus == null) {
            return false;
        }
        return settle(row, settledStatus);
    }

    /**
     * 判读回查应答，只返回明确的终态。
     *
     * <p>三层都不放行就返回 {@code null}（= 本轮未收口）：传输层没通、业务 {@code retCode} 非成功、
     * {@code status} 判不出终态。<b>NEVER 把「判不出」当失败落 FAIL</b> —— 钱可能已经退出去了。
     */
    private String resolveSettledStatus(AlipayRefundLog row, PayCenterReply reply) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                if (!PAY_CENTER_SUCCESS.equals(accepted.retCode())) {
                    // 这一支是 ADR-D92 那类「参数/契约被拒」的唯一可见处：MUST 打 ERROR。
                    // 只打 WARN 会让「每轮都被拒」在日志里淹没，而端点照样返 0000。
                    log.error("退款回查业务应答非成功，本轮不收口，MUST 人工核对出网参数与网关码表, orderNo={}, refundOrderNo={}, retCode={}, retMsg={}",
                            row.getOrderNo(), row.getRefundOrderNo(), accepted.retCode(), accepted.retMsg());
                    return null;
                }
                String status = accepted.field("status");
                // 值域复用全模块唯一那份归一（PayCenterTradeStatus）：供方 §3.2 同样只写了「status 退款状态」
                // 而没给值域。拿到第一条真实应答后 MUST 回来核对；若退款侧确有额外取值（如 REFUNDED），
                // MUST 加进 PayCenterTradeStatus，NEVER 在本类抄第二份白名单。
                String normalized = PayCenterTradeStatus.normalize(status);
                if (normalized == null) {
                    log.info("退款回查未得终态，保持 PROCESSING, orderNo={}, refundOrderNo={}, status={}",
                            row.getOrderNo(), row.getRefundOrderNo(), status);
                }
                return normalized;
            }
            case PayCenterReply.Rejected rejected -> {
                log.error("退款回查未拿到业务应答（网关拒绝），保持 PROCESSING, orderNo={}, refundOrderNo={}, code={}, msg={}",
                        row.getOrderNo(), row.getRefundOrderNo(), rejected.code(), rejected.msg());
                return null;
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                // 与 Rejected 处置相同但仍各写一支：合并会抹掉「地址没配 / IOException / 非 2xx」这一档的存在，
                // 而那一档的修法是去看配置，不是去看网关码表。
                log.error("退款回查未拿到任何应答（地址未配置或网络不可达），保持 PROCESSING, orderNo={}, refundOrderNo={}",
                        row.getOrderNo(), row.getRefundOrderNo());
                return null;
            }
        }
    }

    /** CAS 收口 + 仅成功时重算汇总，与回调收口（{@link RefundCallbackSettler}）走同一条 SQL。 */
    private boolean settle(AlipayRefundLog row, String settledStatus) {
        String resultMsg = "退款回查收口为 " + settledStatus;
        int affected = refundLogRepository.settleFromCallback(row.getRefundOrderNo(), settledStatus, resultMsg);
        if (affected == 0) {
            log.warn("退款回查收口 CAS 命中 0 行，已被回调或人工收口，本轮不重算汇总, orderNo={}, refundOrderNo={}, 回查状态={}",
                    row.getOrderNo(), row.getRefundOrderNo(), settledStatus);
            return false;
        }
        if (RefundLogRepository.REFUND_STATUS_SUCCESS.equals(settledStatus)) {
            refundLogRepository.refreshSummary(row.getOrderNo(), row.getRefundOrderNo());
        }
        log.info("退款回查已收口明细, orderNo={}, refundOrderNo={}, refundStatus={}",
                row.getOrderNo(), row.getRefundOrderNo(), settledStatus);
        return true;
    }
}
