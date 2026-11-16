package com.chinasofti.huateng.acc.es.server.model;


import java.time.LocalDateTime;
import java.io.Serializable;

/**
 * <p>
 * ES设备信息
 * </p>
 *
 * @author fc
 * @since 2020-09-02
 */

public class TblTktEsInfo  implements Serializable {

    private static final long serialVersionUID=1L;

    private String esCode;

    private String esName;

    private String instNo;

    private Integer makeNum;

    private Integer washNum;

    private Integer waleNum;

    private Integer cancelNum;

    private Integer workTimes;

    private String esStat;

    private String loginStat;

    private String loginUser;

    private String lastUpdId;

    private LocalDateTime lastUpdTms;


    public String getEsCode() {
        return esCode;
    }

    public void setEsCode(String esCode) {
        this.esCode = esCode;
    }

    public String getEsName() {
        return esName;
    }

    public void setEsName(String esName) {
        this.esName = esName;
    }

    public String getInstNo() {
        return instNo;
    }

    public void setInstNo(String instNo) {
        this.instNo = instNo;
    }

    public Integer getMakeNum() {
        return makeNum;
    }

    public void setMakeNum(Integer makeNum) {
        this.makeNum = makeNum;
    }

    public Integer getWashNum() {
        return washNum;
    }

    public void setWashNum(Integer washNum) {
        this.washNum = washNum;
    }

    public Integer getWaleNum() {
        return waleNum;
    }

    public void setWaleNum(Integer waleNum) {
        this.waleNum = waleNum;
    }

    public Integer getCancelNum() {
        return cancelNum;
    }

    public void setCancelNum(Integer cancelNum) {
        this.cancelNum = cancelNum;
    }

    public Integer getWorkTimes() {
        return workTimes;
    }

    public void setWorkTimes(Integer workTimes) {
        this.workTimes = workTimes;
    }

    public String getEsStat() {
        return esStat;
    }

    public void setEsStat(String esStat) {
        this.esStat = esStat;
    }

    public String getLoginStat() {
        return loginStat;
    }

    public void setLoginStat(String loginStat) {
        this.loginStat = loginStat;
    }

    public String getLoginUser() {
        return loginUser;
    }

    public void setLoginUser(String loginUser) {
        this.loginUser = loginUser;
    }

    public String getLastUpdId() {
        return lastUpdId;
    }

    public void setLastUpdId(String lastUpdId) {
        this.lastUpdId = lastUpdId;
    }

    public LocalDateTime getLastUpdTms() {
        return lastUpdTms;
    }

    public void setLastUpdTms(LocalDateTime lastUpdTms) {
        this.lastUpdTms = lastUpdTms;
    }

    @Override
    public String toString() {
        return "TblTktEsInfo{" +
        "esCode=" + esCode +
        ", esName=" + esName +
        ", instNo=" + instNo +
        ", makeNum=" + makeNum +
        ", washNum=" + washNum +
        ", waleNum=" + waleNum +
        ", cancelNum=" + cancelNum +
        ", workTimes=" + workTimes +
        ", esStat=" + esStat +
        ", loginStat=" + loginStat +
        ", loginUser=" + loginUser +
        ", lastUpdId=" + lastUpdId +
        ", lastUpdTms=" + lastUpdTms +
        "}";
    }
}
