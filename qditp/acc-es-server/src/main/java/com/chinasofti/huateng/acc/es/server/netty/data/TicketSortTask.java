package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicketSortTask implements WorkTask{

    private String actionCode;


    private String taskNo;


    private String changeSign;


    private String ticketType;


    private String batchNo;


    private String initDate;


    private String version;


    private String useTimes;

    private String beginNo;


    private String endNo;


    private String box1Face;


    private String box2Face;


    private String box3Face;

}
