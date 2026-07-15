package com.chinasofti.huateng.acc.es.server.netty.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class Messagehead {


    /**
     * 包长度 4
     */
    private String dataLength;

    /**
     * 消息类型 4
     */
    private String txnType;

    /**
     * 发起/接收方标识码 8
     */
    private String nodeId;

    /**
     * 会话流水号 9
     */
    private String sequence;

    /**
     * 文件交易  0：无文件；1：有文件
     */
    private byte isFileTransaction;

    /**
     * 1：请求消息；1：应答消息
     */
    private byte requestType;

    /**
     * 1  md5  0：不加密；1：MD5加密
     */
    private byte md5;

    /**
     * 应答码默认两个空格
     */
    private String mack;


}
