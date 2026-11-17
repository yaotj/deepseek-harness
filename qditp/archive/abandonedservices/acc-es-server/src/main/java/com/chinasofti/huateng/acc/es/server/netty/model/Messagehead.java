package com.chinasofti.huateng.acc.es.server.netty.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Messagehead {



    private String dataLength;


    private String txnType;


    private String nodeId;


    private String sequence;


    private byte isFileTransaction;


    private byte requestType;


    private byte md5;


    private String mack;


}
