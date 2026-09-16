package com.chinasofti.huateng.paysign.service.impl;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.PaySignCallbackResult;
import com.chinasofti.huateng.model.app.ReceivePayResultReqDTO;
import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseResult;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.model.app.RequestPayTxnBatchResult;
import com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO;
import com.chinasofti.huateng.model.paysign.PaySignInfoDTO;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.entity.PayTxnDetail;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.mapper.PayTxnDetailMapper;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.model.app.RequestContractResultReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import com.chinasofti.huateng.paysign.service.CallbackDomainService;
import com.chinasofti.huateng.paysign.service.ContractDomainService;
import com.chinasofti.huateng.paysign.service.PaySignService;
import com.chinasofti.huateng.paysign.service.PaymentDomainService;
import com.chinasofti.huateng.paysign.service.RefundDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 支付签约应用服务门面。
 *
 * <p>保持原有 {@link PaySignService} 对外契约，按业务能力将请求路由到签约、支付、回调领域。
 * Controller 和 RPC 调用方无需随内部重构修改；新的跨领域编排应放在此处，
 * 具体业务规则则分别沉淀到对应领域服务。</p>
 */
@Service
public class PaySignServiceImpl implements PaySignService {
    private static final Logger log = LoggerFactory.getLogger(PaySignServiceImpl.class);

    private final ContractDomainService contractDomainService;
    private final PaymentDomainService paymentDomainService;
    private final RefundDomainService refundDomainService;
    private final CallbackDomainService callbackDomainService;
    private final PaySignInfoMapper paySignInfoMapper;
    private final PayTxnDetailMapper payTxnDetailMapper;

    public PaySignServiceImpl(ContractDomainService contractDomainService,
                              PaymentDomainService paymentDomainService,
                              RefundDomainService refundDomainService,
                              CallbackDomainService callbackDomainService,
                              PaySignInfoMapper paySignInfoMapper,
                              PayTxnDetailMapper payTxnDetailMapper) {
        this.contractDomainService = contractDomainService;
        this.paymentDomainService = paymentDomainService;
        this.refundDomainService = refundDomainService;
        this.callbackDomainService = callbackDomainService;
        this.paySignInfoMapper = paySignInfoMapper;
        this.payTxnDetailMapper = payTxnDetailMapper;
    }

    @Override
    public RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request, String signChannel) {
        return contractDomainService.requestSignInfo(request, signChannel);
    }

    @Override
    public RequestSignInfoResult alipayTripRequestSignInfo(AlipayTripAddContractReqDTO request) {
        return contractDomainService.alipayTripRequestSignInfo(request);
    }

    @Override
    public RequestContractAdvisoryRespDTO requestContractAdvisory(RequestContractAdvisoryReqDTO request, String signChannel) {
        return contractDomainService.requestContractAdvisory(request, signChannel);
    }

    @Override
    public RequestContractResultRespDTO requestContractResult(RequestContractResultReqDTO request, String signChannel) {
        return contractDomainService.requestContractResult(request, signChannel);
    }

    @Override
    public RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request, String signChannel) {
        return contractDomainService.requestTermination(request, signChannel);
    }

    @Override
    public RequestAgreeReleaseResult removeSignAgreement(RequestAgreeReleaseReqDTO request, String signChannel) {
        return contractDomainService.removeSignAgreement(request, signChannel);
    }

    @Override
    public RequestPayResult requestPay(RequestPayReqDTO request) {
        return paymentDomainService.requestPay(request);
    }

    @Override
    public RequestRefundResult requestRefund(RequestRefundReqDTO request) {
        // 退款的真实现在退款领域（2026-09-15 从支付领域拆出，纯搬迁）。
        // NEVER 退回「经 PaymentDomainService 转发」—— 那会让支付组重新挂上退款入口，
        // 而支付组一行退款逻辑都没有了。
        return refundDomainService.requestRefund(request);
    }

    @Override
    public PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel) {
        return callbackDomainService.receiveSignResult(request, signChannel);
    }

    @Override
    public PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request, String rawBody) {
        // 支付结果回调的真实现在支付领域（2026-09-15 拆分支付组时随 PAY_TXN_DETAIL 一起搬走）。
        // 这里直接路由到支付领域，NEVER 退回「经 CallbackDomainService 转发」—— 那会让回调组反向
        // 依赖支付组，而回调组自己一行支付逻辑都没有。
        return paymentDomainService.receivePayResult(request, rawBody);
    }

    @Override
    public BaseRespDTO receiveTerminationResult(ReceiveTerminationResultReqDTO request, String signChannel) {
        return callbackDomainService.receiveTerminationResult(request, signChannel);
    }

    @Override
    public PaySignInfoDTO querySignInfoBySeq(String requestSignSeq) {
        PaySignInfo info = paySignInfoMapper.selectBySeq(requestSignSeq, null);
        if (info == null) {
            return null;
        }
        PaySignInfoDTO dto = new PaySignInfoDTO();
        dto.setRequestSignSeq(info.getRequestSignSeq());
        dto.setPayAccountId(info.getPayAccountId());
        return dto;
    }

    @Override
    public boolean updateDisplayAccountByThirdUserId(String thirdUserId, String displayAccount) {
        return paySignInfoMapper.updateDisplayAccountByThirdUserId(thirdUserId, displayAccount) > 0;
    }

    @Override
    public RequestPayTxnBatchResult queryPayTxnBatch(QueryPayTxnBatchReqDTO request) {
        RequestPayTxnBatchResult result = new RequestPayTxnBatchResult();
        List<String> orderNos = request != null ? request.getOrderNos() : null;
        if (orderNos == null || orderNos.isEmpty()) {
            result.setRetCode("9999");
            result.setRetMsg("订单号列表为空");
            return result;
        }
        try {
            List<PayTxnDetail> list = payTxnDetailMapper.selectByOrderNos(orderNos);
            // 26 个字段的逐字段拷贝已外提到 support/PayTxnViews（ADR-D98 判据：零协作者）。
            // NEVER 在这里再手写一份 —— 漏一个 setter 不编译失败、不告警，只会让某一列恒为 null。
            List<com.chinasofti.huateng.model.paysign.PayTxnDetailDTO> dtoList =
                    com.chinasofti.huateng.paysign.support.PayTxnViews.toDtoList(list);
            result.setRetCode("0000");
            result.setRetMsg("成功");
            result.setPayTxnDetailList(dtoList);
        } catch (Exception e) {
            log.error("批量查询支付明细异常, orderNos={}", orderNos, e);
            result.setRetCode("9999");
            result.setRetMsg("批量查询支付明细异常");
        }
        return result;
    }
}
