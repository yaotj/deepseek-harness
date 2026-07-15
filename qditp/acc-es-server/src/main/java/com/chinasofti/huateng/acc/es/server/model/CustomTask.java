package com.chinasofti.huateng.acc.es.server.model;

import com.github.crab2died.annotation.ExcelField;
import lombok.Data;

/**
 * @author rxwnc
 */
@Data
public class CustomTask {

    /**
     * 物理卡号 16 前补0
     */
    @ExcelField(title = "物理卡号",order = 1)
    private String phyCode;

    /**
     * 0:普通乘客
     * 1：地铁员工
     */
    @ExcelField(title = "持卡人类型",order = 2)
    private String personType;

    /**
     * 姓名
     */
    @ExcelField(title = "姓名",order = 3)
    private String userName;

    /**
     * 证据按类型
     */
    @ExcelField(title = "证件类型",order = 4)
    private String idType;

    /**
     * 32位 后补零
     */
    @ExcelField(title = "证件代码",order = 5)
    private String id;

    @ExcelField(title = "员工号",order = 6)
    private String userNo;

    /**
     * 工作单位
     */
    @ExcelField(title = "工作单位",order = 7)
    private String workUnit;










}
