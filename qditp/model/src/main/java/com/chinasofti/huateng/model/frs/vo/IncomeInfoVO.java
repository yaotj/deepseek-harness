package com.chinasofti.huateng.model.frs.vo;


import com.chinasofti.huateng.model.frs.RulIncomeInfoDTO;

public class IncomeInfoVO {
    String preIncomeCode;

    RulIncomeInfoDTO incomeInfo;

    public String getPreIncomeCode() {
        return preIncomeCode;
    }

    public void setPreIncomeCode(String preIncomeCode) {
        this.preIncomeCode = preIncomeCode;
    }

    public RulIncomeInfoDTO getIncomeInfo() {
        return incomeInfo;
    }

    public void setIncomeInfo(RulIncomeInfoDTO incomeInfo) {
        this.incomeInfo = incomeInfo;
    }
}
