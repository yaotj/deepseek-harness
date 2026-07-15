package com.chinasofti.huateng.model.para;

import java.io.Serializable;

/*
线路参数
 */

public class TblStlLineInfo implements Serializable {
    private Integer paraVerNo;

    private String lineCode;

    private String lineNm;

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

    public String getLineENm() {
        return lineENm;
    }

    public void setLineENm(String lineENm) {
        this.lineENm = lineENm;
    }

    private String lineENm;

    private static final long serialVersionUID = 1L;
}