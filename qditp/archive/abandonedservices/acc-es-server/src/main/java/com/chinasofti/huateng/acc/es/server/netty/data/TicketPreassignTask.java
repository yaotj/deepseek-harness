package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicketPreassignTask implements WorkTask{
    /**
     * 动作码
     */
    private String actionCode;

    /**
     * 任务标识
     */
    private String taskNo;

    /**
     * 任务变更标识
     */
    private String changeSign;

    /**
     * 车票类型
     */
    private String ticketType;

    /**
     * 赋值日期
     */
    private String assignDate;

    /**
     * 赋值数量
     */
    private String assignNum;


    /**
     * 批次标识
     */
    private String batchNo;


    /**
     * 初始票值
     */
    private String initAmt;
    /**
     * 初始奖励值
     */
    private String rewordAmt;
    /**
     * 有效期
     */
    private String invalidDays;

    /**
     * 有效期开始时间
     */
    private String beginDate;

    /**
     * 压金
     */
    private String depAmt;
    /**
     * 票卡状态
     */
    private String ticketStatus;
}
