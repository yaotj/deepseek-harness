package com.chinasofti.huateng.model.front.request;

import java.time.LocalDateTime;
import java.time.temporal.ChronoField;
import java.util.concurrent.ThreadLocalRandom;

public class RequestHead {
    private String versionId = "";
    private String msgNo = "";
    private String srcDevNodeId = "";
    private String targetDevNodeId = "";
    private String turnDevNodeId = "";
    private String reqId = "";
    private String sendTime = "";
    private String cryptType = "";
    private String cryptIndex = "";

    public final static RequestHead createDefault() {
        LocalDateTime now = LocalDateTime.now();
        int year = now.get(ChronoField.YEAR);
        int month = now.get(ChronoField.MONTH_OF_YEAR);
        int day = now.get(ChronoField.DAY_OF_MONTH);
        int hour = now.get(ChronoField.HOUR_OF_DAY);
        int minute = now.get(ChronoField.MINUTE_OF_HOUR);
        int second = now.get(ChronoField.SECOND_OF_MINUTE);
        long dateTimePart = (long) year * 10000000000L
                + month * 100000000L
                + day * 1000000L
                + hour * 10000L
                + minute * 100L
                + second;
        long randomPart = ThreadLocalRandom.current().nextLong(1000000000000000000L);
        String reqId = String.format("%014d%018d", dateTimePart, randomPart);
        RequestHead requestHead = new RequestHead();
        requestHead.setSendTime(String.valueOf(dateTimePart));
        requestHead.setReqId(reqId);
        return requestHead;
    }

    public String getVersionId() {
        return versionId;
    }

    public void setVersionId(String versionId) {
        this.versionId = versionId;
    }

    public String getMsgNo() {
        return msgNo;
    }

    public void setMsgNo(String msgNo) {
        this.msgNo = msgNo;
    }

    public String getSrcDevNodeId() {
        return srcDevNodeId;
    }

    public void setSrcDevNodeId(String srcDevNodeId) {
        this.srcDevNodeId = srcDevNodeId;
    }

    public String getTargetDevNodeId() {
        return targetDevNodeId;
    }

    public void setTargetDevNodeId(String targetDevNodeId) {
        this.targetDevNodeId = targetDevNodeId;
    }

    public String getTurnDevNodeId() {
        return turnDevNodeId;
    }

    public void setTurnDevNodeId(String turnDevNodeId) {
        this.turnDevNodeId = turnDevNodeId;
    }

    public String getReqId() {
        return reqId;
    }

    public void setReqId(String reqId) {
        this.reqId = reqId;
    }

    public String getSendTime() {
        return sendTime;
    }

    public void setSendTime(String sendTime) {
        this.sendTime = sendTime;
    }

    public String getCryptType() {
        return cryptType;
    }

    public void setCryptType(String cryptType) {
        this.cryptType = cryptType;
    }

    public String getCryptIndex() {
        return cryptIndex;
    }

    public void setCryptIndex(String cryptIndex) {
        this.cryptIndex = cryptIndex;
    }

    @Override
    public String toString() {
        return "RequestHead{" +
                "versionId='" + versionId + '\'' +
                ", msgNo='" + msgNo + '\'' +
                ", srcDevNodeId='" + srcDevNodeId + '\'' +
                ", targetDevNodeId='" + targetDevNodeId + '\'' +
                ", turnDevNodeId='" + turnDevNodeId + '\'' +
                ", reqId='" + reqId + '\'' +
                ", sendTime='" + sendTime + '\'' +
                ", cryptType='" + cryptType + '\'' +
                ", cryptIndex='" + cryptIndex + '\'' +
                '}';
    }
}
