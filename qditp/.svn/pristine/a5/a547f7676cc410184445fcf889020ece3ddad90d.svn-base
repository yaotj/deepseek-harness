package com.chinasofti.huateng.acc.es.server.netty.model;


public class MessageBean extends Messagehead {
    /**
     * 报文
     */
    private byte[] dataBody;


    public MessageBean() {
    }


    public MessageBean(String dataLength, String txnType,String nodeId,
                       String sequence, byte isFileTransaction, byte requestType, String mack, byte md5, byte[] dataBody) {
        setDataLength(dataLength);
        setTxnType(txnType);
        setNodeId(nodeId);
        setSequence(sequence);
        setIsFileTransaction(isFileTransaction);
        setRequestType(requestType);
        setMack(mack);
        setMd5(md5);
        setDataBody(dataBody);
    }

    public String toString() {
        return "报文长度[" + getDataLength() +
                "]消息类型[" + getTxnType() +
                "]标识码[" + getNodeId() +
                "]会话流水号[" + getSequence() +
                "]文件交易[" + Integer.toHexString(getIsFileTransaction()) +
                "]请求应答标识[" + Integer.toHexString(getRequestType()) +
                "]mack应答码[" + getMack() +
                "]消息体[" + new String(getDataBody()) +
                "]md5[" + Integer.toHexString(getMd5()) + "]";
    }


    public byte[] getDataBody() {
        return dataBody;
    }

    public void setDataBody(byte[] dataBody) {
        this.dataBody = dataBody;
    }

}
