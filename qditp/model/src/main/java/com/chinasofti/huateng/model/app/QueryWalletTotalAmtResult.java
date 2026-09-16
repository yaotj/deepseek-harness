package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

/** 钱包累计金额查询响应，totalAmt 单位为分。 */
public class QueryWalletTotalAmtResult extends CommonResult {
    private Integer totalAmt;

    public Integer getTotalAmt() { return totalAmt; }
    public void setTotalAmt(Integer totalAmt) { this.totalAmt = totalAmt; }
}
