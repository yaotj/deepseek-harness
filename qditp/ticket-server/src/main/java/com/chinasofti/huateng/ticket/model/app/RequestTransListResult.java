package com.chinasofti.huateng.ticket.model.app;

import java.util.List;

/**
 * IF8A-05 请求查询交易记录应答。
 */
public class RequestTransListResult {
    private String retCode;
    private String retMsg;
    private String pageNumber;
    private String pageSize;
    private String totalPage;
    private List<TransRecordDTO> ticketTransRecord;

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
}
