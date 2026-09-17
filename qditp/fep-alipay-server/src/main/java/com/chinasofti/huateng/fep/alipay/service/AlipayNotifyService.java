package com.chinasofti.huateng.fep.alipay.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripCloseResultRespDTO;

/**
 * 支付宝出行通知服务接口。
 */
public interface AlipayNotifyService {

    /**
     * 支付中心-支付结果回调。
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripPayNotifyRespDTO handlePaymentNotify(AlipayTripPayNotifyReqDTO request);

    /**
     * 支付宝出行-业务关闭结果通知。
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripCloseResultRespDTO closeResult(AlipayTripCloseResultReqDTO request);
}
