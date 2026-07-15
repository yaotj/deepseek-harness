package com.chinasofti.huateng.model.frs;

import java.io.Serializable;
import java.time.LocalDateTime;

public class RulOdDistDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private Integer paraVerNo;

    private String runtimeInter;

    private String begStatCode;

    private String endStatCode;

    private String lineCode;

    private Integer innerRate;

    private Integer innerDistance;

    private Integer outRate;

    private Integer outDistance;

    private Integer inRate;

    private Integer inDistance;

    private Integer passRate;

    private Integer passDistance;

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

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public Integer getInnerRate() {
        return innerRate;
    }

    public void setInnerRate(Integer innerRate) {
        this.innerRate = innerRate;
    }

    public Integer getInnerDistance() {
        return innerDistance;
    }

    public void setInnerDistance(Integer innerDistance) {
        this.innerDistance = innerDistance;
    }

    public Integer getOutRate() {
        return outRate;
    }

    public void setOutRate(Integer outRate) {
        this.outRate = outRate;
    }

    public Integer getOutDistance() {
        return outDistance;
    }

    public void setOutDistance(Integer outDistance) {
        this.outDistance = outDistance;
    }

    public Integer getInRate() {
        return inRate;
    }

    public void setInRate(Integer inRate) {
        this.inRate = inRate;
    }

    public Integer getInDistance() {
        return inDistance;
    }

    public void setInDistance(Integer inDistance) {
        this.inDistance = inDistance;
    }

    public Integer getPassRate() {
        return passRate;
    }

    public void setPassRate(Integer passRate) {
        this.passRate = passRate;
    }

    public Integer getPassDistance() {
        return passDistance;
    }

    public void setPassDistance(Integer passDistance) {
        this.passDistance = passDistance;
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
