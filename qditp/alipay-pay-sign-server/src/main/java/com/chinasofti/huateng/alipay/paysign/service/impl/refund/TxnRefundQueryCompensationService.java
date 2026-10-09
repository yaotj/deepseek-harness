package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.domain.PayCenterTradeStatus;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundTxnDetail;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundTxnDetailMapper;
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
 * <b>新表</b>退款回查补偿：把 {@code ALIPAY_REFUND_TXN_DETAIL} 里停在 {@code PROCESSING} 的明细
 * 主动去支付中心问一次并收口。
 *
 * <p><b>补的是哪个缺口</b>：{@link AlipayTxnRefundService} 申请退款后只能落 {@code PROCESSING}
 * （§3.1 请求退款的应答里没有任何结果字段），而新表此前**只有回调这一条收口路径**
 * （{@link TxnRefundCallbackSettler}）。于是只要回调不来 —— 而
 * {@code pay.center.refund-notify-url} 空值时我方压根不送 {@code notifyUrl}、回调必然不来 ——
 * 那一行就**永久卡 `PROCESSING`**，同时申请侧的幂等短路「同一原订单存在 PROCESSING 即拒」
 * 会让**这笔原支付再也退不了**。<b>NEVER 通过放宽那条幂等短路来绕开</b>：那等于允许重复出账。
 *
 * <p><b>与旧表那套（{@link RefundQueryCompensationService}）并存、职责按表正交</b>：
 * 一个 {@code refundOrderNo} 只存在于两张表之一，两个服务各扫自己的表，
 * 由同一个端点（{@code POST /internal/alipay/refund/compensateQuery}）依次驱动。
 * <b>NEVER 合并成一个「同时扫两张表」的实现</b> —— 合并后两张表的退避手段不同（见下）、
 * 收口 SQL 也不同，混在一起没法判「这单走的是哪条链路」。
 *
 * <p><b>与旧表实现的一处实质差异</b>：本表有 {@code NEXT_REQUEST_TIME} / {@code LAST_REQUEST_TIME}
 * / {@code REQUEST_COUNT} 三个退避列，因此未得终态时用
 * {@link AlipayRefundTxnDetailMapper#delayNextRefundQuery} 推时间、下轮不再立刻重问；
 * 旧表 {@code ALIPAY_REFUND_LOG} 没有这些列，只能靠 {@code UPDATE_TIME} 静默期兜。
 * <b>NEVER 照旧表把退避删掉</b>。
 *
 * <p><b>刻意没有 {@code @Scheduled}、也没有 {@code @Transactional}</b>：补偿一律由 web-admin 的
 * Quartz 打 {@code /internal/**} 驱动（AGENTS.md §2.2.1），而方法体是「扫一批 → 逐条出网 → 逐条 CAS 回写」，
 * 出网 NEVER 被事务包住（§5.2 那条 2026-08-26 生产事故）。
 *
 * <p><b>收口复用 {@link TxnRefundCallbackSettler}</b>（明细 CAS + 仅成功时重算汇总只有一份实现）。
 * 连带后果：那个类的日志前缀写的是「退款回调」，回查路径下看着像回调收的 ——
 * <b>判来源看 {@code REMARK}</b>（本路径写「退款回查收口为 X」）。
 * <b>NEVER 为了日志好看在这里抄第二份收口 SQL</b>。
 */
@Service
public class TxnRefundQueryCompensationService {

    private static final Logger log = LoggerFactory.getLogger(TxnRefundQueryCompensationService.class);

    /** 支付中心业务成功的 {@code retCode}，与退款申请侧同一个判据。 */
    private static final String PAY_CENTER_SUCCESS = "SUCCESS";

    /** 只回查最近 N 天创建的退款：更早的行已过人工核对窗口，继续每轮打对端没有意义。 */
    private static final int SCAN_DAYS = 7;

    /**
     * 距上次状态变化至少多少分钟才回查。
     *
     * <p><b>驱动侧 cron 的间隔 MUST 大于这个值</b>，否则同一行会在正常回调还没到达时就被反复回查。
     */
    private static final int STALE_MINUTES = 5;

    /** 未得终态时的退避秒数，与静默期同量级；与 pay-sign 侧 {@code REFUND_QUERY_BACKOFF} 一致。 */
    private static final int BACKOFF_SECONDS = 300;

    private final AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper;
    private final TxnRefundCallbackSettler txnRefundCallbackSettler;
    private final PayCenterPort payCenterPort;

    /** 单轮取多少行。上限在 SQL 的 {@code ROWNUM} 里，不是在 Java 里截断。 */
    @Value("${alipay.refund-query.batch-size:200}")
    private int batchSize;

    public TxnRefundQueryCompensationService(AlipayRefundTxnDetailMapper alipayRefundTxnDetailMapper,
                                            TxnRefundCallbackSettler txnRefundCallbackSettler,
                                            PayCenterPort payCenterPort) {
        this.alipayRefundTxnDetailMapper = alipayRefundTxnDetailMapper;
        this.txnRefundCallbackSettler = txnRefundCallbackSettler;
        this.payCenterPort = payCenterPort;
    }

    /**
     * 扫一批未收口的新表退款明细并逐条回查。
     *
     * @return 本轮扫描结果，不变量 {@code scanned == success + failed}
     */
    public OutboxScan.Result compensate() {
        List<AlipayRefundTxnDetail> pending =
                alipayRefundTxnDetailMapper.selectCompensableRefundQuery(SCAN_DAYS, STALE_MINUTES, batchSize);
        if (pending == null || pending.isEmpty()) {
            log.info("新表退款回查补偿：本轮无待处理行, scanDays={}, staleMinutes={}, batchSize={}",
                    SCAN_DAYS, STALE_MINUTES, batchSize);
            return new OutboxScan.Result(0, 0, 0);
        }
        OutboxScan.Result scan = OutboxScan.run(pending,
                this::settleByQuery,
                row -> log.warn("新表退款回查本轮未收口，已退避 {} 秒等下次重扫, orderNo={}, refundOrderNo={}",
                        BACKOFF_SECONDS, row.getOrderNo(), row.getRefundOrderNo()),
                (row, e) -> log.error("单条新表退款回查异常，NEVER 因此中断整批, orderNo={}, refundOrderNo={}",
                        row.getOrderNo(), row.getRefundOrderNo(), e));
        log.info("新表退款回查补偿完成, scanned={}, settled={}, pendingAgain={}",
                scan.scanned(), scan.success(), scan.failed());
        return scan;
    }

    /**
     * 回查一笔并尝试收口。
     *
     * @return 仅当「支付中心给出终态」且「CAS 真的推进了这一行」才 {@code true}
     */
    private boolean settleByQuery(AlipayRefundTxnDetail row) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        // 两个键都送：ADR-D92 实测只送 refundOrderNo 时网关返 9999「退款流水号或商户退款流水号必填」，
        // 表现为退款单永久空转而端点每轮返 0000、调度日志一片绿。NEVER 只送一个。
        bizData.put("refundOrderNo", row.getRefundOrderNo());
        bizData.put("merchantRefundNo", row.getRefundOrderNo());

        PayCenterReply reply = payCenterPort.refundQuery(bizData);
        log.info("新表退款回查支付中心返回, orderNo={}, refundOrderNo={}, code={}, success={}, msg={}",
                row.getOrderNo(), row.getRefundOrderNo(), reply.code(), reply.success(), reply.msg());

        String settledStatus = resolveSettledStatus(row, reply);
        if (settledStatus == null) {
            delayNextQuery(row);
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
    private String resolveSettledStatus(AlipayRefundTxnDetail row, PayCenterReply reply) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                if (!PAY_CENTER_SUCCESS.equals(accepted.retCode())) {
                    // 这一支是 ADR-D92 那类「参数/契约被拒」的唯一可见处：MUST 打 ERROR。
                    // 只打 WARN 会让「每轮都被拒」在日志里淹没，而端点照样返 0000。
                    log.error("新表退款回查业务应答非成功，本轮不收口，MUST 人工核对出网参数与网关码表, orderNo={}, refundOrderNo={}, retCode={}, retMsg={}",
                            row.getOrderNo(), row.getRefundOrderNo(), accepted.retCode(), accepted.retMsg());
                    return null;
                }
                String status = accepted.field("status");
                // 值域复用全模块唯一那份归一（PayCenterTradeStatus）：供方 §3.2 同样只写了「status 退款状态」
                // 而没给值域。拿到第一条真实应答后 MUST 回来核对；若退款侧确有额外取值（如 REFUNDED），
                // MUST 加进 PayCenterTradeStatus，NEVER 在本类抄第二份白名单。
                String normalized = PayCenterTradeStatus.normalize(status);
                if (normalized == null) {
                    log.info("新表退款回查未得终态，保持 PROCESSING, orderNo={}, refundOrderNo={}, status={}",
                            row.getOrderNo(), row.getRefundOrderNo(), status);
                }
                return normalized;
            }
            case PayCenterReply.Rejected rejected -> {
                log.error("新表退款回查未拿到业务应答（网关拒绝），保持 PROCESSING, orderNo={}, refundOrderNo={}, code={}, msg={}",
                        row.getOrderNo(), row.getRefundOrderNo(), rejected.code(), rejected.msg());
                return null;
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                // 与 Rejected 处置相同但仍各写一支：合并会抹掉「地址没配 / IOException / 非 2xx」这一档的存在，
                // 而那一档的修法是去看配置，不是去看网关码表。
                log.error("新表退款回查未拿到任何应答（地址未配置或网络不可达），保持 PROCESSING, orderNo={}, refundOrderNo={}",
                        row.getOrderNo(), row.getRefundOrderNo());
                return null;
            }
        }
    }

    /** CAS 收口 + 仅成功时重算汇总，与回调收口走同一条 SQL（{@link TxnRefundCallbackSettler}）。 */
    private boolean settle(AlipayRefundTxnDetail row, String settledStatus) {
        RefundCallbackSettler.Outcome outcome = txnRefundCallbackSettler.settle(row.getOrderNo(),
                row.getRefundOrderNo(), settledStatus, "退款回查收口为 " + settledStatus);
        return outcome == RefundCallbackSettler.Outcome.SETTLED_SUCCESS
                || outcome == RefundCallbackSettler.Outcome.SETTLED_FAIL;
    }

    /**
     * 未得终态时推后下次回查时间。
     *
     * <p>影响 0 行只记 INFO：那说明这一行期间已被回调收口，不是异常。
     */
    private void delayNextQuery(AlipayRefundTxnDetail row) {
        int affected = alipayRefundTxnDetailMapper.delayNextRefundQuery(row.getRefundOrderNo(), BACKOFF_SECONDS);
        if (affected == 0) {
            log.info("新表退款回查退避未命中处理中明细，判定期间已被收口, orderNo={}, refundOrderNo={}",
                    row.getOrderNo(), row.getRefundOrderNo());
        }
    }
}
