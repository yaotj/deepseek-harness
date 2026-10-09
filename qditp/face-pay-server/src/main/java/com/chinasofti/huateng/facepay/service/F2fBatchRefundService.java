package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 每日批量退款：把「已支付、过了静默期、仍停在 {@code PAID} 未履约」的订单逐笔发起退款。
 *
 * <p>三类业务（单程票购票 / TVM 充值 / BOM 非现金收款）共用本类，只靠 {@code bizTypes} 区分扫哪一批，
 * 退款来源统一为 {@link F2fRefundService#SOURCE_DAILY_BATCH}：{@code UK_F2F_REFUND_IDEM} 的三要素里已含
 * {@code REFUND_SOURCE}，而一笔订单只属于一个 {@code BIZ_TYPE}，因此**不需要**为三类各起一个来源值，
 * 也就不需要动 {@code CK_F2F_REFUND_SOURCE}。要按类统计走 {@code F2F_ORDER.BIZ_TYPE} 关联。
 *
 * <p>本类刻意不带 {@code @Transactional}（每笔都要调支付中心），NEVER 加。
 */
@Service
public class F2fBatchRefundService {

    /** 单程票购票。 */
    public static final List<String> BIZ_SINGLE_TICKET = List.of("01");

    /** 充值。 */
    public static final List<String> BIZ_TOPUP = List.of("02");

    /** 非现金收款。 */
    public static final List<String> BIZ_NO_CASH = List.of("04");

    private static final Logger log = LoggerFactory.getLogger(F2fBatchRefundService.class);

    private final F2fOrderMapper orderMapper;

    private final F2fRefundService refundService;

    /** 单次最多处理多少笔，防止一次调度打满支付中心。 */
    private final int batchLimit;

    /** 支付完成后多少分钟仍未履约才认定「未取票 / 未到账」，短于它的单可能还在正常流程中。 */
    private final int silenceMinutes;

    /** 只回溯多少天，避免每天把几个月前的死单反复捞出来重试。 */
    private final int lookbackDays;

    public F2fBatchRefundService(F2fOrderMapper orderMapper, F2fRefundService refundService,
                                 @Value("${f2f.batchRefund.limit:200}") int batchLimit,
                                 @Value("${f2f.batchRefund.silenceMinutes:60}") int silenceMinutes,
                                 @Value("${f2f.batchRefund.lookbackDays:7}") int lookbackDays) {
        this.orderMapper = orderMapper;
        this.refundService = refundService;
        this.batchLimit = batchLimit;
        this.silenceMinutes = silenceMinutes;
        this.lookbackDays = lookbackDays;
    }

    /**
     * 跑一批。
     *
     * @param taskName 任务名，只用于日志区分三类
     * @param bizTypes 本任务负责的业务类型，取本类三个常量之一
     * @return 本轮统计，供端点回给 web-admin
     */
    public BatchRefundResult refundBatch(String taskName, List<String> bizTypes) {
        LocalDateTime now = LocalDateTime.now();
        List<F2fOrder> candidates = orderMapper.selectPaidNotFulfilled(bizTypes,
                now.minusDays(lookbackDays), now.minusMinutes(silenceMinutes), batchLimit);
        if (candidates.isEmpty()) {
            log.info("批量退款本轮无候选, task={}, bizTypes={}", taskName, bizTypes);
            return new BatchRefundResult(0, 0, 0, 0);
        }

        int submitted = 0;
        int skipped = 0;
        int failed = 0;
        for (F2fOrder order : candidates) {
            Long amount = order.getOrderAmount();
            if (amount == null || amount <= 0L) {
                skipped++;
                log.warn("订单金额非正，跳过批量退款, task={}, orderNo={}, amount={}",
                        taskName, order.getOrderNo(), amount);
                continue;
            }
            try {
                RefundOutcome outcome = refundService.refund(new RefundCommand(order.getOrderNo(), null,
                        F2fRefundService.SOURCE_DAILY_BATCH, amount, order.getTicketNum(),
                        "每日批量退未履约交易", null, null, order.getTransType(), null, null));
                if (outcome.isRejected()) {
                    skipped++;
                    log.warn("批量退款被拒, task={}, orderNo={}, outcome={}",
                            taskName, order.getOrderNo(), outcome);
                } else {
                    submitted++;
                }
            } catch (RuntimeException e) {
                failed++;
                log.error("批量退款异常, task={}, orderNo={}", taskName, order.getOrderNo(), e);
            }
        }
        log.info("批量退款本轮完成, task={}, 候选={}, 已发起={}, 跳过={}, 异常={}, 静默期={}分钟, 回溯={}天",
                taskName, candidates.size(), submitted, skipped, failed, silenceMinutes, lookbackDays);
        return new BatchRefundResult(candidates.size(), submitted, skipped, failed);
    }

    /**
     * 一轮批量退款的统计。
     *
     * @param scanned   本轮扫出的候选笔数；等于 {@code limit} 说明可能还有未处理完的，下轮继续
     * @param submitted 已向支付中心发起（含幂等命中已有退款单）
     * @param skipped   参数非法或被 {@code validate} 拒绝，需人工看日志
     * @param failed    抛异常的笔数，留给下一轮重扫
     */
    public record BatchRefundResult(int scanned, int submitted, int skipped, int failed) {
    }
}
