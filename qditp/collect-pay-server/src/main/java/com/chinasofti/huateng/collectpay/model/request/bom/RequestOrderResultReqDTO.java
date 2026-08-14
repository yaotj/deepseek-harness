package com.chinasofti.huateng.collectpay.model.request.bom;

import com.chinasofti.huateng.collectpay.model.request.BaseRequestDTO;
import lombok.Data;

@Data
public class RequestOrderResultReqDTO extends BaseRequestDTO {


    private String ticketLogicNum;


    private String transDate;

}
