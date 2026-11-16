package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 车票个性化
 */
@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class CustomTaskReport extends EsTaskReport{

    private String nodeId;

    private String operator;

    private String ticketType;

    private String version;

    private String batchNo;

    private String beginNo;

    private String taskNum;

    private String completedNum;

    private String wastedNum;

    private String beginTime;

    private String endTime;

    private String fileName;

    private String remain;

    















}
