package com.chinasofti.huateng.collectpay.controller.internal;

import com.chinasofti.huateng.collectpay.service.AppPayOrderInternalService;
import com.chinasofti.huateng.model.collectpay.AppPayOrderCloseReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderQueryReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderRegisterReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderResultRespDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderRespDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code TBL_TVM_APP_ORDER} / {@code TBL_TVM_ORDER_PAY_PRE} 的对内写入入口。 */
@RestController
@RequestMapping("/internal/app-order")
public class AppPayOrderInternalController {

    private final AppPayOrderInternalService appPayOrderInternalService;

    public AppPayOrderInternalController(AppPayOrderInternalService appPayOrderInternalService) {
        this.appPayOrderInternalService = appPayOrderInternalService;
    }

    /** 登记订单行 + 支付前置单行，按 {@code orderNo} 幂等。 */
    @PostMapping("/register")
    public AppPayOrderRespDTO register(@RequestBody AppPayOrderRegisterReqDTO request) {
        return appPayOrderInternalService.register(request);
    }

    /** 关闭仍待支付的订单行，带 {@code PAY_STATUS='0'} 白名单，影响 0 行也返成功。 */
    @PostMapping("/close-unpaid")
    public AppPayOrderRespDTO closeUnpaid(@RequestBody AppPayOrderCloseReqDTO request) {
        return appPayOrderInternalService.closeUnpaid(request);
    }

    /** 回查支付结果；查不到时 {@code found=false}，不抛异常。 */
    @PostMapping("/pay-result")
    public AppPayOrderResultRespDTO payResult(@RequestBody AppPayOrderQueryReqDTO request) {
        return appPayOrderInternalService.queryPayResult(request == null ? null : request.getOrderNo());
    }
}
