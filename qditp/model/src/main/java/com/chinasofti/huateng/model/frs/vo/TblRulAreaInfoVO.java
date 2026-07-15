package com.chinasofti.huateng.model.frs.vo;


import java.io.Serializable;
import java.util.Date;

public class TblRulAreaInfoVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer paraVerNo;
    private String priceType;
    private String beginStatCode;
    private String endStatCode;
    private String feeType;

    private Integer feeLevel;

    private Integer areaPrice;

    private Date lastUpdTms;

    private String lastUpdTxnId;

    private String lastUpdUser;

    public Integer getParaVerNo() {
        return paraVerNo;
    }

    public void setParaVerNo(Integer paraVerNo) {
        this.paraVerNo = paraVerNo;
    }

    public String getPriceType() {
        return priceType;
    }

    public void setPriceType(String priceType) {
        this.priceType = priceType;
    }

    public String getBeginStatCode() {
        return beginStatCode;
    }

    public void setBeginStatCode(String beginStatCode) {
        this.beginStatCode = beginStatCode;
    }

    public String getEndStatCode() {
        return endStatCode;
    }

    public void setEndStatCode(String endStatCode) {
        this.endStatCode = endStatCode;
    }

    public String getFeeType() {
        return feeType;
    }

    public void setFeeType(String feeType) {
        this.feeType = feeType;
    }

    public Integer getFeeLevel() {
        return feeLevel;
    }

    public void setFeeLevel(Integer feeLevel) {
        this.feeLevel = feeLevel;
    }

    public Integer getAreaPrice() {
        return areaPrice;
    }

    public void setAreaPrice(Integer areaPrice) {
        this.areaPrice = areaPrice;
    }

    public Date getLastUpdTms() {
        return lastUpdTms;
    }

    public void setLastUpdTms(Date lastUpdTms) {
        this.lastUpdTms = lastUpdTms;
    }

    public String getLastUpdTxnId() {
        return lastUpdTxnId;
    }

    public void setLastUpdTxnId(String lastUpdTxnId) {
        this.lastUpdTxnId = lastUpdTxnId;
    }

    public String getLastUpdUser() {
        return lastUpdUser;
    }

    public void setLastUpdUser(String lastUpdUser) {
        this.lastUpdUser = lastUpdUser;
    }
}