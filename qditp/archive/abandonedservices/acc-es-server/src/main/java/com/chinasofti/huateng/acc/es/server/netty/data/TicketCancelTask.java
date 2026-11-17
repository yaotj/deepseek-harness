package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicketCancelTask implements WorkTask{

    private String actionCode;


    private String taskNo;


    private String changeSign;


    private String ticketType;



    private String version;


    private String taskNum;


    private String useTimes;


    private String beginNo;


    private String endNo;
}
