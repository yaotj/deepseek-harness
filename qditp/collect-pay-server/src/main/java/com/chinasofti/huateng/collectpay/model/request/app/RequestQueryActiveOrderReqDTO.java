package com.chinasofti.huateng.collectpay.model.request.app;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import lombok.Data;

/** IF8A-20 请求下单请求参数DTO。 */
@Data
public class RequestQueryActiveOrderReqDTO extends BaseRequestDTO {

    /** 用户编码。 */
    private String userId;
    private String appType;


}