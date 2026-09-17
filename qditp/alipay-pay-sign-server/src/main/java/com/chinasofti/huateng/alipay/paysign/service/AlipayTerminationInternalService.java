package com.chinasofti.huateng.alipay.paysign.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayProcessTerminationRespDTO;

/**
 * 支付宝出行销卡批处理（内部接口，供 web-admin 的 Quartz 任务调用）。
 */
public interface AlipayTerminationInternalService {

    /**
     * 扫一批 PENDING 的销卡登记并逐条执行。
     * @param request 可为 null，表示不按登记时间过滤
     */
    AlipayProcessTerminationRespDTO processTermination(AlipayProcessTerminationReqDTO request);
}
