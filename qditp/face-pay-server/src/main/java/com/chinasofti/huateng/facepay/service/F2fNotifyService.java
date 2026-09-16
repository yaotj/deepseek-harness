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

/**
 * 出向通知（IF8B-04/05/06/07）的落库与状态推进。<b>本项目没有消息队列</b>，
 * 可靠投递唯一载体就是 {@code F2F_NOTIFY_TASK} + {@code @Scheduled} 扫表重试。
 *
 * <h2>与旧实现的关键差异</h2>
 * <p>旧实现是「插一条通知记录 → <b>同一请求线程内立刻 HTTP 推送</b> → 失败就置
 * {@code NOTICE_FAIL} 完事」，而重推方法 {@code sendNoticeAppTakeTicketRecord}
 * <b>没有任何 {@code @Scheduled} 绑定</b>，要靠外部 web-server 的 Quartz 去调。
 * 结果是：出票结果通知一次失败就永久躺在表里，而设备侧那次请求还白等了一个 HTTP 超时。</p>
 *
 * <p>本实现把「接收」与「投递」彻底解耦：接收链路只 INSERT（毫秒级返回设备），
 * 投递由 {@code F2fNotifyJob} 扫表完成，失败按退避重试，超过
 * {@code MAX_RETRY_TIMES} 自动置 {@code GIVEUP} 等人工介入。</p>
 *
 * <h2>幂等</h2>
 * <p>靠函数唯一索引 {@code UK_F2F_NOTIFY_IDEM (NOTIFY_TYPE, ORDER_NO, NVL(REFUND_NO,'#NONE#'))}。
 * {@link #enqueue} 直接 INSERT 撞索引后回查已有任务，<b>NEVER 先查后插</b>——
 * 设备断网重传时两个线程都查不到就会插两条，等于给 APP 推两次。</p>
 */
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
     * 落一条待投递通知。<b>只 INSERT，不做前置判重</b>。
     *
     * <p>{@code NEXT_RETRY_TMS} 留空即「立即可发」，下一轮扫表（默认 30 秒内）就会取到。
     * 这里<b>不做同步推送</b>——接收链路 MUST 毫秒级返回设备。</p>
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
     * <p><b>唯一在用的场景是 IF8B-05 推设备单</b>（{@code F2fPayCenterFlow.enqueuePayResultNotify}）：
     * 设备单的 {@code THIRD_USER_ID} 为空，报文里 {@code userId} 只能上送 null，
     * 而 APP 侧按 {@code userId} 定位用户，2026-09-16 实测这类通知**必然**返 {@code 7004}
     * （{@code F2F_NOTIFY_TASK} 里 18 条无 {@code userId} 的 {@code PAY_RESULT} 无一例外，
     * 唯一成功那条是带真实 {@code userId} 的 APP 单）。默认 5 次重试对它没有任何意义 ——
     * 只是把同一条注定失败的报文推 5 遍、再刷一条 ERROR 级 GIVEUP 日志，
     * 把真正需要人工介入的失败埋在噪音里。传 1 表示**一次即终态**。</p>
     *
     * <p><b>NEVER 借这个重载去跳过设备单的入队</b>：「设备单也推」是用户裁决（见
     * {@code F2fPayCenterFlow.enqueuePayResultNotify} 的类内注释与 ADR-D89），
     * 本重载只压缩重试次数、不改推送范围 —— 任务照样落库，人工与对账都还能看到它。</p>
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

    /**
     * 只扫某一个通知类型的到期任务，供 {@code /pay/noticeAppTask/**} 三个外部触发端点使用。
     *
     * <p>旧模块按业务类型分了三个 URL（对应三张 {@code TBL_NOTICE_APP_*} 表），本模块表已合一，
     * 但**触发粒度 MUST 保持不变**：运维点「重投退款通知」时不该把取票通知也发一遍。
     */
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

    /**
     * 记一次投递失败并安排下次重试。
     *
     * <p><b>退避策略在应用侧算，SQL 内 NEVER 做时间计算</b>：按已重试次数指数退避，
     * 30s → 60s → 120s → 240s → 480s，上限 10 分钟。是否 {@code GIVEUP} 由
     * mapper 的同一条 UPDATE 用 {@code CASE WHEN} 判定，避免「加次数」与「置 GIVEUP」
     * 拆成两条 SQL 后被重复捞出。</p>
     */
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
