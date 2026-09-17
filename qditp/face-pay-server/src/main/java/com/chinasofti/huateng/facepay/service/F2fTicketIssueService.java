package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.TicketInfo;
import com.chinasofti.huateng.facepay.api.device.tvm.NotiTakeTicketFailResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.NotiTakeTicketResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.entity.F2fResultReport;
import com.chinasofti.huateng.facepay.entity.F2fTicket;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.mapper.F2fResultReportMapper;
import com.chinasofti.huateng.facepay.mapper.F2fTicketMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 出票结果上报（IF2A-05 成功 / IF2A-06 失败）。本类刻意不带 {@code @Transactional}（链路里有支付中心调用），NEVER 加。 */
@Service
public class F2fTicketIssueService {

    /** 上报类型：出票成功。 */
    public static final String REPORT_TAKE_TICKET_OK = "TAKE_TICKET_OK";

    /** 上报类型：出票失败。 */
    public static final String REPORT_TAKE_TICKET_FAIL = "TAKE_TICKET_FAIL";

    private static final String TICKET_ISSUED = "ISSUED";

    private static final String TICKET_FAULT = "FAULT";

    private static final String ORDER_PAID = F2fOrderStatus.PAID.name();

    private static final String ORDER_FULFILLED = F2fOrderStatus.FULFILLED.name();

    private static final String ORDER_FULFILL_FAILED = F2fOrderStatus.FULFILL_FAILED.name();

    /** APP 扫码取票的交易类型，只有这类订单需要回推 APP。 */
    private static final String TRANS_TYPE_APP_TAKE_TICKET = "03";

    private static final Logger log = LoggerFactory.getLogger(F2fTicketIssueService.class);

    private final F2fOrderMapper orderMapper;

    private final F2fTicketMapper ticketMapper;

    private final F2fResultReportMapper resultReportMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fRefundService refundService;

    private final F2fNotifyService notifyService;

    public F2fTicketIssueService(F2fOrderMapper orderMapper, F2fTicketMapper ticketMapper,
                                 F2fResultReportMapper resultReportMapper, F2fPaymentMapper paymentMapper,
                                 F2fRefundService refundService, F2fNotifyService notifyService) {
        this.orderMapper = orderMapper;
        this.ticketMapper = ticketMapper;
        this.resultReportMapper = resultReportMapper;
        this.paymentMapper = paymentMapper;
        this.refundService = refundService;
        this.notifyService = notifyService;
    }

