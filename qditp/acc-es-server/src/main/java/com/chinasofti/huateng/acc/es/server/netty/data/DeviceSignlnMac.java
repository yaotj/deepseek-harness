package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.Data;
import lombok.EqualsAndHashCode;


@Data
@EqualsAndHashCode(callSuper = false)
public class DeviceSignlnMac extends DataHead{

    private String deviceStatus;

    private String operatorRank;

    public byte[] toByteByArray(){

        return null;
    }
}
