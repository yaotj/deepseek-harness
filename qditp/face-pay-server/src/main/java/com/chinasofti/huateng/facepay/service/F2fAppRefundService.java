package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.app.AppRefundNotiResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.app.AppResponses;
import com.chinasofti.huateng.facepay.api.device.app.RequestAppPayResultReqDTO;
import com.chinasofti.huateng.facepay.channel.app.AppNotifyProperties;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.entity.F2fRefund;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.mapper.F2fRefundMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * APP 侧退款：申请退款、退款结果查询、支付中心退款结果回调。
 *
 * <p>由 {@code F2fAppOrderService} 拆出（原类 614 行，同时承载下单/支付/退款/退款回调
 * 四条流程、注入 9 个 bean）。拆分只搬代码<b>不改任何行为</b>，下面四条修掉的旧缺陷与
 * 全部「NEVER」约束逐字随迁。</p>
 *
 * <h2>三处修掉的旧缺陷（NEVER 回退）</h2>
 * <ul>
 *   <li><b>退款结果查询不再把订单号当退款单号用。</b>旧实现变量名叫 {@code refundRrderNo}，
 *       值是 {@code orderNo}，却去 {@code selectByRefundNo} 查——只有恰好两个号相同才查得到。</li>
 *   <li><b>退款通知改成落库 + 定时投递。</b>旧实现在业务线程池里同步 push APP，
 *       失败才落库交给重试任务；现在统一入 {@code F2F_NOTIFY_TASK}，
 *       由 {@code F2fNotifyJob} 投递，见 {@link F2fNotifyService}。</li>
 *   <li><b>退款受理不再无条件返回成功。</b>旧 {@code doRefund} 整段 catch 后回
 *       {@code 9999 请求异常}，但退款单可能已经落库、支付中心可能已经受理，
 *       APP 侧无法区分。这里按 {@link RefundOutcome} 区分拒绝 / 已存在 / 已受理。</li>
 * </ul>
 *
 * <p>整个类<b>不带 {@code @Transactional}</b>：链路里有支付中心调用（经
 * {@link F2fRefundService}）。</p>
 */
@Service
public class F2fAppRefundService {

    private static final Logger log = LoggerFactory.getLogger(F2fAppRefundService.class);

    /** 允许发起退款的状态白名单。已支付但业务未完成、或业务已完成都可退。 */
    private static final List<String> REFUNDABLE = F2fOrderStatus.REFUNDABLE;

    /**
     * 退款相关响应里的 {@code refundDate} 格式：<b>8 位日期</b>。
     *
     * <p>2026-09-11 实测旧服务回 {@code 20260911}、新服务曾回 14 位
     * {@code 20260911204733}，已改回 8 位。{@code requestRefund} 与
     * {@code queryRefundResult} 两处同宽度，NEVER 改成 14 位。</p>
     */
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fRefundMapper refundMapper;

    private final F2fRefundService refundService;

    private final F2fNotifyService notifyService;

    private final String refundNotifyUrl;

