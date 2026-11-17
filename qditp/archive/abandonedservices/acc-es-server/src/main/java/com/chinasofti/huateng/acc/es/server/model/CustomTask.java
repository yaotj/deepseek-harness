package com.chinasofti.huateng.acc.es.server.model;

import com.github.crab2died.annotation.ExcelField;
import lombok.Data;


@Data
public class CustomTask {


    @ExcelField(title = "物理卡号",order = 1)
    private String phyCode;


    @ExcelField(title = "持卡人类型",order = 2)
    private String personType;


    @ExcelField(title = "姓名",order = 3)
    private String userName;


    @ExcelField(title = "证件类型",order = 4)
    private String idType;


    @ExcelField(title = "证件代码",order = 5)
    private String id;

    @ExcelField(title = "员工号",order = 6)
    private String userNo;


    @ExcelField(title = "工作单位",order = 7)
    private String workUnit;










}
