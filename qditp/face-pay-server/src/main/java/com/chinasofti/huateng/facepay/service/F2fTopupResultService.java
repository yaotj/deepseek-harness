package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.tvm.TopupCardFailNotiReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TopupCardResultNotiReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatusTransition;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.entity.F2fResultReport;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.mapper.F2fResultReportMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 充值结果侧：IF2A-06 成功通知、IF2A-07 失败通知（含全额退款）、BOM 的合体通知。本类刻意不带 {@code @Transactional}（链路里有支付中心调用），NEVER 加。 */
@Service
public class F2fTopupResultService {

    private static final Logger log = LoggerFactory.getLogger(F2fTopupResultService.class);

    private static final String STATUS_PAID = F2fOrderStatus.PAID.name();
    private static final String STATUS_FULFILLED = F2fOrderStatus.FULFILLED.name();
    private static final String STATUS_FULFILL_FAILED = F2fOrderStatus.FULFILL_FAILED.name();
    private static final String STATUS_REFUNDING = F2fOrderStatus.REFUNDING.name();

    /** 充值结果上报只在已收款后才有意义，白名单。 */
    private static final List<String> REPORTABLE =
            List.of(STATUS_PAID, STATUS_FULFILLED, STATUS_FULFILL_FAILED);

    private static final String REPORT_TOPUP_OK = "TOPUP_OK";
    private static final String REPORT_TOPUP_FAIL = "TOPUP_FAIL";

    /** {@code F2F_RESULT_REPORT.TOPUP_STATUS}：00 成功，01 失败。 */
    private static final String TOPUP_STATUS_OK = "00";

    private final F2fOrderMapper orderMapper;

    /** 只用于 {@link #payCenterOrderNoOf}（退款报文要带支付中心侧订单号）。 */
    private final F2fPaymentMapper paymentMapper;

    private final F2fResultReportMapper reportMapper;

    private final F2fRefundService refundService;

