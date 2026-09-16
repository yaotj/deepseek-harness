package com.chinasofti.huateng.facepay.controller.ci.tvm;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;
import com.chinasofti.huateng.facepay.api.device.DeviceRequests;
import com.chinasofti.huateng.facepay.api.device.DeviceRetCode;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.tvm.NotiTakeTicketFailResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.NotiTakeTicketResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestActiveTicketReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestGenSjtOrderReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestPaymentReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestRefundReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestTakeTicketAuthReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.RequestTopupReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TopupCardFailNotiReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TopupCardResultNotiReqDTO;
import com.chinasofti.huateng.facepay.api.device.tvm.TvmResponses;
import com.chinasofti.huateng.facepay.api.paycenter.PayCenterCallbackRequest;
import com.chinasofti.huateng.facepay.api.paycenter.PayCenterResponses;
import com.chinasofti.huateng.facepay.api.paycenter.PayNoticeReqDTO;
import com.chinasofti.huateng.facepay.service.F2fDeviceHeartbeatService;
import com.chinasofti.huateng.facepay.service.F2fDeviceRefundService;
import com.chinasofti.huateng.facepay.service.F2fScanPayService;
import com.chinasofti.huateng.facepay.service.F2fTakeTicketService;
import com.chinasofti.huateng.facepay.service.F2fTicketIssueService;
import com.chinasofti.huateng.facepay.service.F2fTopupService;
import com.chinasofti.huateng.facepay.service.F2fTvmOrderService;
import com.chinasofti.huateng.facepay.service.F2fTvmPayResultService;
import com.chinasofti.huateng.facepay.support.F2fChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TVM 侧接口。<b>URL 与旧服务一字不改</b>：类级 {@code /itptvm/ci/tvm}，端口 58101，
 * 无 context-path，因此完整路径与 collect-pay-server 完全一致，蓝绿切换只改 K8s Service selector。
 *
 * <p>入参是 {@code @ModelAttribute} 表单绑定 + {@code bizData} 二次反序列化，
 * NEVER 改成 {@code @RequestBody}。</p>
 *
 * <p><b>本链路无验签</b>，与旧服务一致（AGENTS.md §2.2.1）。加鉴权属契约变更，需独立评审。</p>
 */
@RestController
@RequestMapping("/itptvm/ci/tvm")
public class TvmOrderController {

    private static final Logger log = LoggerFactory.getLogger(TvmOrderController.class);

    /** 商户编码 03 = BOM。本 URL 上按它分流到 BOM 柜台售票，与旧实现一致。 */
    private static final String PROVIDER_BOM = "03";

    private final F2fTvmOrderService tvmOrderService;

    /**
     * 支付结果侧（查询 / 回调 / 收银台反查）的宿主。2026-09-16 从 {@code F2fTvmOrderService}
     * 拆出（P2），<b>URL 与响应形态一行未改</b>。
     */
    private final F2fTvmPayResultService payResultService;

    private final F2fDeviceHeartbeatService heartbeatService;

    private final F2fTicketIssueService ticketIssueService;

    private final F2fDeviceRefundService deviceRefundService;

    private final F2fScanPayService scanPayService;

    private final F2fTakeTicketService takeTicketService;

    private final F2fTopupService topupService;

    public TvmOrderController(F2fTvmOrderService tvmOrderService,
                              F2fTvmPayResultService payResultService,
                              F2fDeviceHeartbeatService heartbeatService,
                              F2fTicketIssueService ticketIssueService,
                              F2fDeviceRefundService deviceRefundService,
                              F2fScanPayService scanPayService,
                              F2fTakeTicketService takeTicketService,
                              F2fTopupService topupService) {
        this.tvmOrderService = tvmOrderService;
        this.payResultService = payResultService;
        this.heartbeatService = heartbeatService;
        this.ticketIssueService = ticketIssueService;
        this.deviceRefundService = deviceRefundService;
        this.scanPayService = scanPayService;
        this.takeTicketService = takeTicketService;
        this.topupService = topupService;
    }

    /**
     * IF2A-04 设备心跳。
     *
     * <p><b>恒回 {@code 0000}</b>，即使 {@code deviceId} 缺失也不报错——心跳接口回失败会让
     * 设备侧告警刷屏，而设备号缺失是对端报文问题。与旧实现的差别是<b>现在真的落库了</b>：
     * 旧实现在 controller 里直接 return success，从不记录任何心跳。</p>
     */
    @PostMapping("/notiDeviceHeard")
    public JSONObject notiDeviceHeard(@ModelAttribute BaseDeviceRequest form) {
        log.debug("接收到 TVM 设备心跳, deviceId={}", form == null ? null : form.getDeviceId());
        if (form != null) {
            heartbeatService.recordHeartbeat(F2fChannel.TVM, form.getDeviceId(), null);
        }
        return TvmResponses.success();
    }

