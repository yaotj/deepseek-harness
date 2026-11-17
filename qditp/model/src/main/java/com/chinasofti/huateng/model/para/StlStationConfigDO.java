package com.chinasofti.huateng.model.para;

import java.io.Serializable;
import java.sql.Timestamp;

import com.chinasofti.huateng.model.enums.DeviceTypeEnum;


public class StlStationConfigDO implements Serializable {


    private Long globalId;


    private Long paraVerNo;


    private String stationCode;


    private String devCode;


    private String devType;


    private String showName;


    private Integer pointX;


    private Integer pointY;


    private Integer pointRotate;


    private String ip;


    private String serverPort;


    private String lastUpdUser;


    private Timestamp lastUpdTms;

    public Long getGlobalId() {
        return globalId;
    }

    public void setGlobalId(Long globalId) {
        this.globalId = globalId;
    }

    public Long getParaVerNo() {
        return paraVerNo;
    }

    public void setParaVerNo(Long paraVerNo) {
        this.paraVerNo = paraVerNo;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }

    public String getDevCode() {
        return devCode;
    }

    public void setDevCode(String devCode) {
        this.devCode = devCode;
    }

    public String getDevType() {
        return devType;
    }

    public void setDevType(String devType) {
        this.devType = devType;
    }

    public DeviceTypeEnum getDevTypeEnum() {
        return DeviceTypeEnum.fromCode(devType);
    }

    public String getShowName() {
        return showName;
    }

    public void setShowName(String showName) {
        this.showName = showName;
    }

    public Integer getPointX() {
        return pointX;
    }

    public void setPointX(Integer pointX) {
        this.pointX = pointX;
    }

    public Integer getPointY() {
        return pointY;
    }

    public void setPointY(Integer pointY) {
        this.pointY = pointY;
    }

    public Integer getPointRotate() {
        return pointRotate;
    }

    public void setPointRotate(Integer pointRotate) {
        this.pointRotate = pointRotate;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getServerPort() {
        return serverPort;
    }

    public void setServerPort(String serverPort) {
        this.serverPort = serverPort;
    }

    public String getLastUpdUser() {
        return lastUpdUser;
    }

    public void setLastUpdUser(String lastUpdUser) {
        this.lastUpdUser = lastUpdUser;
    }

    public Timestamp getLastUpdTms() {
        return lastUpdTms;
    }

    public void setLastUpdTms(Timestamp lastUpdTms) {
        this.lastUpdTms = lastUpdTms;
    }
}
