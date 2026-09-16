package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.entity.MetroTransferPushTask;
import com.chinasofti.huateng.gatetxnpay.fare.FareCalculator;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.paysign.PaySignInitiator;
import com.chinasofti.huateng.gatetxnpay.service.OfflineFareRecoveryService;
import com.chinasofti.huateng.gatetxnpay.station.StationNameBackfiller;
import com.chinasofti.huateng.gatetxnpay.writer.GateTxnPayWriter;
import com.chinasofti.huateng.model.domain.OutboxScan;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 离线码金额补偿：扫待重算行 → 重算 → 抢占 → 委托扣款。
 *
 * <p>补偿的对象是「出站当时算不出价、只留了痕」的行：{@code DEBIT_STATUS='INIT'} +
 * {@code TOTAL_AMOUNT=0} + {@code DISCOUNT_CALC_STATUS='OFFLINE_FARE_PENDING'}。
 * 它**不是免扣费交易**，这是本类全部 NEVER 的来源：</p>
 * <ul>
 *   <li>重算仍失败 → 只标回待重算，**NEVER** 置 FAIL（置了补偿再也捞不到它）；</li>
 *   <li>重算出 0 元 → 同上，**NEVER** 按 0 元收口成 SUCCESS（账面正常、车费永久收不回来）；</li>
 *   <li>抢占（{@code applyOfflineFareRecalculated} 返回 1）之前 **NEVER** 扣款，否则多副本重复扣；</li>
 *   <li>单笔异常 **NEVER** 冲出批次，本轮剩下的每一行都是资损口。</li>
 * </ul>
 *
 * <p>本类 **NEVER** 加 {@code @Transactional}：内部要调 pay-sign（AGENTS.md §5.2 事务内禁 RPC）。
 * 「落待重算痕」那一步不在这里 —— 它属于出站首次落单，留在 {@code GateTxnPayServiceImpl}
 * 的 {@code saveOfflineFarePendingOrder}；本类只负责把痕**推进**掉。</p>
 */