    public F2fTopupResultService(F2fOrderMapper orderMapper,
                                 F2fPaymentMapper paymentMapper,
                                 F2fResultReportMapper reportMapper,
                                 F2fRefundService refundService) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.reportMapper = reportMapper;
        this.refundService = refundService;
    }

    /** IF2A-06 充值成功通知。 */
    public JSONObject topupCardResultNoti(TopupCardResultNotiReqDTO request) {
        F2fOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("充值成功通知 订单不存在, orderNo={}", request.getOrderNo());
            return TvmResponses.fail(DeviceRetCode.ORDER_NO_ERROR);
        }
        boolean reportOnly = !REPORTABLE.contains(order.getOrderStatus());
        warnIfPhysicsNumMismatch(order, request.getTicketPhysicsNum(), "充值成功通知");
        if (!insertReport(buildOkReport(order, request))) {
            log.info("充值成功通知重复到达，幂等返回成功, orderNo={}", order.getOrderNo());
            return TvmResponses.success();
        }
        if (reportOnly) {
            log.warn("充值成功通知 订单未支付，只留上报不推进状态, orderNo={}, status={}",
                    order.getOrderNo(), order.getOrderStatus());
            return TvmResponses.success();
        }
        int updated = orderMapper.updateStatus(order.getOrderNo(), List.of(STATUS_PAID),
                STATUS_FULFILLED, "充值成功");
        log.info("充值成功通知处理完成, orderNo={}, statusUpdated={}", order.getOrderNo(), updated);
        return TvmResponses.success();
    }

    /** IF2A-07 充值失败通知。 */
    public JSONObject topupCardFailNoti(TopupCardFailNotiReqDTO request) {
        F2fOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("充值失败通知 订单不存在, orderNo={}", request.getOrderNo());
            return TvmResponses.fail(DeviceRetCode.ORDER_NO_ERROR);
        }
        boolean reportOnly = !REPORTABLE.contains(order.getOrderStatus())
                && !STATUS_REFUNDING.equals(order.getOrderStatus());
        warnIfPhysicsNumMismatch(order, request.getTicketPhysicsNum(), "充值失败通知");
        boolean firstReport = insertReport(buildFailReport(order, request));
        if (!firstReport) {
            log.info("充值失败通知重复到达，幂等返回成功, orderNo={}", order.getOrderNo());
            return TvmResponses.success();
        }
        if (reportOnly) {
            log.warn("充值失败通知 订单未支付，只留上报不推进状态也不退款, orderNo={}, status={}",
                    order.getOrderNo(), order.getOrderStatus());
            return TvmResponses.success();
        }
        warnIfConflict(order.getOrderNo(), orderMapper.updateStatus(order.getOrderNo(),
                        List.of(STATUS_PAID), STATUS_FULFILL_FAILED, "充值失败:" + request.getErrorCode()),
                F2fOrderStatus.FULFILL_FAILED, "TVM 充值失败");

        if (request.needRefund()) {
            submitFullRefund(order, request.getDeviceId());
        } else {
            log.info("充值失败通知 topupStatus={} 不触发退款, orderNo={}",
                    request.getTopupStatus(), order.getOrderNo());
        }
        return TvmResponses.success();
    }

    /**
     * IF2A-09 BOM 充值结果通知。
     *
     * @param topupStatus {@code 00} 成功 / {@code 01} 失败并退款
     */
    public JSONObject receiveBomTopupResult(String orderNo, String topupStatus,
                                            String deviceId, boolean needRefund, String rawBody) {
        F2fOrder order = orderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("BOM 充值结果通知 订单不存在, orderNo={}", orderNo);
            return BomResponses.failMessage("充值订单不存在");
        }
        boolean reportOnly = !REPORTABLE.contains(order.getOrderStatus())
                && !STATUS_REFUNDING.equals(order.getOrderStatus());
        F2fResultReport report = baseReport(order, needRefund ? REPORT_TOPUP_FAIL : REPORT_TOPUP_OK, deviceId);
        report.setTopupStatus(topupStatus);
        report.setTransAmount(order.getOrderAmount());
        report.setRawBody(rawBody);
        if (!insertReport(report)) {
            log.info("BOM 充值结果通知重复到达，幂等返回成功, orderNo={}", orderNo);
            return BomResponses.success();
        }
        if (reportOnly) {
            log.warn("BOM 充值结果通知 订单未支付，只留上报不推进状态也不退款, orderNo={}, status={}",
                    orderNo, order.getOrderStatus());
            return BomResponses.success();
        }
        if (!needRefund) {
            int updated = orderMapper.updateStatus(orderNo, List.of(STATUS_PAID),
                    STATUS_FULFILLED, "BOM充值成功");
            log.info("BOM 充值成功, orderNo={}, statusUpdated={}", orderNo, updated);
            return BomResponses.success();
        }
        warnIfConflict(orderNo, orderMapper.updateStatus(orderNo, List.of(STATUS_PAID),
                STATUS_FULFILL_FAILED, "BOM充值失败:" + topupStatus), F2fOrderStatus.FULFILL_FAILED, "BOM 充值失败");
        submitFullRefund(order, deviceId);
        return BomResponses.success();
    }

    /** 充值失败的全额退款。 */
    private void submitFullRefund(F2fOrder order, String deviceId) {
        if (order.getOrderAmount() == null || order.getOrderAmount() <= 0) {
            log.error("充值失败退款 订单金额非法，无法退款, orderNo={}, amount={}",
                    order.getOrderNo(), order.getOrderAmount());
            return;
        }
        RefundCommand command = new RefundCommand(order.getOrderNo(), null,
                F2fRefundService.SOURCE_TOPUP_FAIL, order.getOrderAmount(), null,
                "充值失败自动退款", deviceId, null, order.getTransType(),
                null, payCenterOrderNoOf(order.getOrderNo()));
        RefundOutcome outcome = refundService.refund(command);
        if (outcome.isRejected()) {
            log.error("充值失败退款被拒绝，需人工介入, orderNo={}, reason={}",
                    order.getOrderNo(), outcome.failureReason());
            return;
        }
        log.info("充值失败已提交退款, orderNo={}, refundNo={}, alreadyExisted={}",
                order.getOrderNo(), outcome.refundNo(), outcome.alreadyExisted());
    }

    /**
     * @return true 表示本次是首报；false 表示撞唯一索引（重复上报），调用方按幂等处理
     */
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

    private F2fResultReport buildOkReport(F2fOrder order, TopupCardResultNotiReqDTO request) {
        F2fResultReport report = baseReport(order, REPORT_TOPUP_OK, request.getDeviceId());
        report.setTopupStatus(TOPUP_STATUS_OK);
        report.setTransAmount(request.transAmountInFen());
        report.setAfterAmount(request.afterAmountInFen());
        report.setReportTms(request.getTransDate());
        report.setRawBody(request.toString());
        return report;
    }

    private F2fResultReport buildFailReport(F2fOrder order, TopupCardFailNotiReqDTO request) {
        F2fResultReport report = baseReport(order, REPORT_TOPUP_FAIL, request.getDeviceId());
        report.setTopupStatus(request.getTopupStatus());
        report.setTransAmount(order.getOrderAmount());
        report.setFaultSlipSeq(request.getFaultSlipSeq());
        report.setErrorCode(request.getErrorCode());
        report.setErrorMessage(request.getErrorMessage());
        report.setReportTms(request.getFaultOccurDate());
        report.setRawBody(request.toString());
        return report;
    }

    private F2fResultReport baseReport(F2fOrder order, String reportType, String deviceId) {
        F2fResultReport report = new F2fResultReport();
        report.setReportType(reportType);
        report.setOrderNo(order.getOrderNo());
        report.setChannel(order.getChannel());
        report.setDeviceId(deviceId == null ? order.getDeviceId() : deviceId);
        report.setOptResult(reportType);
        report.setProcessed("1");
        report.setReceiveTms(LocalDateTime.now());
        report.setCreateTms(LocalDateTime.now());
        return report;
    }

    /** 取支付中心侧原订单号供退款报文使用。 */
    private String payCenterOrderNoOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayCenterOrderNo();
    }

    /** 记「置失败态的 CAS 没命中」。 */
    private void warnIfConflict(String orderNo, int updatedRows, F2fOrderStatus target, String scene) {
        F2fOrderStatusTransition.Result transit = F2fOrderStatusTransition.classify(
                updatedRows, target, () -> orderMapper.selectOrderStatus(orderNo));
        if (transit.conflict()) {
            log.warn("F2F CAS 冲突 {} 未推进到 {}，仍按原口径应答, orderNo={}, observed={}",
                    scene, target, orderNo, transit.observedStatus());
        }
    }

    /**
     * 记「设备本次上送的物理卡号与下单时留证的不一致」。
     *
     * <p>只告警不拒绝：物理卡号不参与状态推进与金额计算，拒绝会把「钱已收、卡已充」的单挡在门外。
     * 下单时未留证的历史单（1.0.58 之前）一律跳过，那不是设备的问题。
     */
    private void warnIfPhysicsNumMismatch(F2fOrder order, String reported, String scene) {
        String stored = order.getTicketPhysicsNum();
        if (stored == null || stored.isBlank() || reported == null || reported.isBlank()) {
            return;
        }
        if (!stored.equals(reported)) {
            log.warn("{} 物理卡号与下单时不一致，仅告警不拒绝, orderNo={}, stored={}, reported={}",
                    scene, order.getOrderNo(), stored, reported);
        }
    }
}
