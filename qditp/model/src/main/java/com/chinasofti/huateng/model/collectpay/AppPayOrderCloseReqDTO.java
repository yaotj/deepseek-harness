package com.chinasofti.huateng.model.collectpay;

/**
 * 按订单号关闭 {@code TBL_TVM_APP_ORDER} 上待支付行的请求（{@code POST /internal/app-order/close-unpaid}）。
 *
 * <p>关单在 collect-pay 侧是**带白名单的条件更新**（只放 {@code PAY_STATUS='0'}），
 * 因此天然幂等：已支付 / 已失败的行影响 0 行、返回成功。</p>
 *
 * <p>不做这一步的后果：乘客对一张已作废 / 已关闭的补款单再点一次支付时，
 * collect-pay 仍会看到 {@code PAY_STATUS='0'} 而正常发起预下单 —— 钱收进来了、
 * 上游那张单却已关闭，无人收敛。</p>
 */
public class AppPayOrderCloseReqDTO {

    /** 订单号。 */
    private String orderNo;

    /** 关单原因，落 {@code MSG}。 */
    private String msg;

    public String getOrderNo() { return orderNo; }

    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getMsg() { return msg; }

    public void setMsg(String msg) { this.msg = msg; }

    @Override
    public String toString() {
        return "AppPayOrderCloseReqDTO{orderNo='" + orderNo + "', msg='" + msg + "'}";
    }
}
