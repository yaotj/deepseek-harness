package com.chinasofti.huateng.collectpay.controller.ci.bom;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.collectpay.constant.BomPayCodeEnum;
import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.NotiBusResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestGenNoCashOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestGetPayResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestPaymentReqDTO;
import com.chinasofti.huateng.collectpay.model.response.bom.BomOrderResult;
import com.chinasofti.huateng.collectpay.service.BomOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BOM非现金业务接口控制器。
 * 提供BOM终端与ITP平台之间的非现金收款业务接口，包括下单、支付、查询支付结果、业务操作结果通知等。
 */
@RestController
@RequestMapping("/ci/bom")
public class BomOrderController {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(BomOrderController.class);

    /**
     * BOM非现金业务服务。
     */
    @Autowired
    private BomOrderService bomOrderService;

    /**
     * 将公共参数复制到业务请求DTO中。
     * 将BaseRequestDTO中的公共参数（providerId、deviceId等）复制到业务DTO中，并解析bizData为业务参数。
     *
     * @param baseRequest 公共请求参数对象
     * @param clazz       业务DTO类型
     * @param <T>         业务DTO泛型
     * @return 包含公共参数和业务参数的业务DTO对象
     */
    private <T extends BaseRequestDTO> T copyBaseParams(BaseRequestDTO baseRequest, Class<T> clazz) {
        T request = JSON.parseObject(baseRequest.getBizData(), clazz);
        request.setProviderId(baseRequest.getProviderId());
        request.setCharset(baseRequest.getCharset());
        request.setFormat(baseRequest.getFormat());
        request.setTimestamp(baseRequest.getTimestamp());
        request.setDeviceId(baseRequest.getDeviceId());
        request.setSignType(baseRequest.getSignType());
        request.setSign(baseRequest.getSign());
        request.setBizData(baseRequest.getBizData());
        return request;
    }

    /**
     * IF8A-04 请求非现金收款下单。
     * BOM向ITP平台发起非现金收款订单请求，ITP生成订单并返回订单号。
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 响应结果，包含订单号
     */
    @PostMapping("/requestGenNoCashOrder")
    public JSONObject requestGenNoCashOrder(@RequestBody BaseRequestDTO baseRequest) {
        log.info("接收到BOM非现金收款下单请求, baseRequest={}", baseRequest);

        // 解析业务参数
        RequestGenNoCashOrderReqDTO request = copyBaseParams(baseRequest, RequestGenNoCashOrderReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        // 参数校验
        String validMsg = validateRequestGenNoCashOrder(request);
        if (validMsg != null) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), validMsg);
        }

        // 调用业务服务处理
        return bomOrderService.requestGenNoCashOrder(request);
    }

    /**
     * 校验非现金收款下单请求参数。
     *
     * @param request 请求参数
     * @return 校验失败信息，校验通过返回null
     */
    private String validateRequestGenNoCashOrder(RequestGenNoCashOrderReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getTransType())) {
            return "transType不能为空";
        }
        if (!StringUtils.hasText(request.getOperaterId())) {
            return "operaterId不能为空";
        }
        if (!StringUtils.hasText(request.getShiftId())) {
            return "shiftId不能为空";
        }
        if (!StringUtils.hasText(request.getTransAount())) {
            return "transAount不能为空";
        }
        if (!StringUtils.hasText(request.getBomOptSeq())) {
            return "bomOptSeq不能为空";
        }
        // 行政处理类型需要填写行政交易类型代码
        if ("42".equals(request.getTransType()) && !StringUtils.hasText(request.getAdminTransType())) {
            return "transType=42时adminTransType不能为空";
        }
        return null;
    }

    /**
     * IF8A-05 扫码支付。
     * BOM扫描用户付款码后，向ITP平台发起支付请求。
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 响应结果，包含支付结果
     */
    @PostMapping("/requestPayment")
    public JSONObject requestPayment(@RequestBody BaseRequestDTO baseRequest) {
        log.info("接收到BOM扫码支付请求, baseRequest={}", baseRequest);

        // 解析业务参数
        RequestPaymentReqDTO request = copyBaseParams(baseRequest, RequestPaymentReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        // 参数校验
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "orderNo不能为空");
        }
        if (!StringUtils.hasText(request.getPaymentVendor())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "paymentVendor不能为空");
        }

        // 调用业务服务处理
        return bomOrderService.requestPayment(request);
    }

    /**
     * IF8A-06 查询支付结果。
     * BOM轮询查询支付结果，ITP调用支付中心查询并返回支付状态。
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 响应结果，包含支付结果（SUCCESS/FAILED/PROCESSING）
     */
    @PostMapping("/requestGetPayResult")
    public JSONObject requestGetPayResult(@RequestBody BaseRequestDTO baseRequest) {
        log.info("接收到BOM查询支付结果请求, baseRequest={}", baseRequest);

        // 解析业务参数
        RequestGetPayResultReqDTO request = copyBaseParams(baseRequest, RequestGetPayResultReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        // 参数校验
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "orderNo不能为空");
        }

        // 调用业务服务处理
        return bomOrderService.requestGetPayResult(request);
    }

    /**
     * IF2A-08 业务操作结果通知。
     * BOM业务操作完成后，向ITP平台通知操作结果。
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 响应结果
     */
    @PostMapping("/notiBusResult")
    public JSONObject notiBusResult(@RequestBody BaseRequestDTO baseRequest) {
        log.info("接收到BOM业务操作结果通知, baseRequest={}", baseRequest);

        // 解析业务参数
        NotiBusResultReqDTO request = copyBaseParams(baseRequest, NotiBusResultReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        // 参数校验
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "orderNo不能为空");
        }
        if (!StringUtils.hasText(request.getOptResult())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "optResult不能为空");
        }

        // 调用业务服务处理
        return bomOrderService.notiBusResult(request);
    }
}