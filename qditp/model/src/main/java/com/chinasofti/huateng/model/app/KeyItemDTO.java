package com.chinasofti.huateng.model.app;


public class KeyItemDTO {

    private String keyId;


    private String keyType;


    private String keyUserId;


    private String keyPrivate;


    private String keyPublic;


    private String keyPublicEffectiveDate;


    private String signData;


    private String caIdx;


    private String keyWrapValue;


    private String keyEffectiveDate;


    private String kvc;


    private String reserve;

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public String getKeyUserId() {
        return keyUserId;
    }

    public void setKeyUserId(String keyUserId) {
        this.keyUserId = keyUserId;
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

    public String getKeyPublicEffectiveDate() {
        return keyPublicEffectiveDate;
    }

    public void setKeyPublicEffectiveDate(String keyPublicEffectiveDate) {
        this.keyPublicEffectiveDate = keyPublicEffectiveDate;
    }

    public String getSignData() {
        return signData;
    }

    public void setSignData(String signData) {
        this.signData = signData;
    }

    public String getCaIdx() {
        return caIdx;
    }

    public void setCaIdx(String caIdx) {
        this.caIdx = caIdx;
    }

    public String getKeyWrapValue() {
        return keyWrapValue;
    }

    public void setKeyWrapValue(String keyWrapValue) {
        this.keyWrapValue = keyWrapValue;
    }

    public String getKeyEffectiveDate() {
        return keyEffectiveDate;
    }

    public void setKeyEffectiveDate(String keyEffectiveDate) {
        this.keyEffectiveDate = keyEffectiveDate;
    }

    public String getKvc() {
        return kvc;
    }

    public void setKvc(String kvc) {
        this.kvc = kvc;
    }

    public String getReserve() {
        return reserve;
    }

    public void setReserve(String reserve) {
        this.reserve = reserve;
    }

    @Override
    public String toString() {
        return "KeyItemDTO{keyId='" + keyId + "', keyType='" + keyType + "', keyUserId='" + keyUserId
                + "', keyPrivate='" + (keyPrivate != null ? "[length=" + keyPrivate.length() + "]" : null)
                + "', keyPublic='" + (keyPublic != null ? "[length=" + keyPublic.length() + "]" : null)
                + "', keyPublicEffectiveDate='" + keyPublicEffectiveDate
                + "', signData='" + (signData != null ? "[length=" + signData.length() + "]" : null)
                + "', caIdx='" + caIdx + "', keyWrapValue='" + keyWrapValue
                + "', keyEffectiveDate='" + keyEffectiveDate + "', kvc='" + kvc + "', reserve='" + reserve + "'}";
    }
}
