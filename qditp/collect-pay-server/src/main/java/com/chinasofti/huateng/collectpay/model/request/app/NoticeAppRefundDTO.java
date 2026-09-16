package com.chinasofti.huateng.collectpay.model.request.app;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import com.chinasofti.huateng.collectpay.model.request.PayCenterBaseRequestDTO;
import lombok.Data;

/**
 * 5.2 退款回调 支付中心回调itp
 */
@Data
public class NoticeAppRefundDTO {

    private String orderNo;
    private String refundType;
    private String refundResult;
    private String refundResultDesc;
    private String refundDate;
    private String refundAmount;

    @Override
    public String toString() {
        return "NoticeAppRefundDTO{" +
                "orderNo='" + orderNo + '\'' +
                ", refundType='" + refundType + '\'' +
                ", refundResult='" + refundResult + '\'' +
                ", refundResultDesc='" + refundResultDesc + '\'' +
                ", refundDate='" + refundDate + '\'' +
                ", refundAmount='" + refundAmount + '\'' +
                '}';
    }
}
