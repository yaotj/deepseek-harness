package com.chinasofti.huateng.model.fileProxy;

import java.io.Serializable;


public class UploadStatusDTO implements Serializable {


    private boolean success;


    private String errMsg;


    private String httpUrl;

    public UploadStatusDTO() {
    }

    public UploadStatusDTO(boolean success, String errMsg) {
        this.success = success;
        this.errMsg = errMsg;
    }

    public static UploadStatusDTO ok() {
        return new UploadStatusDTO(true, "");
    }

    public static UploadStatusDTO ok(String httpUrl) {
        UploadStatusDTO uploadStatusDTO = new UploadStatusDTO(true, "");
        uploadStatusDTO.setHttpUrl(httpUrl);
        return uploadStatusDTO;
    }

    public static UploadStatusDTO err(String msg) {
        return new UploadStatusDTO(false, msg);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public void setErrMsg(String errMsg) {
        this.errMsg = errMsg;
    }

    public String getHttpUrl() {
        return httpUrl;
    }

    public void setHttpUrl(String httpUrl) {
        this.httpUrl = httpUrl;
    }
}
