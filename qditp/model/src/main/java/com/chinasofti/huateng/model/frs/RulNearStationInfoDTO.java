package com.chinasofti.huateng.model.frs;

import java.io.Serializable;
import java.time.LocalDateTime;

public class RulNearStationInfoDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;

    private Integer paraVerNo;

    private String stationCode;

    private String nextStationCode;

    private String incomeCode;

    private String runtimeInter;

    private Integer runDistance;

    private Integer runTime;

    private Integer percent;

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

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getNextStationCode() {
        return nextStationCode;
    }

    public void setNextStationCode(String nextStationCode) {
        this.nextStationCode = nextStationCode;
    }

    public String getIncomeCode() {
        return incomeCode;
    }

    public void setIncomeCode(String incomeCode) {
        this.incomeCode = incomeCode;
    }

    public String getRuntimeInter() {
        return runtimeInter;
    }

    public void setRuntimeInter(String runtimeInter) {
        this.runtimeInter = runtimeInter;
    }

    public Integer getRunDistance() {
        return runDistance;
    }

    public void setRunDistance(Integer runDistance) {
        this.runDistance = runDistance;
    }

    public Integer getRunTime() {
        return runTime;
    }

    public void setRunTime(Integer runTime) {
        this.runTime = runTime;
    }

    public Integer getPercent() {
        return percent;
    }

    public void setPercent(Integer percent) {
        this.percent = percent;
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
