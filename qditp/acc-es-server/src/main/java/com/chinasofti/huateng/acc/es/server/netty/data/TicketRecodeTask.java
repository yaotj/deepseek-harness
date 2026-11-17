package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TicketRecodeTask implements WorkTask{

    private String actionCode;


    private String taskNo;


    private String changeSign;


    private String ticketType;


    private String ticketMainType;


    private String version;


    private String batchNo;



    private String beginNo;


    private String endNo;


    private String isTest;


    private String isNamed;


    private String isMemorial;


    private String walletUnit;


    private String initAmt;

    private String rewordAmt;

    private String invalidDays;


    private String beginDate;


    private String depAmt;



    private String ticketStatus;

}
