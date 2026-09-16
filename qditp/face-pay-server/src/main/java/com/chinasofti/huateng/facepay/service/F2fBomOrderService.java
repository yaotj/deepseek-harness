package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatusTransition;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.PaymentResult;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.bom.NotiBusResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestGenNoCashOrderReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestOrderResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestTicketRefundReqDTO;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.entity.F2fResultReport;
import com.chinasofti.huateng.facepay.entity.F2fTicket;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.mapper.F2fResultReportMapper;
import com.chinasofti.huateng.facepay.mapper.F2fTicketMapper;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import com.chinasofti.huateng.facepay.support.F2fOrderNo;
import com.chinasofti.huateng.facepay.support.F2fOrderNoGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * BOM 非现金收款：下单、业务操作结果通知、单程票交易查询、单程票退款。
 *
 * <h2>四处修掉的旧缺陷</h2>
 * <ul>
 *   <li><b>下单幂等。</b>{@code bomOptSeq} 落到 {@code DEVICE_SEQ}，由
 *       {@code UK_F2F_ORDER_DEV_SEQ}（CHANNEL + DEVICE_ID + DEVICE_SEQ）挡重复开单；
 *       撞索引时查出已有订单原样返回。旧实现只入库不判重。</li>
 *   <li><b>业务结果通知幂等。</b>{@code UK_F2F_REPORT_IDEM} 挡重复通知，重复的
 *       {@code FAILED} 不会二次退款。旧实现每次都生成新退款单。</li>
 *   <li><b>退款结果不再被忽略。</b>旧实现 {@code doRefund} 返回值只打日志，退款失败照样回
 *       {@code 0000}；这里按 {@link RefundOutcome} 显式区分「拒绝 / 已存在 / 已受理」。</li>
 *   <li><b>交易查询不再 NPE。</b>旧 {@code getBomOrderInfo} 对订单不判空就 {@code .getRsv2()}。</li>
 * </ul>
 *
 * <p>整个类不带 {@code @Transactional}：退款链路内有支付中心调用。</p>
 */
@Service
public class F2fBomOrderService {

    private static final Logger log = LoggerFactory.getLogger(F2fBomOrderService.class);

    /** 业务类型：非现金收款，对应 {@code CK_F2F_ORDER_BIZ} 的 04。 */
    private static final String BIZ_NO_CASH = "04";

    /** {@code transType=42} 行政处理，此时 {@code adminTransType} 必填。 */
    private static final String TRANS_TYPE_ADMIN = "42";

    private static final String STATUS_CREATED = F2fOrderStatus.CREATED.name();
    private static final String STATUS_PAYING = F2fOrderStatus.PAYING.name();
    private static final String STATUS_PAID = F2fOrderStatus.PAID.name();
    private static final String STATUS_FULFILLED = F2fOrderStatus.FULFILLED.name();
    private static final String STATUS_FULFILL_FAILED = F2fOrderStatus.FULFILL_FAILED.name();
    private static final String STATUS_REFUNDING = F2fOrderStatus.REFUNDING.name();

    private static final List<String> PAID_LIKE = List.of(
            STATUS_PAID, STATUS_FULFILLED, STATUS_FULFILL_FAILED, STATUS_REFUNDING, F2fOrderStatus.REFUNDED.name());

    private static final List<String> FAILED_LIKE = F2fOrderStatus.FAILED_LIKE;

    private static final List<String> PENDING = F2fOrderStatus.PENDING;

    /** 业务结果上报只在已收款后才有意义；不在此列的一律 report-only，见 {@code receiveBusResult}。 */
    private static final List<String> REPORTABLE = List.of(STATUS_PAID, STATUS_FULFILLED, STATUS_FULFILL_FAILED);

    private static final String REPORT_BOM_BIZ_RESULT = "BOM_BIZ_RESULT";

    /** 票状态：可退。 */
    private static final List<String> TICKET_REFUNDABLE = List.of("ISSUED", "FAULT");

    /**
     * 票状态：退款中。
     *
     * <p><b>这是 {@code F2F_TICKET.TICKET_STATUS}，不是订单状态</b>——拼写与
     * {@link F2fOrderStatus#REFUNDING} 撞车纯属巧合，两者是**不同的取值域**
     * （见 {@code docs/domain/state-machines.md} §二）。因此 NEVER 改成
     * {@code F2fOrderStatus.REFUNDING.name()}：编译器两边都是 String、发现不了，
     * 但一旦订单侧改了拼写，票状态会被同步改坏。前缀 {@code TICKET_} 也是
     * {@code F2fOrderStatusArchTest} 区分取值域的唯一依据。</p>
     */
    private static final String TICKET_REFUNDING = "REFUNDING";

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fTicketMapper ticketMapper;

