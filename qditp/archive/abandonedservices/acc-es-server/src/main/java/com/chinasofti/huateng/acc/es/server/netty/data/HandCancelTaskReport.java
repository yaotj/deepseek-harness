package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 缴销任务报告
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class HandCancelTaskReport extends EsTaskReport{

    private String esNodeId;

    private String operator;

    private String ticketType;

    private String taskNum;

    private String completedNum;

    private String errorNum;

    private String beginTime;

    private String endTime;

    private String fileName;

    private String remain;







}