    public F2fAppRefundService(F2fOrderMapper orderMapper,
                               F2fPaymentMapper paymentMapper,
                               F2fRefundMapper refundMapper,
                               F2fRefundService refundService,
                               F2fNotifyService notifyService,
                               AppNotifyProperties appNotifyProperties) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.refundMapper = refundMapper;
        this.refundService = refundService;
        this.notifyService = notifyService;
        this.refundNotifyUrl = appNotifyProperties.getRefundNoticeUrl();
    }
    /**
     * 请求退款（整单）。<b>必须在事务外</b>：内部会调支付中心。
     *
     * <p>响应里的 {@code notifyUrl} 回吐我方配置的退款结果通知地址，
     * 照搬旧实现——它把内部配置回显给 APP，看着奇怪但是既有契约。
     * 取值来自 {@code f2f.notify.app.refund-notice-url}，对应旧键
     * {@code pay.center.app-refund-notice-url}（集群 env 实测值指向 fep-app 的
     * {@code /ci/app/receiveRefundResult}）。<b>NEVER 复用
     * {@code f2f.notify.app.refund-result-url}</b>——那是 {@code F2fNotifyJob}
     * 出向推送退款结果的地址（旧键 {@code pay.center.notice-app-refundresult-url}），
     * 两个键在旧实现里是不同的值，合并会改变响应内容。</p>
     *
     * <p><b>状态不允许退款时回 {@code 0000} + {@code refundResult=FAIL}，不是 {@code 9999}。</b>
     * 旧 {@code AppOrderServiceImpl.requestRefundTicket:517} 只校验订单存在、<b>完全不看状态</b>，
     * 直接把未支付单也送去支付中心，被拒后照样回 {@code 0000} 带
     * {@code refundResult=FAIL / refundResultDesc=退款失败}（2026-09-11 新旧双打实测）。
     * APP 端解析的是报文体里的 {@code refundResult}，退化成 {@code 9999} 会让它拿不到结构化结果。
     * 因此<b>响应形态照搬旧实现，但 NEVER 照搬「未支付也真去发起退款」</b>——那会在
     * {@code F2F_REFUND} 里留下必然失败的 {@code INIT} 记录，{@code F2fRefundReconcileJob}
     * 无限重试也收不了口（与 TVM / BOM 侧同一条裁决）。</p>
     */
    public JSONObject requestRefund(RequestAppPayResultReqDTO request) {
        String orderNo = request.getOrderNo();
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("APP 请求退款 订单不存在, orderNo={}", orderNo);
            return AppResponses.failMessage("该订单无支付记录，不可退款");
        }
        if (!REFUNDABLE.contains(order.getOrderStatus())) {
            log.info("APP 请求退款 订单状态不允许退款，按旧形态回退款失败, orderNo={}, status={}",
                    orderNo, order.getOrderStatus());
            return AppResponses.refund(orderNo, LocalDateTime.now().format(DAY_FORMATTER),
                    order.getOrderAmount(), "FAIL", "退款失败", refundNotifyUrl);
        }
        if (order.getOrderAmount() == null || order.getOrderAmount() <= 0) {
            log.error("APP 请求退款 订单金额非法, orderNo={}, amount={}", orderNo, order.getOrderAmount());
            return AppResponses.failMessage("订单金额异常，请联系工作人员");
        }
        int unsettled = orderMapper.countUnsettledRefunds(orderNo);
        if (unsettled > 0) {
            log.warn("APP 请求退款 该订单尚有未收口的退款单，拒绝再退, orderNo={}, unsettled={}", orderNo, unsettled);
            return AppResponses.refund(orderNo, LocalDateTime.now().format(DAY_FORMATTER),
                    order.getOrderAmount(), "PROCESSING", "退款处理中", refundNotifyUrl);
        }
        long refunded = order.getRefundAmount() == null ? 0L : order.getRefundAmount();
        long refundable = order.getOrderAmount() - refunded;
        if (refundable <= 0) {
            log.info("APP 请求退款 已退满，无可退金额, orderNo={}, orderAmount={}, refundAmount={}",
                    orderNo, order.getOrderAmount(), refunded);
            return AppResponses.refund(orderNo, LocalDateTime.now().format(DAY_FORMATTER),
                    order.getOrderAmount(), "FAIL", "退款失败", refundNotifyUrl);
        }

        RefundCommand command = new RefundCommand(orderNo, null, F2fRefundService.SOURCE_APP_REQUEST,
                refundable, null, "APP申请退款", null, request.getUserId(),
                order.getTransType(), null, payCenterOrderNoOf(orderNo));
        RefundOutcome outcome = refundService.refund(command);
        if (outcome.isRejected()) {
            log.warn("APP 请求退款被拒绝, orderNo={}, reason={}", orderNo, outcome.failureReason());
            return AppResponses.failMessage(outcome.failureReason());
        }
        log.info("APP 退款已受理, orderNo={}, refundNo={}, alreadyExisted={}",
                orderNo, outcome.refundNo(), outcome.alreadyExisted());
        return AppResponses.refund(orderNo, LocalDateTime.now().format(DAY_FORMATTER),
                order.getOrderAmount(), "PROCESSING", "退款进行中", refundNotifyUrl);
    }
    /**
     * 退款结果查询。<b>先按退款单号查，未命中再按原订单号查</b>。
     *
     * <p>两条都要留，<b>NEVER 只保一条</b>：入参字段名叫 {@code orderNo}，但旧实现
     * （`AppOrderServiceImpl:811` 把它赋给局部变量 {@code refundRrderNo} 后
     * {@code selectByRefundNo}）拿它当<b>退款单号</b>用。2026-09-11 双打实测证实两侧当时
     * 完全互斥：同一笔已付单，旧服务传订单号回「没有找到对应退款记录」、传退款单号
     * 回 {@code 0000 FAIL}；新服务反过来。而 {@code requestRefund} 的响应体里
     * <b>并不回退款单号</b>，APP 只能从 {@code receiveRefundResult} 回调的 {@code refundNo}
     * 里拿，因此现网 APP 传的一定是退款单号——只按订单号查等于把这个接口对现网打死。</p>
     *
     * <p>响应里的 {@code orderNo} 回<b>请求传入的原值</b>（旧实现即如此，传退款单号就回退款单号），
     * NEVER 改成回订单号。{@code refundDate} 是 <b>8 位日期</b>（{@code yyyyMMdd}）——
     * 见 {@link #DAY_FORMATTER} 的说明。</p>
     *
     * <p>一笔订单可能有多张退款单（不同来源），按订单号命中时取最近一张：
     * {@code selectByOrigOrderNo} 已按创建时间倒序。</p>
     *
     * <p>{@code INIT} / {@code PROCESSING} 时<b>不再同步问支付中心</b>——
     * {@code F2fRefundReconcileJob} 已经在扫表收口，查询接口再发一次网络调用只会
     * 让 APP 侧的响应时间随支付中心抖动，且两处并发更新同一行。</p>
     */
    public JSONObject queryRefundResult(RequestAppPayResultReqDTO request) {
        String key = request.getOrderNo();
        F2fRefund refund = refundMapper.selectByRefundNo(key);
        if (refund == null) {
            List<F2fRefund> refunds = refundMapper.selectByOrigOrderNo(key);
            if (refunds != null && !refunds.isEmpty()) {
                refund = refunds.get(0);
            }
        }
        if (refund == null) {
            log.info("APP 退款结果查询 没有找到对应退款记录, key={}", key);
            return AppResponses.failMessage("没有找到对应退款记录");
        }
        String refundResult;
        String desc;
        if (F2fRefundService.STATUS_SUCCESS.equals(refund.getRefundStatus())) {
            refundResult = "SUCCESS";
            desc = "退款成功";
        } else if (F2fRefundService.STATUS_FAILED.equals(refund.getRefundStatus())) {
            refundResult = "FAIL";
            desc = "退款失败";
        } else {
            refundResult = "PROCESSING";
            desc = "退款中";
        }
        String refundDate = (refund.getFinishTms() == null
                ? refund.getRequestTms() : refund.getFinishTms()).format(DAY_FORMATTER);
        return AppResponses.refund(key, refundDate, refund.getRefundAmount(),
                refundResult, desc, null);
    }
    /**
     * 支付中心退款结果回调。
     *
     * <p>用 {@code refundNo} 定位退款单；已是终态直接回成功（幂等）。
     * 结果落库后<b>入队一条 REFUND_RESULT 通知</b>交给 {@code F2fNotifyJob} 投递 APP——
     * 旧实现是在业务线程池里同步 push。</p>
     *
     * <p><b>{@code MANUAL} 允许被回调救回</b>：转人工只表示我方放弃了自动收口，
     * 回调带来的是支付中心的权威结论，此时 MUST 接受并推进到 SUCCESS / FAILED。
     * 因此前置状态白名单里带上 MANUAL，而终态短路只认 SUCCESS / FAILED。</p>
     */
    public JSONObject receiveRefundResult(AppRefundNotiResultReqDTO request) {
        F2fRefund refund = refundMapper.selectByRefundNo(request.getRefundNo());
        if (refund == null) {
            log.warn("退款回调 退款单不存在, refundNo={}", request.getRefundNo());
            return AppResponses.failMessage("订单号错误");
        }
        String status = refund.getRefundStatus();
        if (F2fRefundService.STATUS_SUCCESS.equals(status) || F2fRefundService.STATUS_FAILED.equals(status)) {
            log.info("退款回调重复到达，已是终态直接回成功, refundNo={}, status={}",
                    request.getRefundNo(), status);
            return AppResponses.success();
        }
        if (!request.isSuccess() && !request.isFailed()) {
            log.warn("退款回调 refundResult 取值不识别，不动状态, refundNo={}, refundResult={}",
                    request.getRefundNo(), request.getRefundResult());
            return AppResponses.success();
        }
        String toStatus = request.isSuccess()
                ? F2fRefundService.STATUS_SUCCESS : F2fRefundService.STATUS_FAILED;
        int updated = refundMapper.updateStatus(request.getRefundNo(),
                List.of(F2fRefundService.STATUS_INIT, F2fRefundService.STATUS_PROCESSING,
                        F2fRefundService.STATUS_MANUAL),
                toStatus, request.getOutRefundNo(), LocalDateTime.now());
        if (updated == 0) {
            log.info("退款回调 状态已被其他路径收口，幂等返回成功, refundNo={}", request.getRefundNo());
            return AppResponses.success();
        }
        // 退款汇总三列重算。**NEVER 改回「把订单推成 REFUNDED」**（ADR-D88）：
        // 退款与支付/履约主状态正交，主状态推成 REFUNDED 会丢掉「退款前出过票」这个事实，
        // 部分退更是表达不了。重算式幂等，失败分支也要算（金额可能因别的来源已变）。
        int summaryRows = orderMapper.updateRefundSummary(refund.getOrigOrderNo());
        log.info("退款回调 订单退款汇总已重算, orderNo={}, refundNo={}, updatedRows={}",
                refund.getOrigOrderNo(), request.getRefundNo(), summaryRows);
        enqueueRefundNotify(refund, request);
        log.info("退款回调处理完成, refundNo={}, toStatus={}", request.getRefundNo(), toStatus);
        return AppResponses.success();
    }
    /**
     * 入队一条退款结果通知。报文体的 6 个 key 逐字照搬旧 {@code NoticeAppRefundDTO}。
     *
     * <p><b>不在事务内调用</b>（本类无事务）；入队失败只记日志——
     * 退款状态已落库，通知丢了可以由运营端补发，但退款结果不能因为通知失败而回滚。</p>
     */
    private void enqueueRefundNotify(F2fRefund refund, AppRefundNotiResultReqDTO request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", refund.getOrigOrderNo());
        payload.put("refundType", "00");
        payload.put("refundResult", request.getRefundResult());
        payload.put("refundResultDesc", request.getRefundResultDesc());
        payload.put("refundDate", request.getRefundDate() == null
                ? LocalDateTime.now().format(DAY_FORMATTER) : request.getRefundDate());
        payload.put("refundAmount", refund.getRefundAmount());
        boolean enqueued = notifyService.enqueue(F2fNotifyService.TYPE_REFUND_RESULT,
                refund.getOrigOrderNo(), refund.getRefundNo(), payload);
        if (!enqueued) {
            log.info("退款结果通知已存在，跳过入队, refundNo={}", refund.getRefundNo());
        }
    }

    /**
     * 支付中心侧订单号，供退款报文的 {@code tradeNo} 使用；未支付时为 null。
     *
     * <p><b>本方法在 {@code F2fAppOrderService} 里有一份逐字相同的副本</b>，那边给
     * {@code queryPayResult} 回吐 {@code tradeNo} 用。拆分时刻意没有抽公共工具类
     * —— 只有 6 行、且 §5.1 明令「NEVER 主动创建新的工具类」，本项目已确立
     * 「宁可留同形副本也不新建工具类」的惯例（{@code warnIfConflict} 在 TVM/BOM/APP
     * 三处并存即先例）。<b>改这里 MUST 同步看齐 {@code F2fAppOrderService} 那份。</b></p>
     */
    private String payCenterOrderNoOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayCenterOrderNo();
    }
}
