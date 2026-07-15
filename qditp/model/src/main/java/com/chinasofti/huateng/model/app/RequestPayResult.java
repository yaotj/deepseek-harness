package com.chinasofti.huateng.model.app;

import java.util.Map;

/**
 * 支付 API 1.1 请求支付响应。
 *
 * <p>retCode/retMsg 给内部服务判断 ITP 侧结果，code/msg/success/data 保留支付网关原始通用响应。</p>
 */
public class RequestPayResult {
    private String retCode;
    private String retMsg;
    private Integer code;
    private String msg;
    private Boolean success;
    private Map<String, Object> data;
    private String orderNo;
    private String merchantOrderNo;
    private String channelOrderNo;
    private String payData;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getMerchantOrderNo() {
        return merchantOrderNo;
    }

    public void setMerchantOrderNo(String merchantOrderNo) {
        this.merchantOrderNo = merchantOrderNo;
    }

    public String getChannelOrderNo() {
        return channelOrderNo;
    }

    public void setChannelOrderNo(String channelOrderNo) {
        this.channelOrderNo = channelOrderNo;
    }

    public String getPayData() {
        return payData;
    }

    public void setPayData(String payData) {
        this.payData = payData;
    }

    @Override
    public String toString() {
        return "RequestPayResult{" +
                "retCode='" + retCode + '\'' +
                ", retMsg='" + retMsg + '\'' +
                ", code=" + code +
                ", msg='" + msg + '\'' +
                ", success=" + success +
                ", orderNo='" + orderNo + '\'' +
                ", merchantOrderNo='" + merchantOrderNo + '\'' +
                ", channelOrderNo='" + channelOrderNo + '\'' +
                '}';
    }
}