    /** 出票成功上报。 */
    public JSONObject receiveTakeTicketResult(NotiTakeTicketResultReqDTO request, String channel) {
        Integer actualNum = request.actualNum();
        if (actualNum == null) {
            log.warn("出票成功上报 actualTakeTicketNum 非法, orderNo={}, raw={}",
                    request.getOrderNo(), request.getActualTakeTicketNum());
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM);
        }
        F2fOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.warn("出票成功上报 订单不存在, orderNo={}, providerId={}",
                    request.getOrderNo(), request.getProviderId());
            return TvmResponses.takeTicketResultOrderNotFound(request.getProviderId());
        }

        F2fResultReport report = baseReport(REPORT_TAKE_TICKET_OK, request.getOrderNo(), channel,
                request.getDeviceId(), order.getTicketNum(), actualNum, request.getTakeTickeDate());
        report.setOptResult("0");
        report.setOptResultDesc("出票成功");
        report.setRawBody(request.getBizData());
        saveTickets(order, request.getTicketList(), TICKET_ISSUED);
        if (!insertReport(report)) {
            return TvmResponses.success();
        }

        int updated = orderMapper.updateStatus(request.getOrderNo(), List.of(ORDER_PAID),
                ORDER_FULFILLED, "出票成功");
        if (updated == 0) {
            log.warn("出票成功上报 订单不在 PAID 状态，仅留上报记录不推进, orderNo={}, status={}",
                    request.getOrderNo(), order.getOrderStatus());
        }
        enqueueAppNotify(order, F2fNotifyService.TYPE_TAKE_TICKET_OK, actualNum,
                request.getTakeTickeDate(), null);
        return TvmResponses.success();
    }

    /** 出票失败上报，含差额退款。 */
    public JSONObject receiveTakeTicketFailResult(NotiTakeTicketFailResultReqDTO request, String channel) {
        Integer actualNum = request.actualNum();
        if (actualNum == null) {
            log.warn("出票失败上报 actualTakeTicketNum 非法, orderNo={}, raw={}",
                    request.getOrderNo(), request.getActualTakeTicketNum());
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM);
        }
        F2fOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.warn("出票失败上报 订单不存在, orderNo={}", request.getOrderNo());
            return TvmResponses.takeTicketFailResultOrderNotFound();
        }

        F2fResultReport report = baseReport(REPORT_TAKE_TICKET_FAIL, request.getOrderNo(), channel,
                request.getDeviceId(), order.getTicketNum(), actualNum, request.getFaultOccurDate());
        report.setOptResult("1");
        report.setOptResultDesc("出票失败");
        report.setFaultSlipSeq(request.getFaultSlipSeq());
        report.setErrorCode(request.getErrorCode());
        report.setErrorMessage(request.getErrorMessage());
        report.setRawBody(request.getBizData());
        saveTickets(order, request.getTicketList(), TICKET_ISSUED);
        if (!insertReport(report)) {
            return TvmResponses.success();
        }

        int updated = orderMapper.updateStatus(request.getOrderNo(), List.of(ORDER_PAID),
                ORDER_FULFILL_FAILED, "出票失败: " + request.getErrorCode());
        if (updated == 0) {
            log.warn("出票失败上报 订单不在 PAID 状态，仅留上报记录不推进, orderNo={}, status={}",
                    request.getOrderNo(), order.getOrderStatus());
        }

        String refundNo = refundShortfall(order, actualNum, request.getFaultSlipSeq());
        enqueueAppNotify(order, F2fNotifyService.TYPE_TAKE_TICKET_FAIL, actualNum,
                request.getFaultOccurDate(), refundNo);
        return TvmResponses.success();
    }

    /**
     * 未出票部分的差额退款。
     *
     * @return 退款单号；未发起退款时返回 null
     */
    private String refundShortfall(F2fOrder order, int actualNum, String faultSlipSeq) {
        Integer orderNum = order.getTicketNum();
        Long price = order.getTicketPrice();
        if (orderNum == null || price == null || price <= 0) {
            log.error("出票失败但单价或购买张数缺失，无法算差额退款，需人工处理, orderNo={}, ticketNum={}, price={}",
                    order.getOrderNo(), orderNum, price);
            return null;
        }
        int shortfall = orderNum - actualNum;
        if (shortfall <= 0) {
            log.warn("出票失败但应退张数非正，不退款, orderNo={}, orderNum={}, actualNum={}",
                    order.getOrderNo(), orderNum, actualNum);
            return null;
        }

        F2fPayment payment = paymentMapper.selectLastAttempt(order.getOrderNo());
        String payCenterOrderNo = payment == null ? null : payment.getPayCenterOrderNo();
        RefundCommand command = new RefundCommand(order.getOrderNo(), null,
                F2fRefundService.SOURCE_TAKE_TICKET_FAIL, price * shortfall, shortfall,
                "出票故障退款,故障单号=" + faultSlipSeq, order.getDeviceId(), order.getOperatorId(),
                order.getTransType(), null, payCenterOrderNo);
        RefundOutcome outcome = refundService.refund(command);
        if (outcome.isRejected()) {
            log.error("出票失败差额退款被拒，需人工处理, orderNo={}, reason={}",
                    order.getOrderNo(), outcome.failureReason());
            return null;
        }
        log.info("出票失败差额退款已发起, orderNo={}, refundNo={}, shortfall={}, amount={}",
                order.getOrderNo(), outcome.refundNo(), shortfall, price * shortfall);
        return outcome.refundNo();
    }

    /**
     * 落上报记录，撞唯一索引即视为重传。
     *
     * @return true 表示本次是首报（可以继续做后续动作）；false 表示重传，调用方 MUST 直接回成功
     */
    private boolean insertReport(F2fResultReport report) {
        try {
            resultReportMapper.insert(report);
            return true;
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            log.info("上报重传，幂等返回成功, reportType={}, orderNo={}",
                    report.getReportType(), report.getOrderNo());
            return false;
        }
    }

    /** 落票明细。 */
    private void saveTickets(F2fOrder order, List<TicketInfo> ticketList, String ticketStatus) {
        if (ticketList == null || ticketList.isEmpty()) {
            return;
        }
        List<F2fTicket> tickets = new ArrayList<>(ticketList.size());
        LocalDateTime now = LocalDateTime.now();
        for (TicketInfo info : ticketList) {
            if (info == null || info.getTicketLogicNum() == null || info.getTicketLogicNum().isBlank()) {
                log.warn("出票明细缺少逻辑卡号，跳过该张, orderNo={}", order.getOrderNo());
                continue;
            }
            F2fTicket ticket = new F2fTicket();
            ticket.setOrderNo(order.getOrderNo());
            ticket.setTicketLogicNum(info.getTicketLogicNum());
            ticket.setTransDate(info.getTransDate());
            ticket.setTicketPrice(info.priceInFen() != null ? info.priceInFen() : order.getTicketPrice());
            ticket.setTicketStatus(ticketStatus);
            ticket.setEntryStationCode(order.getEntryStationCode());
            ticket.setExitStationCode(order.getExitStationCode());
            ticket.setCreateTms(now);
            ticket.setUpdateTms(now);
            tickets.add(ticket);
        }
        if (tickets.isEmpty()) {
            return;
        }
        try {
            ticketMapper.batchInsert(tickets);
        } catch (RuntimeException e) {
            if (!F2fDuplicateKey.isConflict(e)) {
                throw e;
            }
            log.info("批量插票撞唯一索引，降级逐张补插, orderNo={}, size={}", order.getOrderNo(), tickets.size());
            for (F2fTicket ticket : tickets) {
                try {
                    ticketMapper.insert(ticket);
                } catch (RuntimeException dup) {
                    if (!F2fDuplicateKey.isConflict(dup)) {
                        throw dup;
                    }
                    log.debug("该票已落库，跳过, ticketLogicNum={}, transDate={}",
                            ticket.getTicketLogicNum(), ticket.getTransDate());
                }
            }
        }
    }

    /** APP 来源的订单才需要回推结果（{@code TRANS_TYPE=03}）。 */
    private void enqueueAppNotify(F2fOrder order, String notifyType, int actualNum,
                                  String reportTms, String refundNo) {
        if (!TRANS_TYPE_APP_TAKE_TICKET.equals(order.getTransType())) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        if (F2fNotifyService.TYPE_TAKE_TICKET_FAIL.equals(notifyType)) {
            payload.put("userId", order.getThirdUserId());
        }
        payload.put("orderNo", order.getOrderNo());
        payload.put("orderTicketNum", order.getTicketNum() == null ? null
                : String.valueOf(order.getTicketNum()));
        payload.put("actualTakeTicketNum", String.valueOf(actualNum));
        payload.put("takeTickeDate", reportTms);
        if (refundNo != null) {
            payload.put("refundNo", refundNo);
        }
        notifyService.enqueue(notifyType, order.getOrderNo(), refundNo, payload);
    }

    /** 上报记录的公共字段。 */
    private F2fResultReport baseReport(String reportType, String orderNo, String channel, String deviceId,
                                      Integer orderTicketNum, Integer actualTicketNum, String reportTms) {
        F2fResultReport report = new F2fResultReport();
        report.setReportType(reportType);
        report.setOrderNo(orderNo);
        report.setChannel(channel);
        report.setDeviceId(deviceId);
        report.setOrderTicketNum(orderTicketNum);
        report.setActualTicketNum(actualTicketNum);
        report.setReportTms(reportTms);
        report.setReceiveTms(LocalDateTime.now());
        report.setProcessed("0");
        report.setCreateTms(LocalDateTime.now());
        return report;
    }

    /**
     * 补偿入口：按**已落库**的出票上报行重放「推进订单 → 差额退款 → 入队通知」三步。
     *
     * @return true 表示本行已收口、调用方可置 {@code PROCESSED='1'}；
     */
    boolean resumeFromReport(F2fResultReport report) {
        String orderNo = report.getOrderNo();
        Integer actualNum = report.getActualTicketNum();
        if (actualNum == null) {
            log.error("上报补偿 实际出票张数为空，无法重放，需人工处理, reportId={}, orderNo={}",
                    report.getId(), orderNo);
            return false;
        }
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.error("上报补偿 订单不存在，需人工处理, reportId={}, orderNo={}", report.getId(), orderNo);
            return false;
        }
        if (REPORT_TAKE_TICKET_OK.equals(report.getReportType())) {
            int updated = orderMapper.updateStatus(orderNo, List.of(ORDER_PAID), ORDER_FULFILLED, "出票成功(补偿)");
            log.info("上报补偿 出票成功重放, orderNo={}, 状态推进行数={}", orderNo, updated);
            enqueueAppNotify(order, F2fNotifyService.TYPE_TAKE_TICKET_OK, actualNum,
                    report.getReportTms(), null);
            return true;
        }
        if (REPORT_TAKE_TICKET_FAIL.equals(report.getReportType())) {
            int updated = orderMapper.updateStatus(orderNo, List.of(ORDER_PAID), ORDER_FULFILL_FAILED,
                    "出票失败(补偿): " + report.getErrorCode());
            log.info("上报补偿 出票失败重放, orderNo={}, 状态推进行数={}", orderNo, updated);
            String refundNo = refundShortfall(order, actualNum, report.getFaultSlipSeq());
            enqueueAppNotify(order, F2fNotifyService.TYPE_TAKE_TICKET_FAIL, actualNum,
                    report.getReportTms(), refundNo);
            return true;
        }
        log.warn("上报补偿 非出票上报类型，跳过, reportId={}, reportType={}",
                report.getId(), report.getReportType());
        return false;
    }

    /** 供出票失败后按票标记故障状态使用（BOM 侧按票退款时会读这个状态）。 */
    void markTicketsFault(String ticketLogicNum, String transDate) {
        ticketMapper.updateStatus(ticketLogicNum, transDate, List.of(TICKET_ISSUED), TICKET_FAULT);
    }
}
