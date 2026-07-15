package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicketSortTask implements WorkTask{
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
     * 批次标识
     */
    private String batchNo;

    /**
     * 车票初始化日期
     */
    private String initDate;

    /**
     * 版本号
     */
    private String version;

    /**
     * 使用次数
     */
    private String useTimes;
    /**
     * 最小序列号
     */
    private String beginNo;

    /**
     * 最大序列号
     */
    private String endNo;

    /**
     * 票箱1金额
     */
    private String box1Face;

    /**
     * 票箱2金额
     */
    private String box2Face;

    /**
     * 票箱3金额
     */
    private String box3Face;

}
