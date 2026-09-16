package com.chinasofti.huateng.model.collectpay;

/**
 * {@code /internal/app-order/**} 写操作（登记 / 关单）的应答。
 *
 * <p>{@code retCode} 沿用本项目服务间统一口径：{@code 0000} 成功，其余为业务拒绝。
 * 调用方 MUST 用 {@code RpcOutcome} 的三分支模式匹配处置，
 * <b>NEVER</b> 只判「没抛异常就算成功」（AGENTS.md §5.2）。</p>
 */
public class AppPayOrderRespDTO {

    private String retCode;
    private String retMsg;
    private String orderNo;

    public static AppPayOrderRespDTO success(String orderNo, String retMsg) {
        AppPayOrderRespDTO resp = new AppPayOrderRespDTO();
        resp.setRetCode("0000");
        resp.setRetMsg(retMsg);
        resp.setOrderNo(orderNo);
        return resp;
    }

    public static AppPayOrderRespDTO reject(String retCode, String retMsg, String orderNo) {
        AppPayOrderRespDTO resp = new AppPayOrderRespDTO();
        resp.setRetCode(retCode);
        resp.setRetMsg(retMsg);
        resp.setOrderNo(orderNo);
        return resp;
    }

    public String getRetCode() { return retCode; }

    public void setRetCode(String retCode) { this.retCode = retCode; }

    public String getRetMsg() { return retMsg; }

    public void setRetMsg(String retMsg) { this.retMsg = retMsg; }

    public String getOrderNo() { return orderNo; }

    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    @Override
    public String toString() {
        return "AppPayOrderRespDTO{retCode='" + retCode + "', retMsg='" + retMsg
                + "', orderNo='" + orderNo + "'}";
    }
}
