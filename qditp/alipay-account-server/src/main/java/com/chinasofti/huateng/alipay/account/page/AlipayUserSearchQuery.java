package com.chinasofti.huateng.alipay.account.page;

/** 支付宝注册用户后台查询条件。 */
public class AlipayUserSearchQuery {
    private String queryType;
    private String keyword;
    /** 页码，缺省由服务层按 1 兜底。 */
    private Integer pageNum;
    /** 每页条数，缺省由服务层按 10 兜底、上限 100。 */
    private Integer pageSize;

    public String getQueryType() { return queryType; }
    public void setQueryType(String queryType) { this.queryType = queryType; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public Integer getPageNum() { return pageNum; }
    public void setPageNum(Integer pageNum) { this.pageNum = pageNum; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer pageSize) { this.pageSize = pageSize; }
}