@Service
public class OfflineFareRecoveryServiceImpl implements OfflineFareRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(OfflineFareRecoveryServiceImpl.class);

    private final GateTxnPayMapper gateTxnPayMapper;
    private final GateTxnPayWriter gateTxnPayWriter;
    private final FareCalculator fareCalculator;
    private final PaySignInitiator paySignInitiator;
    private final MetroTransferPushTaskProcessor metroTransferPushTaskProcessor;
    /**
     * 站名回填：本类的 {@code calculateOfflineFare} 会把 {@code IN_STATION} 从 NULL / 占位值
     * 改成重查到的真实进站码，因此**同一步 MUST 把两个中文站名列一起改**。
     * 缺这一步时列表侧站名永久为空、只能回落显示编码（成因见 {@link StationNameBackfiller}）。
     */
    private final StationNameBackfiller stationNameBackfiller;

    public OfflineFareRecoveryServiceImpl(GateTxnPayMapper gateTxnPayMapper,
                                          GateTxnPayWriter gateTxnPayWriter,
                                          FareCalculator fareCalculator,
                                          PaySignInitiator paySignInitiator,
                                          MetroTransferPushTaskProcessor metroTransferPushTaskProcessor,
                                          StationNameBackfiller stationNameBackfiller) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.gateTxnPayWriter = gateTxnPayWriter;
        this.fareCalculator = fareCalculator;
        this.paySignInitiator = paySignInitiator;
        this.metroTransferPushTaskProcessor = metroTransferPushTaskProcessor;
        this.stationNameBackfiller = stationNameBackfiller;
    }

    @Override
    public int recoverOfflineFarePendingOrders(int limit, int lookbackDays) {
        int batchSize = limit <= 0 ? 50 : Math.min(limit, 200);
        int days = lookbackDays <= 0 ? 7 : lookbackDays;
        LocalDate today = LocalDate.now();
        String endDate = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        String startDate = today.minusDays(days).format(DateTimeFormatter.BASIC_ISO_DATE);
        List<GateTxnPay> pendingOrders = gateTxnPayMapper.selectOfflineFarePending(startDate, endDate, batchSize);
        if (pendingOrders.isEmpty()) {
            return 0;
        }
        // 循环骨架统一走 model.domain.OutboxScan，它把三条不变量固化下来、不再依赖每个补偿任务
        // 各自记得写 try/catch：单条失败 NEVER 中断整批、每行只计一次、投递方法抛异常时外层兜一层。
        // **NEVER 退回裸 for** —— 漏一个 catch，markOfflineFarePending /
        // applyOfflineFareRecalculated / asyncPaySign 任一抛异常都会冲出循环，
        // 本轮剩余待重算订单全部不处理，而它们是资损口（TOTAL_AMOUNT=0 却不是免扣费交易）。
        //
        // 语义映射：{@code deliver} 返回 false 表示「本轮没推进」（没抢到 / 重算仍为 0），
        // 不是投递失败，该落的痕已由 recoverSingleOfflineFareOrder 自己落完，因此 onFailure 无事可做。
        OutboxScan.Result scan = OutboxScan.run(pendingOrders,
                this::recoverSingleOfflineFareOrder,
                pending -> { },
                (pending, e) -> log.error("单笔离线码金额补偿异常，跳过该笔继续本批, orderNo={}, txnDate={}",
                        pending.getOrderNo(), pending.getTxnDate(), e));
        log.info("离线码金额补偿本轮结束, 扫描区间={}~{}, 待重算={}, 已推进={}",
                startDate, endDate, scan.scanned(), scan.success());
        return scan.success();
    }

    /**
     * 单笔补偿：重算 → 抢占式回写 → 发起扣款。
     *
     * <p>顺序不可调换：{@code applyOfflineFareRecalculated} 返回 1 才代表本副本抢到该笔，
     * 只有此时才允许调 pay-sign，**NEVER** 先扣款后回写，也 **NEVER** 忽略返回值。</p>
     */
    private boolean recoverSingleOfflineFareOrder(GateTxnPay order) {
        GateTxnPayReqDTO request = rebuildOfflineRequest(order);
        try {
            fareCalculator.calculateOfflineFare(order, request);
        } catch (RuntimeException e) {
            String reason = truncate("离线码金额重算仍失败："
                    + (StringUtils.hasText(e.getMessage()) ? e.getMessage() : e.getClass().getSimpleName()), 500);
            gateTxnPayWriter.markOfflineFarePending(order.getOrderNo(), order.getTxnDate(), reason);
            log.warn("离线码金额重算失败，保持待重算态等下一轮, orderNo={}", order.getOrderNo(), e);
            return false;
        }
        order.setTotalAmount((order.getTrxAmount() == null ? 0 : order.getTrxAmount())
                + (order.getOvertimeAmount() == null ? 0 : order.getOvertimeAmount()));
        if (order.getTotalAmount() <= 0) {
            gateTxnPayWriter.markOfflineFarePending(order.getOrderNo(), order.getTxnDate(),
                    "离线码重算金额为 0，疑似票价参数异常，保持待重算态待人工核查");
            log.error("离线码重算金额为 0，NEVER 按 0 金额收口成 SUCCESS, orderNo={}, inStation={}, outStation={}",
                    order.getOrderNo(), order.getInStation(), order.getOutStation());
            return false;
        }
        // 站名回填 MUST 夹在「重算已把 IN_STATION 改对」与「抢占写库」之间：
        // updateOfflineFareRecalculated 会连同两个站名列一起 SET，放在 apply 之后就写不进去了。
        stationNameBackfiller.backfill(order);
        if (gateTxnPayWriter.applyOfflineFareRecalculated(order) != 1) {
            return false;
        }
        MetroTransferPushTask task = metroTransferPushTaskProcessor.buildMetroTransferPushTask(order);
        if (task != null) {
            task.setOrderNo(order.getOrderNo());
            try {
                gateTxnPayWriter.createMetroTransferPushTask(task);
            } catch (RuntimeException e) {
                log.warn("补偿链路建公交换乘推送任务失败，不影响扣款, orderNo={}", order.getOrderNo(), e);
            }
        }
        log.info("离线码金额重算成功，发起扣款, orderNo={}, totalAmount={}, originalFare={}, transferFlag={}",
                order.getOrderNo(), order.getTotalAmount(), order.getOriginalFare(), order.getTransferFlag());
        paySignInitiator.initiateAsync(order, request);
        return true;
    }

    /**
     * 从订单行重建离线码算价所需的请求上下文。
     *
     * <p>钱包判定依赖 {@code PAYMENT_VENDOR}，该列在下单时已持久化，因此补偿时的换乘减免与
     * 钱包折扣口径与出站当时一致。**NEVER** 改成只按订单号重试而不重建上下文——
     * {@code calculateOfflineFare} 读的是 request 的 {@code cardId} / {@code ticketTransSeq} /
     * {@code paymentVendor}。{@code requestSignSeq} 无法从订单行恢复，与 {@link #retryPay}
     * 传 {@code null} 的现状一致，由 pay-sign 侧回查 account 兜底。</p>
     */
    private GateTxnPayReqDTO rebuildOfflineRequest(GateTxnPay order) {
        GateTxnPayReqDTO request = new GateTxnPayReqDTO();
        request.setItpUserId(order.getThirdUserId());
        request.setCardId(order.getCardId());
        request.setCardType(order.getCardType());
        request.setDeviceId(order.getDeviceId());
        request.setTrxType(order.getTrxType());
        request.setTicketTransSeq(order.getTicketTransSeq());
        request.setHandleStationCode(order.getOutStation());
        request.setHandleDateTime(order.getOutTime());
        request.setLastHandleStationCode(order.getInStation());
        request.setLastHandleDateTime(order.getInTime());
        request.setIssueChannelCode(order.getIssueChannelCode());
        request.setSignChannelCode(order.getSignChannelCode());
        request.setOfflineFlag(order.getOfflineFlag());
        request.setCompanionFlag(order.getCompanionFlag());
        request.setPaymentVendor(order.getPaymentVendor());
        request.setPayUserId(order.getPayUserId());
        return request;
    }

    private String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
