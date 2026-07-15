package com.chinasofti.huateng.model.frs;

import java.io.Serializable;
import java.time.LocalDateTime;

public class RulTsfInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private Integer paraVerNo;

    private String fromStatCode;

    private String fromLineCode;

    private String toLineCode;

    private String runtimeInter;

    private String toStatCode;

    private String tsfStationType;

    private Integer tsfTime;

    private Integer tsfDistance;

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

    public String getFromStatCode() {
        return fromStatCode;
    }

    public void setFromStatCode(String fromStatCode) {
        this.fromStatCode = fromStatCode;
    }

    public String getFromLineCode() {
        return fromLineCode;
    }

    public void setFromLineCode(String fromLineCode) {
        this.fromLineCode = fromLineCode;
    }

    public String getToLineCode() {
        return toLineCode;
    }

    public void setToLineCode(String toLineCode) {
        this.toLineCode = toLineCode;
    }

    public String getRuntimeInter() {
        return runtimeInter;
    }

    public void setRuntimeInter(String runtimeInter) {
        this.runtimeInter = runtimeInter;
    }

    public String getToStatCode() {
        return toStatCode;
    }

    public void setToStatCode(String toStatCode) {
        this.toStatCode = toStatCode;
    }

    public String getTsfStationType() {
        return tsfStationType;
    }

    public void setTsfStationType(String tsfStationType) {
        this.tsfStationType = tsfStationType;
    }

    public Integer getTsfTime() {
        return tsfTime;
    }

    public void setTsfTime(Integer tsfTime) {
        this.tsfTime = tsfTime;
    }

    public Integer getTsfDistance() {
        return tsfDistance;
    }

    public void setTsfDistance(Integer tsfDistance) {
        this.tsfDistance = tsfDistance;
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
