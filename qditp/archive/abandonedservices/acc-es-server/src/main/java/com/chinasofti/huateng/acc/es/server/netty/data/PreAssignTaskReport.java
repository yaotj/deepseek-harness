package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 预赋值报告
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class PreAssignTaskReport extends EsTaskReport{

    private String EsNodeId;

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
