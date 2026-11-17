package com.chinasofti.huateng.transquery.controller.ci.alipay;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.transquery.service.TransQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/ci/alipay/travel")
public class AlipayTravelQueryController {

    private static final Logger log = LoggerFactory.getLogger(AlipayTravelQueryController.class);

    @Autowired
    private TransQueryService transQueryService;

    @PostMapping("/list")
    public AlipayTripFindTravelListRespDTO findTravelList(@RequestBody AlipayTripFindTravelListReqDTO request) {
        log.info("支付宝出行-查询乘车记录列表,请求参数: {}", request);
        return transQueryService.findTravelList(request);
    }

    @PostMapping("/detail")
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(@RequestBody AlipayTripFindTravelDetailReqDTO request) {
        log.info("支付宝出行-查询乘车记录详情,请求参数: {}", request);
        return transQueryService.findTravelDetail(request);
    }
}
