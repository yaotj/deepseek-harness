package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.entity.F2fNotifyTask;
import com.chinasofti.huateng.facepay.mapper.F2fNotifyTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 出向通知（IF8B-04/05/06/07）的落库与状态推进。 */
@Service
public class F2fNotifyService {

    /** 出票成功通知。 */
    public static final String TYPE_TAKE_TICKET_OK = "TAKE_TICKET_OK";

    /** 出票失败通知。 */
    public static final String TYPE_TAKE_TICKET_FAIL = "TAKE_TICKET_FAIL";

    /** 退款结果通知。 */
    public static final String TYPE_REFUND_RESULT = "REFUND_RESULT";

    /** 支付结果通知。 */
    public static final String TYPE_PAY_RESULT = "PAY_RESULT";

    /** 目前只有 APP 一个通知目标（{@code CK_F2F_NOTIFY_TARGET} 也只允许这一个值）。 */
    public static final String TARGET_APP = "APP";

    private static final String STATUS_PENDING = "PENDING";

    private static final int DEFAULT_MAX_RETRY_TIMES = 5;

    private static final Logger log = LoggerFactory.getLogger(F2fNotifyService.class);

    private final F2fNotifyTaskMapper notifyTaskMapper;

    public F2fNotifyService(F2fNotifyTaskMapper notifyTaskMapper) {
        this.notifyTaskMapper = notifyTaskMapper;
    }

    /**
     * 落一条待投递通知。
     *
     * @param refundNo 退款类通知传退款单号，其余传 null（唯一索引里用 {@code #NONE#} 占位）
     * @param payload  通知报文体，序列化成 JSON 存 {@code PAYLOAD} 列
     * @return true 表示本次新建了任务；false 表示已存在（幂等命中），调用方 NEVER 再触发别的动作
     */
    public boolean enqueue(String notifyType, String orderNo, String refundNo, Map<String, Object> payload) {
        return enqueue(notifyType, orderNo, refundNo, payload, DEFAULT_MAX_RETRY_TIMES);
    }

    /**
     * 同上，但可指定重试上限，用于「已知对端必然拒绝」的通知。
     *
     * @param maxRetryTimes 重试上限，MUST 为正数；传 1 即失败一次就转 {@code GIVEUP}
     */
    public boolean enqueue(String notifyType, String orderNo, String refundNo,
                           Map<String, Object> payload, int maxRetryTimes) {
        F2fNotifyTask task = new F2fNotifyTask();
        task.setNotifyType(notifyType);
        task.setTarget(TARGET_APP);
        task.setOrderNo(orderNo);
        task.setRefundNo(refundNo);
        task.setNotifyStatus(STATUS_PENDING);
        task.setPayload(payload == null ? null : JSON.toJSONString(payload));
        task.setRetryTimes(0);
        task.setMaxRetryTimes(maxRetryTimes <= 0 ? DEFAULT_MAX_RETRY_TIMES : maxRetryTimes);
        LocalDateTime now = LocalDateTime.now();
        task.setCreateTms(now);
        task.setUpdateTms(now);
        try {
            notifyTaskMapper.insert(task);
            log.info("通知任务已入队, notifyType={}, orderNo={}, refundNo={}, maxRetry={}",
                    notifyType, orderNo, refundNo, task.getMaxRetryTimes());
            return true;
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            log.info("通知任务已存在，幂等跳过, notifyType={}, orderNo={}, refundNo={}",
                    notifyType, orderNo, refundNo);
            return false;
        }
    }

    /** 扫出到期任务，供 {@code F2fNotifyJob} 使用。 */
    public List<F2fNotifyTask> loadDueTasks(int limit) {
        return notifyTaskMapper.selectDueTasks(LocalDateTime.now(), limit);
    }

    /** 只扫某一个通知类型的到期任务，供 {@code /pay/noticeAppTask/**} 三个外部触发端点使用。 */
    public List<F2fNotifyTask> loadDueTasksByType(String notifyType, int limit) {
        return notifyTaskMapper.selectDueTasksByType(LocalDateTime.now(), notifyType, limit);
    }

    /**
     * 记投递成功。
     *
     * @return true 表示本次成功推进；false 表示任务已成功或已 GIVEUP（并发下另一副本先做完了）
     */
    public boolean markSuccess(Long id) {
        return notifyTaskMapper.markSuccess(id, LocalDateTime.now()) > 0;
    }

    /** 记一次投递失败并安排下次重试。 */
    public void markFailure(F2fNotifyTask task, String error) {
        int retried = task.getRetryTimes() == null ? 0 : task.getRetryTimes();
        long backoffSeconds = Math.min(600L, 30L * (1L << Math.min(retried, 5)));
        LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(backoffSeconds);
        notifyTaskMapper.markFailure(task.getId(), nextRetry, truncate(error));
        int maxRetry = task.getMaxRetryTimes() == null ? DEFAULT_MAX_RETRY_TIMES : task.getMaxRetryTimes();
        if (retried + 1 >= maxRetry) {
            log.error("通知投递已放弃（GIVEUP），需人工介入, id={}, notifyType={}, orderNo={}, refundNo={}, 已重试={}, 上限={}, error={}",
                    task.getId(), task.getNotifyType(), task.getOrderNo(), task.getRefundNo(),
                    retried + 1, maxRetry, error);
            return;
        }
        log.warn("通知投递失败，{} 秒后重试, id={}, notifyType={}, orderNo={}, error={}",
                backoffSeconds, task.getId(), task.getNotifyType(), task.getOrderNo(), error);
    }

    /** {@code LAST_ERROR} 列宽 512，超长由调用方截断（mapper 文档已注明）。 */
    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= 512 ? error : error.substring(0, 512);
    }
}
