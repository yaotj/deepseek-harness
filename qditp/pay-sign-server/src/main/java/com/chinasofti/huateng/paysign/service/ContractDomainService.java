package com.chinasofti.huateng.paysign.service;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.PaySignGatewayResponse;
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

    /**
     * 供内部解约流程调用，向支付平台发起解约请求。
     *
     * <p>2026-09-15 随签约 + 解约组由 {@code PaySignWorkflow} 搬到 {@code ContractDomainServiceImpl}
     * 并在此声明：{@code TerminationProcessor} 与 {@code TerminationInternalServiceImpl} 改为注入
     * 本接口调用（**纯换宿主，方法体逐字保留**）。</p>
     *
     * @param requestSignSeq 签约流水号
     * @return 支付平台网关响应
     */
    PaySignGatewayResponse requestPayPlatformTermination(String requestSignSeq);

    /**
     * 供内部解约流程调用，向支付平台查询协议状态。
     *
     * <p>支付中心没有独立的「解约结果查询」接口：网关文档 §2.4 查询签约结果的 status
     * 同时承载签约态与解约态，{@code status=UNSIGNED} 即该协议已解约。解约收口 MUST 以主动查询
     * 为准，回调只作为快速路径。</p>
     *
     * @param requestSignSeq 签约流水号
     * @return 支付平台网关响应
     */
    PaySignGatewayResponse queryPayPlatformContractStatus(String requestSignSeq);
}
