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
import com.chinasofti.huateng.paysign.model.request.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.BaseRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import com.chinasofti.huateng.paysign.service.CallbackDomainService;
import com.chinasofti.huateng.paysign.service.ContractDomainService;
import com.chinasofti.huateng.paysign.service.PaySignService;
import com.chinasofti.huateng.paysign.service.PaymentDomainService;
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
    private final CallbackDomainService callbackDomainService;
    private final PaySignInfoMapper paySignInfoMapper;
    private final PayTxnDetailMapper payTxnDetailMapper;

    public PaySignServiceImpl(ContractDomainService contractDomainService,
                              PaymentDomainService paymentDomainService,
                              CallbackDomainService callbackDomainService,
                              PaySignInfoMapper paySignInfoMapper,
                              PayTxnDetailMapper payTxnDetailMapper) {
        this.contractDomainService = contractDomainService;
        this.paymentDomainService = paymentDomainService;
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
        return paymentDomainService.requestRefund(request);
    }

    @Override
    public PaySignCallbackResult receiveSignResult(ReceiveSignResultReqDTO request, String signChannel) {
        return callbackDomainService.receiveSignResult(request, signChannel);
    }

    @Override
    public PaySignCallbackResult receivePayResult(ReceivePayResultReqDTO request, String rawBody) {
        return callbackDomainService.receivePayResult(request, rawBody);
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
            List<com.chinasofti.huateng.model.paysign.PayTxnDetailDTO> dtoList = new ArrayList<>(list.size());
            for (PayTxnDetail entity : list) {
                com.chinasofti.huateng.model.paysign.PayTxnDetailDTO dto = new com.chinasofti.huateng.model.paysign.PayTxnDetailDTO();
                dto.setId(entity.getId());
                dto.setOrderNo(entity.getOrderNo());
                dto.setPayType(entity.getPayType());
                dto.setPayStatus(entity.getPayStatus());
                dto.setThirdUserId(entity.getThirdUserId());
                dto.setCardId(entity.getCardId());
                dto.setCardType(entity.getCardType());
                dto.setPaymentVendor(entity.getPaymentVendor());
                dto.setRequestSignSeq(entity.getRequestSignSeq());
                dto.setAmount(entity.getAmount());
                dto.setTotalAmount(entity.getTotalAmount());
                dto.setCashAmount(entity.getCashAmount());
                dto.setCouponAmount(entity.getCouponAmount());
                dto.setRefundStatus(entity.getRefundStatus());
                dto.setRefundAmount(entity.getRefundAmount());
                dto.setLastRefundTime(entity.getLastRefundTime());
                dto.setMerchantOrderNo(entity.getMerchantOrderNo());
                dto.setChannelOrderNo(entity.getChannelOrderNo());
                dto.setPayUserId(entity.getPayUserId());
                dto.setRequestCount(entity.getRequestCount());
                dto.setNextRequestTime(entity.getNextRequestTime());
                dto.setLastRequestTime(entity.getLastRequestTime());
                dto.setFirstRequestTime(entity.getFirstRequestTime());
                dto.setResponseTime(entity.getResponseTime());
                dto.setPayTime(entity.getPayTime());
                dto.setTxnDate(entity.getTxnDate());
                dto.setCreateTime(entity.getCreateTime());
                dto.setUpdateTime(entity.getUpdateTime());
                dto.setDiscountInfo(entity.getDiscountInfo());
                dto.setDebitRequestResult(entity.getDebitRequestResult());
                dto.setDiscountFee(entity.getDiscountFee());
                dtoList.add(dto);
            }
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
