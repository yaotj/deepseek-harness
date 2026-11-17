package com.chinasofti.huateng.alipay.paysign.service.impl.contract;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripTerminateContractReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripAddContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripTerminateContractRespDTO;
import com.chinasofti.huateng.alipay.paysign.service.AlipayContractService;
import com.chinasofti.huateng.alipay.paysign.service.impl.channelsync.ChannelSyncDeliverer;
import com.chinasofti.huateng.alipay.paysign.service.AlipayTerminationService;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.domain.AlipaySignStatus;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;


@Service
public class AlipayContractServiceImpl implements AlipayContractService {

    private static final Logger log = LoggerFactory.getLogger(AlipayContractServiceImpl.class);
    private static final String CHANNEL_ALIPAY = "ALIPAY";

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    @Autowired
    private AlipayAccountClient alipayAccountClient;

    @Autowired
    private ChannelSyncDeliverer channelSyncDeliverer;


    @Autowired
    private AlipayTerminationService alipayTerminationService;

    @Autowired
    private SignLogRecorder signLogRecorder;

    @Override
    public AlipayTripAddContractRespDTO addContract(AlipayTripAddContractReqDTO request) {
        AlipayTripAddContractRespDTO response = new AlipayTripAddContractRespDTO();
        log.info("接收到支付宝添加签约信息报文: {}", JSON.toJSONString(request));

        if (request == null || request.getThirdUserId() == null || request.getThirdUserId().trim().isEmpty()
                || request.getChannel() == null || request.getChannel().trim().isEmpty()
                || request.getAgreementCode() == null || request.getAgreementCode().trim().isEmpty()
                || request.getChannelUserAccount() == null || request.getChannelUserAccount().trim().isEmpty()) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(),
                    "thirdUserId/channel/agreementCode/channelUserAccount不能为空");
        }

        AlipaySignInfo existingSign = alipaySignInfoMapper.selectByThirdUserIdAndChannel(request.getThirdUserId(), CHANNEL_ALIPAY);
        if (existingSign != null) {
            return onExistingActiveSign(request, existingSign, response);
        }

        AlipayUserInfoDTO userInfo = alipayAccountClient.selectByThirdUserId(request.getThirdUserId());
        if (userInfo == null || userInfo.getCardId() == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "用户未开户，无法签约");
        }

        AlipaySignInfo signInfo = buildSignInfo(request, userInfo);
        if (alipaySignInfoMapper.selectAnyByThirdUserIdAndChannel(request.getThirdUserId(), CHANNEL_ALIPAY) != null) {
            if (alipaySignInfoMapper.reactivateSign(signInfo) == 0) {
                return onWriteConflict(request, response);
            }
        } else {
            try {
                alipaySignInfoMapper.insert(signInfo);
            } catch (RuntimeException e) {
                if (!isUniqueConflict(e)) {
                    throw e;
                }
                return onWriteConflict(request, response);
            }
        }

        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("成功");
        response.setAgreementCode(request.getAgreementCode());

        signLogRecorder.recordSignSuccess(signInfo, request, response);

        syncPaymentChannel(request);

        log.info("支付宝签约成功, thirdUserId={}, agreementCode={}", request.getThirdUserId(), request.getAgreementCode());
        return response;
    }

    private AlipayTripAddContractRespDTO onExistingActiveSign(AlipayTripAddContractReqDTO request,
                                                              AlipaySignInfo existingSign,
                                                              AlipayTripAddContractRespDTO response) {
        if (!request.getAgreementCode().equals(existingSign.getAgreementCode())) {
            log.error("该用户已有生效签约且协议号不一致，拒绝签约, thirdUserId={}, 库内协议号={}, 入参协议号={}",
                    request.getThirdUserId(), existingSign.getAgreementCode(), request.getAgreementCode());
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "该用户已存在生效中的签约，请先解约后再签约");
        }
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("该用户已签约");
        response.setAgreementCode(existingSign.getAgreementCode());
        return response;
    }

    private AlipayTripAddContractRespDTO onWriteConflict(AlipayTripAddContractReqDTO request,
                                                        AlipayTripAddContractRespDTO response) {
        log.warn("签约落库与并发请求冲突，判为重复请求, thirdUserId={}, agreementCode={}",
                request.getThirdUserId(), request.getAgreementCode());
        AlipaySignInfo committed = alipaySignInfoMapper.selectByThirdUserIdAndChannel(request.getThirdUserId(), CHANNEL_ALIPAY);
        if (committed == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "签约冲突且回查不到生效签约，请重试");
        }
        return onExistingActiveSign(request, committed, response);
    }

    private boolean isUniqueConflict(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof DuplicateKeyException || t instanceof DataIntegrityViolationException) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private AlipaySignInfo buildSignInfo(AlipayTripAddContractReqDTO request, AlipayUserInfoDTO userInfo) {
        AlipaySignInfo signInfo = new AlipaySignInfo();
        signInfo.setAgreementCode(request.getAgreementCode());
        signInfo.setThirdUserId(request.getThirdUserId());
        signInfo.setCardId(userInfo.getCardId());
        signInfo.setCardType(userInfo.getCardType());
        signInfo.setChannel(CHANNEL_ALIPAY);
        signInfo.setChannelAgreementCode(request.getChannelAgreementCode());
        signInfo.setChannelUserAccount(request.getChannelUserAccount());
        signInfo.setSignStatus(AlipaySignStatus.SIGNED.name());
        signInfo.setOperationType("SIGN");
        signInfo.setSignTime(LocalDateTime.now());
        signInfo.setDeleteFlag("0");
        signInfo.setVersion("1");
        signInfo.setCreateTime(LocalDateTime.now());
        signInfo.setUpdateTime(LocalDateTime.now());
        return signInfo;
    }

    private void syncPaymentChannel(AlipayTripAddContractReqDTO request) {
        channelSyncDeliverer.deliver(request.getThirdUserId(), request.getChannelUserAccount(),
                request.getAgreementCode());
    }

    @Override
    public AlipayTripTerminateContractRespDTO terminateContract(AlipayTripTerminateContractReqDTO request) {
        return alipayTerminationService.terminateContract(request);
    }

    @Override
    public AlipaySignInfoDTO selectSignInfo(String thirdUserId) {
        if (!StringUtils.hasText(thirdUserId)) {
            return null;
        }
        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByThirdUserIdAndChannel(thirdUserId, CHANNEL_ALIPAY);
        if (signInfo == null) {
            return null;
        }
        AlipaySignInfoDTO dto = new AlipaySignInfoDTO();
        dto.setThirdUserId(signInfo.getThirdUserId());
        dto.setChannelAgreementCode(signInfo.getChannelAgreementCode());
        dto.setCardId(signInfo.getCardId());
        dto.setCardType(signInfo.getCardType());
        return dto;
    }

    @Override
    public AlipayCommonResponse executeTermination(String agreementCode) {
        return alipayTerminationService.executeTermination(agreementCode);
    }
}
