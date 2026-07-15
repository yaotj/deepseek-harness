package com.chinasofti.huateng.para.entity.network;

/**
 * 线路信息参数表 TBL_LINE_INFO。
 */
public class LineInfo {
    private Long paraVerNo;
    private String lineCode;
    private String lineNm;
    private String lineENm;

    public Long getParaVerNo() {
        return paraVerNo;
    }

    public void setParaVerNo(Long paraVerNo) {
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

    @Override
    public String toString() {
        return "LineInfo{" +
                "paraVerNo=" + paraVerNo +
                ", lineCode='" + lineCode + '\'' +
                ", lineNm='" + lineNm + '\'' +
                ", lineENm='" + lineENm + '\'' +
                '}';
    }
}
