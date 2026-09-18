package com.chinasofti.huateng.accsimulator;

import com.chinasofti.huateng.model.employee.EmployeeCardInfoDTO;

import java.util.ArrayList;
import java.util.List;

public class AccNotifySimulationRequest {

    private String targetUrl;
    private List<EmployeeCardInfoDTO> cardList = new ArrayList<>();
    private String providerId;
    private String charset;
    private String format;
    private String deviceId;
    private String signType;
    private String sign;

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public List<EmployeeCardInfoDTO> getCardList() {
        return cardList;
    }

    public void setCardList(List<EmployeeCardInfoDTO> cardList) {
        this.cardList = cardList;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSignType() {
        return signType;
    }

    public void setSignType(String signType) {
        this.signType = signType;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }
}
