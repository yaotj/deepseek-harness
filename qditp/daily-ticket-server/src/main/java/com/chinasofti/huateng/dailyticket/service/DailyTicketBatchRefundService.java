package com.chinasofti.huateng.dailyticket.service;

import com.chinasofti.huateng.dailyticket.mapper.DailyTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.mapper.TravelTicketOrderMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketOrder;
import com.chinasofti.huateng.dailyticket.model.TravelTicketOrder;
import com.chinasofti.huateng.dailyticket.service.refund.DailyTicketRefundInitiationService;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketOrderNoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketRefundResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;


@Service
public class DailyTicketBatchRefundService {

    private static final String ORDER_TYPE_DAILY_TICKET = "1";

    private static final String ORDER_TYPE_TRAVEL_TICKET = "2";

    private static final String SUCCESS_CODE = "0000";

    private static final Logger log = LoggerFactory.getLogger(DailyTicketBatchRefundService.class);

    private final DailyTicketOrderMapper orderMapper;

    private final TravelTicketOrderMapper travelOrderMapper;

    private final DailyTicketRefundInitiationService refundInitiationService;

    private final int batchLimit;

    private final int waitDays;

    private final int dailyLookbackDays;

    private final int monthlyLookbackDays;

    public DailyTicketBatchRefundService(DailyTicketOrderMapper orderMapper,
                                        TravelTicketOrderMapper travelOrderMapper,
                                        DailyTicketRefundInitiationService refundInitiationService,
                                        @Value("${daily.batchRefund.limit:200}") int batchLimit,
                                        @Value("${daily.batchRefund.waitDays:3}") int waitDays,
                                        @Value("${daily.batchRefund.daily.lookbackDays:7}") int dailyLookbackDays,
                                        @Value("${daily.batchRefund.monthly.lookbackDays:60}") int monthlyLookbackDays) {
        this.orderMapper = orderMapper;
        this.travelOrderMapper = travelOrderMapper;
        this.refundInitiationService = refundInitiationService;
        this.batchLimit = batchLimit;
        this.waitDays = waitDays;
        this.dailyLookbackDays = dailyLookbackDays;
        this.monthlyLookbackDays = monthlyLookbackDays;
    }

    public BatchRefundResult refundDaily() {
        return refundBatch("多日票批量退款(当日)", dailyLookbackDays);
    }

    public BatchRefundResult refundMonthly() {
        return refundBatch("多日票批量退款(月度)", monthlyLookbackDays);
    }


    private BatchRefundResult refundBatch(String taskName, int lookbackDays) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime paidBefore = now.minusDays(waitDays);
        LocalDateTime paidAfter = now.minusDays(lookbackDays);

        List<DailyTicketOrder> dailyOrders =
                orderMapper.selectPaidNotActivated(paidBefore, paidAfter, batchLimit);
        List<TravelTicketOrder> travelOrders =
                travelOrderMapper.selectPaidNotActivated(paidBefore, paidAfter, batchLimit);

        int dailyCount = dailyOrders == null ? 0 : dailyOrders.size();
        int travelCount = travelOrders == null ? 0 : travelOrders.size();
        int submitted = 0;
        int skipped = 0;
        int failed = 0;

        if (dailyOrders != null) {
            for (DailyTicketOrder order : dailyOrders) {
                try {
                    DailyTicketRefundResult result =
                            refundInitiationService.requestRefundTicket(buildRequest(order.getOrderNo(), ORDER_TYPE_DAILY_TICKET));
                    if (result != null && SUCCESS_CODE.equals(result.getRetCode())) {
                        submitted++;
                    } else {
                        skipped++;
                        log.warn("日票批量退款被拒, task={}, orderNo={}, retCode={}, retMsg={}", taskName,
                                order.getOrderNo(),
                                result == null ? null : result.getRetCode(),
                                result == null ? null : result.getRetMsg());
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.error("日票批量退款异常, task={}, orderNo={}", taskName, order.getOrderNo(), e);
                }
            }
        }

        if (travelOrders != null) {
            for (TravelTicketOrder parent : travelOrders) {
                try {
                    DailyTicketRefundResult result =
                            refundInitiationService.requestRefundTicket(buildRequest(parent.getOrderNo(), ORDER_TYPE_TRAVEL_TICKET));
                    if (result != null && SUCCESS_CODE.equals(result.getRetCode())) {
                        submitted++;
                    } else {
                        skipped++;
                        log.warn("旅游票批量退款被拒, task={}, parentOrderNo={}, retCode={}, retMsg={}", taskName,
                                parent.getOrderNo(),
                                result == null ? null : result.getRetCode(),
                                result == null ? null : result.getRetMsg());
                    }
                } catch (RuntimeException e) {
                    failed++;
                    log.error("旅游票批量退款异常, task={}, parentOrderNo={}", taskName, parent.getOrderNo(), e);
                }
            }
        }

        int scanned = dailyCount + travelCount;
        log.info("多日票批量退款本轮完成, task={}, 候选={}（日票={}, 旅游票主单={}）, 已发起={}, 跳过={}, 异常={},"
                        + " 等待期={}天, 回溯={}天, 单批上限={}",
                taskName, scanned, dailyCount, travelCount, submitted, skipped, failed,
                waitDays, lookbackDays, batchLimit);
        return new BatchRefundResult(scanned, submitted, skipped, failed);
    }

    private static DailyTicketOrderNoReqDTO buildRequest(String orderNo, String orderType) {
        DailyTicketOrderNoReqDTO request = new DailyTicketOrderNoReqDTO();
        request.setOrderNo(orderNo);
        request.setOrderType(orderType);
        return request;
    }


    public record BatchRefundResult(int scanned, int submitted, int skipped, int failed) {
    }
}
