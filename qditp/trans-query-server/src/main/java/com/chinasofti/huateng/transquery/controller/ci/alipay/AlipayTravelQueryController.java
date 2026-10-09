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

/**
 * 支付宝出行行程查询入口（由 fep-alipay-server 经 rpc 调用）。
 *
 * <p>对外的支付宝渠道 URL 仍是 fep-alipay-server 的 {@code /channel/findTravelList} 与
 * {@code /channel/findTravelDetail}，本服务只承载内部查询端点，因此另起 {@code /ci/alipay/travel} 前缀。
 */
@RestController
@RequestMapping("/ci/alipay/travel")
public class AlipayTravelQueryController {

    private static final Logger log = LoggerFactory.getLogger(AlipayTravelQueryController.class);

    @Autowired
    private TransQueryService transQueryService;

    /** 支付宝出行-查询乘车记录列表。 */
    @PostMapping("/list")
    public AlipayTripFindTravelListRespDTO findTravelList(@RequestBody AlipayTripFindTravelListReqDTO request) {
        log.info("支付宝出行-查询乘车记录列表,请求参数: {}", request);
        return transQueryService.findTravelList(request);
    }

    /** 支付宝出行-查询乘车记录详情。 */
    @PostMapping("/detail")
    public AlipayTripFindTravelDetailRespDTO findTravelDetail(@RequestBody AlipayTripFindTravelDetailReqDTO request) {
        log.info("支付宝出行-查询乘车记录详情,请求参数: {}", request);
        return transQueryService.findTravelDetail(request);
    }
}
