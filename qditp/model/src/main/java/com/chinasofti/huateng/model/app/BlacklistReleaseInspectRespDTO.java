package com.chinasofti.huateng.model.app;

import java.util.List;


public class BlacklistReleaseInspectRespDTO {

    private String resultCode;

    private String resultMsg;

    private int scanned;

    private int settled;

    private int unsettled;

    private int unknown;

    private List<BlacklistReleaseCandidateDTO> details;

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultMsg() {
        return resultMsg;
    }

    public void setResultMsg(String resultMsg) {
        this.resultMsg = resultMsg;
    }

    public int getScanned() {
        return scanned;
    }

    public void setScanned(int scanned) {
        this.scanned = scanned;
    }

    public int getSettled() {
        return settled;
    }

    public void setSettled(int settled) {
        this.settled = settled;
    }

    public int getUnsettled() {
        return unsettled;
    }

    public void setUnsettled(int unsettled) {
        this.unsettled = unsettled;
    }

    public int getUnknown() {
        return unknown;
    }

    public void setUnknown(int unknown) {
        this.unknown = unknown;
    }

    public List<BlacklistReleaseCandidateDTO> getDetails() {
        return details;
    }

    public void setDetails(List<BlacklistReleaseCandidateDTO> details) {
        this.details = details;
    }
}
