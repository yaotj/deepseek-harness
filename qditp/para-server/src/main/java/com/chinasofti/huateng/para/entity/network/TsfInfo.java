package com.chinasofti.huateng.para.entity.network;

/**
 * 换乘站信息表 TBL_TSF_INFO。
 */
public class TsfInfo {
    private Long paraVerNo;
    private String fromStatCode;
    private String fromLineCode;
    private String toLineCode;
    private String toStatCode;
    private String tsfStationType;
    private Integer tsfTime;
    private Integer tsfDistance;

    public Long getParaVerNo() {
        return paraVerNo;
    }

    public void setParaVerNo(Long paraVerNo) {
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

    @Override
    public String toString() {
        return "TsfInfo{" +
                "paraVerNo=" + paraVerNo +
                ", fromStatCode='" + fromStatCode + '\'' +
                ", fromLineCode='" + fromLineCode + '\'' +
                ", toLineCode='" + toLineCode + '\'' +
                ", toStatCode='" + toStatCode + '\'' +
                ", tsfStationType='" + tsfStationType + '\'' +
                ", tsfTime=" + tsfTime +
                ", tsfDistance=" + tsfDistance +
                '}';
    }
}
