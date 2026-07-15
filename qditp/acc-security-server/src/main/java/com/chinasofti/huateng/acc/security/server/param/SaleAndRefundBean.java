package com.chinasofti.huateng.acc.security.server.param;

/**
 * @author houkepan
 * @date 2021/3/10 11:05
 */
public class SaleAndRefundBean {

    /**
     * 命令类型
     */
    private byte[] orderType = {(byte) 0xB0};

    /**
     * 命令
     */
    private byte[] order;

    /**
     * 用户保留字
     */
    private byte[] userRetain;

    /**
     * 次主密钥索引
     */
    private byte[] index;

    /**
     * 分散次数
     */
    private byte[] disperseNum = {0x02};

    /**
     * 分散数据
     */
    private byte[] disperseData;

    /**
     * 数据长度
     */
    private byte[] bytesLength = {0x00, 0x08};

    /**
     * 数据
     */
    private byte[] bytes;

    public byte[] getOrderType() {
        return orderType;
    }

    public void setOrderType(byte[] orderType) {
        this.orderType = orderType;
    }

    public byte[] getOrder() {
        return order;
    }

    public void setOrder(byte[] order) {
        this.order = order;
    }

    public byte[] getUserRetain() {
        return userRetain;
    }

    public void setUserRetain(byte[] userRetain) {
        this.userRetain = userRetain;
    }

    public byte[] getIndex() {
        return index;
    }

    public void setIndex(byte[] index) {
        this.index = index;
    }

    public byte[] getDisperseNum() {
        return disperseNum;
    }

    public void setDisperseNum(byte[] disperseNum) {
        this.disperseNum = disperseNum;
    }

    public byte[] getDisperseData() {
        return disperseData;
    }

    public void setDisperseData(byte[] disperseData) {
        this.disperseData = disperseData;
    }

    public byte[] getBytesLength() {
        return bytesLength;
    }

    public void setBytesLength(byte[] bytesLength) {
        this.bytesLength = bytesLength;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }
}
