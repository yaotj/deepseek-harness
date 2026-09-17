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
import com.chinasofti.huateng.facepay.service.F2fTopupResultService;
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

/** TVM 侧接口。 */
@RestController
@RequestMapping("/itptvm/ci/tvm")
public class TvmOrderController {

    private static final Logger log = LoggerFactory.getLogger(TvmOrderController.class);

    /** 商户编码 03 = BOM。 */
    private static final String PROVIDER_BOM = "03";

    private final F2fTvmOrderService tvmOrderService;

    /** 支付结果侧（查询 / 回调 / 收银台反查）的宿主。 */
    private final F2fTvmPayResultService payResultService;

    private final F2fDeviceHeartbeatService heartbeatService;

    private final F2fTicketIssueService ticketIssueService;

    private final F2fDeviceRefundService deviceRefundService;

    private final F2fScanPayService scanPayService;

    private final F2fTakeTicketService takeTicketService;

    private final F2fTopupService topupService;

    /** 充值结果上报（IF2A-06 / IF2A-07）的宿主。 */
    private final F2fTopupResultService topupResultService;

    public TvmOrderController(F2fTvmOrderService tvmOrderService,
                              F2fTvmPayResultService payResultService,
                              F2fDeviceHeartbeatService heartbeatService,
                              F2fTicketIssueService ticketIssueService,
                              F2fDeviceRefundService deviceRefundService,
                              F2fScanPayService scanPayService,
                              F2fTakeTicketService takeTicketService,
                              F2fTopupService topupService,
                              F2fTopupResultService topupResultService) {
        this.tvmOrderService = tvmOrderService;
        this.payResultService = payResultService;
        this.heartbeatService = heartbeatService;
        this.ticketIssueService = ticketIssueService;
        this.deviceRefundService = deviceRefundService;
        this.scanPayService = scanPayService;
        this.takeTicketService = takeTicketService;
        this.topupService = topupService;
        this.topupResultService = topupResultService;
    }

    /** IF2A-04 设备心跳。 */
    @PostMapping("/notiDeviceHeard")
    public JSONObject notiDeviceHeard(@ModelAttribute BaseDeviceRequest form) {
        log.debug("接收到 TVM 设备心跳, deviceId={}", form == null ? null : form.getDeviceId());
        if (form != null) {
            heartbeatService.recordHeartbeat(F2fChannel.TVM, form.getDeviceId(), null);
        }
        return TvmResponses.success();
    }

    /** IF2A-05 出票成功结果上报。 */
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

    /** 设备侧主动退款。 */
    @PostMapping("/requestRefund")
    public JSONObject requestRefund(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到设备退款请求, form={}", form);
        RequestRefundReqDTO request = DeviceRequests.unwrap(form, RequestRefundReqDTO.class);
        if (request == null || isBlank(request.getOrderNo()) || isBlank(request.getRefundAmt())) {
            return TvmResponses.refundFail("订单号和退款金额不能为空");
        }
        return deviceRefundService.requestRefund(request);
    }

    /** IF2A-01 提交单程票订单。 */
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

    /** IF2A-11 付款码支付。 */
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

    /** IF8A-15 激活取票订单。 */
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

    /** IF2A-08 扫码取票订单查询。 */
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

    /** IF2A-06 充值成功通知。 */
    @PostMapping("/topupCardResultNoti")
    public JSONObject topupCardResultNoti(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到充值成功通知, form={}", form);
        TopupCardResultNotiReqDTO request = DeviceRequests.unwrap(form, TopupCardResultNotiReqDTO.class);
        if (request == null || isBlank(request.getOrderNo()) || isBlank(request.getTicketLogicNum())
                || isBlank(request.getTicketPhysicsNum())) {
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM);
        }
        return topupResultService.topupCardResultNoti(request);
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
        return topupResultService.topupCardFailNoti(request);
    }

    /** 支付中心反查 ITP 订单详情。 */
    @PostMapping("/requestPayOrderDetail")
    public JSONObject requestPayOrderDetail(@ModelAttribute RequestPayResultReqDTO form) {
        log.info("接收到支付中心查询订单详情请求, orderNo={}", form == null ? null : form.getOrderNo());
        if (form == null || isBlank(form.getOrderNo())) {
            return TvmResponses.fail(DeviceRetCode.INVALID_PARAM);
        }
        return payResultService.requestPayOrderDetail(form);
    }

    /** 支付结果回调（支付中心 → ITP），JSON body 形态。 */
    @PostMapping(value = "/payNotice", consumes = MediaType.APPLICATION_JSON_VALUE)
    public JSONObject payNoticeJson(@RequestBody(required = false) PayCenterCallbackRequest body) {
        return handlePayNotice(body);
    }

    /** 支付结果回调的 form 兜底入口：{@code x-www-form-urlencoded} 与不带 {@code Content-Type} 的请求都落到这里。 */
    @PostMapping("/payNotice")
    public JSONObject payNotice(@ModelAttribute PayCenterCallbackRequest form) {
        return handlePayNotice(form);
    }

    /** 两个入口共用的处理逻辑。 */
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

    /** 校验顺序与文案逐字照搬旧实现，返回 null 表示通过。 */
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

    /** 上报类接口的渠道归属。 */
    private static String channelOf(String providerId) {
        String channel = F2fChannel.fromProviderId(providerId);
        return channel == null ? F2fChannel.TVM : channel;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
