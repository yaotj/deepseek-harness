package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
public class PublishTaskReport extends EsTaskReport {

    private String esNodeId;

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
