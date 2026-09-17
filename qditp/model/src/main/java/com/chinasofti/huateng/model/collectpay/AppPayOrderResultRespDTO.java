package com.chinasofti.huateng.model.collectpay;

/**
 * {@code TBL_TVM_APP_ORDER} 的支付结果回查应答（{@code POST /internal/app-order/pay-result}）。
 */
public class AppPayOrderResultRespDTO {

    private String retCode;
    private String retMsg;

    /** APP 订单表里是否存在该订单号对应的行。 */
    private boolean found;

    private String orderNo;
    private String payStatus;
    private String payAmount;
    private String payTime;
    private String payChannelCode;
    private String merchantOrderNo;
    private String paymentInfo;

    public static AppPayOrderResultRespDTO notFound(String orderNo) {
        AppPayOrderResultRespDTO resp = new AppPayOrderResultRespDTO();
        resp.setRetCode("0000");
        resp.setRetMsg("成功");
        resp.setFound(false);
        resp.setOrderNo(orderNo);
        return resp;
    }

    public String getRetCode() { return retCode; }

    public void setRetCode(String retCode) { this.retCode = retCode; }

    public String getRetMsg() { return retMsg; }

    public void setRetMsg(String retMsg) { this.retMsg = retMsg; }

    public boolean isFound() { return found; }

    public void setFound(boolean found) { this.found = found; }

    public String getOrderNo() { return orderNo; }

    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getPayStatus() { return payStatus; }

    public void setPayStatus(String payStatus) { this.payStatus = payStatus; }

    public String getPayAmount() { return payAmount; }

    public void setPayAmount(String payAmount) { this.payAmount = payAmount; }

    public String getPayTime() { return payTime; }

    public void setPayTime(String payTime) { this.payTime = payTime; }

    public String getPayChannelCode() { return payChannelCode; }

    public void setPayChannelCode(String payChannelCode) { this.payChannelCode = payChannelCode; }

    public String getMerchantOrderNo() { return merchantOrderNo; }

    public void setMerchantOrderNo(String merchantOrderNo) { this.merchantOrderNo = merchantOrderNo; }

    public String getPaymentInfo() { return paymentInfo; }

    public void setPaymentInfo(String paymentInfo) { this.paymentInfo = paymentInfo; }

    @Override
    public String toString() {
        return "AppPayOrderResultRespDTO{" +
                "retCode='" + retCode + '\'' +
                ", found=" + found +
                ", orderNo='" + orderNo + '\'' +
                ", payStatus='" + payStatus + '\'' +
                ", payAmount='" + payAmount + '\'' +
                ", payTime='" + payTime + '\'' +
                ", payChannelCode='" + payChannelCode + '\'' +
                ", merchantOrderNo='" + merchantOrderNo + '\'' +
                '}';
    }
}
