package com.chinasofti.huateng.dailyticket.service.paylog;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketPayLogMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketPayLog;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.springframework.stereotype.Component;

import java.util.Date;


@Component
public class DailyTicketPayLogWriter {

    private final DailyTicketPayLogMapper payLogMapper;

    public DailyTicketPayLogWriter(DailyTicketPayLogMapper payLogMapper) {
        this.payLogMapper = payLogMapper;
    }


    public void insert(String orderNo, String bizType, String payChannelCode, Object request, Object response) {
        DailyTicketPayLog payLog = new DailyTicketPayLog();
        payLog.setId(DailyTicketOrderSupport.nextId());
        payLog.setOrderNo(orderNo);
        payLog.setBizType(bizType);
        payLog.setPayChannelCode(payChannelCode);
        payLog.setRequestBody(JSON.toJSONString(request));
        payLog.setResponseBody(JSON.toJSONString(response));
        if (response instanceof DailyTicketBaseResult) {
            payLog.setResultCode(((DailyTicketBaseResult) response).getRetCode());
            payLog.setResultMsg(((DailyTicketBaseResult) response).getRetMsg());
        } else if (response instanceof DailyTicketPayGatewayResponse) {
            DailyTicketPayGatewayResponse gatewayResponse = (DailyTicketPayGatewayResponse) response;
            payLog.setResultCode(gatewayResponse.getCode() == null ? null : String.valueOf(gatewayResponse.getCode()));
            payLog.setResultMsg(gatewayResponse.getMsg());
        }
        payLog.setCreateTime(new Date());
        payLogMapper.insert(payLog);
    }
}
