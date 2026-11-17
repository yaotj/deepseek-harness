package com.chinasofti.huateng.collectticket.service;

import com.chinasofti.huateng.collectticket.model.request.CancelTicketCollectOrderReqDTO;
import com.chinasofti.huateng.collectticket.model.request.CreateTicketCollectOrderReqDTO;
import com.chinasofti.huateng.collectticket.model.request.QueryTicketCollectOrderReqDTO;
import com.chinasofti.huateng.collectticket.model.request.RequestPaymentInfoReqDTO;
import com.chinasofti.huateng.collectticket.model.request.RequestTicketPriceByStationReqDTO;
import com.chinasofti.huateng.collectticket.model.request.TicketCollectNotifyReqDTO;
import com.chinasofti.huateng.collectticket.model.request.TicketCollectPayNotifyReqDTO;
import com.chinasofti.huateng.collectticket.model.response.CancelTicketCollectOrderRespDTO;
import com.chinasofti.huateng.collectticket.model.response.CreateTicketCollectOrderRespDTO;
import com.chinasofti.huateng.collectticket.model.response.QueryTicketCollectOrderRespDTO;
import com.chinasofti.huateng.collectticket.model.response.RequestBuySingleTicketMaxNumRespDTO;
import com.chinasofti.huateng.collectticket.model.response.RequestPaymentInfoRespDTO;
import com.chinasofti.huateng.collectticket.model.response.RequestTicketPriceByStationRespDTO;
import com.chinasofti.huateng.collectticket.model.response.TicketCollectNotifyRespDTO;
import com.chinasofti.huateng.collectticket.model.response.TicketCollectPayNotifyRespDTO;

public interface TicketCollectService {
    RequestBuySingleTicketMaxNumRespDTO requestBuySinlgeTicketMaxNum();

    RequestTicketPriceByStationRespDTO requestTicketPriceByStation(RequestTicketPriceByStationReqDTO request);

    RequestPaymentInfoRespDTO requestPaymentInfo(RequestPaymentInfoReqDTO request);

    CreateTicketCollectOrderRespDTO createTicketCollectOrder(CreateTicketCollectOrderReqDTO request);

    QueryTicketCollectOrderRespDTO queryTicketCollectOrder(QueryTicketCollectOrderReqDTO request);

    TicketCollectNotifyRespDTO ticketCollectNotify(TicketCollectNotifyReqDTO request);

    CancelTicketCollectOrderRespDTO cancelTicketCollectOrder(CancelTicketCollectOrderReqDTO request);

    TicketCollectPayNotifyRespDTO ticketCollectPayNotify(TicketCollectPayNotifyReqDTO request);
}
