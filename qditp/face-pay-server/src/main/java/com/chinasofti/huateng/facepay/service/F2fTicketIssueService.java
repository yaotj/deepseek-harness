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

/**
 * 出票结果上报（IF2A-05 成功 / IF2A-06 失败）。TVM 与 BOM 共用——
 * 旧实现按 {@code providerId=03} 分流到两套代码，但两边做的是同一件事
 * （插主票记录 + 明细 + 失败时差额退款），新表统一后不再需要分流，只有 {@code CHANNEL} 不同。
 *
 * <h2>四条设计要点</h2>
 * <ol>
 *   <li><b>幂等靠 {@code UK_F2F_REPORT_IDEM (REPORT_TYPE, ORDER_NO)}。</b>
 *       规格要求设备断网后重传，旧实现无任何判重，重传就重复插主票+子票记录。
 *       这里直接 INSERT，撞索引即视为「已收到过」并回成功——这正是断网重传要的效果。</li>
 *   <li><b>接收与后续动作解耦。</b>本方法只负责落上报、落票、推进订单状态、把通知<b>入队</b>；
 *       通知投递由 {@code F2fNotifyJob} 扫表完成，NEVER 在设备请求线程里做 HTTP 推送。</li>
 *   <li><b>差额退款金额算不出来就不退。</b>旧实现在 {@code transType} 非 01/03 时
 *       {@code ticketPrice} 为空串，{@code new BigDecimal("")} 直接抛异常；
 *       更糟的是实际张数大于购买张数时算出<b>负数</b>仍会发起退款。
 *       这里两种情况都拒绝并留日志。</li>
 *   <li><b>不校验订单已支付就记出票，是旧实现的行为，这里改成白名单。</b>
 *       只有 {@code PAID} 的订单能记出票结果——未支付的订单出票属于对账事故，
 *       必须拒绝而不是默默记账。</li>
 * </ol>
 *
 * <p>不带 {@code @Transactional}：失败分支要调支付中心退款（AGENTS.md §5.2）。
 * 每条 SQL 自动提交，顺序是「<b>落票 → 落上报（幂等锚点）</b> → 推进订单 → 退款 → 入队通知」。</p>
 *
 * <p><b>落票 MUST 在落上报之前，NEVER 调换。</b>上报行是幂等闸门且单条自动提交：
 * 若先插上报、后插票，插票一旦失败（2026-09-10 双跑重放实测踩到 {@code ORA-01400}），
 * 上报行已提交，设备重传会被幂等挡住直接回 {@code 0000}，
 * <b>票永久丢失且设备侧看到的是成功</b>。反过来插票在前时，插票失败会抛到全局异常处理器、
 * 上报行不落库，设备重传能完整重跑一遍；票的插入本身靠
 * {@code UK_F2F_TICKET_LOGIC} 幂等，重跑不会重复。</p>
 */
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

    /** 出票成功上报。响应恒为 {@code 0000}（除参数与订单不存在），与旧契约一致。 */
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

    /** 出票失败上报，含差额退款。响应恒为 {@code 0000}——<b>退款成功与否不影响应答</b>（既有契约）。 */
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
     * <p>拒绝三种情况并只记日志（<b>不影响给设备的应答</b>）：单价缺失、应退张数非正、
     * 原支付流水查不到支付中心订单号。前两种旧实现会抛异常或退负数金额。</p>
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

    /**
     * 落票明细。
     *
     * <p>用 {@code batchInsert} 一次插完（Oracle 的 {@code INSERT ALL}）。
     * 撞 {@code UK_F2F_TICKET_LOGIC (TICKET_LOGIC_NUM, TRANS_DATE)} 说明这批票里有已落库的，
     * 此时<b>降级为逐张插</b>，把没落过的票补上——批量整批回滚会让本来能落的票也丢掉。</p>
     */
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
            // CREATE_TMS / UPDATE_TMS 是 NOT NULL 且表上没有默认值，漏赋值会抛
            // ORA-01400，整条出票上报退化成全局异常处理器的 UUID retCode。
            // 2026-09-10 双跑重放实测踩到，NEVER 依赖数据库默认值。
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

    /**
     * APP 来源的订单才需要回推结果（{@code TRANS_TYPE=03}）。
     *
     * <p><b>payload 的 4 个 key 逐字照搬旧 {@code noticeAppTakeTicketResult}</b>
     * （{@code TvmOrderServiceImpl:446-450}）：{@code orderNo / orderTicketNum /
     * actualTakeTicketNum / takeTickeDate}——最后一个<b>拼写少一个 t 是既有契约</b>，
     * APP 侧按这个 key 取值，NEVER 改名。退款场景额外带 {@code refundNo}。</p>
     *
     * <p><b>IF8B-07（{@code TAKE_TICKET_FAIL}）额外带 {@code userId}，IF8B-06 不带</b>
     * —— 2026-09-16 对 APP 网关做 A/B 实测确立（ADR-D112）。同一个已 {@code GIVEUP} 的真实
     * 订单 {@code 00202609161406170219}、同一套信封，只改 {@code bizData}：</p>
     * <ul>
     *   <li>{@code receiveTakeTicketFaultResult} 原 5 键（无 {@code userId}）→ {@code 7004 处理过程出现错误!}；
     *       加 {@code userId} 后 → {@code 7005 当前数据已经在处理中}（= 已解析、已受理）</li>
     *   <li>{@code receiveTakeTicketResult} 原 4 键（无 {@code userId}）→ <b>{@code 0000 成功}</b></li>
     * </ul>
     * <p>因此 <b>{@code userId} 不是这一族通知的通用必填项，只有 IF8B-07 那个端点要</b>：
     * NEVER 顺手给 IF8B-06 也加（它无 {@code userId} 已实测 {@code 0000}，改形态等于把一条已验证
     * 通过的链路推回未知），也 NEVER 因为「两条通知长得像」就把两个 payload 合并成一份。
     * 这同时是对 {@code AppNotifyClient} 类注释里「7004 = 对端处理异常、NEVER 补字段」那条的
     * <b>范围收窄</b>：那条结论只对 IF8B-05 {@code receivePaymentResult} 成立（该端点 7 组探针全 7004），
     * IF8B-07 的 7004 确实是我方缺 {@code userId}。</p>
     *
     * <p>{@code userId} 取 {@code THIRD_USER_ID}，与 IF8B-05 里唯一投递成功那条
     * （{@code F2fPayCenterFlow.enqueuePayResultNotify}，2026-09-16 14:06:25 实测 SUCCESS）同源。
     * 本方法只对 {@code TRANS_TYPE='03'} 的 APP 单入队，那些单该列必然非空。</p>
     *
     * <p>只入队、不投递。旧实现在这里同步发 HTTP，设备要陪着等一个超时。</p>
     */
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

    /** 上报记录的公共字段。{@code PROCESSED='0'} 让后续动作扫表任务能捞到。 */
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
     * <p>存在理由是本类那条幂等设计的另一面：{@code insertReport} 撞
     * {@code UK_F2F_REPORT_IDEM} 就直接回 {@code 0000}，因此**首报时落上报之后那几步一旦失败，
     * 设备重传会被幂等挡住、后续动作永久不再执行** —— 订单卡 {@code PAID}、差额退款不发起、
     * APP 收不到通知，且没有任何自愈路径。{@code PROCESSED} 列本来就是为这件事留的，
     * 但在 1.0.45 之前**没有任何调用方**（`selectPendingReports` / `markProcessed` 零引用）。
     *
     * <p><b>三步都幂等，所以重放安全</b>：状态推进是带 {@code PAID} 白名单的 CAS（重复即 0 行）；
     * 差额退款撞 {@code UK_F2F_REFUND_IDEM (ORIG_ORDER_NO, NVL(TICKET_LOGIC_NUM,'#WHOLE#'),
     * REFUND_SOURCE)} —— 出票故障退款是整单粒度 + 固定 source，同一单重放不会二次出款；
     * 通知入队撞 {@code UK_F2F_NOTIFY_IDEM}。**NEVER 在这里补「先查有没有做过」的判断**，
     * 那等于把幂等从唯一索引挪到应用层（本项目的既有取舍见 AGENTS.md §5.1 末条）。
     *
     * @return true 表示本行已收口、调用方可置 {@code PROCESSED='1'}；
     *         false 表示订单查不到或类型不属本任务，**留着 {@code '0'} 等人工**
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
