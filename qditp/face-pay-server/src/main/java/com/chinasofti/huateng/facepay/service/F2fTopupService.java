package com.chinasofti.huateng.facepay.service;

import com.chinasofti.huateng.facepay.domain.F2fDuplicateKey;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatus;
import com.chinasofti.huateng.facepay.domain.F2fOrderStatusTransition;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestTopupReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TopupCardFailNotiReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TopupCardResultNotiReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterClient;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterMessageFactory;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterPayCommand;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterRequest;
import com.chinasofti.huateng.facepay.channel.paycenter.PayCenterResult;
import com.chinasofti.huateng.facepay.channel.paycenter.PayScene;
import com.chinasofti.huateng.facepay.entity.F2fOrder;
import com.chinasofti.huateng.facepay.entity.F2fPayment;
import com.chinasofti.huateng.facepay.entity.F2fResultReport;
import com.chinasofti.huateng.facepay.mapper.F2fOrderMapper;
import com.chinasofti.huateng.facepay.mapper.F2fPaymentMapper;
import com.chinasofti.huateng.facepay.mapper.F2fResultReportMapper;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import com.chinasofti.huateng.facepay.support.F2fOrderNo;
import com.chinasofti.huateng.facepay.support.F2fOrderNoGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * TVM 票卡充值：IF2A-09 充值下单、IF2A-06 充值结果通知、IF2A-07 充值失败通知。
 *
 * <h2>与购票链路的关系</h2>
 * 充值和购票<b>共用 {@code F2F_ORDER}</b>，靠 {@code BIZ_TYPE}（01 购票 / 02 充值）区分。
 * 旧实现是 {@code TVM_TOPUP_ORDER} + {@code TVM_PAY_PRE_ORDER} 两张表，
 * 查支付结果时还要先查前置表拿 {@code transType} 再分流（{@code TvmOrderPreServiceImpl}）。
 * 表合一后这一层路由消失，{@code requestPayResult} 一个实现同时覆盖两种业务。
 *
 * <h2>充值失败必须退款</h2>
 * 钱已经收了但卡没充上，{@code topupCardFailNoti} 里 {@code topupStatus=01} 即触发全额退款。
 * 退款走 {@link F2fRefundService}，来源 {@code TOPUP_FAIL}，幂等键是
 * 「原订单号 + #WHOLE# + TOPUP_FAIL」，同一笔重复通知只会产生一张退款单。
 *
 * <p>整个类不带 {@code @Transactional}：下单链路里有支付中心调用，
 * 失败通知链路里有退款提交（同样是网络调用）。</p>
 */
@Service
public class F2fTopupService {

    private static final Logger log = LoggerFactory.getLogger(F2fTopupService.class);

    /** 业务类型：充值，对应 {@code CK_F2F_ORDER_BIZ} 的 02。 */
    private static final String BIZ_TOPUP = "02";

    private static final String STATUS_CREATED = F2fOrderStatus.CREATED.name();
    private static final String STATUS_PAYING = F2fOrderStatus.PAYING.name();
    private static final String STATUS_PAID = F2fOrderStatus.PAID.name();
    private static final String STATUS_PAY_FAILED = F2fOrderStatus.PAY_FAILED.name();
    private static final String STATUS_FULFILLED = F2fOrderStatus.FULFILLED.name();
    private static final String STATUS_FULFILL_FAILED = F2fOrderStatus.FULFILL_FAILED.name();
    private static final String STATUS_REFUNDING = F2fOrderStatus.REFUNDING.name();

    private static final List<String> PENDING = F2fOrderStatus.PENDING;

    /** 充值结果上报只在已收款后才有意义，白名单。 */
    private static final List<String> REPORTABLE = List.of(STATUS_PAID, STATUS_FULFILLED, STATUS_FULFILL_FAILED);

    private static final String REPORT_TOPUP_OK = "TOPUP_OK";
    private static final String REPORT_TOPUP_FAIL = "TOPUP_FAIL";

    /** {@code F2F_RESULT_REPORT.TOPUP_STATUS}：00 成功，01 失败。照搬旧 {@code getNotiy}。 */
    private static final String TOPUP_STATUS_OK = "00";

    private static final String PAY_SUBJECT = "地铁票卡充值";

    private static final String PAY_BODY = "地铁票卡充值";

    private final F2fOrderMapper orderMapper;

    private final F2fPaymentMapper paymentMapper;

    private final F2fResultReportMapper reportMapper;

