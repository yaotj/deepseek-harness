package com.chinasofti.huateng.fep.app.service.impl;

import com.chinasofti.huateng.fep.app.service.IndustryDataService;
import com.chinasofti.huateng.model.app.RequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.app.RequestIndustryDataResult;
import com.chinasofti.huateng.model.app.RequestNoSignalDataReqDTO;
import com.chinasofti.huateng.model.app.RequestNoSignalDataResult;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.springframework.stereotype.Service;

/**
 * IF8A-03 请求行业数据 / IF8D-03 获取离线码数据的接入层转发。
 *
 * <p><b>编排已于 2026-09-17 整体迁到 ticket-server</b>（`ticket/industry/IndustryDataOrchestrator`，
 * ADR-D142）：本类此前是 425 行、注 4 个下游 Client、31 个 `if`，还按卡种 / 渠道 / 站内外分流，
 * 在同模块另外 6 个 Service 都是单行转发（行数中位数 89）的背景下属分层越位。
 * 迁移的直接收益是「查乘车码状态」那一跳从 RPC 变成 ticket-server 的进程内调用，
 * 且两条链路那 4 组逐字副本随之消掉。
 *
 * <p><b>NEVER 把业务判断加回本类</b>：这里只允许做报文转发。HCE 短路、签约渠道解析、
 * 日票族前置校验、进出站双码都在 ticket-server 那个编排类里。
 */
@Service
public class IndustryDataServiceImpl implements IndustryDataService {

    private final TicketClient ticketClient;

    public IndustryDataServiceImpl(TicketClient ticketClient) {
        this.ticketClient = ticketClient;
    }

    @Override
    public RequestIndustryDataResult requestIndustryData(RequestIndustryDataReqDTO request) {
        return ticketClient.requestIndustryData(request);
    }

    @Override
    public RequestNoSignalDataResult requestNoSignalData(RequestNoSignalDataReqDTO request) {
        return ticketClient.requestNoSignalData(request);
    }
}
