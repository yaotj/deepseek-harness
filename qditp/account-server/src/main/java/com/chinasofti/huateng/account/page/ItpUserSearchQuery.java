package com.chinasofti.huateng.account.page;

/** 非支付宝用户注册信息后台查询条件。 */
public class ItpUserSearchQuery {
    private String queryType;
    private String keyword;

    public String getQueryType() { return queryType; }
    public void setQueryType(String queryType) { this.queryType = queryType; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
}
