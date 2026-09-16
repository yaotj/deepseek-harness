package com.chinasofti.huateng.facepay.controller.ci.bom;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;
import com.chinasofti.huateng.facepay.api.device.DeviceRequests;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.bom.NotiBusResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.NotiTopupResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.NotiUpdateHceDataReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestGenNoCashOrderReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestOrderResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.bom.RequestTicketRefundReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.NotiTakeTicketFailResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.NotiTakeTicketResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestPaymentReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.service.F2fBomOrderService;
import com.chinasofti.huateng.facepay.service.F2fDeviceHeartbeatService;
import com.chinasofti.huateng.facepay.service.F2fHceService;
import com.chinasofti.huateng.facepay.service.F2fScanPayService;
import com.chinasofti.huateng.facepay.service.F2fTicketIssueService;
import com.chinasofti.huateng.facepay.service.F2fTopupService;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BOM 侧接口。<b>URL 与旧服务一字不改</b>：类级 {@code /itpbom/ci/bom}。
 *
 * <p>错误码族是 BOM 的 {@code 0000 / 8999 / 80xx}（{@code BomPayCodeEnum}），
 * 与 TVM 的 2xxx 不同。校验失败统一用 {@code 8003 非法参数}——
 * 但旧实现的 {@code BomOrderResult.fail(8003, ...)} 与 {@code failMessage(8999, ...)}
 * 混用，本实现照搬各端点的实际码值，见每个方法。</p>
 *
 * <p><b>本链路无验签</b>，与旧服务一致（AGENTS.md §2.2.1）。</p>
 */
@RestController
@RequestMapping("/itpbom/ci/bom")
public class BomOrderController {

    private static final Logger log = LoggerFactory.getLogger(BomOrderController.class);

    /** 非法参数，BOM 族。旧实现的各端点校验失败都用这个码。 */
    private static final String CODE_INVALID_PARAM = "8003";

    private final F2fBomOrderService bomOrderService;

    private final F2fDeviceHeartbeatService heartbeatService;

    private final F2fScanPayService scanPayService;

    private final F2fTopupService topupService;

    private final F2fHceService hceService;

    private final F2fTicketIssueService ticketIssueService;

    /**
     * 构造注入 BOM 域的六个业务服务。
     *
     * @param bomOrderService    BOM 非现金订单（下单 / 支付结果 / 退款）
     * @param heartbeatService   设备心跳落库
     * @param scanPayService     扫码付
     * @param topupService       储值卡充值
     * @param hceService         HCE 票卡分析与补站（IF5A-01 / IF5A-03）
     * @param ticketIssueService 出票结果上报，供本类两条 BOM 前缀别名复用
     */
    public BomOrderController(F2fBomOrderService bomOrderService,
                             F2fDeviceHeartbeatService heartbeatService,
                             F2fScanPayService scanPayService,
                             F2fTopupService topupService,
                             F2fHceService hceService,
                             F2fTicketIssueService ticketIssueService) {
        this.bomOrderService = bomOrderService;
        this.heartbeatService = heartbeatService;
        this.scanPayService = scanPayService;
        this.topupService = topupService;
        this.hceService = hceService;
        this.ticketIssueService = ticketIssueService;
    }

    /**
     * 设备心跳。<b>恒回 0000</b>。与旧实现的差别是<b>现在真的落库了</b>——
     * 旧实现在 controller 里直接 return success，从不记录心跳。
     */
    @PostMapping("/notiDeviceHeard")
    public JSONObject notiDeviceHeard(@ModelAttribute BaseDeviceRequest form) {
        log.debug("接收到 BOM 设备心跳, deviceId={}", form == null ? null : form.getDeviceId());
        if (form != null) {
            heartbeatService.recordHeartbeat(F2fChannel.BOM, form.getDeviceId(), null);
        }
        return BomResponses.success();
    }

