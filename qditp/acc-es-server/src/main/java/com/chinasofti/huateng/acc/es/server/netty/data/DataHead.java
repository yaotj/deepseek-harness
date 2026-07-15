package com.chinasofti.huateng.acc.es.server.netty.data;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @program: spring-cloud-acc
 * @description: 报文头
 * @author: fc
 * @create: 2020-09-09 10:40
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DataHead implements Serializable {

    /**
     * 消息类型码
     */
    private String txnType;

    /**
     * 发起/接收 方标识码
     */
    private String nodeId;


    /**
     * 会话流水号
     */
    private String sequence;
    /**
     * 文件交易  0：无文件；1：有文件
     */
    private byte isFileTransaction;

    /**
     * 请求应答标识
     */
    private byte requestType;

    /**
     * 消息报文版本
     */
    private byte dataVersion;

    /**
     * 应答码
     */
    private String mack;

}
