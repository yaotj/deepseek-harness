package com.chinasofti.huateng.model.app;

import com.chinasofti.huateng.common.response.CommonResult;

public class RequestNoSignalDataResult extends CommonResult {
    private String exitData;
    private String entryData;
    private String channel;

    public String getExitData() {
        return exitData;
    }

    public void setExitData(String exitData) {
        this.exitData = exitData;
    }

    public String getEntryData() {
        return entryData;
    }

    public void setEntryData(String entryData) {
        this.entryData = entryData;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }
}
