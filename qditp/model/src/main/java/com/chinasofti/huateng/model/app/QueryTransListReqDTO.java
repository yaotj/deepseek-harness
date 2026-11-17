package com.chinasofti.huateng.model.app;

import java.util.List;


public class QueryTransListReqDTO {

    private String thirdUserId;
    private String cardId;
    private List<String> cardIdList;
    private String cardType;

    private List<String> cardTypeList;
    private Integer pageNumber;
    private Integer pageSize;
    private String startDate;
    private String endDate;
    private String debitRequestResult;
    private String ticketCode;

    private String issueChannelCode;
    private Integer offset;
    private Integer limit;

    public static final int MAX_PAGE_SIZE = 100;

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public List<String> getCardIdList() { return cardIdList; }
    public void setCardIdList(List<String> cardIdList) { this.cardIdList = cardIdList; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public List<String> getCardTypeList() { return cardTypeList; }
    public void setCardTypeList(List<String> cardTypeList) { this.cardTypeList = cardTypeList; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public String getDebitRequestResult() { return debitRequestResult; }
    public void setDebitRequestResult(String debitRequestResult) { this.debitRequestResult = debitRequestResult; }
    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }
    public String getIssueChannelCode() { return issueChannelCode; }
    public void setIssueChannelCode(String issueChannelCode) { this.issueChannelCode = issueChannelCode; }
    public Integer getOffset() { return offset; }
    public void setOffset(Integer offset) { this.offset = offset; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
