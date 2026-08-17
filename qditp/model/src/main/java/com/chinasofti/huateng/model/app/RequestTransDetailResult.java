package com.chinasofti.huateng.model.app;

/**
 * IF8A-34 获取订单详情应答。
 */
public class RequestTransDetailResult {
    private String retCode;
    private String retMsg;
    private TransRecordDTO ticketTransRecord;

    public String getRetCode() { return retCode; }
    public void setRetCode(String retCode) { this.retCode = retCode; }
    public String getRetMsg() { return retMsg; }
    public void setRetMsg(String retMsg) { this.retMsg = retMsg; }
    public TransRecordDTO getTicketTransRecord() { return ticketTransRecord; }
    public void setTicketTransRecord(TransRecordDTO ticketTransRecord) { this.ticketTransRecord = ticketTransRecord; }
}
