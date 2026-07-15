package com.chinasofti.huateng.model.frs;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RulLineInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private Integer paraVerNo;

    private String lineCode;

    private String lineNm;

    private String lineEName;

    private String lineType;

    private BigDecimal lineAreaNo;

    private Integer comfortGrade;

    private Integer runInterval;

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

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public String getLineNm() {
        return lineNm;
    }

    public void setLineNm(String lineNm) {
        this.lineNm = lineNm;
    }

    public String getLineEName() {
        return lineEName;
    }

    public void setLineEName(String lineEName) {
        this.lineEName = lineEName;
    }

    public String getLineType() {
        return lineType;
    }

    public void setLineType(String lineType) {
        this.lineType = lineType;
    }

    public BigDecimal getLineAreaNo() {
        return lineAreaNo;
    }

    public void setLineAreaNo(BigDecimal lineAreaNo) {
        this.lineAreaNo = lineAreaNo;
    }

    public Integer getComfortGrade() {
        return comfortGrade;
    }

    public void setComfortGrade(Integer comfortGrade) {
        this.comfortGrade = comfortGrade;
    }

    public Integer getRunInterval() {
        return runInterval;
    }

    public void setRunInterval(Integer runInterval) {
        this.runInterval = runInterval;
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