    private final F2fOrderNoGenerator orderNoGenerator;

    private final PayCenterMessageFactory messageFactory;

    private final F2fPayCenterFlow payCenterFlow;

    private final F2fRefundService refundService;

    private final int qrcodeExpireSeconds;

    public F2fTopupService(F2fOrderMapper orderMapper,
                          F2fPaymentMapper paymentMapper,
                          F2fResultReportMapper reportMapper,
                          F2fOrderNoGenerator orderNoGenerator,
                          PayCenterMessageFactory messageFactory,
                          F2fPayCenterFlow payCenterFlow,
                          F2fRefundService refundService,
                          @Value("${f2f.order.qrcodeExpireSeconds:180}") int qrcodeExpireSeconds) {
        this.orderMapper = orderMapper;
        this.paymentMapper = paymentMapper;
        this.reportMapper = reportMapper;
        this.orderNoGenerator = orderNoGenerator;
        this.messageFactory = messageFactory;
        this.payCenterFlow = payCenterFlow;
        this.refundService = refundService;
        this.qrcodeExpireSeconds = qrcodeExpireSeconds;
    }
    /**
     * IF2A-09 充值下单。顺序：取号 → INSERT 订单（CREATED）→
     * {@code payType=0} 出本地聚合码返回；否则事务外调支付中心预下单。
     *
     * <p>旧实现<b>没校验金额格式</b>，{@code Integer.parseInt} 在非数字时抛异常、
     * 退化成全局异常处理器的 UUID retCode。这里显式挡在前面，回 2002。</p>
     */
    public JSONObject requestTopup(RequestTopupReqDTO request) {
        Long transAmount = request.transAmountInFen();
        Long beforeAmount = request.beforeAmountInFen();
        if (transAmount == null || beforeAmount == null) {
            log.warn("充值下单入参非数字, transAmount={}, beforeAmount={}",
                    request.getTransAmount(), request.getBeforeAmount());
            return TvmResponses.topupFail(DeviceRetCode.INVALID_PARAM, "transAmount或beforeAmount不是合法数字");
        }
        if (transAmount <= 0) {
            return TvmResponses.topupFail(DeviceRetCode.INVALID_PARAM, "transAmount必须为正数");
        }

        String orderNo = orderNoGenerator.next(F2fOrderNo.BIZ_TOPUP);
        LocalDateTime now = LocalDateTime.now();
        orderMapper.insert(buildOrder(orderNo, request, transAmount, beforeAmount, now));
        log.info("充值下单已落库, orderNo={}, transAmount={}, ticketLogicNum={}",
                orderNo, transAmount, request.getTicketLogicNum());

        if ("0".equals(request.getPayType())) {
            String payUrl = messageFactory.buildAggregateCodePayUrl(orderNo);
            paymentMapper.insert(buildPayment(orderNo, transAmount, request.getPayType(),
                    "INIT", payUrl, null, now));
            log.info("充值 payType=0 走本地聚合码, orderNo={}", orderNo);
            return TvmResponses.topupSuccess(orderNo, payUrl);
        }
        return preOrderAtPayCenter(orderNo, transAmount, request.getPayType(), now);
    }

