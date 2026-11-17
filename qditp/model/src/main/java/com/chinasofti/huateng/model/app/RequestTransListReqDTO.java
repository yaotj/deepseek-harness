package com.chinasofti.huateng.model.app;


public class RequestTransListReqDTO {
    private String thirdUserId;
    private String cardId;
    private String cardType;
    private Integer pageNumber;
    private Integer pageSize;
    private Integer totalPage;
    private String startDate;
    private String endDate;
    private String debitRequestResult;
    private String ticketCode;

    public String getThirdUserId() { return thirdUserId; }
    public void setThirdUserId(String thirdUserId) { this.thirdUserId = thirdUserId; }
    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
    public Integer getTotalPage() { return totalPage; }
    public void setTotalPage(Integer totalPage) { this.totalPage = totalPage; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public String getDebitRequestResult() { return debitRequestResult; }
    public void setDebitRequestResult(String debitRequestResult) { this.debitRequestResult = debitRequestResult; }
    public String getTicketCode() { return ticketCode; }
    public void setTicketCode(String ticketCode) { this.ticketCode = ticketCode; }

    @Override
    public String toString() {
        return "RequestTransListReqDTO{thirdUserId='" + thirdUserId + "', cardId='" + cardId
                + "', cardType='" + cardType + "', pageNumber=" + pageNumber
                + ", pageSize=" + pageSize + ", startDate='" + startDate
                + "', endDate='" + endDate + "', debitRequestResult='" + debitRequestResult
                + "', ticketCode='" + ticketCode + "'}";
    }
}
