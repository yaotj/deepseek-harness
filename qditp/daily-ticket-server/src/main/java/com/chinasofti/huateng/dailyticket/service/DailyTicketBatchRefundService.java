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

/**
 * 多日票批量退款（甲方需求 16 当日 / 17 月度）：把「已支付、过了等待期、仍未激活」的票逐笔发起退款。
 *
 * <p>候选判据统一为：订单 {@code ORDER_STATUS='PAID'} + {@code PAY_STATUS='PAID'}，
 * 且 {@code DAILY_TICKET_INSTANCE} 里没有同 {@code ORDER_NO} 的行（票实例首次落库即 {@code ACTIVATED}，
 * 订单表没有任何激活状态列，这是唯一判法），且 {@code PAY_DATE <= 当前时间 - waitDays}。
 *
 * <p>两条任务只差回溯窗口（当日 7 天 / 月度 60 天），谓词有重叠，靠幂等兼容：
 * {@code UK_DAILY_TICKET_REFUND_ORDER (ORDER_NO)} + {@code requestRefundTicket} 内的
 * {@code refundMapper.selectByOrderNo} 前置短路。
 *
 * <p>旅游票纳入但**按主单整单退**：主单候选只保证「全部子单无票实例」，子单是否已使用 / 已退款的校验
 * 由 {@code DailyTicketServiceImpl.requestTravelRefund} 内建，**NEVER 在本类再写一份子单校验**。
 *
 * <p>本类只负责扫表 + 逐笔委派 {@link DailyTicketRefundInitiationService#requestRefundTicket}，
 * 退款网关调用、{@code refundType} 判定、锁票与状态回写全在那边，**NEVER 在此复制一份退款逻辑**。
 *
 * <p>本类刻意不带 {@code @Transactional}（每笔都要调支付网关），NEVER 加。
 */
@Service
public class DailyTicketBatchRefundService {

    /** 独立日票订单类型，与 APP 退款接口口径一致。 */
    private static final String ORDER_TYPE_DAILY_TICKET = "1";

    /** 旅游票主单订单类型，内部自动走 requestTravelRefund。 */
    private static final String ORDER_TYPE_TRAVEL_TICKET = "2";

    private static final String SUCCESS_CODE = "0000";

    private static final Logger log = LoggerFactory.getLogger(DailyTicketBatchRefundService.class);

    private final DailyTicketOrderMapper orderMapper;

    private final TravelTicketOrderMapper travelOrderMapper;

    private final DailyTicketRefundInitiationService refundInitiationService;

    /** 单次最多处理多少笔，防止一次调度打满支付网关。 */
    private final int batchLimit;

    /** 支付完成后多少天仍未激活才认定「用户不会用了」，短于它的单可能还在正常流程中。 */
    private final int waitDays;

    /** 当日任务的回溯天数。 */
    private final int dailyLookbackDays;

    /** 月度任务的回溯天数。 */
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

    /** 甲方需求 16：每天 20 点跑一次，只回溯近 {@code daily.batchRefund.daily.lookbackDays} 天。 */
    public BatchRefundResult refundDaily() {
        return refundBatch("多日票批量退款(当日)", dailyLookbackDays);
    }

    /** 甲方需求 17：每月最后一天 20 点跑一次，回溯 {@code daily.batchRefund.monthly.lookbackDays} 天兜住漏网单。 */
    public BatchRefundResult refundMonthly() {
        return refundBatch("多日票批量退款(月度)", monthlyLookbackDays);
    }

    /**
     * 跑一批：先扫独立日票、再扫旅游票主单，各自受 {@code limit} 约束。
     *
     * @param taskName     任务名，只用于日志区分当日 / 月度
     * @param lookbackDays 本任务的回溯天数
     * @return 本轮统计，供端点回给 web-admin
     */
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

    /**
     * 一轮批量退款的统计。
     *
     * @param scanned   本轮扫出的候选笔数（日票 + 旅游票主单）；某一类等于 {@code limit} 说明可能还有未处理完的，
     *                  下轮继续
     * @param submitted 已发起退款（含幂等命中已有退款单）
     * @param skipped   被 {@code requestRefundTicket} 拒绝（{@code retCode} 非 {@code 0000}），需人工看日志
     * @param failed    抛异常的笔数，留给下一轮重扫
     */
    public record BatchRefundResult(int scanned, int submitted, int skipped, int failed) {
    }
}
