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

/** 离线码金额补偿：扫待重算行 → 重算 → 抢占 → 委托扣款。 */
@Service
public class OfflineFareRecoveryServiceImpl implements OfflineFareRecoveryService {
    private static final Logger log = LoggerFactory.getLogger(OfflineFareRecoveryServiceImpl.class);

    private final GateTxnPayMapper gateTxnPayMapper;
    private final GateTxnPayWriter gateTxnPayWriter;
    private final FareCalculator fareCalculator;
    private final PaySignInitiator paySignInitiator;
    private final MetroTransferPushTaskProcessor metroTransferPushTaskProcessor;
    /** 站名回填：本类的 {@code calculateOfflineFare} 会把 {@code IN_STATION} 从 NULL / 占位值 改成重查到的真实进站码。 */
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
        OutboxScan.Result scan = OutboxScan.run(pendingOrders,
                this::recoverSingleOfflineFareOrder,
                pending -> { },
                (pending, e) -> log.error("单笔离线码金额补偿异常，跳过该笔继续本批, orderNo={}, txnDate={}",
                        pending.getOrderNo(), pending.getTxnDate(), e));
        log.info("离线码金额补偿本轮结束, 扫描区间={}~{}, 待重算={}, 已推进={}",
                startDate, endDate, scan.scanned(), scan.success());
        return scan.success();
    }

    /** 单笔补偿：重算 → 抢占式回写 → 发起扣款。 */
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

    /** 从订单行重建离线码算价所需的请求上下文。 */
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
