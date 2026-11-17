package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.Builder;
import lombok.Data;

@Data
@Builder(toBuilder = true)
public class TicketHandCancelTask implements WorkTask{

    private String actionCode;


    private String taskNo;


    private String changeSign;


    private String taskNum;


    private String ticketType;


    private String batchNo;
}
