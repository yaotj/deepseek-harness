package com.chinasofti.huateng.gatetxnpay.controller.app;

import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayQueryService;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** APP 场景入口（IF8A-*）。 */
@RestController
@RequestMapping("/ci/gateTxnPay/app")
public class GateTxnPayAppController {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayAppController.class);

    @Autowired
    private GateTxnPayQueryService gateTxnPayQueryService;

    @PostMapping("/requestTransList")
    public List<GateTxnPayListDTO> requestTransList(@RequestBody QueryTransListReqDTO request) {
        log.info("APP查询交易记录列表, 入参={}", request);
        List<GateTxnPayListDTO> list = gateTxnPayQueryService.selectTransList(
                request.getThirdUserId(),
                request.getCardIdList(),
                request.getCardType(),
                request.getCardTypeList(),
                request.getStartDate(),
                request.getEndDate(),
                request.getTicketCode(),
                request.getDebitRequestResult(),
                request.getIssueChannelCode(),
                request.getOffset(),
                request.getLimit());
        log.info("APP查询交易记录列表完成, 返回{}条", list == null ? 0 : list.size());
        return list;
    }

    @PostMapping("/countTransList")
    public int countTransList(@RequestBody QueryTransListReqDTO request) {
        log.info("APP统计交易记录总数, 入参={}", request);
        int count = gateTxnPayQueryService.countTransList(
                request.getThirdUserId(),
                request.getCardIdList(),
                request.getCardType(),
                request.getCardTypeList(),
                request.getStartDate(),
                request.getEndDate(),
                request.getTicketCode(),
                request.getDebitRequestResult(),
                request.getIssueChannelCode());
        log.info("APP统计交易记录总数完成, 返回={}", count);
        return count;
    }

    /** IF8A-41 账单统计：返回原价 / 实付 / 优惠 / 超时费四个合计与订单数。 */
    @PostMapping("/requestTransStatistics")
    public RequestTransStatisticsResult requestTransStatistics(@RequestBody RequestTransStatisticsReqDTO request) {
        log.info("IF8A-41 APP查询账单统计, 入参={}", request);
        RequestTransStatisticsResult response = gateTxnPayQueryService.requestTransStatistics(request);
        log.info("IF8A-41 APP查询账单统计完成, 返回={}", response == null ? null : response.getTripData());
        return response;
    }

    @PostMapping("/queryByOrderNo")
    public GateTxnPayListDTO queryByOrderNo(@RequestBody Map<String, String> request) {
        String orderNo = request != null ? request.get("orderNo") : null;
        log.info("APP查询订单详情, orderNo={}", orderNo);
        GateTxnPayListDTO dto = gateTxnPayQueryService.selectByOrderNo(orderNo);
        log.info("APP查询订单详情完成, 返回={}", dto != null ? dto.getOrderNo() : null);
        return dto;
    }

    /** IF8A-35 查询用户账务信息：返回未支付订单数与扣费失败订单数，供 APP 做欠费提醒。 */
    @PostMapping("/requestUserAccInfo")
    public RequestUserAccInfoResult requestUserAccInfo(@RequestBody RequestUserAccInfoReqDTO request) {
        log.info("IF8A-35 查询用户账务信息, 入参={}", request);
        RequestUserAccInfoResult response = gateTxnPayQueryService.requestUserAccInfo(request);
        log.info("IF8A-35 查询用户账务信息返回={}", response);
        return response;
    }
}
