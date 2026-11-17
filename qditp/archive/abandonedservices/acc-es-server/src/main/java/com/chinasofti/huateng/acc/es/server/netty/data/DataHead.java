package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataHead implements Serializable {


    private String txnType;


    private String nodeId;



    private String sequence;

    private byte isFileTransaction;


    private byte requestType;


    private byte dataVersion;


    private String mack;

}
