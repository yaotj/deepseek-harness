package com.chinasofti.huateng.model.frs;

import java.io.Serializable;
import java.time.LocalDateTime;

public class RulFeeInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private Integer paraVerNo;

    private String feeType;

    private Integer lineAreaNo;

    private Integer areaNo;

    private Integer beginScope;

    private Integer endScope;

    private Integer areaPrice;

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

    public String getFeeType() {
        return feeType;
    }

    public void setFeeType(String feeType) {
        this.feeType = feeType;
    }

    public Integer getLineAreaNo() {
        return lineAreaNo;
    }

    public void setLineAreaNo(Integer lineAreaNo) {
        this.lineAreaNo = lineAreaNo;
    }

    public Integer getAreaNo() {
        return areaNo;
    }

    public void setAreaNo(Integer areaNo) {
        this.areaNo = areaNo;
    }

    public Integer getBeginScope() {
        return beginScope;
    }

    public void setBeginScope(Integer beginScope) {
        this.beginScope = beginScope;
    }

    public Integer getEndScope() {
        return endScope;
    }

    public void setEndScope(Integer endScope) {
        this.endScope = endScope;
    }

    public Integer getAreaPrice() {
        return areaPrice;
    }

    public void setAreaPrice(Integer areaPrice) {
        this.areaPrice = areaPrice;
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
