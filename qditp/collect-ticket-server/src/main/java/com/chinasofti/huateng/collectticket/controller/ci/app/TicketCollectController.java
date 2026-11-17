package com.chinasofti.huateng.collectticket.controller.ci.app;

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
import com.chinasofti.huateng.collectticket.service.TicketCollectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/ci/app")
public class TicketCollectController {
    private static final Logger log = LoggerFactory.getLogger(TicketCollectController.class);

    @Autowired
    private TicketCollectService ticketCollectService;


    @PostMapping("/requestBuySinlgeTicketMaxNum")
    public RequestBuySingleTicketMaxNumRespDTO requestBuySinlgeTicketMaxNum() {
        log.info("接收到获取购买最多张数接口报文");
        return ticketCollectService.requestBuySinlgeTicketMaxNum();
    }


    @PostMapping("/requestTicketPriceByStation")
    public RequestTicketPriceByStationRespDTO requestTicketPriceByStation(
            @RequestBody RequestTicketPriceByStationReqDTO request) {
        log.info("接收到计算票价接口报文: {}", request);
        return ticketCollectService.requestTicketPriceByStation(request);
    }


    @PostMapping("/requestPaymentInfo")
    public RequestPaymentInfoRespDTO requestPaymentInfo(@RequestBody RequestPaymentInfoReqDTO request) {
        log.info("接收到请求支付接口报文: {}", request);
        return ticketCollectService.requestPaymentInfo(request);
    }


    @PostMapping("/requestOrder")
    public CreateTicketCollectOrderRespDTO requestOrder(@RequestBody CreateTicketCollectOrderReqDTO request) {
        log.info("接收到请求下单接口报文: {}", request);
        return ticketCollectService.createTicketCollectOrder(request);
    }


    @PostMapping("/queryTicketCollectOrder")
    public QueryTicketCollectOrderRespDTO queryTicketCollectOrder(@RequestBody QueryTicketCollectOrderReqDTO request) {
        log.info("接收到查询取票订单状态接口报文: {}", request);
        return ticketCollectService.queryTicketCollectOrder(request);
    }


    @PostMapping("/ticketCollectNotify")
    public TicketCollectNotifyRespDTO ticketCollectNotify(@RequestBody TicketCollectNotifyReqDTO request) {
        log.info("接收取票订单通知报文: {}", request);
        return ticketCollectService.ticketCollectNotify(request);
    }


    @PostMapping("/cancelTicketCollectOrder")
    public CancelTicketCollectOrderRespDTO cancelTicketCollectOrder(@RequestBody CancelTicketCollectOrderReqDTO request) {
        log.info("接收到取消取票订单接口报文: {}", request);
        return ticketCollectService.cancelTicketCollectOrder(request);
    }


    @PostMapping("/ticketCollectPayNotify")
    public TicketCollectPayNotifyRespDTO ticketCollectPayNotify(@RequestBody TicketCollectPayNotifyReqDTO request) {
        log.info("接收取票订单支付结果通知报文: {}", request);
        return ticketCollectService.ticketCollectPayNotify(request);
    }
}
