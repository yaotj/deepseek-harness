package com.chinasofti.huateng.collectpay.controller.ci.app;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import com.chinasofti.huateng.collectpay.model.request.PayCenterBaseRequestDTO;
import com.chinasofti.huateng.collectpay.model.request.app.RequestOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.app.RequestPayInfoReqDTO;
import com.chinasofti.huateng.collectpay.model.request.app.RequestQueryActiveOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.APPRefundNotiResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.collectpay.model.response.app.AppOrderResult;
import com.chinasofti.huateng.collectpay.service.AppOrderService;
import com.chinasofti.huateng.collectpay.utils.TransforUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * APP订单接口控制器。
 * 提供APP下单、支付、支付结果查询和支付结果通知等接口。
 */
@RestController
@Slf4j
@RequestMapping("/ci/app")
public class TvmAppOrderController {

    @Autowired
    private AppOrderService appOrderService;


    /**
     * IF8A-20 请求下单。
     * APP_SERVER向ITP平台发起下单请求。
     * 接口地址：/ci/app/requestOrder
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 应答结果，包含订单号
     */
    @PostMapping("/requestOrder")
    public JSONObject requestOrder(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到APP下单请求, baseRequest={}", baseRequest);

        RequestOrderReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestOrderReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        String validMsg = validateRequestOrder(request);
        if (validMsg != null) {
            log.info("参数校验失败: {}", validMsg);
            return AppOrderResult.fail("8001", validMsg);
        }

        return appOrderService.requestOrder(request);
    }

    /**
     * 校验请求下单参数。
     *
     * @param request 请求对象
     * @return 校验失败信息，校验通过返回null
     */
    private String validateRequestOrder(RequestOrderReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getUserId())) {
            return "userId不能为空";
        }
        if (!StringUtils.hasText(request.getEntryStationCode())) {
            return "entryStationCode不能为空";
        }
        if (!StringUtils.hasText(request.getExitStationCode())) {
            return "exitStationCode不能为空";
        }
        if (!StringUtils.hasText(request.getTicketPrice())) {
            return "ticketPrice不能为空";
        }
        if (!StringUtils.hasText(request.getSingelTicketNum())) {
            return "singelTicketNum不能为空";
        }
        if (!StringUtils.hasText(request.getSingleTicketType())) {
            return "singleTicketType不能为空";
        }
        return null;
    }


    /**
     * IF8A-11 请求支付信息。
     * APP_SERVER向ITP平台发起支付请求，ITP根据支付通道编码创建支付订单，
     * 请求对应的支付通道预下单，将预下单返回的支付信息签名后返回。
     * 接口地址：/ci/app/requestPayInfo
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 应答结果，包含支付通道编码、支付信息、签名类型和签名
     */
    @PostMapping("/requestPaymentInfo")
    public JSONObject requestPaymentInfo(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到APP请求支付信息, baseRequest={}", baseRequest);

        RequestPayInfoReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestPayInfoReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            log.info("参数校验失败，orderNo为空");
            return AppOrderResult.fail("8001", "非法参数");
        }
        if (!StringUtils.hasText(request.getPayChannelCode())) {
            log.info("参数校验失败，payChannelCode为空");
            return AppOrderResult.fail("8001", "非法参数");
        }

        return appOrderService.requestPayInfo(request);
    }

    /**
     * IF8A-18 支付结果查询。
     * APP_SERVER向ITP平台发起支付结果查询。
     * 先查数据库，如果数据库有成功或者失败的结果，则直接返回；
     * 如果没有成功或者失败的结果，则请求支付中心查询支付结果。
     * 接口地址：/ci/app/requestPayResult
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 应答结果，包含交易流水号、支付结果、支付金额、支付时间
     */
    @PostMapping("/requestPayResult")
    public JSONObject requestPayResult(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到APP支付结果查询请求, baseRequest={}", baseRequest);

        RequestPayResultReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestPayResultReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        if (request == null || !StringUtils.hasText(request.getUserId())) {
            log.info("参数校验失败，userId为空");
            return AppOrderResult.fail("8003", "非法参数");
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            log.info("参数校验失败，orderNo为空");
            return AppOrderResult.fail("8003", "非法参数");
        }

        return appOrderService.requestPayResult(request);
    }



    /**
     * app 获取激活订单
     * @param baseRequest
     * @return
     */
    @PostMapping("/requestPreActiveOrderList")
    public JSONObject requestPreActiveOrderList(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到APP获取激活订单请求, baseRequest={}", baseRequest);

        RequestQueryActiveOrderReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestQueryActiveOrderReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        if (request == null || !StringUtils.hasText(request.getUserId())) {
            log.info("参数校验失败，userId为空");
            return AppOrderResult.fail("8003", "非法参数");
        }
        if (!StringUtils.hasText(request.getAppType())) {
            log.info("参数校验失败，appType为空");
            return AppOrderResult.fail("8003", "非法参数");
        }

        return appOrderService.requestPreActiveOrderList(request);
    }


    /**
     * 请求退款
     * @param baseRequest
     * @return
     */
    @PostMapping("/requestRefundTicket")
    public JSONObject requestRefundTicket(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到APP退款请求, baseRequest={}", baseRequest);

        RequestPayResultReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestPayResultReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        if (!StringUtils.hasText(request.getOrderNo())) {
            log.info("参数校验失败，orderNo为空");
            return AppOrderResult.fail("8003", "非法参数,orderNo不能为空");
        }

        return appOrderService.requestRefundTicket(request);
    }

    /**
     * 退款结果查询
     * @param baseRequest
     * @return
     */
    @PostMapping("/requestRefundTicketResult")
    public JSONObject requestRefundTicketResult(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到APP退款结果查询请求, baseRequest={}", baseRequest);

        RequestPayResultReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestPayResultReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        if (request == null || !StringUtils.hasText(request.getUserId())) {
            log.info("参数校验失败，userId为空");
            return AppOrderResult.fail("8003", "userId不能为空");
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            log.info("参数校验失败，orderNo为空");
            return AppOrderResult.fail("8003", "orderNo不能为空");
        }

        return appOrderService.requestRefundTicketResult(request);
    }

    /**
     *  退款结果通知 支付中心通知itp , 当前只有app支付有退款回调通知
     * @param baseRequest
     * @return
     */
    @PostMapping("/receiveRefundResult")
    public JSONObject receiveRefundResult(@ModelAttribute PayCenterBaseRequestDTO baseRequest) {
        log.info("接收到APP退款结果通知请求, baseRequest={}", baseRequest);

        APPRefundNotiResultReqDTO request = TransforUtils.paycentercopyBaseParams(baseRequest, APPRefundNotiResultReqDTO.class);
        log.info("转换后请求参数为 {}", request);


        if (!StringUtils.hasText(request.getOrderNo())) {
            log.info("参数校验失败，orderNo不能为空");
            return AppOrderResult.fail("8003", "非法参数,orderNo不能为空");
        }
        if (!StringUtils.hasText(request.getRefundResult())) {
            log.info("参数校验失败，refundResult不能为空");
            return AppOrderResult.fail("8003", "非法参数,refundResult不能为空");
        }

        return appOrderService.receiveRefundResult(request);
    }

}