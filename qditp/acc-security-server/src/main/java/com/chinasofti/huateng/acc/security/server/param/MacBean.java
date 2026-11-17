package com.chinasofti.huateng.acc.security.server.param;


public class MacBean {


    private byte[] orderType = {(byte) 0xB0};


    private byte[] order;


    private byte[] userRetain;


    private byte[] macType = {(byte) 0x00};


    private byte[] index;


    private byte[] disperseNum = {0x01};


    private byte[] disperseData;


    private byte[] temporaryAlgorithm = {0x00};


    private byte[] sessionKey;


    private byte[] initial = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};


    private byte[] mac;


    private byte[] bytesLength;


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

    public byte[] getMacType() {
        return macType;
    }

    public void setMacType(byte[] macType) {
        this.macType = macType;
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

    public byte[] getTemporaryAlgorithm() {
        return temporaryAlgorithm;
    }

    public void setTemporaryAlgorithm(byte[] temporaryAlgorithm) {
        this.temporaryAlgorithm = temporaryAlgorithm;
    }

    public byte[] getSessionKey() {
        return sessionKey;
    }

    public void setSessionKey(byte[] sessionKey) {
        this.sessionKey = sessionKey;
    }

    public byte[] getInitial() {
        return initial;
    }

    public void setInitial(byte[] initial) {
        this.initial = initial;
    }

    public byte[] getMac() {
        return mac;
    }

    public void setMac(byte[] mac) {
        this.mac = mac;
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
