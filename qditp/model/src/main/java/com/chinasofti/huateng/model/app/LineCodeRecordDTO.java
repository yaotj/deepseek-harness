package com.chinasofti.huateng.model.app;


public class LineCodeRecordDTO {
    private String lineCode;
    private String lineNameZH;
    private String lineNameEN;
    private String orderIndex;

    public String getLineCode() { return lineCode; }
    public void setLineCode(String lineCode) { this.lineCode = lineCode; }
    public String getLineNameZH() { return lineNameZH; }
    public void setLineNameZH(String lineNameZH) { this.lineNameZH = lineNameZH; }
    public String getLineNameEN() { return lineNameEN; }
    public void setLineNameEN(String lineNameEN) { this.lineNameEN = lineNameEN; }
    public String getOrderIndex() { return orderIndex; }
    public void setOrderIndex(String orderIndex) {
        this.orderIndex = orderIndex;
    }

    @Override
    public String toString() {
        return "LineCodeRecordDTO{lineCode='" + lineCode + "', lineNameZH='" + lineNameZH + "', lineNameEN='" + lineNameEN + "', orderIndex='" + orderIndex + "'}";
    }
}
