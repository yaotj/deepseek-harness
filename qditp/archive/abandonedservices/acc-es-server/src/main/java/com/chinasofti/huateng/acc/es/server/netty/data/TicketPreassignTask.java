package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicketPreassignTask implements WorkTask{

    private String actionCode;


    private String taskNo;


    private String changeSign;


    private String ticketType;


    private String assignDate;


    private String assignNum;



    private String batchNo;



    private String initAmt;

    private String rewordAmt;

    private String invalidDays;


    private String beginDate;


    private String depAmt;

    private String ticketStatus;
}
