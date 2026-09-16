package com.chinasofti.huateng.model.app;

import java.util.List;

/**
 * IF8A-05 查询交易记录列表请求参数（重构版）。
 *
 * <p>替代 {@link com.chinasofti.huateng.ticket.model.app.RequestTransListReqDTO}，
 * 修复原 DTO 中 {@code int offset/limit} 基本类型导致的 NPE/默认值 0 问题。
 * 所有分页字段统一使用 {@link Integer} 包装类型。</p>
 */
public class QueryTransListReqDTO {

    /** 第三方用户ID（必填） */
    private String thirdUserId;
    /** 卡号，支持逗号分隔多卡号 */
    private String cardId;
    /** 解析后的卡号列表（内部使用） */
    private List<String> cardIdList;
    /** APP 卡类型，经 CardTypeMapping 映射后查询发卡卡类型 */
    private String cardType;
    /**
     * 映射后的发卡卡类型列表（内部使用，由 CardTypeMapping#toIssueCardTypes 生成）。
     *
     * <p>非空时下游按 {@code CARD_TYPE IN (...)} 过滤并**忽略** {@link #cardType}；
     * APP 的日票聚合码 05 会展开成 0445~0448 四个值，因此这里 MUST 用列表而非单值。</p>
     */
    private List<String> cardTypeList;
    /** 页码，从 1 开始，默认 1 */
    private Integer pageNumber;
    /** 每页条数，默认 10，最大 MAX_PAGE_SIZE */
    private Integer pageSize;
    /** 开始日期 yyyy-MM-dd（非必填） */
    private String startDate;
    /** 结束日期 yyyy-MM-dd（非必填） */
    private String endDate;
    /** 扣款结果：空=全部，"0"=成功，"1"=失败（非必填） */
    private String debitRequestResult;
    /** 日票票号（非必填） */
    private String ticketCode;
    /** 服务端计算的分页偏移量（非 APP 传入） */
    private Integer offset;
    /** 服务端计算的分页限制（非 APP 传入） */
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
    public Integer getOffset() { return offset; }
    public void setOffset(Integer offset) { this.offset = offset; }
    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
