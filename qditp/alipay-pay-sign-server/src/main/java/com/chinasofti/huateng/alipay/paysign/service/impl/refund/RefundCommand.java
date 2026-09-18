package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import org.springframework.util.StringUtils;

/**
 * 一次退款申请的**已校验**入参，退款链路内部只认它、不再传裸 DTO 做判断。
 *
 * <p>只有 {@code orderNo} 是必填 —— 它是「退哪一笔」的唯一依据。
 * {@code refundAmount} <b>刻意允许为空</b>：空即「按可退余额全额退」，
 * 由 {@code RefundAmountCalculator} 用原支付金额减已退金额算出。
 * <b>NEVER 把它改成必填</b>：运维侧全额退款一直是不传这个字段，改必填等于打挂在用的入口。
 *
 * <p>校验文案与迁移前逐字一致 —— 运维侧已按这句话排错，改字面量等于改契约。
 */
record RefundCommand(String orderNo, String refundAmount) {

    static RefundCommand from(AlipayTripRequestRefundReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }
        return new RefundCommand(request.getOrderNo(), request.getRefundAmount());
    }
}
