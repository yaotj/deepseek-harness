package com.chinasofti.huateng.para.entity.network;

/**
 * 区段信息参数 TBL_SECT_INFO。
 */
public class SectInfo {
    private Long paraVerNo;
    private Integer sectNo;
    private String sectName;
    private String sectEName;
    private String statCode1;
    private String statCode2;

    public Long getParaVerNo() {
        return paraVerNo;
    }

    public void setParaVerNo(Long paraVerNo) {
        this.paraVerNo = paraVerNo;
    }

    public Integer getSectNo() {
        return sectNo;
    }

    public void setSectNo(Integer sectNo) {
        this.sectNo = sectNo;
    }

    public String getSectName() {
        return sectName;
    }

    public void setSectName(String sectName) {
        this.sectName = sectName;
    }

    public String getSectEName() {
        return sectEName;
    }

    public void setSectEName(String sectEName) {
        this.sectEName = sectEName;
    }

    public String getStatCode1() {
        return statCode1;
    }

    public void setStatCode1(String statCode1) {
        this.statCode1 = statCode1;
    }

    public String getStatCode2() {
        return statCode2;
    }

    public void setStatCode2(String statCode2) {
        this.statCode2 = statCode2;
    }

    @Override
    public String toString() {
        return "SectInfo{" +
                "paraVerNo=" + paraVerNo +
                ", sectNo=" + sectNo +
                ", sectName='" + sectName + '\'' +
                ", sectEName='" + sectEName + '\'' +
                ", statCode1='" + statCode1 + '\'' +
                ", statCode2='" + statCode2 + '\'' +
                '}';
    }
}