    /** IF8A-04 非现金收款下单。{@code bomOptSeq} 是幂等键。 */
    @PostMapping("/requestGenNoCashOrder")
    public JSONObject requestGenNoCashOrder(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 BOM 非现金收款下单请求, form={}", form);
        RequestGenNoCashOrderReqDTO request = DeviceRequests.unwrap(form, RequestGenNoCashOrderReqDTO.class);
        if (request == null) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "请求报文不能为空");
        }
        if (isBlank(request.getTransType())) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "transType不能为空");
        }
        if (isBlank(request.getOperaterId())) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "operaterId不能为空");
        }
        if (isBlank(request.getShiftId())) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "shiftId不能为空");
        }
        if (isBlank(request.getTransAount())) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "transAount不能为空");
        }
        if (isBlank(request.getBomOptSeq())) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "bomOptSeq不能为空");
        }
        if (isBlank(request.getDeviceId())) {
            return BomResponses.orderNoFail(CODE_INVALID_PARAM, "deviceId不能为空");
        }
        return bomOrderService.createNoCashOrder(request);
    }

    /**
     * IF8A-05 扫码支付。与 TVM 的 {@code requestPayment} 是同一实现、同一响应族——
     * 旧实现里 TVM 那条 URL 本来就复用了 BOM 的代码。
     *
     * <p><b>只校验 {@code orderNo} 与 {@code paymentVendor}，不校验 {@code paymentCode}</b>：
     * 旧实现对缺 {@code paymentCode} 的报文照样往下走、真去调支付中心，最后回
     * {@code 0000 + paymentResult=FAILED/支付失败}（2026-09-11 新旧双打实测：旧返 FAILED、
     * 新曾返 {@code 8003 paymentCode不能为空}）。缺渠道码时支付中心必然拒付、不会动钱，
     * 因此照搬旧口径。与 {@code requestUpdateCardData} 同一条裁决（用户 2026-09-11「不校验」），
     * NEVER 再以「补齐校验」为理由加回来。</p>
     *
     * <p>注意 BOM 这两个字段是<b>错位</b>的既有形态：{@code paymentCode} 实际是渠道码，
     * {@code paymentVendor} 实际是付款码。NEVER「修正」字段名。</p>
     */
    @PostMapping("/requestPayment")
    public JSONObject requestPayment(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 BOM 扫码支付请求, form={}", form);
        RequestPaymentReqDTO request = DeviceRequests.unwrap(form, RequestPaymentReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return BomResponses.paymentResultFail(CODE_INVALID_PARAM, "orderNo不能为空");
        }
        if (isBlank(request.getPaymentVendor())) {
            return BomResponses.paymentResultFail(CODE_INVALID_PARAM, "paymentVendor不能为空");
        }
        return scanPayService.requestPayment(request);
    }

    /**
     * IF8A-06 查询支付结果。BOM 会轮询本接口。
     *
     * <p>旧实现的 {@code requestPayment} 在服务端 {@code Thread.sleep} 轮询本方法最长 180 秒；
     * 新实现让 BOM 自己轮询，服务端不再阻塞（AGENTS.md §5.2）。</p>
     */
    @PostMapping("/requestGetPayResult")
    public JSONObject requestGetPayResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 BOM 查询支付结果请求, form={}", form);
        RequestPayResultReqDTO request = DeviceRequests.unwrap(form, RequestPayResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return BomResponses.paymentResultFail(CODE_INVALID_PARAM, "orderNo不能为空");
        }
        return scanPayService.queryPayResult(request.getOrderNo());
    }

    /** IF2A-08 业务操作结果通知。{@code optResult=FAILED} 触发原单全额退款。 */
    @PostMapping("/notiBusResult")
    public JSONObject notiBusResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 BOM 业务操作结果通知, form={}", form);
        NotiBusResultReqDTO request = DeviceRequests.unwrap(form, NotiBusResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return BomResponses.fail(CODE_INVALID_PARAM, "orderNo不能为空");
        }
        if (isBlank(request.getOptResult())) {
            return BomResponses.fail(CODE_INVALID_PARAM, "optResult不能为空");
        }
        return bomOrderService.receiveBusResult(request);
    }

    /** IF2A-09 充值结果通知。{@code topupStatus=01} 是失败并退款，见 DTO 注释。 */
    @PostMapping("/notiTopupResult")
    public JSONObject notiTopupResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 BOM 充值结果通知, form={}", form);
        NotiTopupResultReqDTO request = DeviceRequests.unwrap(form, NotiTopupResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return BomResponses.fail(CODE_INVALID_PARAM, "orderNo不能为空");
        }
        if (isBlank(request.getTopupStatus())) {
            return BomResponses.fail(CODE_INVALID_PARAM, "topupStatus不能为空");
        }
        return topupService.receiveBomTopupResult(request.getOrderNo(), request.getTopupStatus(),
                request.getDeviceId(), request.needRefund(), request.toString());
    }

    /** 单程票交易查询。 */
    @PostMapping("/requestOrderResult")
    public JSONObject requestOrderResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到单程票交易查询请求, form={}", form);
        RequestOrderResultReqDTO request = DeviceRequests.unwrap(form, RequestOrderResultReqDTO.class);
        if (request == null || isBlank(request.getTicketLogicNum())) {
            return BomResponses.orderResultFail(CODE_INVALID_PARAM, "ticketLogicNum不能为空");
        }
        if (isBlank(request.getTransDate())) {
            return BomResponses.orderResultFail(CODE_INVALID_PARAM, "transDate不能为空");
        }
        return bomOrderService.requestOrderResult(request);
    }

    /** 单程票退款。 */
    @PostMapping("/requestTicketRefund")
    public JSONObject requestTicketRefund(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到单程票退款请求, form={}", form);
        RequestTicketRefundReqDTO request = DeviceRequests.unwrap(form, RequestTicketRefundReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return BomResponses.refundFail(CODE_INVALID_PARAM, "orderNo不能为空");
        }
        if (isBlank(request.getTicketLogicNum())) {
            return BomResponses.refundFail(CODE_INVALID_PARAM, "ticketLogicNum不能为空");
        }
        if (isBlank(request.getTransAmount())) {
            return BomResponses.refundFail(CODE_INVALID_PARAM, "transAmount不能为空");
        }
        if (isBlank(request.getTransType())) {
            return BomResponses.refundFail(CODE_INVALID_PARAM, "transType不能为空");
        }
        return bomOrderService.requestTicketRefund(request);
    }

    /** IF5A-01 票卡分析，透传 ticket-server。 */
    @PostMapping("/requestCardDataAnalyse")
    public JSONObject requestCardDataAnalyse(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到票卡分析请求, form={}", form);
        RequestCardDataAnalyseReqDTO request = DeviceRequests.unwrap(form, RequestCardDataAnalyseReqDTO.class);
        if (request == null || isBlank(request.getCardId())) {
            return BomResponses.cardDataAnalyseFail(CODE_INVALID_PARAM, "cardId不能为空");
        }
        if (isBlank(request.getUpdateType())) {
            return BomResponses.cardDataAnalyseFail(CODE_INVALID_PARAM, "updateType不能为空");
        }
        return hceService.requestCardDataAnalyse(request);
    }

    /**
     * IF5A-03 票卡更新，透传 ticket-server。
     *
     * <p><b>校验项与旧实现逐条对齐，2026-09-11 新旧双打逐字段实测确认</b>：
     * 旧服务校验 {@code cardId} / {@code adviceOpt} / {@code updateStationCode} /
     * {@code optDate}（四条都回 {@code 8003 xxx不能为空}），但<b>不校验
     * {@code updateType}、也不校验 {@code optDate} 的格式</b>——这两种情况旧服务直接把请求
     * 透传给 ticket-server，返回下游码（实测 {@code 8004 未注册用户}）。
     * 曾按「补齐校验」加过这两条，用户 2026-09-11 裁决「不校验」，已移除。
     * NEVER 再以「旧实现漏校验」为理由加回来。</p>
     */
    @PostMapping("/requestUpdateCardData")
    public JSONObject requestUpdateCardData(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到票卡更新请求, form={}", form);
        RequestCardDataUpdateReqDTO request = DeviceRequests.unwrap(form, RequestCardDataUpdateReqDTO.class);
        if (request == null || isBlank(request.getCardId())) {
            return BomResponses.cardDataUpdateFail(CODE_INVALID_PARAM, "cardId不能为空");
        }
        if (isBlank(request.getAdviceOpt())) {
            return BomResponses.cardDataUpdateFail(CODE_INVALID_PARAM, "adviceOpt不能为空");
        }
        if (isBlank(request.getUpdateStationCode())) {
            return BomResponses.cardDataUpdateFail(CODE_INVALID_PARAM, "updateStationCode不能为空");
        }
        if (isBlank(request.getOptDate())) {
            return BomResponses.cardDataUpdateFail(CODE_INVALID_PARAM, "optDate不能为空");
        }
        return hceService.requestUpdateCardData(request);
    }

    /** IF5A-09 HCE 票卡更新结果通知。 */
    @PostMapping("/notiUpdateHceData")
    public JSONObject notiUpdateHceData(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 HCE 票卡更新结果通知, deviceId={}", form == null ? null : form.getDeviceId());
        NotiUpdateHceDataReqDTO request = DeviceRequests.unwrap(form, NotiUpdateHceDataReqDTO.class);
        if (request == null || isBlank(request.getCardId())) {
            return BomResponses.fail(CODE_INVALID_PARAM, "cardId不能为空");
        }
        if (isBlank(request.getHceData())) {
            return BomResponses.fail(CODE_INVALID_PARAM, "hceData不能为空");
        }
        return hceService.receiveHceUpdateResult(request);
    }

    /**
     * IF2A-05 出票成功结果上报的 <b>BOM 前缀别名</b>，与
     * {@code /itptvm/ci/tvm/notiTakeTicketResult} 同一实现、同一响应族（2xxx）。
     *
     * <p><b>为什么有这条别名</b>：甲方规格把出票上报归在 TVM 前缀下，
     * 旧 collect-pay-server 也只在 {@code /itptvm/ci/tvm} 上挂过它，
     * 但现场有设备按 {@code /itpbom/ci/bom/notiTakeTicketResult} 上报（2026-09-16 实测：
     * 订单 {@code 00202609160953330199} 已 {@code PAID}、票已实际出，
     * 上报却落到静态资源解析、被全局异常处理器兜成 HTTP 200 + UUID {@code retCode}，
     * 订单永久卡在 {@code PAID}、{@code F2F_TICKET} / {@code F2F_RESULT_REPORT} 零行）。
     * 设备侧 URL 不在我方控制内，故服务端兼容收下，见 ADR-D97。</p>
     *
     * <p>渠道归属默认 {@code BOM}（本 URL 挂在 {@code /itpbom} 下），
     * {@code providerId} 合法时以它为准 —— 与 {@code TvmOrderController} 的
     * {@code channelOf} 只差兜底值，NEVER 把兜底值改成 TVM。</p>
     */
    @PostMapping("/notiTakeTicketResult")
    public JSONObject notiTakeTicketResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到出票成功结果上报(BOM 前缀), form={}", form);
        NotiTakeTicketResultReqDTO request = DeviceRequests.unwrap(form, NotiTakeTicketResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM, "orderNo不能为空");
        }
        return ticketIssueService.receiveTakeTicketResult(request, channelOf(request.getProviderId()));
    }

    /** IF2A-06 出票失败结果上报的 <b>BOM 前缀别名</b>，成因与上一条相同（ADR-D97）。 */
    @PostMapping("/notiTakeTicketFailResult")
    public JSONObject notiTakeTicketFailResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到出票失败结果上报(BOM 前缀), form={}", form);
        NotiTakeTicketFailResultReqDTO request =
                DeviceRequests.unwrap(form, NotiTakeTicketFailResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM, "orderNo不能为空");
        }
        return ticketIssueService.receiveTakeTicketFailResult(request, channelOf(request.getProviderId()));
    }

    /** 本类两条上报别名的渠道归属：{@code providerId} 非法时兜底 BOM。 */
    private static String channelOf(String providerId) {
        String channel = F2fChannel.fromProviderId(providerId);
        return channel == null ? F2fChannel.BOM : channel;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
