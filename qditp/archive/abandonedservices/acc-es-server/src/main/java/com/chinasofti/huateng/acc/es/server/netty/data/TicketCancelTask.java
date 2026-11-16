package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicketCancelTask implements WorkTask{
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
     * 版本号
     */
    private String version;

    /**
     * 任务数量
     */
    private String taskNum;

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
}
