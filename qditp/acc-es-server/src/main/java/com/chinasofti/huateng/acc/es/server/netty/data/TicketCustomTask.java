package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicketCustomTask implements WorkTask{


    private String actionCode;


    private String taskNo;


    private String changeSign;


    private String ticketType;


    private String version;


    private String batchNo;


    private String taskNum;


    private String customFileName;

}
