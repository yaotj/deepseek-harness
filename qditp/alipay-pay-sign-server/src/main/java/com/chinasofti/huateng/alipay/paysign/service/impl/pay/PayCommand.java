package com.chinasofti.huateng.alipay.paysign.service.impl.pay;

import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import org.springframework.util.StringUtils;


record PayCommand(String orderNo,
                  String thirdUserId,
                  Integer amount,
                  String industryType,
                  String subject,
                  String body,
                  String industryDetail) {


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
