package com.chinasofti.huateng.facepay.api.device.tvm;

import com.chinasofti.huateng.facepay.api.device.BaseDeviceRequest;

/**
 * IF2A-01 提交单程票订单（TVM 拉码下单）请求报文。
 * 对应 {@code POST /itptvm/ci/tvm/requestGenSjtOrder} 的 {@code bizData}。
 *
 * <p><b>字段名逐字照搬旧 {@code RequestGenSjtOrderReqDTO}，包括拼写错误。</b>
 * {@code singelTicketNum} 少一个 {@code l}（不是 {@code singleTicketNum}），而同一份报文里的
 * {@code singleTicketType} 拼写正常。这是设备实际发送的名字，NEVER 顺手改正——改了就收不到值。</p>
 *
 * <p><b>没有 {@code orderNo} 字段</b>：订单号由 ITP 生成后返给设备，不是设备传入。这条否掉了
 * 「靠 `UK_F2F_ORDER_NO` 兜设备重发」的设想，防重口径见设计文档 §十六。</p>
 *
 * <p>金额单位是<b>分</b>，但类型是 {@code String}（旧契约如此）。</p>
 */
public class RequestGenSjtOrderReqDTO extends BaseDeviceRequest {

    /** 起点站点代码。{@code singleTicketType=0}（按站点购票）时必填。 */
    private String entryStationCode;

    /** 终点站点代码。{@code singleTicketType=0}（按站点购票）时必填。 */
    private String exitStationCode;

    /** 票价，单位分。必填。 */
    private String ticketPrice;

    /** 购买数量。必填。**注意拼写 singel**。 */
    private String singelTicketNum;

    /** 购票类型：0-按站点购票，1-按固定票价购票。必填。 */
    private String singleTicketType;

    /** 0-其他支付方式，1-数字人民币 APP。{@code 0} 走本地聚合码分支、不调支付中心。 */
    private String payType;

    public String getEntryStationCode() {
        return entryStationCode;
    }

    public void setEntryStationCode(String entryStationCode) {
        this.entryStationCode = entryStationCode;
    }

    public String getExitStationCode() {
        return exitStationCode;
    }

    public void setExitStationCode(String exitStationCode) {
        this.exitStationCode = exitStationCode;
    }

    public String getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(String ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getSingelTicketNum() {
        return singelTicketNum;
    }

    public void setSingelTicketNum(String singelTicketNum) {
        this.singelTicketNum = singelTicketNum;
    }

    public String getSingleTicketType() {
        return singleTicketType;
    }

    public void setSingleTicketType(String singleTicketType) {
        this.singleTicketType = singleTicketType;
    }

    public String getPayType() {
        return payType;
    }

    public void setPayType(String payType) {
        this.payType = payType;
    }

    @Override
    public String toString() {
        return super.toString() + ",RequestGenSjtOrderReqDTO{entryStationCode=" + entryStationCode
                + ", exitStationCode=" + exitStationCode
                + ", ticketPrice=" + ticketPrice
                + ", singelTicketNum=" + singelTicketNum
                + ", singleTicketType=" + singleTicketType
                + ", payType=" + payType
                + '}';
    }
}
