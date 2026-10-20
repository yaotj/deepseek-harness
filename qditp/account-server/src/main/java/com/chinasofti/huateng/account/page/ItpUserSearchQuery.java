package com.chinasofti.huateng.account.page;

/**
 * 非支付宝用户注册信息后台查询条件。
 */
public class ItpUserSearchQuery {
    private String queryType;
    private String keyword;
    /** 注册状态：{@code ACTIVE}=有效（{@code DEL_YN=1}），{@code CANCELED}=已注销（{@code DEL_YN=0}）。 */
    private String status;

    public String getQueryType() { return queryType; }
    public void setQueryType(String queryType) { this.queryType = queryType; }
    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
