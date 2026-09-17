package com.chinasofti.huateng.fep.app.controller;

import com.chinasofti.huateng.model.app.ItpCommonFormRequest;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.pay.SupplementOrderReqDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderRespDTO;
import com.chinasofti.huateng.rpc.facepay.FacePayClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 过闸补款接口入口。
 */
@RestController
public class GateTxnPayController extends BaseAppController {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayController.class);

    private final GateTxnPayClient gateTxnPayClient;
    private final FacePayClient facePayClient;

    public GateTxnPayController(GateTxnPayClient gateTxnPayClient, FacePayClient facePayClient) {
        this.gateTxnPayClient = gateTxnPayClient;
        this.facePayClient = facePayClient;
    }

    /**
     * IF8A-26 请求补款下单。
     */
    @PostMapping({"/ci/app/requestPayOrder", "/app/requestPayOrder"})
    public SupplementOrderRespDTO requestPayOrder(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-26 请求补款下单, 请求参数: {}", request);
        return facePayClient.requestPayOrder(parseBizData(request, SupplementOrderReqDTO.class));
    }

    /**
     * IF8A-35 查询用户账务信息：未支付订单数 + 扣费失败订单数。
     */
    @PostMapping({"/ci/app/requestUserAccInfo", "/app/requestUserAccInfo"})
    public RequestUserAccInfoResult requestUserAccInfo(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-35 查询用户账务信息, 请求参数: {}", request);
        return gateTxnPayClient.requestUserAccInfo(parseBizData(request, RequestUserAccInfoReqDTO.class));
    }
}