    /** IF2A-05 出票成功结果上报。重复上报由 {@code UK_F2F_REPORT_IDEM} 幂等挡住。 */
    @PostMapping("/notiTakeTicketResult")
    public JSONObject notiTakeTicketResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到出票成功结果上报, form={}", form);
        NotiTakeTicketResultReqDTO request = DeviceRequests.unwrap(form, NotiTakeTicketResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM, "orderNo不能为空");
        }
        return ticketIssueService.receiveTakeTicketResult(request, channelOf(request.getProviderId()));
    }

    /** IF2A-06 出票失败结果上报，含未出票部分的差额退款。 */
    @PostMapping("/notiTakeTicketFailResult")
    public JSONObject notiTakeTicketFailResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到出票失败结果上报, form={}", form);
        NotiTakeTicketFailResultReqDTO request =
                DeviceRequests.unwrap(form, NotiTakeTicketFailResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM, "orderNo不能为空");
        }
        return ticketIssueService.receiveTakeTicketFailResult(request, channelOf(request.getProviderId()));
    }

    /**
     * 设备侧主动退款。
     *
     * <p><b>错误码是 9999 而不是 2xxx</b>，逐字照搬旧 {@code RequestRefundRespDTO.fail}。
     * 同一个服务里三套错误码族并存（2xxx / 8999 / 9999）是既有契约，NEVER 统一。</p>
     */
    @PostMapping("/requestRefund")
    public JSONObject requestRefund(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到设备退款请求, form={}", form);
        RequestRefundReqDTO request = DeviceRequests.unwrap(form, RequestRefundReqDTO.class);
        if (request == null || isBlank(request.getOrderNo()) || isBlank(request.getRefundAmt())) {
            return TvmResponses.refundFail("订单号和退款金额不能为空");
        }
        return deviceRefundService.requestRefund(request);
    }

    /**
     * IF2A-01 提交单程票订单。
     *
     * <p>一条 URL 两个渠道：{@code providerId=03} 是 BOM 柜台售票，其余是 TVM 拉码。
     * 分流点与旧实现一致（旧 {@code TvmOrderController} 里的 {@code "03".equals(providerId)}），
     * 但<b>校验提到了分流之前</b>：两个渠道的必填项完全相同，旧实现只在 TVM 分支校验，
     * BOM 分支缺参时会在 {@code new BigDecimal(null)} 处抛 NPE、退化成 UUID retCode。</p>
     */
    @PostMapping("/requestGenSjtOrder")
    public JSONObject requestGenSjtOrder(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到提交单程票订单请求, form={}", form);
        RequestGenSjtOrderReqDTO request = DeviceRequests.unwrap(form, RequestGenSjtOrderReqDTO.class);
        if (request == null) {
            return TvmResponses.genSjtOrderFail(DeviceRetCode.INVALID_PARAM, "bizData不能为空或格式错误");
        }
        String invalid = validateGenSjtOrder(request);
        if (invalid != null) {
            return TvmResponses.genSjtOrderFail(DeviceRetCode.INVALID_PARAM, invalid);
        }
        if (PROVIDER_BOM.equals(request.getProviderId())) {
            return tvmOrderService.createBomSaleOrder(request);
        }
        return tvmOrderService.createSingleTicketOrder(request);
    }

    /** IF2A-03 查询支付结果。 */
    @PostMapping("/requestPayResult")
    public JSONObject requestPayResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到查询支付结果请求, form={}", form);
        RequestPayResultReqDTO request = DeviceRequests.unwrap(form, RequestPayResultReqDTO.class);
        if (request == null || request.getOrderNo() == null || request.getOrderNo().isBlank()) {
            return TvmResponses.payResultFail(DeviceRetCode.INVALID_PARAM, "orderNo不能为空");
        }
        return payResultService.queryPayResult(request);
    }

    /**
     * IF2A-11 付款码支付。<b>错误码族是 8999</b>（BOM 族），不是 TVM 的 2999——
     * 旧实现在这条 URL 上复用了 BOM 的返回组装，属既有契约。
     */
    @PostMapping("/requestPayment")
    public JSONObject requestPayment(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到付款码支付请求, form={}", form);
        RequestPaymentReqDTO request = DeviceRequests.unwrap(form, RequestPaymentReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return BomResponses.paymentResultFail(BomResponses.CODE_FAIL, "orderNo不能为空");
        }
        if (isBlank(request.getPaymentVendor())) {
            return BomResponses.paymentResultFail(BomResponses.CODE_FAIL, "paymentVendor不能为空");
        }
        if (isBlank(request.getPaymentCode())) {
            return BomResponses.paymentResultFail(BomResponses.CODE_FAIL, "paymentCode不能为空");
        }
        return scanPayService.requestPayment(request);
    }

    /** IF8A-15 激活取票订单。手机扫 TVM 二维码后调用。 */
    @PostMapping("/requestActiveTicket")
    public JSONObject requestActiveTicket(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到激活取票订单请求, form={}", form);
        RequestActiveTicketReqDTO request = DeviceRequests.unwrap(form, RequestActiveTicketReqDTO.class);
        if (request == null || isBlank(request.getOrderNo()) || isBlank(request.getDeviceId())
                || isBlank(request.getQrcodeGenDate()) || isBlank(request.getRandomFact())) {
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM);
        }
        return takeTicketService.requestActiveTicket(request);
    }

    /** IF2A-08 扫码取票订单查询。<b>没有 orderNo</b>，靠二维码三要素定位。 */
    @PostMapping("/requestTakeTicketAuth")
    public JSONObject requestTakeTicketAuth(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到扫码取票订单查询, form={}", form);
        RequestTakeTicketAuthReqDTO request = DeviceRequests.unwrap(form, RequestTakeTicketAuthReqDTO.class);
        if (request == null || isBlank(request.getDeviceId())
                || isBlank(request.getQrcodeGenDate()) || isBlank(request.getRandomFact())) {
            return TvmResponses.takeTicketAuthFail(DeviceRetCode.INVALID_PARAM,
                    DeviceRetCode.INVALID_PARAM.getMsg());
        }
        return takeTicketService.requestTakeTicketAuth(request);
    }

    /** IF2A-09 请求充值下单。 */
    @PostMapping("/requestTopup")
    public JSONObject requestTopup(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到充值下单请求, form={}", form);
        RequestTopupReqDTO request = DeviceRequests.unwrap(form, RequestTopupReqDTO.class);
        if (request == null || isBlank(request.getTicketPhysicsNum()) || isBlank(request.getTicketLogicNum())
                || isBlank(request.getBeforeAmount()) || isBlank(request.getTransAmount())) {
            return TvmResponses.topupFail(DeviceRetCode.INVALID_PARAM);
        }
        if (isBlank(request.getPayType())) {
            return TvmResponses.topupFail(DeviceRetCode.INVALID_PARAM, "payType不能为空");
        }
        return topupService.requestTopup(request);
    }

    /** IF2A-06 充值成功通知。重复上报由 {@code UK_F2F_REPORT_IDEM} 幂等挡住。 */
    @PostMapping("/topupCardResultNoti")
    public JSONObject topupCardResultNoti(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到充值成功通知, form={}", form);
        TopupCardResultNotiReqDTO request = DeviceRequests.unwrap(form, TopupCardResultNotiReqDTO.class);
        if (request == null || isBlank(request.getOrderNo()) || isBlank(request.getTicketLogicNum())
                || isBlank(request.getTicketPhysicsNum())) {
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM);
        }
        return topupService.topupCardResultNoti(request);
    }

    /** IF2A-07 充值失败通知，{@code topupStatus=01} 触发全额退款。 */
    @PostMapping("/topupCardFailNoti")
    public JSONObject topupCardFailNoti(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到充值失败通知, form={}", form);
        TopupCardFailNotiReqDTO request = DeviceRequests.unwrap(form, TopupCardFailNotiReqDTO.class);
        if (request == null || isBlank(request.getOrderNo()) || isBlank(request.getTicketLogicNum())
                || isBlank(request.getTicketPhysicsNum())) {
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM);
        }
        return topupService.topupCardFailNoti(request);
    }

    /**
     * 支付中心反查 ITP 订单详情。
     *
     * <p><b>这条不走 bizData</b>：调用方是支付中心收银台，{@code orderNo} 直接放在表单里，
     * 因此绑定后即用、不做二次反序列化。照搬旧实现的入参形态，NEVER 改成 unwrap。</p>
     */
    @PostMapping("/requestPayOrderDetail")
    public JSONObject requestPayOrderDetail(@ModelAttribute RequestPayResultReqDTO form) {
        log.info("接收到支付中心查询订单详情请求, orderNo={}", form == null ? null : form.getOrderNo());
        if (form == null || isBlank(form.getOrderNo())) {
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM);
        }
        return payResultService.requestPayOrderDetail(form);
    }

    /**
     * 支付结果回调（支付中心 → ITP），JSON body 形态。地址即旧配置 {@code pay.center.pay-notice}
     * 指向的 {@code /itptvm/ci/tvm/payNotice}，与预下单报文里的 {@code notifyUrl} 必须一致。
     *
     * <p><b>支付中心用 {@code application/json} 发请求</b>（网关文档第 26 行
     * 「Content-Type: application/json」），因此这条是主路径。重写初版只写了
     * {@code @ModelAttribute}，收 JSON body 时六个字段全 null、一律回「bizData不能为空」，
     * 等于回调从未被处理过。</p>
     *
     * <p>⚠️ <b>不验签</b>，与旧实现一致；风险见 {@link PayCenterCallbackRequest} 类注释。</p>
     */
    @PostMapping(value = "/payNotice", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JSONObject payNoticeJson(@RequestBody(required = false) PayCenterCallbackRequest body) {
        return handlePayNotice(body);
    }

    /**
     * 支付结果回调的 <b>form 兜底入口</b>：{@code x-www-form-urlencoded} 与不带
     * {@code Content-Type} 的请求都落到这里。
     *
     * <p>保留它的理由不是文档，而是<b>无法排除</b>：旧应用的 {@code payNotice} 用
     * {@code @ModelAttribute}（只能收 form），从上线到 2026-09-11 一次都没被真实调用过
     * （当天翻遍旧应用日志，{@code /itptvm/ci/tvm/payNotice} 入站记录为 0），
     * 所以「支付中心到底发 JSON 还是 form」在我方没有实证样本。两种都收下，
     * 比赌一种更稳；等真实回调到达后按日志确认形态，再决定是否收窄。</p>
     */
    @PostMapping("/payNotice")
    public JSONObject payNotice(@ModelAttribute PayCenterCallbackRequest form) {
        return handlePayNotice(form);
    }

    /**
     * 两个入口共用的处理逻辑。
     *
     * <p>{@code bizData} 走 {@link PayCenterCallbackRequest#bizDataJson()} 拿明文——
     * 网关的 {@code bizData} 是 Base64 后的 JSON，<b>NEVER 直接 parse</b>。</p>
     */
    private JSONObject handlePayNotice(PayCenterCallbackRequest request) {
        log.info("支付中心支付回调开始, request={}", request);
        if (request == null) {
            return PayCenterResponses.fail("bizData不能为空");
        }
        String bizDataJson = request.bizDataJson();
        if (bizDataJson == null) {
            return PayCenterResponses.fail("bizData不能为空");
        }
        PayNoticeReqDTO notice;
        try {
            notice = JSON.parseObject(bizDataJson, PayNoticeReqDTO.class);
        } catch (RuntimeException e) {
            log.error("支付回调 bizData 解析失败", e);
            return PayCenterResponses.fail("bizData格式错误");
        }
        if (notice == null) {
            return PayCenterResponses.fail("bizData格式错误");
        }
        return payResultService.receivePayNotice(notice);
    }

    /**
     * 校验顺序与文案逐字照搬旧实现，返回 null 表示通过。
     *
     * <p>旧实现<b>没有校验 payType</b>，而下游 {@code request.getPayType().equals("0")} 会在
     * payType 为空时抛 NPE、退化成全局异常处理器的 UUID retCode。这里补一条显式校验，
     * 是有意的行为修正：失败结果不变，但错误码从 500 变成 2002。</p>
     */
    private String validateGenSjtOrder(RequestGenSjtOrderReqDTO request) {
        if (isBlank(request.getTicketPrice())) {
            return "ticketPrice不能为空";
        }
        if (isBlank(request.getSingelTicketNum())) {
            return "singelTicketNum不能为空";
        }
        if (isBlank(request.getSingleTicketType())) {
            return "singleTicketType不能为空";
        }
        if ("0".equals(request.getSingleTicketType())) {
            if (isBlank(request.getEntryStationCode())) {
                return "按站点购票时entryStationCode不能为空";
            }
            if (isBlank(request.getExitStationCode())) {
                return "按站点购票时exitStationCode不能为空";
            }
        }
        if (isBlank(request.getPayType())) {
            return "payType不能为空";
        }
        return null;
    }

    /**
     * 上报类接口的渠道归属。
     *
     * <p>出票结果上报<b>本来就同时服务 TVM 与 BOM</b>：旧实现在同一个 URL 里按
     * {@code providerId=03} 分流到 BOM 的一套代码，两边做的是同一件事。
     * 新表统一后不再分流，只需把渠道码记对。</p>
     *
     * <p>{@code providerId} 缺失或非法时归到 TVM——这条 URL 挂在 {@code /itptvm} 下，
     * 默认归属 TVM 比归到 null 更符合事实（旧实现在这里是
     * {@code request.getProviderId().equals("03")}，缺失直接 NPE）。</p>
     */
    private static String channelOf(String providerId) {
        String channel = F2fChannel.fromProviderId(providerId);
        return channel == null ? F2fChannel.TVM : channel;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
