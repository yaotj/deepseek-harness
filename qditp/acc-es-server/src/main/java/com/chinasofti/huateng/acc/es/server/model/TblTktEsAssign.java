package com.chinasofti.huateng.acc.es.server.model;


import java.time.LocalDateTime;
import java.io.Serializable;


/**
 * <p>
 * ES任务分配
 * </p>
 *
 * @author fc
 * @since 2020-09-02
 */
public class TblTktEsAssign implements Serializable {

    private static final long serialVersionUID=1L;

    private Integer taskNo;

    private String esCode;

    private Integer taskNum;

    private Integer beginNo;

    private Integer endNo;

    private String taskStat;

    private String fileNm;

    private String lastUpdId;

    private boolean custom;

    private LocalDateTime lastUpdTms;

    public Integer getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(Integer taskNo) {
        this.taskNo = taskNo;
    }

    public String getEsCode() {
        return esCode;
    }

    public void setEsCode(String esCode) {
        this.esCode = esCode;
    }

    public Integer getTaskNum() {
        return taskNum;
    }

    public void setTaskNum(Integer taskNum) {
        this.taskNum = taskNum;
    }

    public Integer getBeginNo() {
        return beginNo;
    }

    public void setBeginNo(Integer beginNo) {
        this.beginNo = beginNo;
    }

    public Integer getEndNo() {
        return endNo;
    }

    public void setEndNo(Integer endNo) {
        this.endNo = endNo;
    }

    public String getTaskStat() {
        return taskStat;
    }

    public void setTaskStat(String taskStat) {
        this.taskStat = taskStat;
    }

    public String getFileNm() {
        return fileNm;
    }

    public void setFileNm(String fileNm) {
        this.fileNm = fileNm;
    }

    public String getLastUpdId() {
        return lastUpdId;
    }

    public void setLastUpdId(String lastUpdId) {
        this.lastUpdId = lastUpdId;
    }

    public boolean getCustom() {
        return custom;
    }

    public void setCustom(boolean custom) {
        this.custom = custom;
    }

    public LocalDateTime getLastUpdTms() {
        return lastUpdTms;
    }

    public void setLastUpdTms(LocalDateTime lastUpdTms) {
        this.lastUpdTms = lastUpdTms;
    }
}
