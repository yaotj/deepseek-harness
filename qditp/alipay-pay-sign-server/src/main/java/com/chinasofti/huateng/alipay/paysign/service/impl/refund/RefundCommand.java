package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import org.springframework.util.StringUtils;


record RefundCommand(String orderNo, String refundAmount) {

    static RefundCommand from(AlipayTripRequestRefundReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }
        return new RefundCommand(request.getOrderNo(), request.getRefundAmount());
    }
}
