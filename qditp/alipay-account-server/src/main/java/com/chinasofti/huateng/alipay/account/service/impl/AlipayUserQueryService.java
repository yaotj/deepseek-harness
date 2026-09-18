package com.chinasofti.huateng.alipay.account.service.impl;

import com.chinasofti.huateng.alipay.account.entity.AlipayUserInfo;
import com.chinasofti.huateng.alipay.account.mapper.AlipayUserInfoMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付宝渠道用户档案的读取与支付通道回写（ADR-D143）。
 *
 * <p>与 {@link AlipayRegistrationService} 拆开的理由：本类只碰 `ALIPAY_USER_INFO` 一张表、
 * 不出网、不需要卡池与乘车状态；把它和开户放在一起时，那 5 个开户专用依赖对这三个方法是纯噪音。
 *
 * <p>{@code updatePaymentChannel} 由 alipay-pay-sign 侧签约成功后回调，
 * 它带 `@Transactional` 且**方法内没有任何 RPC**，符合「事务内 NEVER 发 RPC」。
 */
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

    /**
     * 实体转对外 DTO。
     *
     * <p><b>只映射 7 个字段是刻意的</b>：`AlipayUserInfoDTO` 是跨模块契约，
     * `cardIssueCode` / `status` / `extend*` 等列不在其中。下游需要新字段时
     * MUST 先确认那是不是它该知道的信息，NEVER 顺手把整个实体摊平。
     */
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
