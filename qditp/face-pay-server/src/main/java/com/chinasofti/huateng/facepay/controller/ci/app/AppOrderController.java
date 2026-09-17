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

/** APP 扫码取票接口。 */
@RestController
@RequestMapping("/ci/app")
public class AppOrderController {

    private static final Logger log = LoggerFactory.getLogger(AppOrderController.class);

    private final F2fAppOrderService appOrderService;

    /** 退款三条的宿主。 */
    private final F2fAppRefundService appRefundService;

    private final SupplementOrderService supplementOrderService;

    public AppOrderController(F2fAppOrderService appOrderService,
                              F2fAppRefundService appRefundService,
                              SupplementOrderService supplementOrderService) {
        this.appOrderService = appOrderService;
        this.appRefundService = appRefundService;
        this.supplementOrderService = supplementOrderService;
    }

    /** IF8A-20 下单。 */
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

    /** IF8A-11 请求支付信息。 */
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

    /** IF8A-18 支付结果查询。 */
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

    /** 请求退款（整单）。 */
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

    /** 支付中心退款结果回调。 */
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
