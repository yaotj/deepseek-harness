package com.chinasofti.huateng.gatetxnpay.notify;

import com.chinasofti.huateng.gatetxnpay.constant.DebitStatus;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 行程订单通知门面：只把「收敛到终态」的订单交给 {@link OrderPayNoticeNotifier}，异步执行，绝不阻塞扣费主链路。
 *
 * <p><b>触发边界（NEVER 放松）</b>：仅 {@code SUCCESS} / {@code FAIL} 两个终态推送；
 * {@code INIT} / {@code PROCESSING} / {@code RETRY} 是过程态，推了会让 APP 收到同一订单的多条中间结果。</p>
 *
 * <p><b>为什么保留退避重试</b>：2026-11-20 测试环境曾观察到「首推返 {@code 7004}、隔 5 秒重推即返 {@code 0000}」，
 * 当时的成因判断（撞上 APP 处理支付中心结果的并发窗口）**已被推翻** —— 两次投递的报文逐字相同，不可能靠时间自愈。
 * 真实根因是站时间传了 8 位 {@code yyyyMMdd}（APP 要求 14 位 {@code yyyyMMddHHmmss}），APP 解析异常落兜底码 7004；
 * 而重推的 {@code 0000} 是 APP 侧首次已登记订单后的**幂等短路**，<b>不代表首次已被正确处理</b>
 * （详见 {@link OrderPayNoticeNotifier} 的格式约定与 IT-088）。</p>
 *
 * <p>因此重试只作为<b>对端临时故障</b>的兜底：一次投递未受理就退避重投，避免通知静默丢失。
 * <b>判定「修复是否生效」的判据是「首推即 {@code 0000}」</b>，NEVER 用重试成功当验收证据。</p>
 */
@Service
public class GateTxnPayNoticeService {

    private static final Logger log = LoggerFactory.getLogger(GateTxnPayNoticeService.class);

    private final Executor appNotifyExecutor;
    private final ScheduledExecutorService appNotifyScheduler;
    private final OrderPayNoticeNotifier orderPayNoticeNotifier;
    private final GateTxnPayMapper gateTxnPayMapper;
    private final long[] retryDelaysMs;

    public GateTxnPayNoticeService(
            @Qualifier("appNotifyExecutor") Executor appNotifyExecutor,
            @Qualifier("appNotifyScheduler") ScheduledExecutorService appNotifyScheduler,
            OrderPayNoticeNotifier orderPayNoticeNotifier,
            GateTxnPayMapper gateTxnPayMapper,
            @Value("${app.notify.order-pay-notice-retry-delays-ms:5000,15000,30000}") String retryDelays) {
        this.appNotifyExecutor = appNotifyExecutor;
        this.appNotifyScheduler = appNotifyScheduler;
        this.orderPayNoticeNotifier = orderPayNoticeNotifier;
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.retryDelaysMs = parseRetryDelays(retryDelays);
    }

    public void notifySettled(GateTxnPay order, String debitStatus, String scene) {
        if (order == null || order.getOrderNo() == null) {
            log.warn("行程订单通知跳过：订单为空, scene={}, debitStatus={}", scene, debitStatus);
            return;
        }
        if (!isSettled(debitStatus)) {
            log.debug("行程订单通知跳过：非终态, scene={}, orderNo={}, debitStatus={}",
                    scene, order.getOrderNo(), debitStatus);
            return;
        }
        try {
            appNotifyExecutor.execute(() -> pushWithRetry(order, debitStatus, 0));
        } catch (RuntimeException e) {
            log.error("行程订单通知任务提交失败, scene={}, orderNo={}, debitStatus={}",
                    scene, order.getOrderNo(), debitStatus, e);
        }
    }

    /**
     * 运维补偿：按订单号重推一次（历史单漏推、对端故障后补发）。返回 {@code null} 表示对端已受理，否则为失败原因。
     */
    public String repush(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return "orderNo不能为空";
        }
        String key = orderNo.trim();
        GateTxnPay order = gateTxnPayMapper.selectByOrderNo(key);
        if (order == null) {
            return "未找到过闸扣费订单：" + key;
        }
        String debitStatus = order.getDebitStatus();
        if (!isSettled(debitStatus)) {
            return "订单非终态，不推送：" + debitStatus;
        }
        log.info("行程订单通知补偿重推, orderNo={}, debitStatus={}", key, debitStatus);
        return orderPayNoticeNotifier.push(order, debitStatus) ? null : "对端未受理，详见应用日志";
    }

    /**
     * 第 {@code attempt} 次（0 起）推送；失败且有剩余重试额度时按退避间隔重投。
     */
    private void pushWithRetry(GateTxnPay order, String debitStatus, int attempt) {
        boolean accepted;
        try {
            accepted = orderPayNoticeNotifier.push(order, debitStatus);
        } catch (Exception e) {
            log.error("推送行程订单通知异常, orderNo={}, debitStatus={}, attempt={}",
                    order.getOrderNo(), debitStatus, attempt, e);
            accepted = false;
        }
        if (accepted) {
            return;
        }
        if (attempt >= retryDelaysMs.length) {
            log.error("行程订单通知重试耗尽，已停止重投，MUST 用补偿入口补推, orderNo={}, debitStatus={}, attempts={}",
                    order.getOrderNo(), debitStatus, attempt + 1);
            return;
        }
        long delay = retryDelaysMs[attempt];
        log.warn("行程订单通知未受理，{}ms 后第 {} 次重试, orderNo={}, debitStatus={}",
                delay, attempt + 1, order.getOrderNo(), debitStatus);
        try {
            appNotifyScheduler.schedule(() -> pushWithRetry(order, debitStatus, attempt + 1),
                    delay, TimeUnit.MILLISECONDS);
        } catch (RuntimeException e) {
            log.error("行程订单通知重试调度失败，已停止重投, orderNo={}, debitStatus={}",
                    order.getOrderNo(), debitStatus, e);
        }
    }

    private boolean isSettled(String debitStatus) {
        return DebitStatus.SUCCESS.is(debitStatus) || DebitStatus.FAIL.is(debitStatus);
    }

    private long[] parseRetryDelays(String value) {
        if (!StringUtils.hasText(value)) {
            return new long[0];
        }
        long[] delays = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .mapToLong(item -> {
                    try {
                        return Long.parseLong(item);
                    } catch (NumberFormatException e) {
                        log.warn("行程订单通知重试间隔非法，已忽略该项: {}", item);
                        return -1L;
                    }
                })
                .filter(delay -> delay >= 0)
                .toArray();
        return delays;
    }
}
