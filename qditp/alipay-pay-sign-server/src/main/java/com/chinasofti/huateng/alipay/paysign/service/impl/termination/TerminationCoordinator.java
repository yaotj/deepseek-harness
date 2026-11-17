package com.chinasofti.huateng.alipay.paysign.service.impl.termination;

import com.chinasofti.huateng.alipay.paysign.mapper.AlipayTerminationRequestMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipayTerminationService;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipayTerminationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class TerminationCoordinator implements AlipayTerminationService {

    private static final Logger log = LoggerFactory.getLogger(TerminationCoordinator.class);

    @Autowired
    private AlipayTerminationRequestMapper alipayTerminationRequestMapper;

    @Autowired
    private TerminationNotifier terminationNotifier;

    @Autowired
    private TerminationRegistrationService terminationRegistrationService;


    public AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request) {
        return terminationRegistrationService.terminateContract(request);
    }


    public AlipayCommonResponse executeTermination(String agreementCode) {
        log.info("支付宝出行-执行解约, agreementCode={}", agreementCode);

        if (!org.springframework.util.StringUtils.hasText(agreementCode)) {
            return commonResponse(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "无效的参数：agreementCode不能为空");
        }

        if (alipayTerminationRequestMapper.countByAgreementCode(agreementCode) == 0) {
            AlipayTripTerminateContractReqDTO registerRequest = new AlipayTripTerminateContractReqDTO();
            registerRequest.setAgreementCode(agreementCode);
            AlipayTripTerminateContractRespDTO registerResponse = terminationRegistrationService.terminateContract(registerRequest);
            if (!FepAppErrorCodeEnum.SUCCESS.getCode().equals(registerResponse.getRetCode())) {
                log.warn("支付宝出行-执行解约,补登记未成功, agreementCode={}, retCode={}", agreementCode, registerResponse.getRetCode());
                return commonResponse(registerResponse.getRetCode(), registerResponse.getRetMsg());
            }
        }

        AlipayTerminationRequest terminationRequest = alipayTerminationRequestMapper.selectByAgreementCode(agreementCode);
        if (terminationRequest == null) {
            log.warn("支付宝出行-执行解约,解约登记记录不存在, agreementCode={}", agreementCode);
            return commonResponse(FepAppErrorCodeEnum.FAIL.getCode(), "解约登记记录不存在");
        }

        TerminationNotifier.Outcome outcome = terminationNotifier.execute(terminationRequest);
        return switch (outcome) {
            case TERMINATED -> commonResponse(FepAppErrorCodeEnum.SUCCESS.getCode(), "成功");
            case FAILED -> commonResponse(FepAppErrorCodeEnum.FAIL.getCode(), "解约失败，已置终态");
            case RETRY_LATER -> commonResponse(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "解约未完成，登记保持 PENDING 待重试");
        };
    }

    private AlipayCommonResponse commonResponse(String retCode, String retMsg) {
        AlipayCommonResponse response = new AlipayCommonResponse();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        return response;
    }
}
