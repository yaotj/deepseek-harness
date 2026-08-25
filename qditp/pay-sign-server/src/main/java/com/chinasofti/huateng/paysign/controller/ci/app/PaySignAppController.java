package com.chinasofti.huateng.paysign.controller.ci.app;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.app.*;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.model.request.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.service.PaySignService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 地铁APP签约渠道
 */
@RestController
@RequestMapping("/ci/app")
public class PaySignAppController {
    private static final Logger log = LoggerFactory.getLogger(PaySignAppController.class);

    @Autowired
    private PaySignService paySignService;

    @PostMapping("/requestContractAdvisory")
    public RequestContractAdvisoryRespDTO requestContractAdvisory(@RequestBody RequestContractAdvisoryReqDTO request) {
        log.info("接收到信用能力咨询报文: {}", request);
        // 地铁APP专属入口，固定签约渠道为 METRO_APP
        return paySignService.requestContractAdvisory(request, SignChannelEnum.METRO_APP.getCode());
    }

    @PostMapping("/requestContractResult")
    public RequestContractResultRespDTO requestContractResult(@RequestBody RequestContractResultReqDTO request) {
        log.info("接收到签约结果咨询报文: {}", request);
        // 地铁APP专属入口，固定签约渠道为 METRO_APP
        return paySignService.requestContractResult(request, SignChannelEnum.METRO_APP.getCode());
    }

    @PostMapping("/requestTermination")
    public RequestTerminationRespDTO requestTermination(@RequestBody RequestTerminationReqDTO request) {
        log.info("接收到请求解约报文: {}", request);
        // 地铁APP专属入口，固定签约渠道为 METRO_APP
        return paySignService.requestTermination(request, SignChannelEnum.METRO_APP.getCode());
    }

    /**
     * IF8A-36 请求移除签约信息。
     *
     * <p>与解约不同，移除签约不请求支付系统，直接将签约记录状态改为解约成功。</p>
     */
    @PostMapping("/requestAgreeRelease")
    public RequestAgreeReleaseResult requestAgreeRelease(@RequestBody RequestAgreeReleaseReqDTO request) {
        log.info("接收到请求移除签约信息报文: {}", request);
        // 地铁APP专属入口，固定签约渠道为 METRO_APP
        return paySignService.removeSignAgreement(request, SignChannelEnum.METRO_APP.getCode());
    }

    /**
     * 支付 API 1.1 请求支付内部入口。
     *
     * <p>内部交易服务只传支付业务参数，pay-sign-server 负责组装支付网关公共参数和签名。</p>
     */
    @PostMapping("/requestPay")
    public RequestPayResult requestPay(@RequestBody RequestPayReqDTO request) {
        log.info("接收到请求支付报文: {}", request);
        return paySignService.requestPay(request);
    }

    /**
     * 支付 API 3.1 请求退款内部入口。
     *
     * <p>内部调用方只传 orderNo 和 refundAmount，pay-sign-server 负责补齐原支付商户订单号、
     * 生成退款单号，并组装支付网关公共参数和签名。</p>
     */
    @PostMapping("/requestRefund")
    public RequestRefundResult requestRefund(@RequestBody RequestRefundReqDTO request) {
        log.info("接收到请求退款报文: {}", request);
        return paySignService.requestRefund(request);
    }

    @PostMapping("/receiveSignResult")
    public PaySignCallbackResult receiveSignResult(@RequestBody ReceiveSignResultReqDTO request) {
        log.info("接收到内部签约结果通知报文: {}", request);
        // 地铁APP专属入口，固定签约渠道为 METRO_APP
        return paySignService.receiveSignResult(request, SignChannelEnum.METRO_APP.getCode());
    }

    /**
     * 支付 API 5.1 支付回调内部入口。
     *
     * <p>fep-app 接收支付平台回调后，通过 RPC 透传到这里，由 pay-sign-server
     * 负责回写支付订单状态并记录回调流水。</p>
     */
    @PostMapping("/receivePayResult")
    public PaySignCallbackResult receivePayResult(@RequestBody ReceivePayResultReqDTO request) {
        log.info("接收到内部支付结果通知报文: {}", request);
        return paySignService.receivePayResult(request, com.alibaba.fastjson2.JSON.toJSONString(request));
    }

    /**
     * 支付 API 5.3 解约回调内部入口。
     *
     * <p>fep-app 接收支付平台回调后，通过 RPC 透传到这里，由 pay-sign-server
     * 负责更新解约状态并发送 IF8B-02 APP 解约结果通知。</p>
     *
     * @param request 解约回调业务参数
     * @return 回调处理结果
     */
    @PostMapping("/receiveTerminationResult")
    public Object receiveTerminationResult(@RequestBody ReceiveTerminationResultReqDTO request) {
        log.info("接收到内部解约结果通知报文: {}", request);
        return paySignService.receiveTerminationResult(request, SignChannelEnum.METRO_APP.getCode());
    }

    /**
     * IF8A-05 批量查询支付明细（供 ticket-server 双源合并）。
     */
    @PostMapping("queryPayTxnBatch")
    public com.chinasofti.huateng.model.app.RequestPayTxnBatchResult queryPayTxnBatch(@RequestBody QueryPayTxnBatchReqDTO request) {
        log.info("接收到批量查询支付明细报文, request={}", request);
        return paySignService.queryPayTxnBatch(request);
    }

    /**
     * 更新用户签约展示账号（如更换手机号时同步更新）。
     */
    @PostMapping("/updateDisplayAccount")
    public CommonResult updateDisplayAccount(@RequestParam String thirdUserId, @RequestParam String displayAccount) {
        log.info("接收到更新签约展示账号请求, thirdUserId={}, displayAccount={}", thirdUserId, displayAccount);
        boolean success = paySignService.updateDisplayAccountByThirdUserId(thirdUserId, displayAccount);
        if (success) {
            CommonResult result = new CommonResult();
            result.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
            result.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
            return result;
        }
        CommonResult result = new CommonResult();
        result.setRetCode(PaySignErrorCodeEnum.FAIL.getCode());
        result.setRetMsg(PaySignErrorCodeEnum.FAIL.getMsg());
        return result;
    }
}
