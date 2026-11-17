package com.chinasofti.huateng.fep.app.service.impl;

import com.chinasofti.huateng.fep.app.service.IndustryDataService;
import com.chinasofti.huateng.model.app.RequestIndustryDataReqDTO;
import com.chinasofti.huateng.model.app.RequestIndustryDataResult;
import com.chinasofti.huateng.model.app.RequestNoSignalDataReqDTO;
import com.chinasofti.huateng.model.app.RequestNoSignalDataResult;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.springframework.stereotype.Service;


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
