package com.chinasofti.huateng.facepay.controller.ci.app;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;
import com.chinasofti.huateng.facepay.api.device.DeviceRequests;
import com.chinasofti.huateng.facepay.api.device.app.AppRefundNotiResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.app.AppResponses;
import com.chinasofti.huateng.facepay.api.device.app.RequestAppPayResultReqDTO;
import com.chinasofti.huateng.facepay.api.device.app.RequestOrderReqDTO;
import com.chinasofti.huateng.facepay.api.device.app.RequestPayInfoReqDTO;
import com.chinasofti.huateng.facepay.api.device.app.RequestQueryActiveOrderReqDTO;
import com.chinasofti.huateng.facepay.api.paycenter.PayCenterCallbackRequest;
import com.chinasofti.huateng.facepay.service.F2fAppOrderService;
import com.chinasofti.huateng.facepay.service.F2fAppRefundService;
import com.chinasofti.huateng.facepay.service.supplement.SupplementOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 扫码取票接口。<b>URL 与旧服务一字不改</b>：类级 {@code /ci/app}。
 *
 * <p>旧 {@code CollectPayController}（{@code /ci/app/requestPay} 等四条）整个文件被注释掉，
 * 运行时并不存在，<b>因此本服务不实现它们</b>。</p>
 *
 * <p>错误码是第四套族（{@code 0000 / 8001 / 8003 / 8999 / 9999}），
 * 且 controller 与 service 层用的码不一致（下单 controller 回 8001、service 回 8003），
 * 见 {@link AppResponses} 类注释。</p>
 *
 * <p><b>本链路无验签、无归属校验</b>，与旧服务一致：{@code userId} 只判非空，
 * 不校验订单是否属于该用户。加鉴权属契约变更，需独立评审。</p>
 */
@RestController
@RequestMapping("/ci/app")
public class AppOrderController {

    private static final Logger log = LoggerFactory.getLogger(AppOrderController.class);

    private final F2fAppOrderService appOrderService;

    /**
     * 退款三条的宿主。2026-09-16 从 {@code F2fAppOrderService} 拆出（P1），
     * <b>URL 与响应形态一行未改</b>。
     */
    private final F2fAppRefundService appRefundService;

    private final SupplementOrderService supplementOrderService;

    public AppOrderController(F2fAppOrderService appOrderService,
                              F2fAppRefundService appRefundService,
                              SupplementOrderService supplementOrderService) {
        this.appOrderService = appOrderService;
        this.appRefundService = appRefundService;
        this.supplementOrderService = supplementOrderService;
    }

