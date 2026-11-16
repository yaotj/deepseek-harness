package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class TicketHandCancelTask implements WorkTask{
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
     * 任务数量
     */
    private String taskNum;

    /**
     * 车票类型
     */
    private String ticketType;

    /**
     * 批次标识
     */
    private String batchNo;
}
