package com.chinasofti.huateng.fep.alipay.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTerminateContractRespDTO;

/**
 * 支付宝出行合约服务接口。
 */
public interface AlipayContractService {

    /**
     * 添加签约信息。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request);

    /**
     * 解约登记。
     *
     * @param request 请求对象
     * @return 响应对象
     */
    AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request);
}
