package com.chinasofti.huateng.alipay.paysign.service.impl.termination;

import com.chinasofti.huateng.model.alipaytrip.AlipayTerminationRequest;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.domain.AlipayTerminationStatus;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayTerminationRequestMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class TerminationRegistrationService {
    private static final Logger log = LoggerFactory.getLogger(TerminationRegistrationService.class);
    private static final String CHANNEL_ALIPAY = "ALIPAY";

    @Autowired
    private AlipayTerminationRequestMapper alipayTerminationRequestMapper;

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;


    @Transactional(rollbackFor = Exception.class)
    public AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request) {
        AlipayTripTerminateContractRespDTO response = new AlipayTripTerminateContractRespDTO();
        log.info("接收到支付宝解约登记报文: {}", JSON.toJSONString(request));

        if (request == null || request.getAgreementCode() == null || request.getAgreementCode().trim().isEmpty()) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("agreementCode不能为空");
            return response;
        }

        if (alipayTerminationRequestMapper.countByAgreementCode(request.getAgreementCode()) > 0) {
            log.info("支付宝解约登记已存在，幂等返回成功, agreementCode={}", request.getAgreementCode());
            response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("成功");
            response.setAgreementCode(request.getAgreementCode());
            return response;
        }

        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByAgreementCode(request.getAgreementCode());
        if (signInfo == null || signInfo.getThirdUserId() == null || signInfo.getThirdUserId().trim().isEmpty()) {
            log.warn("支付宝解约登记被拒绝：查不到签约信息或签约记录缺少 thirdUserId, agreementCode={}",
                    request.getAgreementCode());
            response.setRetCode(FepAppErrorCodeEnum.USER_NOT_SIGNED.getCode());
            response.setRetMsg(FepAppErrorCodeEnum.USER_NOT_SIGNED.getMsg());
            response.setAgreementCode(request.getAgreementCode());
            return response;
        }

        AlipayTerminationRequest terminationRequest = buildTerminationRequest(request, signInfo);
        alipayTerminationRequestMapper.insert(terminationRequest);

        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("成功");
        response.setAgreementCode(request.getAgreementCode());
        log.info("支付宝解约登记成功, agreementCode={}", request.getAgreementCode());
        return response;
    }

    private AlipayTerminationRequest buildTerminationRequest(AlipayTripTerminateContractReqDTO request,
                                                            AlipaySignInfo signInfo) {
        AlipayTerminationRequest terminationRequest = new AlipayTerminationRequest();
        terminationRequest.setTerminationSeq(UUID.randomUUID().toString().replaceAll("-", ""));
        terminationRequest.setAgreementCode(request.getAgreementCode());
        terminationRequest.setThirdUserId(signInfo.getThirdUserId());
        terminationRequest.setCardId(signInfo.getCardId());
        terminationRequest.setCardType(signInfo.getCardType());
        terminationRequest.setChannel(CHANNEL_ALIPAY);
        terminationRequest.setMerchantNo(request.getMerchantNo());
        terminationRequest.setOperationType("TERMINATE");
        terminationRequest.setStatus(AlipayTerminationStatus.PENDING.name());
        terminationRequest.setDeleteFlag("0");
        terminationRequest.setVersion("1");
        terminationRequest.setCreateTime(LocalDateTime.now());
        terminationRequest.setUpdateTime(LocalDateTime.now());
        log.info("解约登记补充签约信息成功, agreementCode={}, thirdUserId={}, cardId={}, cardType={}",
                request.getAgreementCode(), signInfo.getThirdUserId(), signInfo.getCardId(), signInfo.getCardType());
        return terminationRequest;
    }
}
