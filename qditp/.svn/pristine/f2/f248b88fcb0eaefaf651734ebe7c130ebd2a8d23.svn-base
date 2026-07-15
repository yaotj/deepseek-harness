package com.chinasofti.huateng.acc.security.server.param;

import java.util.Arrays;

/**
 * @author houkepan
 * @date 2021/1/27 20:28
 */
public class TacBean {

    /**
     * 命令类型
     */
    private byte[] orderType = {(byte) 0xB0};

    /**
     * 命令
     */
    private byte[] order = {(byte) 0x84};

    /**
     * 用户保留字
     */
    private byte[] userRetain;

    /**
     * TAC类型
     */
    private byte[] tacType = {(byte) 0x00};

    /**
     * 次主密钥索引
     */
    private byte[] index;

    /**
     * 分散次数
     */
    private byte[] disperseNum;

    /**
     * 分散数据
     */
    private byte[] disperseData;

    /**
     * TAC初始数据
     */
    private byte[] initial = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    /**
     * TAC
     */
    private byte[] tac;

    /**
     * TAC数据长度
     */
    private byte[] bytesLength;

    /**
     * TAC数据
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

    public byte[] getTacType() {
        return tacType;
    }

    public void setTacType(byte[] tacType) {
        this.tacType = tacType;
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

    public byte[] getInitial() {
        return initial;
    }

    public void setInitial(byte[] initial) {
        this.initial = initial;
    }

    public byte[] getTac() {
        return tac;
    }

    public void setTac(byte[] tac) {
        this.tac = tac;
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

    @Override
    public String toString() {
        return "ShortConnectTacBean{" +
                "orderType=" + Arrays.toString(orderType) +
                ", order=" + Arrays.toString(order) +
                ", userRetain=" + Arrays.toString(userRetain) +
                ", tacType=" + Arrays.toString(tacType) +
                ", index=" + Arrays.toString(index) +
                ", disperseNum=" + Arrays.toString(disperseNum) +
                ", disperseData=" + Arrays.toString(disperseData) +
                ", initial=" + Arrays.toString(initial) +
                ", tac=" + Arrays.toString(tac) +
                ", bytesLength=" + Arrays.toString(bytesLength) +
                ", bytes=" + Arrays.toString(bytes) +
                '}';
    }
}
