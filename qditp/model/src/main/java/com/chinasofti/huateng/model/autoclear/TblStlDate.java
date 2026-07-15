package com.chinasofti.huateng.model.autoclear;

import java.io.Serializable;

public class TblStlDate implements Serializable {
    private static final long serialVersionUID = 1L;

    private String prevStlDate;

    private String stlDate;

    private String cycleDate;

    private String batchDate;

    private String stlStatus;

    public String getPrevStlDate() {
        return prevStlDate;
    }

    public void setPrevStlDate(String prevStlDate) {
        this.prevStlDate = prevStlDate;
    }

    public String getStlDate() {
        return stlDate;
    }

    public void setStlDate(String stlDate) {
        this.stlDate = stlDate;
    }

    public String getCycleDate() {
        return cycleDate;
    }

    public void setCycleDate(String cycleDate) {
        this.cycleDate = cycleDate;
    }

    public String getBatchDate() {
        return batchDate;
    }

    public void setBatchDate(String batchDate) {
        this.batchDate = batchDate;
    }

    public String getStlStatus() {
        return stlStatus;
    }

    public void setStlStatus(String stlStatus) {
        this.stlStatus = stlStatus;
    }
}
