package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;

/**
 * 签约领域入口。
 *
 * <p>聚合签约发起、咨询、结果查询和解约请求，不包含支付订单或异步回调处理。
 * 对外 Facade 只依赖此接口，后续替换签约实现时不影响其他领域。</p>
 */
public interface ContractDomainService {
    RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request, String signChannel);

    RequestSignInfoResult alipayTripRequestSignInfo(AlipayTripAddContractReqDTO request);

    RequestContractAdvisoryRespDTO requestContractAdvisory(RequestContractAdvisoryReqDTO request, String signChannel);

    RequestContractResultRespDTO requestContractResult(RequestContractResultReqDTO request, String signChannel);

    RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request, String signChannel);

    /**
     * IF8A-36 请求移除签约信息。
     *
     * <p>与解约不同，移除签约不请求支付系统，直接更新签约记录状态为解约成功。</p>
     */
    RequestAgreeReleaseResult removeSignAgreement(RequestAgreeReleaseReqDTO request, String signChannel);
}
