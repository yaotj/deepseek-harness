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
 *
 * <p>承接 APP 对扣费失败订单的补款下单请求，透传到 face-pay-server
 * （2026-09-15 补款功能自 gate-txn-pay-server 迁入）。</p>
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
     *
     * <p>只生成补款单并返回补款单号，不发起支付。归属校验在 face-pay-server 侧完成：
     * bizData 传了 thirdUserId / cardId 就按其校验，未传则从原订单反推，
     * 详见 {@link SupplementOrderReqDTO} 的类注释。</p>
     */
    @PostMapping({"/ci/app/requestPayOrder", "/app/requestPayOrder"})
    public SupplementOrderRespDTO requestPayOrder(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-26 请求补款下单, 请求参数: {}", request);
        return facePayClient.requestPayOrder(parseBizData(request, SupplementOrderReqDTO.class));
    }

    /**
     * IF8A-35 查询用户账务信息：未支付订单数 + 扣费失败订单数。
     *
     * <p>只读，供 APP 做欠费提醒。落在 gate-txn-pay-server 而不是 account-server：
     * 数据源就是 {@code GATE_TXN_PAY}，account-server 没有任何账务表也没注入 GateTxnPayClient，
     * 经它中转只是多一跳。与同域的 IF8A-05 / IF8A-26 / IF8A-34 走同一条透传链路。</p>
     */
    @PostMapping({"/ci/app/requestUserAccInfo", "/app/requestUserAccInfo"})
    public RequestUserAccInfoResult requestUserAccInfo(@ModelAttribute ItpCommonFormRequest request) {
        log.info("IF8A-35 查询用户账务信息, 请求参数: {}", request);
        return gateTxnPayClient.requestUserAccInfo(parseBizData(request, RequestUserAccInfoReqDTO.class));
    }
}
