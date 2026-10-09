package com.chinasofti.huateng.dailyticket.service.paylog;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.dailyticket.client.DailyTicketPayGatewayResponse;
import com.chinasofti.huateng.dailyticket.mapper.DailyTicketPayLogMapper;
import com.chinasofti.huateng.dailyticket.model.DailyTicketPayLog;
import com.chinasofti.huateng.dailyticket.service.support.DailyTicketOrderSupport;
import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * {@code DAILY_TICKET_PAY_LOG} 的唯一写入方：支付 / 退款 / 两条回调的请求应答留证。
 *
 * <p>它被支付、退款、支付回调、退款回调四条链路共用，所以在退款聚合搬家之前先抽出来 ——
 * 否则新的退款服务要么反向依赖 {@code DailyTicketServiceImpl}，要么把这段逐字复制一份。
 *
 * <p><b>已识别但本轮刻意未改的风险</b>：本类不吞异常，与原 {@code insertPayLog} 逐字一致。
 * 而它的调用点全部在 `return` 之前，因此一旦 `RESPONSE_BODY` 超长或表空间满，
 * 留证据失败会把**业务上已经成功**的应答冲成全局异常处理器的 UUID `retCode`、引来上游重推。
 * 拆分这一轮只保证行为一致，**要不要加兜底属于行为变更，须单独决策**。
 */
@Component
public class DailyTicketPayLogWriter {

    private final DailyTicketPayLogMapper payLogMapper;

    public DailyTicketPayLogWriter(DailyTicketPayLogMapper payLogMapper) {
        this.payLogMapper = payLogMapper;
    }

    /**
     * 落一条请求应答证据。{@code response} 的两种已知形态会额外抽出应答码：
     * 对 APP / 设备的 {@link DailyTicketBaseResult} 取 {@code retCode} / {@code retMsg}，
     * 支付中心的 {@link DailyTicketPayGatewayResponse} 取 {@code code} / {@code msg}；
     * 其余形态只留报文、不填应答码。
     */
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