    private final F2fResultReportMapper reportMapper;

    private final F2fOrderNoGenerator orderNoGenerator;

    private final F2fRefundService refundService;

    public F2fBomOrderService(F2fOrderMapper orderMapper,
                             F2fPaymentMapper paymentMapper,
                             F2fTicketMapper ticketMapper,
                             F2fResultReportMapper reportMapper,
                             F2fOrderNoGenerator orderNoGenerator,
                             F2fRefundService refundService) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.ticketMapper = ticketMapper;
        this.reportMapper = reportMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.refundService = refundService;
    }
    /**
     * IF8A-04 非现金收款下单。只 INSERT，不先查后插——并发下先查再插无效，
     * 重复请求由 {@code UK_F2F_ORDER_DEV_SEQ} 挡住后回查已有订单幂等返回。
     */
    public JSONObject createNoCashOrder(RequestGenNoCashOrderReqDTO request) {
        Long amount = request.amountInFen();
        if (amount == null) {
            log.warn("BOM 下单 transAount 非数字, transAount={}", request.getTransAount());
            return BomResponses.orderNoFail(BomResponses.CODE_FAIL, "transAount不是合法数字");
        }
        if (amount <= 0) {
            return BomResponses.orderNoFail(BomResponses.CODE_FAIL, "transAount必须为正数");
        }
        if (TRANS_TYPE_ADMIN.equals(request.getTransType())
                && (request.getAdminTransType() == null || request.getAdminTransType().isBlank())) {
            return BomResponses.orderNoFail(BomResponses.CODE_FAIL, "transType=42时adminTransType不能为空");
        }

        String orderNo = orderNoGenerator.next(F2fOrderNo.BIZ_SINGLE_TICKET);
        LocalDateTime now = LocalDateTime.now();
        try {
            orderMapper.insert(buildOrder(orderNo, request, amount, now));
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            F2fOrder existing = orderMapper.selectByDeviceSeq(
                    F2fChannel.BOM, request.getDeviceId(), request.getBomOptSeq());
            if (existing == null) {
                log.error("BOM 下单撞唯一索引但回查不到订单, deviceId={}, bomOptSeq={}",
                        request.getDeviceId(), request.getBomOptSeq(), e);
                return BomResponses.orderNoFail(BomResponses.CODE_FAIL, "失败");
            }
            log.info("BOM 下单重复请求，返回已有订单, orderNo={}, bomOptSeq={}",
                    existing.getOrderNo(), request.getBomOptSeq());
            return BomResponses.successOrderNo(existing.getOrderNo());
        }
        log.info("BOM 非现金收款下单已落库, orderNo={}, amount={}, bomOptSeq={}",
                orderNo, amount, request.getBomOptSeq());
        return BomResponses.successOrderNo(orderNo);
    }

    /**
     * IF2A-08 业务操作结果通知。{@code FAILED} 触发原单全额退款。
     *
     * <p>幂等靠 {@code UK_F2F_REPORT_IDEM}：同一订单的重复通知不会二次退款。
     * <b>退款提交失败也回成功</b>——上报与退款单都已落库，
     * {@code F2fRefundReconcileJob} 会重试；回失败只会让 BOM 无意义重推。</p>
     *
     * <p><b>订单不在 {@link #REPORTABLE} 里（钱没收到）时只落上报、原样回 {@code 0000}，
     * 不动状态、NEVER 退款。</b>两条实测依据：①BOM 在一笔支付失败后会固定补发一次
     * {@code optResult=FAILED} 作为调用收尾，这是正常话务不是异常，2026-09-11 并跑期间
     * 在真实流量里命中 3 次（16:35 / 18:15 / 18:17）；②同日 BOM 全量双打，未支付
     * （{@code CREATED}）的单子旧返 {@code 0000}、新返 {@code 8999 订单状态不允许上报业务结果}。
     * 旧实现除「订单不存在」外根本不看状态，所以判据是「取反 {@code REPORTABLE}」而不是
     * 枚举「哪些算支付未成功」——曾按 {@code PAY_FAILED / EXPIRED} 两个状态放行，漏了 {@code CREATED}。</p>
     *
     * <p>放行上报但 NEVER 走退款分支：钱没收到，退款单会被支付中心拒（{@code 错误的订单号}）
     * 并留下 stranded {@code INIT} 记录，靠 {@code F2fRefundReconcileJob} 无限重试也收不了口。</p>
     */
    public JSONObject receiveBusResult(NotiBusResultReqDTO request) {
        F2fOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("BOM 业务结果通知 订单不存在, orderNo={}", request.getOrderNo());
            return BomResponses.orderNotFound();
        }
        boolean reportOnly = !REPORTABLE.contains(order.getOrderStatus());
        if (!insertReport(buildBizReport(order, request))) {
            log.info("BOM 业务结果通知重复到达，幂等返回成功, orderNo={}", order.getOrderNo());
            return BomResponses.success();
        }
        if (reportOnly) {
            log.info("BOM 业务结果通知 该单支付未成功，只留上报不动状态也不退款, orderNo={}, status={}, optResult={}",
                    order.getOrderNo(), order.getOrderStatus(), request.getOptResult());
            return BomResponses.success();
        }
        if (request.isSuccess()) {
            int updated = orderMapper.updateStatus(order.getOrderNo(), List.of(STATUS_PAID),
                    STATUS_FULFILLED, "BOM业务操作成功");
            log.info("BOM 业务操作成功, orderNo={}, statusUpdated={}", order.getOrderNo(), updated);
            return BomResponses.success();
        }
        if (!request.isFailed()) {
            log.warn("BOM 业务结果通知 optResult 取值不识别，只留上报不动状态, orderNo={}, optResult={}",
                    order.getOrderNo(), request.getOptResult());
            return BomResponses.success();
        }
        warnIfConflict(order.getOrderNo(), orderMapper.updateStatus(order.getOrderNo(),
                        List.of(STATUS_PAID), STATUS_FULFILL_FAILED, "BOM业务操作失败"),
                F2fOrderStatus.FULFILL_FAILED, "BOM业务操作失败");
        submitWholeRefund(order, request.getDeviceId());
        return BomResponses.success();
    }
    /**
     * 单程票交易查询。两要素（逻辑卡号 + 交易日期）定位票，再回查订单。
     *
     * <p>{@code paymentResult} 值域是 {@code SUCCESS / FAILED / UNPAID}，
     * 与 {@code requestGetPayResult} 的 {@code ORDERED} 不同，见
     * {@link BomResponses#orderResult}。</p>
     */
    public JSONObject requestOrderResult(RequestOrderResultReqDTO request) {
        F2fTicket ticket = ticketMapper.selectByLogicNumAndTransDate(
                request.getTicketLogicNum(), request.getTransDate());
        if (ticket == null) {
            log.info("单程票交易查询 没有查找到出票信息, ticketLogicNum={}, transDate={}",
                    request.getTicketLogicNum(), request.getTransDate());
            return BomResponses.orderResultFail(BomResponses.CODE_FAIL, "没有查找到出票信息");
        }
        F2fOrder order = orderMapper.selectByOrderNo(ticket.getOrderNo());
        if (order == null) {
            log.error("单程票交易查询 票存在但订单缺失（数据不一致）, ticketLogicNum={}, orderNo={}",
                    request.getTicketLogicNum(), ticket.getOrderNo());
            return BomResponses.orderResultFail(BomResponses.CODE_FAIL, "无对应的订单信息");
        }
        String status = order.getOrderStatus();
        String paymentResult = PAID_LIKE.contains(status) ? "SUCCESS"
                : FAILED_LIKE.contains(status) ? "FAILED" : "UNPAID";
        String desc = "SUCCESS".equals(paymentResult) ? "支付成功"
                : "FAILED".equals(paymentResult) ? "支付失败" : "未支付";
        return BomResponses.orderResult(paymentResult, desc, order.getOrderNo(),
                ticket.getTransDate(), ticket.getTicketPrice(),
                ticket.getPayChannelCode() == null
                        ? channelCodeOf(order.getOrderNo()) : ticket.getPayChannelCode());
    }

    /**
     * 单程票退款。按票退，幂等键是「原订单号 + 逻辑卡号 + BOM_ORIGINAL」。
     *
     * <p>旧实现先无条件写一条退款请求记录（{@code insertTicketRefundRecord}，该表没有唯一索引），
     * 再按 {@code transType} 分流到三套几乎相同的退款代码，退款成功后把退款单号写回子票表。
     * 这里合成一条链路：校验 → 提交退款 → 回写票的 {@code REFUND_NO} 与状态。</p>
     * <p><b>响应形态（2026-09-16）</b>：成功与失败都回
     * {@code refundResult / refundResultDesc / refundNo} 三个业务键，失败支值全 JSON null，
     * 判据与 NEVER 事项写在 {@code BomResponses.refundSuccess} / {@code refundFail} 上。
     * <b>「订单不存在」那一支的 {@code 9999} 是 BOM 域独一份的既有码，NEVER 归一成 8999 / 8006。</b></p>
     */
    public JSONObject requestTicketRefund(RequestTicketRefundReqDTO request) {
        Long amount = request.amountInFen();
        if (amount == null || amount <= 0) {
            log.warn("单程票退款 金额非法, transAmount={}", request.getTransAmount());
            return BomResponses.refundFail(BomResponses.CODE_FAIL, "transAmount不是合法的正整数金额");
        }
        F2fOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("单程票退款 订单不存在, orderNo={}", request.getOrderNo());
            return BomResponses.refundFail("9999", "订单号错误,没有找到匹配的订单");
        }
        if (!PAID_LIKE.contains(order.getOrderStatus())) {
            log.info("单程票退款 订单未收款，拒绝退款, orderNo={}, status={}",
                    order.getOrderNo(), order.getOrderStatus());
            return BomResponses.refundFail(BomResponses.CODE_FAIL, "订单未支付，不可退款");
        }
        F2fTicket ticket = ticketMapper.selectLatestByLogicNum(request.getTicketLogicNum());
        if (ticket == null) {
            log.info("单程票退款 找不到该逻辑卡号的出票记录, ticketLogicNum={}", request.getTicketLogicNum());
            return BomResponses.refundFail(BomResponses.CODE_FAIL, "没有查找到出票信息");
        }
        if (!TICKET_REFUNDABLE.contains(ticket.getTicketStatus())) {
            log.info("单程票退款 票状态不允许退款, ticketLogicNum={}, ticketStatus={}",
                    request.getTicketLogicNum(), ticket.getTicketStatus());
            return BomResponses.refundFail(BomResponses.CODE_FAIL, "该票已退款或状态不允许退款");
        }
        if (order.getOrderAmount() != null) {
            long refunded = order.getRefundAmount() == null ? 0L : order.getRefundAmount();
            if (amount > order.getOrderAmount() - refunded) {
                log.warn("单程票退款 金额超过可退金额, orderNo={}, refund={}, orderAmount={}, refunded={}",
                        order.getOrderNo(), amount, order.getOrderAmount(), refunded);
                return BomResponses.refundFail(BomResponses.CODE_FAIL, "退款金额不能大于订单金额");
            }
        }

        RefundCommand command = new RefundCommand(order.getOrderNo(), request.getTicketLogicNum(),
                F2fRefundService.SOURCE_BOM_ORIGINAL, amount, 1, "BOM单程票退款",
                request.getDeviceId(), null, request.getTransType(), ticket.getTransDate(),
                payCenterOrderNoOf(order.getOrderNo()));
        RefundOutcome outcome = refundService.refund(command);
        if (outcome.isRejected()) {
            log.warn("单程票退款被拒绝, orderNo={}, reason={}", order.getOrderNo(), outcome.failureReason());
            return BomResponses.refundFail(BomResponses.CODE_FAIL, outcome.failureReason());
        }
        ticketMapper.updateRefundNo(request.getTicketLogicNum(), ticket.getTransDate(), outcome.refundNo());
        int ticketRows = ticketMapper.updateStatus(request.getTicketLogicNum(), ticket.getTransDate(),
                TICKET_REFUNDABLE, TICKET_REFUNDING);
        if (ticketRows == 0) {
            // 票状态 CAS 0 行 —— 退款已提交给支付中心（refundService 在前面），说明这张票在本次
            // 校验之后被别人推走了，最常见是 BOM 对同一张票并发发退款。**这是重复退款的唯一信号**，
            // NEVER 降级成 info。订单主状态不再参与退款（ADR-D88），票状态是现在仅存的那道 CAS。
            log.warn("F2F CAS 冲突 单程票退款票状态未推进，疑似重复退款, orderNo={}, ticketLogicNum={}, refundNo={}",
                    order.getOrderNo(), request.getTicketLogicNum(), outcome.refundNo());
        }
        log.info("单程票退款已提交, orderNo={}, ticketLogicNum={}, refundNo={}, alreadyExisted={}",
                order.getOrderNo(), request.getTicketLogicNum(), outcome.refundNo(), outcome.alreadyExisted());
        boolean settled = F2fRefundService.STATUS_SUCCESS.equals(outcome.refundStatus());
        // 只有终态 SUCCESS 才回 SUCCESS：支付中心受理（PROCESSING）不等于钱已到账，收口要等
        // F2fRefundService.reconcileRefund 回查。INIT / MANUAL 一并按 PROCESSING 上报 ——
        // 对 BOM 来说都是「还没有结论」，NEVER 映射成 FAILED（会让设备把在途退款当成失败）。
        return BomResponses.refundSuccess(settled ? "SUCCESS" : "PROCESSING",
                settled ? "退款成功" : "退款处理中", outcome.refundNo());
    }
    /** 业务操作失败的原单全额退款。<b>必须在事务外</b>：内部会调支付中心。 */
    private void submitWholeRefund(F2fOrder order, String deviceId) {
        if (order.getOrderAmount() == null || order.getOrderAmount() <= 0) {
            log.error("BOM 业务失败退款 订单金额非法，无法退款, orderNo={}, amount={}",
                    order.getOrderNo(), order.getOrderAmount());
            return;
        }
        RefundCommand command = new RefundCommand(order.getOrderNo(), null,
                F2fRefundService.SOURCE_BOM_ORIGINAL, order.getOrderAmount(), null,
                "业务操作失败", deviceId, order.getOperatorId(), order.getTransType(),
                null, payCenterOrderNoOf(order.getOrderNo()));
        RefundOutcome outcome = refundService.refund(command);
        if (outcome.isRejected()) {
            log.error("BOM 业务失败退款被拒绝，需人工介入, orderNo={}, reason={}",
                    order.getOrderNo(), outcome.failureReason());
            return;
        }
        log.info("BOM 业务失败已提交退款, orderNo={}, refundNo={}, alreadyExisted={}",
                order.getOrderNo(), outcome.refundNo(), outcome.alreadyExisted());
    }

    /** @return true 表示本次是首报；false 表示撞唯一索引（重复上报） */
    private boolean insertReport(F2fResultReport report) {
        try {
            reportMapper.insert(report);
            return true;
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            return false;
        }
    }

    private F2fResultReport buildBizReport(F2fOrder order, NotiBusResultReqDTO request) {
        F2fResultReport report = new F2fResultReport();
        report.setReportType(REPORT_BOM_BIZ_RESULT);
        report.setOrderNo(order.getOrderNo());
        report.setChannel(order.getChannel());
        report.setDeviceId(request.getDeviceId() == null ? order.getDeviceId() : request.getDeviceId());
        report.setOperatorId(order.getOperatorId());
        report.setOptResult(request.getOptResult());
        report.setOptResultDesc(request.getOptResultDesc());
        report.setReportTms(request.getTranDate());
        report.setTransAmount(order.getOrderAmount());
        report.setProcessed("1");
        report.setRawBody(request.toString());
        report.setReceiveTms(LocalDateTime.now());
        report.setCreateTms(LocalDateTime.now());
        return report;
    }

    private F2fOrder buildOrder(String orderNo, RequestGenNoCashOrderReqDTO request,
                                long amount, LocalDateTime now) {
        F2fOrder order = new F2fOrder();
        order.setOrderNo(orderNo);
        order.setChannel(F2fChannel.BOM);
        order.setBizType(BIZ_NO_CASH);
        order.setTransType(request.getTransType());
        order.setAdminTransType(request.getAdminTransType());
        order.setOrderStatus(STATUS_CREATED);
        order.setOrderAmount(amount);
        order.setDeviceId(request.getDeviceId());
        order.setDeviceSeq(request.getBomOptSeq());
        order.setOperatorId(request.getOperaterId());
        order.setShiftId(request.getShiftId());
        order.setCardId(request.getCardId());
        order.setActivateFlag("0");
        order.setCreateTms(now);
        order.setUpdateTms(now);
        return order;
    }

    /** 渠道码要等支付回调或查询结果才有值，可能为 null。 */
    private String channelCodeOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayChannelCode();
    }

    /**
     * 取支付中心侧原订单号供退款报文使用。旧实现这里硬编码空串
     * （{@code BomOrderServiceImpl:640} 留着「根据实际情况填写」的注释）。
     */
    private String payCenterOrderNoOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayCenterOrderNo();
    }

    /**
     * 记「CAS 没命中」。口径与 {@code F2fTvmOrderService.warnIfConflict} 一致：
     * <b>只告警、不改应答、不拒绝请求</b>，改一处 MUST 看齐其余三处（TVM / APP / 充值）。
     */
    private void warnIfConflict(String orderNo, int updatedRows, F2fOrderStatus target, String scene) {
        F2fOrderStatusTransition.Result transit = F2fOrderStatusTransition.classify(
                updatedRows, target, () -> orderMapper.selectOrderStatus(orderNo));
        if (transit.conflict()) {
            log.warn("F2F CAS 冲突 {} 未推进到 {}，仍按原口径应答, orderNo={}, observed={}",
                    scene, target, orderNo, transit.observedStatus());
        }
    }

}
