package com.chinasofti.huateng.paysign.controller.ci.app;

import com.chinasofti.huateng.common.response.CommonResult;
import com.chinasofti.huateng.model.app.*;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import com.chinasofti.huateng.paysign.constant.SignChannelEnum;
import com.chinasofti.huateng.paysign.service.PaySignService;
import com.chinasofti.huateng.paysign.service.TerminationInternalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 地铁APP签约渠道。 */
@RestController
@RequestMapping("/ci/app")
public class PaySignAppController {
    private static final Logger log = LoggerFactory.getLogger(PaySignAppController.class);

    private final PaySignService paySignService;

    private final TerminationInternalService terminationInternalService;

    /** 协作者一律构造注入（2026-09-16，ADR-D96）：字段 {@code final} ⇒ 对象一建成即完备。 */
    public PaySignAppController(PaySignService paySignService, TerminationInternalService terminationInternalService) {
        this.paySignService = paySignService;
        this.terminationInternalService = terminationInternalService;
    }

    @PostMapping("/requestContractAdvisory")
    public RequestContractAdvisoryRespDTO requestContractAdvisory(@RequestBody RequestContractAdvisoryReqDTO request) {
        log.info("接收到信用能力咨询报文: {}", request);
        return paySignService.requestContractAdvisory(request, SignChannelEnum.METRO_APP.getCode());
    }

    @PostMapping("/requestContractResult")
    public RequestContractResultRespDTO requestContractResult(@RequestBody RequestContractResultReqDTO request) {
        log.info("接收到签约结果咨询报文: {}", request);
        return paySignService.requestContractResult(request, SignChannelEnum.METRO_APP.getCode());
    }

    @PostMapping("/requestTermination")
    public RequestTerminationRespDTO requestTermination(@RequestBody RequestTerminationReqDTO request) {
        log.info("接收到请求解约报文: {}", request);
        return paySignService.requestTermination(request, SignChannelEnum.METRO_APP.getCode());
    }

    /** IF8A-36 请求移除签约信息。 */
    @PostMapping("/requestAgreeRelease")
    public RequestAgreeReleaseResult requestAgreeRelease(@RequestBody RequestAgreeReleaseReqDTO request) {
        log.info("接收到请求移除签约信息报文: {}", request);
        return paySignService.removeSignAgreement(request, SignChannelEnum.METRO_APP.getCode());
    }

    /** IF8A-75 直接解绑支付方式。 */
    @PostMapping("/unbindAgreement")
    public UnbindAgreementResult unbindAgreement(@RequestBody UnbindAgreementReqDTO request) {
        log.info("接收到直接解绑支付方式报文: {}", request);
        return terminationInternalService.unbindAgreement(request);
    }

    /** 支付 API 1.1 请求支付内部入口。 */
    @PostMapping("/requestPay")
    public RequestPayResult requestPay(@RequestBody RequestPayReqDTO request) {
        log.info("接收到请求支付报文: {}", request);
        return paySignService.requestPay(request);
    }

    /** 支付 API 3.1 请求退款内部入口。 */
    @PostMapping("/requestRefund")
    public RequestRefundResult requestRefund(@RequestBody RequestRefundReqDTO request) {
        log.info("接收到请求退款报文: {}", request);
        return paySignService.requestRefund(request);
    }

    @PostMapping("/receiveSignResult")
    public PaySignCallbackResult receiveSignResult(@RequestBody ReceiveSignResultReqDTO request) {
        log.info("接收到内部签约结果通知报文: {}", request);
        return paySignService.receiveSignResult(request, SignChannelEnum.METRO_APP.getCode());
    }

    /** 支付 API 5.1 支付回调内部入口。 */
    @PostMapping("/receivePayResult")
    public PaySignCallbackResult receivePayResult(@RequestBody ReceivePayResultReqDTO request) {
        log.info("接收到内部支付结果通知报文: {}", request);
        return paySignService.receivePayResult(request, com.alibaba.fastjson2.JSON.toJSONString(request));
    }

    /**
     * 支付中心网关 §5.2 退款回调内部入口（2026-09-22 新增，P1-3）。
     *
     * <p>**本端点没有验签**（用户裁决「不补验签」）：与上面 {@code receivePayResult} 现状一致，
     * 属**已知待补的安全缺口**，**NEVER 拿它当「新增状态变更型端点可以免签」的依据**（AGENTS.md §5.2 仍然有效）。
     */
    @PostMapping("/receiveRefundResult")
    public PaySignCallbackResult receiveRefundResult(@RequestBody ReceiveRefundResultReqDTO request) {
        log.info("接收到内部退款结果通知报文: {}", request);
        return paySignService.receiveRefundResult(request);
    }

    /**
     * 支付 API 5.3 解约回调内部入口。
     *
     * @param request 解约回调业务参数
     * @return 回调处理结果
     */
    @PostMapping("/receiveTerminationResult")
    public Object receiveTerminationResult(@RequestBody ReceiveTerminationResultReqDTO request) {
        log.info("接收到内部解约结果通知报文: {}", request);
        return paySignService.receiveTerminationResult(request, SignChannelEnum.METRO_APP.getCode());
    }

    /** IF8A-05 批量查询支付明细（供 ticket-server 双源合并）。 */
    @PostMapping("queryPayTxnBatch")
    public com.chinasofti.huateng.model.app.RequestPayTxnBatchResult queryPayTxnBatch(@RequestBody QueryPayTxnBatchReqDTO request) {
        log.info("接收到批量查询支付明细报文, request={}", request);
        return paySignService.queryPayTxnBatch(request);
    }

    /** 更新用户签约展示账号（如更换手机号时同步更新）。 */
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
