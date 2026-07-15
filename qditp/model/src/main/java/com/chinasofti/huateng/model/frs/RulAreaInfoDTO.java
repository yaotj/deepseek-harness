package com.chinasofti.huateng.model.frs;


import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RulAreaInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private BigDecimal feeType;

    private Integer paraVerNo;

    private String priceType;

    private String begStatCode;

    private String endStatCode;

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

    public BigDecimal getFeeType() {
        return feeType;
    }

    public void setFeeType(BigDecimal feeType) {
        this.feeType = feeType;
    }

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
