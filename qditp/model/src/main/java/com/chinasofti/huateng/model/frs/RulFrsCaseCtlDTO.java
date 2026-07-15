package com.chinasofti.huateng.model.frs;

import java.io.Serializable;
import java.time.LocalDateTime;

public class RulFrsCaseCtlDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private Integer paraVerNo;

    private String calStatus;

    private Long calRate;

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

    public String getCalStatus() {
        return calStatus;
    }

    public void setCalStatus(String calStatus) {
        this.calStatus = calStatus;
    }

    public Long getCalRate() {
        return calRate;
    }

    public void setCalRate(Long calRate) {
        this.calRate = calRate;
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
