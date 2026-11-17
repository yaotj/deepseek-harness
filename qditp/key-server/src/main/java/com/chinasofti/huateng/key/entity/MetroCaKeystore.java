package com.chinasofti.huateng.key.entity;

import java.util.Date;


public class MetroCaKeystore {

    private Long id;


    private String keyIdx;


    private String keyPrivate;


    private String keyPublic;


    private String keyEffectiveDate;


    private Integer keyStatus;


    private String managerId;


    private String reserve;


    private String remark;


    private Date updateDate;


    private Date regDate;


    private String keyPair;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKeyIdx() {
        return keyIdx;
    }

    public void setKeyIdx(String keyIdx) {
        this.keyIdx = keyIdx;
    }

    public String getKeyPrivate() {
        return keyPrivate;
    }

    public void setKeyPrivate(String keyPrivate) {
        this.keyPrivate = keyPrivate;
    }

    public String getKeyPublic() {
        return keyPublic;
    }

    public void setKeyPublic(String keyPublic) {
        this.keyPublic = keyPublic;
    }

    public String getKeyEffectiveDate() {
        return keyEffectiveDate;
    }

    public void setKeyEffectiveDate(String keyEffectiveDate) {
        this.keyEffectiveDate = keyEffectiveDate;
    }

    public Integer getKeyStatus() {
        return keyStatus;
    }

    public void setKeyStatus(Integer keyStatus) {
        this.keyStatus = keyStatus;
    }

    public String getManagerId() {
        return managerId;
    }

    public void setManagerId(String managerId) {
        this.managerId = managerId;
    }

    public String getReserve() {
        return reserve;
    }

    public void setReserve(String reserve) {
        this.reserve = reserve;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Date getUpdateDate() {
        return updateDate;
    }

    public void setUpdateDate(Date updateDate) {
        this.updateDate = updateDate;
    }

    public Date getRegDate() {
        return regDate;
    }

    public void setRegDate(Date regDate) {
        this.regDate = regDate;
    }

    public String getKeyPair() {
        return keyPair;
    }

    public void setKeyPair(String keyPair) {
        this.keyPair = keyPair;
    }
}
