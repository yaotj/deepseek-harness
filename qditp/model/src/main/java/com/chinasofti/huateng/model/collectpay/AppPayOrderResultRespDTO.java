package com.chinasofti.huateng.model.collectpay;

/**
 * {@code TBL_TVM_APP_ORDER} 的支付结果回查应答（{@code POST /internal/app-order/pay-result}）。
 *
 * <p><b>{@code found} 与「调用失败」是两件不同的事，调用方 MUST 分开处置</b>：
 * {@code found=false} 是对端明确答复「这张单没登记进 APP 订单表」，属真实的业务事实
 * （乘客根本无法支付，MUST 落 ERROR 转人工 / 重新登记）；而连不上、超时、5xx
 * 会让 {@code CollectPayClient} 的查询方法**抛异常**，那时什么都不知道，
 * <b>NEVER</b> 当成 {@code found=false} —— 否则一次网络抖动就会被误判成「单据丢失」。</p>
 *
 * <p>{@code payStatus} 取值来自 collect-pay 的 {@code ItpStatusEnum}：{@code 0} / {@code 1} /
 * {@code 2} / {@code 3}。判成功 MUST 严格等于 {@code 1}，<b>NEVER</b> 写成「非 2 即成功」。</p>
 *
 * <p><b>{@code 0} 的措辞在 collect-pay 侧是矛盾的，NEVER 按字面理解</b>：
 * {@code ItpStatusEnum.PAYING} 的 desc 写的是「支付中」，但 {@code TBL_TVM_APP_ORDER} 上
 * {@code PAY_STATUS='0'} 实际就是**下单后尚未支付**的初始态 —— 关单接口的白名单
 * （{@code TvmAppOrderMapper.closeUnpaidByOrderNo} 的 {@code and PAY_STATUS = '0'}）正是靠它
 * 选中可关的行。把它当成「钱可能正在路上、不能动」会得出完全相反的结论。
 * 另注意该枚举里 {@code 0} / {@code 1} / {@code 2} 各有两个常量（支付组与退款组共用 code），
 * {@code fromCode} 只会返回先声明的支付组，<b>NEVER</b> 拿它去解释退款状态。</p>
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
