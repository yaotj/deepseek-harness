package com.chinasofti.huateng.collectpay.controller.ci.bom;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.collectpay.constant.BomPayCodeEnum;
import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.*;
import com.chinasofti.huateng.collectpay.model.response.bom.BomOrderResult;
import com.chinasofti.huateng.collectpay.service.BomOrderService;
import com.chinasofti.huateng.collectpay.utils.TransforUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BOM非现金业务接口控制器。
 * 提供BOM终端与ITP平台之间的非现金收款业务接口，包括下单、支付、查询支付结果、业务操作结果通知、充值结果通知等。
 */
@RestController
@RequestMapping("/itpbom/ci/bom")
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

    /**--------------------------------- 心跳检测 ---------------------------------------**/

    /**
     * 心跳检测
     */
    @PostMapping("/notiDeviceHeard")
    public JSONObject notiDeviceHeard(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("设备心跳检测, baseRequest={}", baseRequest);
        return BomOrderResult.success();
    }

    /**
     * IF8A-04 请求非现金收款下单。
     * BOM向ITP平台发起非现金收款订单请求，ITP生成订单并返回订单号。
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 响应结果，包含订单号
     */
    @PostMapping("/requestGenNoCashOrder")
    public JSONObject requestGenNoCashOrder(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到BOM非现金收款下单请求, baseRequest={}", baseRequest);

        // 解析业务参数
        RequestGenNoCashOrderReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestGenNoCashOrderReqDTO.class);
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
    public JSONObject requestPayment(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到BOM扫码支付请求, baseRequest={}", baseRequest);

        // 解析业务参数
        RequestPaymentReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestPaymentReqDTO.class);
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
    public JSONObject requestGetPayResult(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到BOM查询支付结果请求, baseRequest={}", baseRequest);

        // 解析业务参数
        RequestGetPayResultReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestGetPayResultReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        // 参数校验
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "orderNo不能为空");
        }

        // 调用业务服务处理
        return bomOrderService.requestGetPayResult(request.getOrderNo());
    }

    /**
     * IF2A-08 业务操作结果通知。
     * BOM业务操作完成后，向ITP平台通知操作结果。
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 响应结果
     */
    @PostMapping("/notiBusResult")
    public JSONObject notiBusResult(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到BOM业务操作结果通知, baseRequest={}", baseRequest);

        // 解析业务参数
        NotiBusResultReqDTO request = TransforUtils.copyBaseParams(baseRequest, NotiBusResultReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        // 参数校验
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "orderNo不能为空");
        }
        if (!StringUtils.hasText(request.getOptResult())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "optResult不能为空");
        }

        // 调用业务服务处理
        return bomOrderService.notiBusResult(request,"02");
    }

    /**
     * 7.3.3.5　IF2A-09 充值结果通知。
     * BOM充值操作完成后，向ITP平台通知充值结果。
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 响应结果
     */
    @PostMapping("/notiTopupResult")
    public JSONObject notiTopupResult(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到BOM充值结果通知, baseRequest={}", baseRequest);

        // 解析业务参数
        NotiTopupResultReqDTO request = TransforUtils.copyBaseParams(baseRequest, NotiTopupResultReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        // 参数校验
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "orderNo不能为空");
        }
        if (!StringUtils.hasText(request.getTopupStatus())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "topupStatus不能为空");
        }

        // 调用业务服务处理
        return bomOrderService.notiTopupResult(request);
    }

    /**
     * IF5A-01 请求票卡分析。
     * 后付费二维码票分析。
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 响应结果，包含票卡分析结果
     */
    @PostMapping("/requestCardDataAnalyse")
    public JSONObject requestCardDataAnalyse(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到BOM请求票卡分析请求, baseRequest={}", baseRequest);

        // 解析业务参数
        RequestCardDataAnalyseReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestCardDataAnalyseReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        // 参数校验
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "cardId不能为空");
        }
        if (!StringUtils.hasText(request.getUpdateType())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "updateType不能为空");
        }

        // 调用业务服务处理
        return bomOrderService.requestCardDataAnalyse(request);
    }

    /**
     * IF5A-03 请求票卡更新。
     * BOM票卡更新。
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 响应结果，包含最新行业数据
     */
    @PostMapping("/requestUpdateCardData")
    public JSONObject requestUpdateCardData(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到BOM请求票卡更新请求, baseRequest={}", baseRequest);

        // 解析业务参数
        RequestCardDataUpdateReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestCardDataUpdateReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        // 参数校验
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "cardId不能为空");
        }
        if (!StringUtils.hasText(request.getAdviceOpt())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "adviceOpt不能为空");
        }
        if (!StringUtils.hasText(request.getUpdateStationCode())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "updateStationCode不能为空");
        }
        if (!StringUtils.hasText(request.getOptDate())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "optDate不能为空");
        }

        // 调用业务服务处理
        return bomOrderService.requestCardDataUpdate(request);
    }


    /**
     * 单程票交易查询
     */
    @PostMapping("/requestOrderResult")
    public JSONObject requestOrderResult(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到BOM请, baseRequest={}", baseRequest);

        // 解析业务参数
        RequestOrderResultReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestOrderResultReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        // 参数校验
        if (request == null || !StringUtils.hasText(request.getTicketLogicNum())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "ticketLogicNum不能为空");
        }
        if (!StringUtils.hasText(request.getTransDate())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "transDate不能为空");
        }

        // 调用业务服务处理
        return bomOrderService.requestOrderTResult(request);
    }

    @PostMapping("/requestTicketRefund")
    public JSONObject requestTicketRefund(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到BOM请, baseRequest={}", baseRequest);

        // 解析业务参数
        RequestTicketRefundReqDTO request = TransforUtils.copyBaseParams(baseRequest, RequestTicketRefundReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        // 参数校验
        if (!StringUtils.hasText(request.getOrderNo())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "orderNo不能为空");
        }
        if (!StringUtils.hasText(request.getTicketLogicNum())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "ticketLogicNum不能为空");
        }
        if (!StringUtils.hasText(request.getTransAmount())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "transAmount不能为空");
        }
        if (!StringUtils.hasText(request.getTransType())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "transType不能为空");
        }

        // 调用业务服务处理
        return bomOrderService.requestTicketTRefund(request);
    }

    /**
     * IF5A-09 HCE票卡更新结果通知。
     * BOM更新HCE票数据后，向ITP平台通知更新结果。
     *
     * @param baseRequest 包含公共参数和业务参数的请求对象
     * @return 响应结果
     */
    @PostMapping("/notiUpdateHceData")
    public JSONObject notiUpdateHceData(@ModelAttribute BaseRequestDTO baseRequest) {
        log.info("接收到IF5A-09 HCE票卡更新结果通知, baseRequest={}", baseRequest);

        // 解析业务参数
        NotiUpdateHceDataReqDTO request = TransforUtils.copyBaseParams(baseRequest, NotiUpdateHceDataReqDTO.class);
        log.info("转换后请求参数为 {}", request);

        // 参数校验
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "cardId不能为空");
        }
        if (!StringUtils.hasText(request.getHceData())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "hceData不能为空");
        }

        // 调用业务服务处理
        return bomOrderService.notiUpdateHceData(request);
    }
}
