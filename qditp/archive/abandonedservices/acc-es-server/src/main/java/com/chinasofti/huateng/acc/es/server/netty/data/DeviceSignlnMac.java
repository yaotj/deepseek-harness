package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @program: spring-cloud-acc
 * @description: 设备签到应答
 * @author: fc
 * @create: 2020-09-10 10:15
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class DeviceSignlnMac extends DataHead{
    /**
     * 设备状态报告间隔
     */
    private String deviceStatus;
    /**
     * 操作员级别
     */
    private String operatorRank;

    public byte[] toByteByArray(){

        return null;
    }
}