    /** IF8A-20 下单。参数校验失败回 {@code 8001}，照搬旧 controller。 */
    @PostMapping("/requestOrder")
    public JSONObject requestOrder(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 APP 取票下单请求, form={}", form);
        RequestOrderReqDTO request = DeviceRequests.unwrap(form, RequestOrderReqDTO.class);
        if (request == null) {
            return AppResponses.fail(AppResponses.CODE_INVALID_ORDER_PARAM, "请求报文不能为空");
        }
        if (isBlank(request.getUserId())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_ORDER_PARAM, "userId不能为空");
        }
        if (isBlank(request.getEntryStationCode())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_ORDER_PARAM, "entryStationCode不能为空");
        }
        if (isBlank(request.getExitStationCode())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_ORDER_PARAM, "exitStationCode不能为空");
        }
        if (isBlank(request.getTicketPrice())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_ORDER_PARAM, "ticketPrice不能为空");
        }
        if (isBlank(request.getSingelTicketNum())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_ORDER_PARAM, "singelTicketNum不能为空");
        }
        if (isBlank(request.getSingleTicketType())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_ORDER_PARAM, "singleTicketType不能为空");
        }
        return appOrderService.createOrder(request);
    }

    /**
     * IF8A-11 请求支付信息。
     *
     * <p>URL 是 {@code /requestPaymentInfo}——旧实现的 Javadoc 写成
     * {@code /ci/app/requestPayInfo}，是注释错误，以注解为准。</p>
     *
     * <p><b>{@code SP} 前缀的补款单走补款分支</b>：IF8A-26 建的单在 {@code SUPPLEMENT_ORDER}，
     * 取票单在 {@code F2F_ORDER}，两张表没有交集。缺这条分流时补款单恒返
     * {@code 9999 订单号错误}，APP 侧显示「生成订单失败」（2026-09-16 实测，
     * 同一笔在 10:52:18 建单成功、20ms 后取支付信息即失败）。
     * <b>按订单号前缀分流是因为 APP 侧无法改动</b>：它对两类单调的是同一个 URL、同一份报文。</p>
     */
    @PostMapping("/requestPaymentInfo")
    public JSONObject requestPaymentInfo(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 APP 请求支付信息, form={}", form);
        RequestPayInfoReqDTO request = DeviceRequests.unwrap(form, RequestPayInfoReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_ORDER_PARAM, "非法参数");
        }
        if (isBlank(request.getPayChannelCode())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_ORDER_PARAM, "非法参数");
        }
        if (request.getOrderNo().startsWith(SupplementOrderService.ORDER_NO_PREFIX)) {
            return supplementOrderService.requestPayInfo(request);
        }
        return appOrderService.requestPayInfo(request);
    }

    /** IF8A-18 支付结果查询。参数校验失败回 {@code 8003}。 */
    @PostMapping("/requestPayResult")
    public JSONObject requestPayResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 APP 支付结果查询, form={}", form);
        RequestAppPayResultReqDTO request = DeviceRequests.unwrap(form, RequestAppPayResultReqDTO.class);
        if (request == null || isBlank(request.getUserId())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数");
        }
        if (isBlank(request.getOrderNo())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数");
        }
        return appOrderService.queryPayResult(request);
    }

    /** 获取已激活的取票订单列表。 */
    @PostMapping("/requestPreActiveOrderList")
    public JSONObject requestPreActiveOrderList(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到获取激活订单列表请求, form={}", form);
        RequestQueryActiveOrderReqDTO request =
                DeviceRequests.unwrap(form, RequestQueryActiveOrderReqDTO.class);
        if (request == null || isBlank(request.getUserId())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数");
        }
        if (isBlank(request.getAppType())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数");
        }
        return appOrderService.listActiveOrders(request);
    }

    /**
     * 请求退款（整单）。
     *
     * <p><b>只校验 orderNo，不校验 userId</b>：旧实现这里连 request 判空都没有、也不校验
     * {@code userId}，缺 {@code userId} 时照样回 {@code 0000} 带 {@code refundResult}
     * （2026-09-11 新旧双打实测：旧返退款报文体、新曾返 {@code 8003 非法参数,userId不能为空}）。
     * 曾以「{@code userId} 要落到退款单 {@code OPERATOR_ID} 供对账追溯」为由补上该校验，
     * 与 {@code requestUpdateCardData} / BOM {@code paymentCode} 同属一类，按用户
     * 2026-09-11「不校验」的裁决移除。缺 {@code userId} 时退款单的 {@code OPERATOR_ID}
     * 为空，属既有形态，NEVER 再加回校验。</p>
     */
    @PostMapping("/requestRefundTicket")
    public JSONObject requestRefundTicket(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 APP 请求退款, form={}", form);
        RequestAppPayResultReqDTO request = DeviceRequests.unwrap(form, RequestAppPayResultReqDTO.class);
        if (request == null || isBlank(request.getOrderNo())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数,orderNo不能为空");
        }
        return appRefundService.requestRefund(request);
    }

    /** 退款结果查询。 */
    @PostMapping("/requestRefundTicketResult")
    public JSONObject requestRefundTicketResult(@ModelAttribute BaseDeviceRequest form) {
        log.info("接收到 APP 退款结果查询, form={}", form);
        RequestAppPayResultReqDTO request = DeviceRequests.unwrap(form, RequestAppPayResultReqDTO.class);
        if (request == null || isBlank(request.getUserId())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "userId不能为空");
        }
        if (isBlank(request.getOrderNo())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "orderNo不能为空");
        }
        return appRefundService.queryRefundResult(request);
    }

    /**
     * 支付中心退款结果回调。入参是<b>支付中心信封</b>，不是设备信封。
     *
     * <p>旧实现校验 {@code orderNo} 非空却用 {@code refundNo} 查库——只传
     * {@code orderNo} 时过校验但必然查不到。本实现两者都校验。</p>
     *
     * <p>⚠️ <b>不验签</b>，与旧实现一致。</p>
     */
    @PostMapping("/receiveRefundResult")
    public JSONObject receiveRefundResult(@ModelAttribute PayCenterCallbackRequest form) {
        log.info("接收到支付中心退款回调, form={}", form);
        if (form.getBizData() == null || form.getBizData().isBlank()) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数,bizData不能为空");
        }
        AppRefundNotiResultReqDTO request;
        try {
            request = JSON.parseObject(form.getBizData(), AppRefundNotiResultReqDTO.class);
        } catch (RuntimeException e) {
            log.error("退款回调 bizData 解析失败", e);
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数,bizData格式错误");
        }
        if (request == null || isBlank(request.getOrderNo())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数,orderNo不能为空");
        }
        if (isBlank(request.getRefundResult())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数,refundResult不能为空");
        }
        if (isBlank(request.getRefundNo())) {
            return AppResponses.fail(AppResponses.CODE_INVALID_PARAM, "非法参数,refundNo不能为空");
        }
        return appRefundService.receiveRefundResult(request);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
