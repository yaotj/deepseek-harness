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

/**
 * 单边订单定时处理入口。
 *
 * <p>NEVER 加回 {@code @Scheduled}，已改由 web-admin {@code sys_job} 触发（job_id 待分配，如 405）。
 *
 * <p>业务规则（按地铁 AFC 规范）：
 * <ul>
 *   <li><b>单边入（ORDER_EXP_TYPE='1'）</b>：进站有记录、出站缺失。乘客已出站但未正常扣费，
 *       系统需自动创建补款单或等待乘客主动补扣。</li>
 *   <li><b>单边出（ORDER_EXP_TYPE='2'）</b>：出站有记录、进站缺失。视为无效行程，
 *       需自动退款或标记为异常待人工处理。</li>
 * </ul>
 *
 * <p>当前实现：仅统计并记录日志，实际处理动作（创建补款单 / 发起退款）待业务确认后扩展。
 */
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

    /**
     * 跑一轮单边订单处理，返回本轮已扫描的笔数。
     *
     * <p>处理逻辑：
     * <ol>
     *   <li>扫出单边入（ORDER_EXP_TYPE='1'）且 DEBIT_STATUS 非终态的订单</li>
     *   <li>扫出单边出（ORDER_EXP_TYPE='2'）且 DEBIT_STATUS 非终态的订单</li>
     *   <li>对每笔订单按业务规则处理（当前仅打日志，后续扩展为创建补款单/退款）</li>
     * </ol>
     */
    public int processOneSideOrders() {
        String endDate = LocalDate.now().format(DATE_FMT);
        String startDate = LocalDate.now().minusDays(lookbackDays).format(DATE_FMT);

        int processed = 0;

        // 处理单边入订单
        processed += processOneSideIn(startDate, endDate);

        // 处理单边出订单
        processed += processOneSideOut(startDate, endDate);

        log.info("单边订单处理完成, startDate={}, endDate={}, processed={}", startDate, endDate, processed);
        return processed;
    }

    /**
     * 处理单边入订单（ORDER_EXP_TYPE='1'）。
     *
     * <p>业务规则：进站有记录、出站缺失。乘客已出站但未正常扣费。
     * 当前实现：标记为需补款状态，后续由补款单系统处理。
     */
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

    /**
     * 处理单边出订单（ORDER_EXP_TYPE='2'）。
     *
     * <p>业务规则：出站有记录、进站缺失。视为无效行程。
     * 当前实现：标记为异常待人工处理，不自动退款（防止误退）。
     */
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

    /** 判断单边入订单是否应被处理。 */
    private boolean shouldProcessOneSideIn(GateTxnPay order) {
        // 只处理 INIT / RETRY / FAIL 状态的单边入订单
        return DebitStatus.isRetryable(order.getDebitStatus())
                || DebitStatus.FAIL.is(order.getDebitStatus());
    }

    /** 判断单边出订单是否应被处理。 */
    private boolean shouldProcessOneSideOut(GateTxnPay order) {
        // 只处理 INIT / RETRY / FAIL 状态的单边出订单
        return DebitStatus.isRetryable(order.getDebitStatus())
                || DebitStatus.FAIL.is(order.getDebitStatus());
    }

    /**
     * 处理单边入订单。
     *
     * <p>当前实现：更新订单备注，标记为「单边入待补款」。
     * 后续扩展：创建补款单（SUPPLEMENT_ORDER），由 face-pay-server 处理。
     */
    private void handleOneSideIn(GateTxnPay order) {
        log.info("处理单边入订单, orderNo={}, cardId={}, thirdUserId={}, totalAmount={}",
                order.getOrderNo(), order.getCardId(), order.getThirdUserId(), order.getTotalAmount());

        // 当前仅标记日志，实际补款单创建待业务确认
        String remark = buildOneSideRemark(order, "单边入订单待补款");
        log.info("单边入订单已标记, orderNo={}, remark={}", order.getOrderNo(), remark);
    }

    /**
     * 处理单边出订单。
     *
     * <p>当前实现：更新订单备注，标记为「单边出异常待处理」。
     * 不自动退款，需人工确认后处理。
     */
    private void handleOneSideOut(GateTxnPay order) {
        log.info("处理单边出订单, orderNo={}, cardId={}, thirdUserId={}, totalAmount={}",
                order.getOrderNo(), order.getCardId(), order.getThirdUserId(), order.getTotalAmount());

        // 当前仅标记日志，实际退款处理待业务确认
        String remark = buildOneSideRemark(order, "单边出异常待处理");
        log.info("单边出订单已标记, orderNo={}, remark={}", order.getOrderNo(), remark);
    }

    /** 构建备注文本。 */
    private String buildOneSideRemark(GateTxnPay order, String action) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(order.getRemark())) {
            sb.append(order.getRemark()).append("; ");
        }
        sb.append(action).append(",").append(LocalDateTime.now());
        return sb.toString();
    }
}
