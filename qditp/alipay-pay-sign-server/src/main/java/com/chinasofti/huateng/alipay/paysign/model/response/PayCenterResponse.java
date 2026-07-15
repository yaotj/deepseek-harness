package com.chinasofti.huateng.alipay.paysign.model.response;

import java.util.Map;

/**
 * 支付中心网关响应封装。
 */
public class PayCenterResponse {
    /**
     * 响应码（0-成功，其他-失败）。
     */
    private Integer code;
    /**
     * 响应信息。
     */
    private String msg;
    /**
     * 响应数据（业务数据）。
     */
    private Map<String, Object> data;
    /**
     * 是否成功。
     */
    private Boolean success;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
}
