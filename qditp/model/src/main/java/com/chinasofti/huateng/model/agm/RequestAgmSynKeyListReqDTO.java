package com.chinasofti.huateng.model.agm;

import java.util.List;

/**
 * Internal request for AGM key synchronization.
 */
public class RequestAgmSynKeyListReqDTO {
    private String deviceId;
    private String requestBizData;
    private List<AgmKeyCurVerDTO> keyCurVerList;

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getRequestBizData() { return requestBizData; }
    public void setRequestBizData(String requestBizData) { this.requestBizData = requestBizData; }
    public List<AgmKeyCurVerDTO> getKeyCurVerList() { return keyCurVerList; }
    public void setKeyCurVerList(List<AgmKeyCurVerDTO> keyCurVerList) { this.keyCurVerList = keyCurVerList; }
}
