package com.chinasofti.huateng.cardpool.entity;

import java.time.LocalDateTime;

public class LogicCardPoolCard {

    private Long id;

    private String cardNo;

    private Long batchNo;

    private String cardType;

    private String status;

    private String reservationId;

    private String businessType;

    private String businessId;

    private String ownerId;

    private LocalDateTime reservedTime;

    private LocalDateTime expireTime;

    private LocalDateTime confirmTime;


    public Long getId() {
        return id;
    }


    public void setId(Long id) {
        this.id = id;
    }


    public String getCardNo() {
        return cardNo;
    }


    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }


    public Long getBatchNo() {
        return batchNo;
    }


    public void setBatchNo(Long batchNo) {
        this.batchNo = batchNo;
    }


    public String getCardType() {
        return cardType;
    }


    public void setCardType(String cardType) {
        this.cardType = cardType;
    }


    public String getStatus() {
        return status;
    }


    public void setStatus(String status) {
        this.status = status;
    }


    public String getReservationId() {
        return reservationId;
    }


    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }


    public String getBusinessType() {
        return businessType;
    }


    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }


    public String getBusinessId() {
        return businessId;
    }


    public void setBusinessId(String businessId) {
        this.businessId = businessId;
    }


    public String getOwnerId() {
        return ownerId;
    }


    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }


    public LocalDateTime getReservedTime() {
        return reservedTime;
    }


    public void setReservedTime(LocalDateTime reservedTime) {
        this.reservedTime = reservedTime;
    }


    public LocalDateTime getExpireTime() {
        return expireTime;
    }


    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }


    public LocalDateTime getConfirmTime() {
        return confirmTime;
    }


    public void setConfirmTime(LocalDateTime confirmTime) {
        this.confirmTime = confirmTime;
    }



}
