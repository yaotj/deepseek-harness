package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

public class QueryWalletTotalAmtResult extends CommonResult {
    private Integer totalAmt;

    public Integer getTotalAmt() { return totalAmt; }
    public void setTotalAmt(Integer totalAmt) { this.totalAmt = totalAmt; }
}
