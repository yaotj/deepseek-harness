package com.chinasofti.huateng.ticket.controller.ci.channel;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.ticket.service.TicketTransService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ci/channel")
public class AlipayTripController {
    private static final Logger log = LoggerFactory.getLogger(AlipayTripController.class);

    @Autowired
    private TicketTransService ticketTransService;

    @PostMapping("/findTravelList")
    public AlipayTripFindTravelListRespDTO findTravelList(@RequestBody AlipayTripFindTravelListReqDTO request) {
        log.info("支付宝出行-查询乘车记录列表,请求参数: {}", request);
        return ticketTransService.alipayTripFindTravelList(request);
    }

    @PostMapping("/findTravelDetail")
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(@RequestBody AlipayTripFindTravelDetailReqDTO request) {
        log.info("支付宝出行-查询乘车记录详情,请求参数: {}", request);
        return ticketTransService.alipayTripFindTravelDetail(request);
    }
}
