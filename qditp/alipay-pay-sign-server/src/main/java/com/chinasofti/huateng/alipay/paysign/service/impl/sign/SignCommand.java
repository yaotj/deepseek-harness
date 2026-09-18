package com.chinasofti.huateng.alipay.paysign.service.impl.sign;

import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripAddContractReqDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.domain.AlipaySignStatus;

import java.time.LocalDateTime;

/**
 * 签约请求的**已校验值对象**：过了 {@link #from} 就保证四个必填项非空非空白。
 *
 * <p>抽它的理由不是「看起来整齐」，而是把校验做成**类型上的一次性事件**：编排层拿到
 * {@code SignCommand} 即可直接用字段，不必在每个分支里重复 {@code request.getXxx() == null || trim().isEmpty()}。
 * 原实现那 6 行连写的空判散落在 {@code addContract} 开头，任何新分支都可能绕过它。
 *
 * <p><b>刻意不保存 {@code channel}</b>：入参里的 {@code channel} 只参与「必填校验」，
 * 落库与查询一律用常量 {@link #CHANNEL_ALIPAY} —— 本服务只服务支付宝一个渠道。
 * 把它存进值对象会让人以为「换个渠道值就能签别的渠道」，那是假的扩展点。
 * <b>NEVER 把 {@code CHANNEL_ALIPAY} 改成取入参。</b>
 *
 * <p>校验失败的错误码与文案**逐字沿用**重构前的形态（特征测试钉住了）：
 * {@code INVALID_PARAM} + 「thirdUserId/channel/agreementCode/channelUserAccount不能为空」。
 */
record SignCommand(String thirdUserId,
                   String agreementCode,
                   String channelAgreementCode,
                   String channelUserAccount) {

    /** 本服务只服务支付宝渠道，落库与查询的 {@code CHANNEL} 恒为该值。 */
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

    /**
     * 组装待落库的签约行。卡号与卡类型只能来自账户域（{@code userInfo}），**NEVER 由渠道上送**。
     *
     * <p>状态、操作类型、删除标记、版本号四个字面量与重构前逐字一致：
     * {@code SIGNED} / {@code SIGN} / {@code "0"} / {@code "1"}。
     */
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