    /** <b>必须在事务外</b>：中间那次 {@code execute} 是网络调用。 */
    private JSONObject preOrderAtPayCenter(String orderNo, long amount, String payType, LocalDateTime now) {
        PayCenterRequest message = messageFactory.buildPayRequest(new PayCenterPayCommand(
                orderNo, PayScene.QRCODE, null, payType, amount, PAY_SUBJECT, PAY_BODY, null));
        paymentMapper.insert(buildPayment(orderNo, amount, payType, "INIT", null, message.getBizData(), now));

        F2fPayCenterFlow.Submitted submitted = payCenterFlow.submit(new F2fPayCenterFlow.SubmitSpec(
                orderNo, 1, message, "二维码串",
                new F2fPayCenterFlow.RejectTransition(PENDING, "充值预下单失败:"),
                false, "充值预下单"));
        return switch (submitted) {
            case F2fPayCenterFlow.Submitted.Accepted accepted ->
                    TvmResponses.topupSuccess(orderNo, accepted.result().string("data"));
            case F2fPayCenterFlow.Submitted.Rejected ignored -> TvmResponses.topupFail(DeviceRetCode.FAIL);
            case F2fPayCenterFlow.Submitted.Unknown ignored -> TvmResponses.topupFail(DeviceRetCode.FAIL);
            // 充值拉码没开同步支付状态判定，构造上不可能收到。
            case F2fPayCenterFlow.Submitted.SyncPaid ignored ->
                    throw new IllegalStateException("充值拉码不应收到同步支付成功, orderNo=" + orderNo);
        };
    }
    /**
     * IF2A-06 充值成功通知。落一条 {@code TOPUP_OK} 上报并把订单推进到 {@code FULFILLED}。
     *
     * <p>幂等：{@code UK_F2F_REPORT_IDEM}（reportType + orderNo）挡重复上报，
     * 撞唯一索引直接回成功——设备重推不该收到失败。</p>
     *
     * <p>旧实现只 INSERT 通知记录，<b>订单状态一直停在支付成功</b>，
     * 于是「已收款未完成业务」的每日批量退款任务无法区分「真的没充上」和「充好了没记账」。
     * 这里补上状态推进。</p>
     *
     * <p><b>订单未支付时只留上报、不推状态</b>（{@code reportOnly}）。旧实现除了「订单不存在」
     * 之外<b>完全不看状态</b>，一律 INSERT 通知并回 {@code 0000}（{@code TvmTopupServiceImpl:281-297}）。
     * 曾经改成「非可上报状态回 2005」，但支付中心 payNotice 迟到时 TVM 可能已经把卡充好并上报，
     * 拒收等于把这条唯一的现场证据丢掉，且设备不会再补。因此保留旧的「收下」语义，
     * 只把**状态推进**锁在 {@code PAID} 上——两者是独立的两件事，
     * NEVER 因为「白名单优先」把上报也一起拒掉。2026-09-11 新旧双打实测：旧 {@code 0000}、新 {@code 2005}。</p>
     */
    public JSONObject topupCardResultNoti(TopupCardResultNotiReqDTO request) {
        F2fOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("充值成功通知 订单不存在, orderNo={}", request.getOrderNo());
            return TvmResponses.fail(DeviceRetCode.ORDER_NO_ERROR);
        }
        boolean reportOnly = !REPORTABLE.contains(order.getOrderStatus());
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

