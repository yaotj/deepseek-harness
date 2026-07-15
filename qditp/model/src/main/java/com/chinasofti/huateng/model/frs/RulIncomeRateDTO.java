package com.chinasofti.huateng.model.frs;

import java.io.Serializable;
import java.time.LocalDateTime;

public class RulIncomeRateDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;

    private Integer paraVerNo;

    private String runtimeInter;

    private String begStatCode;

    private String endStatCode;

    private String incomeCode;

    private Integer incomeRate;

    private String lastUpdUser;

    private String lastUpdTxnId;

    private LocalDateTime lastUpdTms;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Integer getParaVerNo() {
        return paraVerNo;
    }

    public void setParaVerNo(Integer paraVerNo) {
        this.paraVerNo = paraVerNo;
    }

    public String getRuntimeInter() {
        return runtimeInter;
    }

    public void setRuntimeInter(String runtimeInter) {
        this.runtimeInter = runtimeInter;
    }

    public String getBegStatCode() {
        return begStatCode;
    }

    public void setBegStatCode(String begStatCode) {
        this.begStatCode = begStatCode;
    }

    public String getEndStatCode() {
        return endStatCode;
    }

    public void setEndStatCode(String endStatCode) {
        this.endStatCode = endStatCode;
    }

    public String getIncomeCode() {
        return incomeCode;
    }

    public void setIncomeCode(String incomeCode) {
        this.incomeCode = incomeCode;
    }

    public Integer getIncomeRate() {
        return incomeRate;
    }

    public void setIncomeRate(Integer incomeRate) {
        this.incomeRate = incomeRate;
    }

    public String getLastUpdUser() {
        return lastUpdUser;
    }

    public void setLastUpdUser(String lastUpdUser) {
        this.lastUpdUser = lastUpdUser;
    }

    public String getLastUpdTxnId() {
        return lastUpdTxnId;
    }

    public void setLastUpdTxnId(String lastUpdTxnId) {
        this.lastUpdTxnId = lastUpdTxnId;
    }

    public LocalDateTime getLastUpdTms() {
        return lastUpdTms;
    }

    public void setLastUpdTms(LocalDateTime lastUpdTms) {
        this.lastUpdTms = lastUpdTms;
    }
}
