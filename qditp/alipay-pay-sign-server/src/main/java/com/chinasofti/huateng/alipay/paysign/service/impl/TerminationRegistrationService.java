package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.model.alipaytrip.AlipayTerminationRequest;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
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
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_COMPLETED = "COMPLETED";

    @Autowired
    private AlipayTerminationRequestMapper alipayTerminationRequestMapper;

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    @Transactional(rollbackFor = Exception.class)
    public AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request) {
        AlipayTripTerminateContractRespDTO response = new AlipayTripTerminateContractRespDTO();
        try {
            log.info("接收到支付宝解约登记报文: {}", JSON.toJSONString(request));

            if (request == null || request.getAgreementCode() == null || request.getAgreementCode().trim().isEmpty()) {
                response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("agreementCode不能为空");
                return response;
            }

            // 存在性判断用 COUNT：该表无唯一约束，历史脏数据会让 selectByAgreementCode 抛 TooManyResultsException
            if (alipayTerminationRequestMapper.countByAgreementCode(request.getAgreementCode()) > 0) {
                log.info("支付宝解约登记已存在，幂等返回成功, agreementCode={}", request.getAgreementCode());
                response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg("成功");
                response.setAgreementCode(request.getAgreementCode());
                return response;
            }

            // 销卡批处理按 THIRD_USER_ID 做用户维度校验，回填不到就 NEVER 登记：
            // 一条三字段全 NULL 的 PENDING 记录既跑不通批处理，也无法人工追溯（生产已产生过一条）。
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
        } catch (Exception e) {
            log.error("支付宝解约登记异常", e);
            response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg("系统内部错误");
            return response;
        }
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
        terminationRequest.setStatus(STATUS_PENDING);
        terminationRequest.setDeleteFlag("0");
        terminationRequest.setVersion("1");
        terminationRequest.setCreateTime(LocalDateTime.now());
        terminationRequest.setUpdateTime(LocalDateTime.now());
        log.info("解约登记补充签约信息成功, agreementCode={}, thirdUserId={}, cardId={}, cardType={}",
                request.getAgreementCode(), signInfo.getThirdUserId(), signInfo.getCardId(), signInfo.getCardType());
        return terminationRequest;
    }
}
