package com.chinasofti.huateng.model.app;

/**
 * IF8A-07 获取线路代码响应中的单条线路记录。
 */
public class LineCodeRecordDTO {
    /** 线路代码。 */
    private String lineCode;
    /** 线路中文名称。 */
    private String lineNameZH;
    /** 线路英文名称。 */
    private String lineNameEN;
    /** 展示排序序号。 */
    private String orderIndex;

    public String getLineCode() { return lineCode; }
    public void setLineCode(String lineCode) { this.lineCode = lineCode; }
    public String getLineNameZH() { return lineNameZH; }
    public void setLineNameZH(String lineNameZH) { this.lineNameZH = lineNameZH; }
    public String getLineNameEN() { return lineNameEN; }
    public void setLineNameEN(String lineNameEN) { this.lineNameEN = lineNameEN; }
    public String getOrderIndex() { return orderIndex; }
    public void setOrderIndex(String orderIndex) { this.orderIndex = orderIndex; }
}
