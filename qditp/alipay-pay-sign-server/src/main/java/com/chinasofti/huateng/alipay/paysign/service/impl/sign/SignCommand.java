package com.chinasofti.huateng.alipay.paysign.service.impl.sign;

import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.domain.AlipaySignStatus;

import java.time.LocalDateTime;


record SignCommand(String thirdUserId,
                   String agreementCode,
                   String channelAgreementCode,
                   String channelUserAccount) {

    static final String CHANNEL_ALIPAY = "ALIPAY";

    static SignCommand from(AlipayTripAddContractReqDTO request) {
        if (request == null
                || isBlank(request.getThirdUserId())
                || isBlank(request.getChannel())
                || isBlank(request.getAgreementCode())
                || isBlank(request.getChannelUserAccount())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(),
                    "thirdUserId/channel/agreementCode/channelUserAccount不能为空");
        }
        return new SignCommand(request.getThirdUserId(), request.getAgreementCode(),
                request.getChannelAgreementCode(), request.getChannelUserAccount());
    }


    AlipaySignInfo toSignRow(AlipayUserInfoDTO userInfo) {
        AlipaySignInfo signInfo = new AlipaySignInfo();
        signInfo.setAgreementCode(agreementCode);
        signInfo.setThirdUserId(thirdUserId);
        signInfo.setCardId(userInfo.getCardId());
        signInfo.setCardType(userInfo.getCardType());
        signInfo.setChannel(CHANNEL_ALIPAY);
        signInfo.setChannelAgreementCode(channelAgreementCode);
        signInfo.setChannelUserAccount(channelUserAccount);
        signInfo.setSignStatus(AlipaySignStatus.SIGNED.name());
        signInfo.setOperationType("SIGN");
        signInfo.setSignTime(LocalDateTime.now());
        signInfo.setDeleteFlag("0");
        signInfo.setVersion("1");
        signInfo.setCreateTime(LocalDateTime.now());
        signInfo.setUpdateTime(LocalDateTime.now());
        return signInfo;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
