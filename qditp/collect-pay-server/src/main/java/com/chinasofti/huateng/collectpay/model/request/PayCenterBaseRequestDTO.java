package com.chinasofti.huateng.collectpay.model.request;

import lombok.Data;

/** 请求公共参数基类（7.5.1.1 请求公共参数）。 */
@Data
public class PayCenterBaseRequestDTO {

    private String merchantNo;


    private String apiVersion;


    private String signType;


    private String sign;


    private String charset;



    /** 业务数据（JSON字符串）。 */
    private String bizData;


}
