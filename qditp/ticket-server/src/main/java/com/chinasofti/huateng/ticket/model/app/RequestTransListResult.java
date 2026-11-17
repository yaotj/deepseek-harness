package com.chinasofti.huateng.ticket.model.app;

import com.chinasofti.huateng.model.app.TransRecordDTO;

import java.util.List;

public class RequestTransListResult {
    private String retCode;
    private String retMsg;
    private String pageNumber;
    private String pageSize;
    private String totalPage;
    private List<TransRecordDTO> ticketTransRecord;
    private String signType;
    private String sign;

    public String getRetCode() { return retCode; }
    public void setRetCode(String retCode) { this.retCode = retCode; }
    public String getRetMsg() { return retMsg; }
    public void setRetMsg(String retMsg) { this.retMsg = retMsg; }
    public String getPageNumber() { return pageNumber; }
    public void setPageNumber(String pageNumber) { this.pageNumber = pageNumber; }
    public String getPageSize() { return pageSize; }
    public void setPageSize(String pageSize) { this.pageSize = pageSize; }
    public String getTotalPage() { return totalPage; }
    public void setTotalPage(String totalPage) { this.totalPage = totalPage; }
    public List<TransRecordDTO> getTicketTransRecord() { return ticketTransRecord; }
    public void setTicketTransRecord(List<TransRecordDTO> ticketTransRecord) { this.ticketTransRecord = ticketTransRecord; }
    public String getSignType() { return signType; }
    public void setSignType(String signType) { this.signType = signType; }
    public String getSign() { return sign; }
    public void setSign(String sign) { this.sign = sign; }
}
