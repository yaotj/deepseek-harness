package com.chinasofti.huateng.model.frs;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RulCaseMapDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private BigDecimal caseNo;

    private String caseName;

    private String caseDesc;

    private String caseStat;

    private String lastUpdUser;

    private LocalDateTime lastUpdTms;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public BigDecimal getCaseNo() {
        return caseNo;
    }

    public void setCaseNo(BigDecimal caseNo) {
        this.caseNo = caseNo;
    }

    public String getCaseName() {
        return caseName;
    }

    public void setCaseName(String caseName) {
        this.caseName = caseName;
    }

    public String getCaseDesc() {
        return caseDesc;
    }

    public void setCaseDesc(String caseDesc) {
        this.caseDesc = caseDesc;
    }

    public String getCaseStat() {
        return caseStat;
    }

    public void setCaseStat(String caseStat) {
        this.caseStat = caseStat;
    }

    public String getLastUpdUser() {
        return lastUpdUser;
    }

    public void setLastUpdUser(String lastUpdUser) {
        this.lastUpdUser = lastUpdUser;
    }

    public LocalDateTime getLastUpdTms() {
        return lastUpdTms;
    }

    public void setLastUpdTms(LocalDateTime lastUpdTms) {
        this.lastUpdTms = lastUpdTms;
    }
}
