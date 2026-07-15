package com.chinasofti.huateng.model.para;


import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作员信息参数表
 */
public class TblStlOperatorInfoDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long globalId;

    /**
     * 参数版本号
     */
    private Long paraVerNo;

    /**
     * 操作员编号
     */
    private String operatorId;

    /**
     * 操作员姓名
     */
    private String operatorName;

    /**
     * 所属组编号
     */
    private Long operatorGroup;

    /**
     * 可操作区域编号
     */
    private Long operatingArea;

    /**
     * 操作员卡号
     */
    private String operatorCardId;

    /**
     * 操作员密码
     */
    private String pwd;

    /**
     * 有效开始日期
     */
    private String validBeginTms;

    /**
     * 有效结束日期
     */
    private String validEndTms;

    private String lastUpdUser;

    private LocalDateTime lastUpdTms;

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

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public Long getOperatorGroup() {
        return operatorGroup;
    }

    public void setOperatorGroup(Long operatorGroup) {
        this.operatorGroup = operatorGroup;
    }

    public Long getOperatingArea() {
        return operatingArea;
    }

    public void setOperatingArea(Long operatingArea) {
        this.operatingArea = operatingArea;
    }

    public String getOperatorCardId() {
        return operatorCardId;
    }

    public void setOperatorCardId(String operatorCardId) {
        this.operatorCardId = operatorCardId;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public String getValidBeginTms() {
        return validBeginTms;
    }

    public void setValidBeginTms(String validBeginTms) {
        this.validBeginTms = validBeginTms;
    }

    public String getValidEndTms() {
        return validEndTms;
    }

    public void setValidEndTms(String validEndTms) {
        this.validEndTms = validEndTms;
    }

    public String getLastUpdUser() {
        return lastUpdUser;
    }

    public void setLastUpdUser(String lastUpdUser) {
        this.lastUpdUser = lastUpdUser;
    }

    public LocalDateTime getLastUpdTms() {
        return lastUpdTms;
    }

    public void setLastUpdTms(LocalDateTime lastUpdTms) {
        this.lastUpdTms = lastUpdTms;
    }

    @Override
    public String toString() {
        return "TblStlOperatorInfoDO{" +
                "globalId = " + globalId +
                ", paraVerNo = " + paraVerNo +
                ", operatorId = " + operatorId +
                ", operatorName = " + operatorName +
                ", operatorGroup = " + operatorGroup +
                ", operatingArea = " + operatingArea +
                ", operatorCardId = " + operatorCardId +
                ", pwd = " + pwd +
                ", validBeginTms = " + validBeginTms +
                ", validEndTms = " + validEndTms +
                ", lastUpdUser = " + lastUpdUser +
                ", lastUpdTms = " + lastUpdTms +
                "}";
    }
}
