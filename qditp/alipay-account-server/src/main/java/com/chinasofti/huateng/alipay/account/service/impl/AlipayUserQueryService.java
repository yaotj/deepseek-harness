package com.chinasofti.huateng.alipay.account.service.impl;

import com.chinasofti.huateng.alipay.account.entity.AlipayUserInfo;
import com.chinasofti.huateng.alipay.account.mapper.AlipayUserInfoMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class AlipayUserQueryService {
    private static final Logger log = LoggerFactory.getLogger(AlipayUserQueryService.class);

    private final AlipayUserInfoMapper alipayUserInfoMapper;

    public AlipayUserQueryService(AlipayUserInfoMapper alipayUserInfoMapper) {
        this.alipayUserInfoMapper = alipayUserInfoMapper;
    }

    public AlipayUserInfoDTO selectByThirdUserId(String thirdUserId) {
        if (thirdUserId == null || thirdUserId.trim().isEmpty()) {
            return null;
        }
        return toUserInfoDto(alipayUserInfoMapper.selectByThirdUserId(thirdUserId.trim()));
    }

    public AlipayUserInfoDTO selectByCardId(String cardId) {
        if (cardId == null || cardId.trim().isEmpty()) {
            return null;
        }
        return toUserInfoDto(alipayUserInfoMapper.selectByCardId(cardId.trim()));
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updatePaymentChannel(String thirdUserId, String thirdPayId, String reqContractNo) {
        if (thirdUserId == null || thirdUserId.trim().isEmpty()) {
            log.warn("更新用户支付通道失败, thirdUserId 为空");
            return false;
        }
        try {
            AlipayUserInfo userInfo = new AlipayUserInfo();
            userInfo.setThirdUserId(thirdUserId);
            userInfo.setThirdPayId(thirdPayId);
            userInfo.setReqContractNo(reqContractNo);
            int updated = alipayUserInfoMapper.updatePaymentChannel(userInfo);
            if (updated > 0) {
                log.info("更新用户支付通道成功, thirdUserId={}, thirdPayId={}, reqContractNo={}",
                        thirdUserId, thirdPayId, reqContractNo);
                return true;
            }
            log.warn("更新用户支付通道失败，用户不存在, thirdUserId={}", thirdUserId);
            return false;
        } catch (Exception e) {
            log.error("更新用户支付通道异常, thirdUserId={}", thirdUserId, e);
            return false;
        }
    }


    private AlipayUserInfoDTO toUserInfoDto(AlipayUserInfo userInfo) {
        if (userInfo == null) {
            return null;
        }
        AlipayUserInfoDTO dto = new AlipayUserInfoDTO();
        dto.setThirdUserId(userInfo.getThirdUserId());
        dto.setCardId(userInfo.getCardId());
        dto.setCardType(userInfo.getCardType());
        dto.setThirdPayId(userInfo.getThirdPayId());
        dto.setReqContractNo(userInfo.getReqContractNo());
        dto.setChannel(userInfo.getChannel());
        dto.setPhone(userInfo.getMsisdn());
        return dto;
    }
}
