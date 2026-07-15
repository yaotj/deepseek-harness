package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class RecodeTaskReport extends EsTaskReport{

    private String EsNodeId;

    private String operator;

    private String ticketType;

    private String version;

    private String batchNo;

    private String beginNo;

    private String taskNum;

    private String completeNum;

    private String wastedNum;

    private String beginTime;

    private String endTime;

    private String fileName;

    private String remain;


















}
