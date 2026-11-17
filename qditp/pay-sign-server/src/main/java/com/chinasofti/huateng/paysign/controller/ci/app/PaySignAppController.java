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

@RestController
@RequestMapping("/ci/app")
public class PaySignAppController {
    private static final Logger log = LoggerFactory.getLogger(PaySignAppController.class);

    private final PaySignService paySignService;

    private final TerminationInternalService terminationInternalService;

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

    @PostMapping("/requestAgreeRelease")
    public RequestAgreeReleaseResult requestAgreeRelease(@RequestBody RequestAgreeReleaseReqDTO request) {
        log.info("接收到请求移除签约信息报文: {}", request);
        return paySignService.removeSignAgreement(request, SignChannelEnum.METRO_APP.getCode());
    }

    @PostMapping("/unbindAgreement")
    public UnbindAgreementResult unbindAgreement(@RequestBody UnbindAgreementReqDTO request) {
        log.info("接收到直接解绑支付方式报文: {}", request);
        return terminationInternalService.unbindAgreement(request);
    }

    @PostMapping("/requestPay")
    public RequestPayResult requestPay(@RequestBody RequestPayReqDTO request) {
        log.info("接收到请求支付报文: {}", request);
        return paySignService.requestPay(request);
    }

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

    @PostMapping("/receivePayResult")
    public PaySignCallbackResult receivePayResult(@RequestBody ReceivePayResultReqDTO request) {
        log.info("接收到内部支付结果通知报文: {}", request);
        return paySignService.receivePayResult(request, com.alibaba.fastjson2.JSON.toJSONString(request));
    }


    @PostMapping("/receiveRefundResult")
    public PaySignCallbackResult receiveRefundResult(@RequestBody ReceiveRefundResultReqDTO request) {
        log.info("接收到内部退款结果通知报文: {}", request);
        return paySignService.receiveRefundResult(request);
    }


    @PostMapping("/receiveTerminationResult")
    public Object receiveTerminationResult(@RequestBody ReceiveTerminationResultReqDTO request) {
        log.info("接收到内部解约结果通知报文: {}", request);
        return paySignService.receiveTerminationResult(request, SignChannelEnum.METRO_APP.getCode());
    }

    @PostMapping("queryPayTxnBatch")
    public com.chinasofti.huateng.model.app.RequestPayTxnBatchResult queryPayTxnBatch(@RequestBody QueryPayTxnBatchReqDTO request) {
        log.info("接收到批量查询支付明细报文, request={}", request);
        return paySignService.queryPayTxnBatch(request);
    }

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
