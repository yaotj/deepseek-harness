package com.chinasofti.huateng.acc.security.feign.domain.base;


import com.chinasofti.huateng.acc.security.feign.domain.BaseResult;

import java.io.Serializable;

/**
 * 加密服务通用响应体
 *
 * @author 49935
 */
public class BaseCheckResult extends BaseResult implements Serializable {

    private static final long serialVersionUID = 7335924420887010081L;
    /**
     * 校验结果
     */
    private String result;
    /**
     * 中心处理流水
     */
    private String centerFlowWater;

    public BaseCheckResult() {
        super();
    }

    public BaseCheckResult(int errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    public BaseCheckResult(String result, String centerFlowWater) {
        super();
        this.result = result;
        this.centerFlowWater = centerFlowWater;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getCenterFlowWater() {
        return centerFlowWater;
    }

    public void setCenterFlowWater(String centerFlowWater) {
        this.centerFlowWater = centerFlowWater;
    }

}