    /**
     * IF2A-07 充值失败通知。落 {@code TOPUP_FAIL} 上报；{@code topupStatus=01} 时全额退款。
     *
     * <p><b>退款提交失败不影响本接口回成功</b>：上报已经落库，退款单也已落库（状态 INIT），
     * {@code F2fRefundReconcileJob} 会重试。回失败只会让设备无意义重推。
     * 旧实现在这里靠 {@code doRefund} 的返回值决定是否记退款单号，而那个方法
     * <b>恒返回 true</b>（{@code TvmCommonServiceImpl}），等于没有判断。</p>
     *
     * <p><b>订单未支付时只留上报、不推状态、也不退款</b>（{@code reportOnly}），口径同
     * {@link #topupCardResultNoti}。旧实现不看状态，连未支付单也照样进 {@code topupStatus=01}
     * 的退款分支（{@code TvmTopupServiceImpl:330-346}）——钱没收到却发起退款，
     * 这一处 <b>NEVER 照搬</b>。</p>
     */
    public JSONObject topupCardFailNoti(TopupCardFailNotiReqDTO request) {
        F2fOrder order = orderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("充值失败通知 订单不存在, orderNo={}", request.getOrderNo());
            return TvmResponses.fail(DeviceRetCode.ORDER_NO_ERROR);
        }
        boolean reportOnly = !REPORTABLE.contains(order.getOrderStatus())
                && !STATUS_REFUNDING.equals(order.getOrderStatus());
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
            submitRefund(order, request);
        } else {
            log.info("充值失败通知 topupStatus={} 不触发退款, orderNo={}",
                    request.getTopupStatus(), order.getOrderNo());
        }
        return TvmResponses.success();
    }

    /** 全额退款。<b>必须在事务外</b>：内部会调支付中心。 */
    private void submitRefund(F2fOrder order, TopupCardFailNotiReqDTO request) {
        if (order.getOrderAmount() == null || order.getOrderAmount() <= 0) {
            log.error("充值失败退款 订单金额非法，无法退款, orderNo={}, amount={}",
                    order.getOrderNo(), order.getOrderAmount());
            return;
        }
        RefundCommand command = new RefundCommand(order.getOrderNo(), null,
                F2fRefundService.SOURCE_TOPUP_FAIL, order.getOrderAmount(), null,
                "充值失败自动退款", request.getDeviceId(), null, order.getTransType(),
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
     * IF2A-09 BOM 充值结果通知。与 TVM 的两个通知接口是同一件事，
     * 只是 BOM 把成功与失败合到一个接口、用 {@code topupStatus} 区分。
     *
     * <p>因此复用同一套上报类型（{@code TOPUP_OK} / {@code TOPUP_FAIL}）与同一条退款路径，
     * <b>返回体是 BOM 族</b>（{@code 0000} / {@code 8999}）。</p>
     *
     * <p>旧实现有三处问题在此修掉：①查的是 TVM 充值订单表而不是 BOM 自己的表（表合一后不再有此问题）；
     * ②不检查是否已退款，重复 {@code 01} 通知会重复退款；
     * ③{@code afterAmount} 直接复制 {@code transAmount}，数据是错的——这里不再写该列。</p>
     *
     * <p><b>订单未支付时只留上报、不推状态、也不退款</b>（{@code reportOnly}），口径与
     * {@link #topupCardResultNoti} / {@link #topupCardFailNoti} 完全一致。旧
     * {@code BomOrderServiceImpl.notiTopupResult:698-861} 同样只校验订单存在，
     * 记完通知就按 {@code topupStatus=01} 无条件退款（连状态都直接改成 {@code '2'} 支付失败）——
     * 「收下上报」照搬，「没收到钱也退款」<b>NEVER 照搬</b>。</p>
     *
     * <p>事务形态两边现已一致：旧方法的 {@code @Transactional} 已于 2026-09-14 摘除
     * （它把支付中心退款调用包在了事务里，违反 AGENTS.md 5.2），本类本来就整个不带事务。
     * 因此 <b>NEVER 因为「旧的有事务」而给本类加 {@code @Transactional}</b>。</p>
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
        submitTopupRefund(order, deviceId);
        return BomResponses.success();
    }

    /** 全额退款。<b>必须在事务外</b>：内部会调支付中心。 */
    private void submitTopupRefund(F2fOrder order, String deviceId) {
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

    /** @return true 表示本次是首报；false 表示撞唯一索引（重复上报），调用方按幂等处理 */
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

    private F2fOrder buildOrder(String orderNo, RequestTopupReqDTO request,
                                long transAmount, long beforeAmount, LocalDateTime now) {
        F2fOrder order = new F2fOrder();
        order.setOrderNo(orderNo);
        order.setChannel(F2fChannel.TVM);
        order.setBizType(BIZ_TOPUP);
        order.setOrderStatus(STATUS_CREATED);
        order.setOrderAmount(transAmount);
        order.setDeviceId(request.getDeviceId());
        order.setCardId(request.getTicketLogicNum());
        order.setCardBeforeAmount(beforeAmount);
        order.setActivateFlag("0");
        order.setExpireTms(now.plusSeconds(qrcodeExpireSeconds));
        order.setCreateTms(now);
        order.setUpdateTms(now);
        return order;
    }

    private F2fPayment buildPayment(String orderNo, long amount, String payType, String payStatus,
                                    String payUrl, String requestBody, LocalDateTime now) {
        F2fPayment payment = new F2fPayment();
        payment.setOrderNo(orderNo);
        payment.setAttemptNo(1);
        payment.setPayScene(PayScene.QRCODE.getCode());
        payment.setPayStatus(payStatus);
        payment.setPayAmount(amount);
        payment.setPayType(payType);
        payment.setPayUrl(payUrl);
        payment.setRequestBody(requestBody);
        payment.setRequestTms(now);
        payment.setCreateTms(now);
        payment.setUpdateTms(now);
        return payment;
    }

    /**
     * 取支付中心侧原订单号供退款报文使用。
     *
     * <p>旧实现这里硬编码空串（{@code BomOrderServiceImpl:640} 留着「根据实际情况填写」的注释），
     * 支付中心只能靠商户订单号定位。这里取真值。</p>
     */
    private String payCenterOrderNoOf(String orderNo) {
        F2fPayment last = paymentMapper.selectLastAttempt(orderNo);
        return last == null ? null : last.getPayCenterOrderNo();
    }

    /**
     * 记「置失败态的 CAS 没命中」。口径与 {@code F2fTvmOrderService.warnIfConflict} 一致：
     * <b>只告警、不改应答、不拒绝请求</b>，改一处 MUST 看齐其余三处（TVM / BOM 扫码 / APP）。
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
