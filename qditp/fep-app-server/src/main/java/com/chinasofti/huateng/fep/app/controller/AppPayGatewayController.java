package com.chinasofti.huateng.fep.app.controller;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.model.app.ItpCommonFormRequest;
import com.chinasofti.huateng.rpc.facepay.FacePayAppClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;


/**
 * APP 域网关：IF8A 系列 form 报文原样转发 face-pay。
 *
 * <p>8 条路径全部指向 face-pay 的 APP 域控制器，<b>本类只做透传，不承载任何业务分支</b>——
 * 补款单（SP 前缀）与非补款单的分流在 face-pay 的 {@code AppOrderController} 内部完成，
 * 判在前置层会让网关耦合补款业务（2026-11-20 改造，原实现在此处判 SP 前缀）。</p>
 *
 * <p>历史：本类原名 {@code CollectPayController}，转发的下游是 2026-10-13 弃用的 collect-pay-server。
 * 那个实例现仍在集群里但整站 403，NEVER 把下游改回它，见 docs/business/app-gateway.md §二②。</p>
 */
@RestController
public class AppPayGatewayController extends BaseAppController {

    private static final Logger log = LoggerFactory.getLogger(AppPayGatewayController.class);

    private final FacePayAppClient facePayAppClient;

    public AppPayGatewayController(FacePayAppClient facePayAppClient) {
        this.facePayAppClient = facePayAppClient;
    }

    @PostMapping({"/ci/app/requestOrder"})
    public JSONObject requestOrder(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-20 请求下单, request={}", request);
        return facePayAppClient.requestOrder(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/requestPaymentInfo"})
    public JSONObject requestPaymentInfo(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-11 请求支付信息, request={}", request);
        return facePayAppClient.requestPaymentInfo(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/requestPayResult"})
    public JSONObject requestPayResult(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-18 支付结果查询, request={}", request);
        return facePayAppClient.requestPayResult(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/requestRefundTicket"})
    public JSONObject requestRefundTicket(@ModelAttribute ItpCommonFormRequest request) {
        log.info("请求退款, request={}", request);
        return facePayAppClient.requestRefundTicket(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/requestRefundTicketResult"})
    public JSONObject requestRefundTicketResult(@ModelAttribute ItpCommonFormRequest request) {
        log.info("退款结果查询, request={}", request);
        return facePayAppClient.requestRefundTicketResult(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/requestPreActiveOrderList"})
    public JSONObject requestPreActiveOrderList(@ModelAttribute ItpCommonFormRequest request) {
        log.info("查询激活订单列表, request={}", request);
        return facePayAppClient.requestPreActiveOrderList(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/requestActiveTicket"})
    public JSONObject requestActiveTicket(@ModelAttribute ItpCommonFormRequest request) {
        log.info("TVM激活请求, request={}", request);
        return facePayAppClient.requestActiveTicket(toFormDataMap(request));
    }

    @PostMapping({"/ci/app/receiveRefundResult"})
    public JSONObject receiveRefundResult(@ModelAttribute ItpCommonFormRequest request) {
        log.info("接收退款结果通知, request={}", request);
        return facePayAppClient.receiveRefundResult(toFormDataMap(request));
    }

    /**
     * 公共参数原样透传（键顺序与 IF8A 报文一致），签名不重算。
     * NEVER 在这里补/改字段——APP 的签名覆盖这些参数，改一个字节对端就验签失败。
     */
    private Map<String, String> toFormDataMap(ItpCommonFormRequest request) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("providerId", request.getProviderId());
        map.put("charset", request.getCharset());
        map.put("format", request.getFormat());
        map.put("timestamp", request.getTimestamp());
        map.put("deviceId", request.getDeviceId());
        map.put("signType", request.getSignType());
        map.put("sign", request.getSign());
        map.put("bizData", request.getBizData());
        return map;
    }
}
