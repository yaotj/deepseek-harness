package com.chinasofti.huateng.model.app;

/**
 * IF8A-06 请求解约响应。
 *
 * <p>retCode/retMsg 返回给 APP；code/msg/success/data 兼容支付平台通用响应结构，
 * 便于 fep-app、rpc 和 pay-sign-server 之间直接透传。</p>
 */
public class RequestTerminationResult {
    /** ITP 侧返回码，0000 表示请求解约已成功发送到支付平台。 */
    private String retCode;
    /** ITP 侧返回信息。 */
    private String retMsg;
    /** 支付平台通用返回码，0 表示成功。 */
    private Integer code;
    /** 支付平台通用返回信息。 */
    private String msg;
    /** 支付平台通用成功标识。 */
    private Boolean success;
    /** 支付平台返回的业务数据。 */
    private Object data;

    public String getRetCode() {
        return retCode;
    }

    public void setRetCode(String retCode) {
        this.retCode = retCode;
    }

    public String getRetMsg() {
        return retMsg;
    }

    public void setRetMsg(String retMsg) {
        this.retMsg = retMsg;
    }

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

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
