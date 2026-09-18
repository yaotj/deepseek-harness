package com.chinasofti.huateng.alipay.paysign.service.impl.pay;

import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import org.springframework.util.StringUtils;

/**
 * 一次扣费申请的**已校验**入参，扣费链路内部只认它、不再传裸 DTO 做判断。
 *
 * <p>{@code AlipayTripRequestPayReqDTO} 有 16 个字段，其中只有这 7 个参与「这笔申请能不能发」的判定；
 * 其余 9 个（{@code scene} / {@code paymentVendor} / {@code orderTimeOut} / {@code authCode} /
 * {@code notifyUrl} / {@code returnUrl} / {@code ipAddress} / {@code remark} / {@code requestSignSeq}）
 * 只在组装出网 bizData 时用到，**归 {@code BizDataBuilder} 管，NEVER 搬进本记录** —— 搬进来就等于
 * 把「入参契约」和「出网报文契约」焊在一起，那两者的变更节奏完全不同。
 *
 * <p><b>{@code thirdUserId} 必填是本链路与更旧的 {@code PaymentRequestService} 的实质差异</b>：
 * 那份实现的六项校验里没有它，null 时会一路走到查签约、被当成「用户未签约」返回，
 * 把参数问题伪装成业务问题。<b>NEVER 把它从必填里去掉。</b>
 *
 * <p>{@code requestSignSeq} 刻意不在本记录里：入参带的那个值**不可信**，实现会用本地生效签约的
 * {@code agreementCode} 覆盖它（沿用既有口径）。放进值对象会让人以为它是判定依据。
 */
record PayCommand(String orderNo,
                  String thirdUserId,
                  Integer amount,
                  String industryType,
                  String subject,
                  String body,
                  String industryDetail) {

    /**
     * 校验并收口入参。文案与既有实现逐字一致 —— 上游 gate-txn-pay 侧已按这句话排错，改字面量等于改契约。
     */
    static PayCommand from(AlipayTripRequestPayReqDTO request) {
        boolean valid = request != null
                && StringUtils.hasText(request.getOrderNo())
                && StringUtils.hasText(request.getThirdUserId())
                && request.getAmount() != null
                && StringUtils.hasText(request.getIndustryType())
                && StringUtils.hasText(request.getSubject())
                && StringUtils.hasText(request.getBody())
                && StringUtils.hasText(request.getIndustryDetail());
        if (!valid) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(),
                    "订单号/第三方用户号/支付金额/行业类型/订单标题/订单描述/行业详情不能为空");
        }
        return new PayCommand(request.getOrderNo(), request.getThirdUserId(), request.getAmount(),
                request.getIndustryType(), request.getSubject(), request.getBody(), request.getIndustryDetail());
    }
}
