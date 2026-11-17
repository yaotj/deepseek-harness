package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.constant.DebitStatus;
import com.chinasofti.huateng.gatetxnpay.constant.OrderExpType;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;


@Service
public class OneSideOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(OneSideOrderProcessor.class);

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_LOOKBACK_DAYS = 30;

    private final GateTxnPayMapper gateTxnPayMapper;
    private final int batchSize;
    private final int lookbackDays;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.BASIC_ISO_DATE;

    public OneSideOrderProcessor(GateTxnPayMapper gateTxnPayMapper,
                                 @Value("${gate.oneside.batch-size:100}") int batchSize,
                                 @Value("${gate.oneside.lookback-days:30}") int lookbackDays) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
        this.lookbackDays = lookbackDays > 0 ? lookbackDays : DEFAULT_LOOKBACK_DAYS;
    }


    public int processOneSideOrders() {
        String endDate = LocalDate.now().format(DATE_FMT);
        String startDate = LocalDate.now().minusDays(lookbackDays).format(DATE_FMT);

        int processed = 0;

        processed += processOneSideIn(startDate, endDate);

        processed += processOneSideOut(startDate, endDate);

        log.info("单边订单处理完成, startDate={}, endDate={}, processed={}", startDate, endDate, processed);
        return processed;
    }


    private int processOneSideIn(String startDate, String endDate) {
        List<GateTxnPay> orders = gateTxnPayMapper.selectOneSideInCandidates(startDate, endDate, batchSize);
        if (orders == null || orders.isEmpty()) {
            return 0;
        }

        int handled = 0;
        for (GateTxnPay order : orders) {
            try {
                if (shouldProcessOneSideIn(order)) {
                    handleOneSideIn(order);
                    handled++;
                }
            } catch (Exception e) {
                log.error("单边入订单处理异常, orderNo={}, cardId={}", order.getOrderNo(), order.getCardId(), e);
            }
        }
        return handled;
    }


    private int processOneSideOut(String startDate, String endDate) {
        List<GateTxnPay> orders = gateTxnPayMapper.selectOneSideOutCandidates(startDate, endDate, batchSize);
        if (orders == null || orders.isEmpty()) {
            return 0;
        }

        int handled = 0;
        for (GateTxnPay order : orders) {
            try {
                if (shouldProcessOneSideOut(order)) {
                    handleOneSideOut(order);
                    handled++;
                }
            } catch (Exception e) {
                log.error("单边出订单处理异常, orderNo={}, cardId={}", order.getOrderNo(), order.getCardId(), e);
            }
        }
        return handled;
    }

    private boolean shouldProcessOneSideIn(GateTxnPay order) {
        return DebitStatus.isRetryable(order.getDebitStatus())
                || DebitStatus.FAIL.is(order.getDebitStatus());
    }

    private boolean shouldProcessOneSideOut(GateTxnPay order) {
        return DebitStatus.isRetryable(order.getDebitStatus())
                || DebitStatus.FAIL.is(order.getDebitStatus());
    }


    private void handleOneSideIn(GateTxnPay order) {
        log.info("处理单边入订单, orderNo={}, cardId={}, thirdUserId={}, totalAmount={}",
                order.getOrderNo(), order.getCardId(), order.getThirdUserId(), order.getTotalAmount());

        String remark = buildOneSideRemark(order, "单边入订单待补款");
        log.info("单边入订单已标记, orderNo={}, remark={}", order.getOrderNo(), remark);
    }


    private void handleOneSideOut(GateTxnPay order) {
        log.info("处理单边出订单, orderNo={}, cardId={}, thirdUserId={}, totalAmount={}",
                order.getOrderNo(), order.getCardId(), order.getThirdUserId(), order.getTotalAmount());

        String remark = buildOneSideRemark(order, "单边出异常待处理");
        log.info("单边出订单已标记, orderNo={}, remark={}", order.getOrderNo(), remark);
    }

    private String buildOneSideRemark(GateTxnPay order, String action) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(order.getRemark())) {
            sb.append(order.getRemark()).append("; ");
        }
        sb.append(action).append(",").append(LocalDateTime.now());
        return sb.toString();
    }
}
