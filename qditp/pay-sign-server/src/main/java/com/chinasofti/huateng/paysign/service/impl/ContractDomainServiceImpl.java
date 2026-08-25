package com.chinasofti.huateng.paysign.service.impl;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseReqDTO;
import com.chinasofti.huateng.model.app.RequestAgreeReleaseResult;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoResult;
import com.chinasofti.huateng.paysign.constant.PaySignErrorCodeEnum;
import com.chinasofti.huateng.paysign.entity.PaySignInfo;
import com.chinasofti.huateng.paysign.mapper.PaySignInfoMapper;
import com.chinasofti.huateng.paysign.model.request.RequestContractAdvisoryReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestContractResultReqDTO;
import com.chinasofti.huateng.paysign.model.request.RequestTerminationReqDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractAdvisoryRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestContractResultRespDTO;
import com.chinasofti.huateng.paysign.model.response.RequestTerminationRespDTO;
import com.chinasofti.huateng.paysign.service.ContractDomainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class ContractDomainServiceImpl implements ContractDomainService {

    private static final Logger log = LoggerFactory.getLogger(ContractDomainServiceImpl.class);
    private static final String STATUS_UNSIGNED = "UNSIGNED";

    private final PaySignWorkflow paySignWorkflow;
    private final PaySignInfoMapper paySignInfoMapper;

    public ContractDomainServiceImpl(PaySignWorkflow paySignWorkflow, PaySignInfoMapper paySignInfoMapper) {
        this.paySignWorkflow = paySignWorkflow;
        this.paySignInfoMapper = paySignInfoMapper;
    }

    @Override
    public RequestSignInfoResult requestSignInfo(RequestSignInfoReqDTO request, String signChannel) {
        return paySignWorkflow.requestSignInfo(request, signChannel);
    }

    @Override
    public RequestSignInfoResult alipayTripRequestSignInfo(AlipayTripAddContractReqDTO request) {
        return paySignWorkflow.alipayTripRequestSignInfo(request);
    }

    @Override
    public RequestContractAdvisoryRespDTO requestContractAdvisory(RequestContractAdvisoryReqDTO request, String signChannel) {
        return paySignWorkflow.requestContractAdvisory(request, signChannel);
    }

    @Override
    public RequestContractResultRespDTO requestContractResult(RequestContractResultReqDTO request, String signChannel) {
        return paySignWorkflow.requestContractResult(request, signChannel);
    }

    @Override
    public RequestTerminationRespDTO requestTermination(RequestTerminationReqDTO request, String signChannel) {
        return paySignWorkflow.requestTermination(request, signChannel);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestAgreeReleaseResult removeSignAgreement(RequestAgreeReleaseReqDTO request, String signChannel) {
        RequestAgreeReleaseResult result = new RequestAgreeReleaseResult();
        if (!StringUtils.hasText(request.getAgreementCode())) {
            result.setRetCode(PaySignErrorCodeEnum.INVALID_PARAM.getCode());
            result.setRetMsg("agreementCode不能为空");
            result.setCode(400);
            result.setMsg("agreementCode不能为空");
            result.setSuccess(false);
            return result;
        }
        PaySignInfo signInfo = paySignInfoMapper.selectBySeq(request.getAgreementCode(), null);
        if (signInfo == null) {
            result.setRetCode(PaySignErrorCodeEnum.RECORD_NOT_EXIST.getCode());
            result.setRetMsg("签约记录不存在");
            result.setCode(404);
            result.setMsg("签约记录不存在");
            result.setSuccess(false);
            return result;
        }
        signInfo.setSignStatus(STATUS_UNSIGNED);
        signInfo.setTerminationTime(LocalDateTime.now());
        paySignInfoMapper.updateBySeq(signInfo);
        log.info("移除签约成功, agreementCode={}, signChannel={}", request.getAgreementCode(), signChannel);
        result.setRetCode(PaySignErrorCodeEnum.SUCCESS.getCode());
        result.setRetMsg(PaySignErrorCodeEnum.SUCCESS.getMsg());
        result.setCode(0);
        result.setMsg("成功");
        result.setSuccess(true);
        return result;
    }
}
