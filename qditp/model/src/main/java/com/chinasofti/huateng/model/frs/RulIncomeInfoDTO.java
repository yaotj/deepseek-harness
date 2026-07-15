package com.chinasofti.huateng.model.frs;

import java.io.Serializable;
import java.time.LocalDateTime;

public class RulIncomeInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;

    private Integer paraVerNo;

    private String incomeCode;

    private String incomeNm;

    private String incomeConNm;

    private String incomeConTel;

    private String incomeAddr;

    private String note;

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

    public String getIncomeCode() {
        return incomeCode;
    }

    public void setIncomeCode(String incomeCode) {
        this.incomeCode = incomeCode;
    }

    public String getIncomeNm() {
        return incomeNm;
    }

    public void setIncomeNm(String incomeNm) {
        this.incomeNm = incomeNm;
    }

    public String getIncomeConNm() {
        return incomeConNm;
    }

    public void setIncomeConNm(String incomeConNm) {
        this.incomeConNm = incomeConNm;
    }

    public String getIncomeConTel() {
        return incomeConTel;
    }

    public void setIncomeConTel(String incomeConTel) {
        this.incomeConTel = incomeConTel;
    }

    public String getIncomeAddr() {
        return incomeAddr;
    }

    public void setIncomeAddr(String incomeAddr) {
        this.incomeAddr = incomeAddr;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
