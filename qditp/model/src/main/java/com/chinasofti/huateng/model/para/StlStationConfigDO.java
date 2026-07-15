package com.chinasofti.huateng.model.para;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * @description:车站配置参数表
 **/
public class StlStationConfigDO implements Serializable {

    /**
     * desc:
     **/
    private Long globalId;

    /**
     * desc:参数版本号（不唯一）
     **/
    private Long paraVerNo;

    /**
     * desc:车站编号
     **/
    private String stationCode;

    /**
     * desc:设备编号
     **/
    private String devCode;

    /**
     * desc:设备类型
     **/
    private String devType;

    /**
     * desc:设备名称
     **/
    private String showName;

    /**
     * desc:x坐标
     **/
    private Integer pointX;

    /**
     * desc:y坐标
     **/
    private Integer pointY;

    /**
     * desc:旋转角度
     **/
    private Integer pointRotate;

    /**
     * desc:设备ip
     **/
    private String ip;

    /**
     * desc:服务端口
     **/
    private String serverPort;

    /**
     * desc:最后更新人
     **/
    private String lastUpdUser;

    /**
     * desc:最后更新时间
     **/
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