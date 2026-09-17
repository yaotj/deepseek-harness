package com.chinasofti.huateng.collectpay.service;

import com.chinasofti.huateng.model.collectpay.AppPayOrderCloseReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderRegisterReqDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderResultRespDTO;
import com.chinasofti.huateng.model.collectpay.AppPayOrderRespDTO;

/** {@code TBL_TVM_APP_ORDER} / {@code TBL_TVM_ORDER_PAY_PRE} 的对内写入入口。 */
public interface AppPayOrderInternalService {

    /** 把一张外部单据登记成 APP 订单行 + 支付前置单行。 */
    AppPayOrderRespDTO register(AppPayOrderRegisterReqDTO request);

    /** 关闭仍待支付的订单行（带 {@code PAY_STATUS='0'} 白名单，影响 0 行也算成功）。 */
    AppPayOrderRespDTO closeUnpaid(AppPayOrderCloseReqDTO request);

    /** 按订单号回查支付结果，供调用方收敛自己的单据状态。 */
    AppPayOrderResultRespDTO queryPayResult(String orderNo);
}
