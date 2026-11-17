package com.chinasofti.huateng.alipay.paysign.controller.legacy;

import com.chinasofti.huateng.alipay.paysign.model.response.AlipayPayLogVO;
import com.chinasofti.huateng.alipay.paysign.model.response.PageResult;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayLogQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequestMapping("/api/payment")
public class AlipayPayLogController {

    private static final Logger log = LoggerFactory.getLogger(AlipayPayLogController.class);
    private final AlipayPayLogQueryService alipayPayLogQueryService;

    public AlipayPayLogController(AlipayPayLogQueryService alipayPayLogQueryService) {
        this.alipayPayLogQueryService = alipayPayLogQueryService;
    }


    @GetMapping("/payLog/list")
    public PageResult<AlipayPayLogVO> list(@RequestParam(required = false) String thirdUserId,
                                           @RequestParam(required = false) String startTime,
                                           @RequestParam(required = false) String endTime,
                                           @RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return queryList("查询订单列表", thirdUserId, startTime, endTime,
                (pageNum - 1) * pageSize, pageSize, null, null);
    }


    @PostMapping("/payLog/list")
    public PageResult<AlipayPayLogVO> listPost(@RequestBody Map<String, Object> params) {
        int pageNum = intValue(params, "pageNum", 1);
        int pageSize = intValue(params, "pageSize", 10);
        return queryList("查询订单列表(POST)", stringValue(params, "thirdUserId"),
                stringValue(params, "startTime"), stringValue(params, "endTime"),
                (pageNum - 1) * pageSize, pageSize, null, null);
    }


    @GetMapping("/payLog/travelList")
    public PageResult<AlipayPayLogVO> travelList(@RequestParam String thirdUserId,
                                                 @RequestParam(required = false) String startDate,
                                                 @RequestParam(required = false) String endDate,
                                                 @RequestParam(required = false) String debitRequestResult,
                                                 @RequestParam(required = false) String invoice,
                                                 @RequestParam(defaultValue = "0") int pageNum,
                                                 @RequestParam(defaultValue = "10") int pageSize) {
        return queryList("查询乘车记录列表", thirdUserId, startDate, endDate,
                pageNum * pageSize, pageSize, debitRequestResult, invoice);
    }


    @PostMapping("/payLog/travelList")
    public PageResult<AlipayPayLogVO> travelListPost(@RequestBody Map<String, Object> params) {
        int pageNum = intValue(params, "pageNum", 0);
        int pageSize = intValue(params, "pageSize", 10);
        return queryList("查询乘车记录列表(POST)", stringValue(params, "thirdUserId"),
                stringValue(params, "startDate"), stringValue(params, "endDate"),
                pageNum * pageSize, pageSize,
                stringValue(params, "debitRequestResult"), stringValue(params, "invoice"));
    }


    @GetMapping("/payLog/detail")
    public AlipayPayLogVO detail(@RequestParam String orderNo) {
        log.info("查询订单详情: orderNo={}", orderNo);
        AlipayPayLogVO response = alipayPayLogQueryService.selectByOrderNo(orderNo);
        log.info("查询订单详情响应结果：{}", response != null ? response.getOrderNo() : "null");
        return response;
    }


    @GetMapping("/payLog/entryId")
    public AlipayPayLogVO entryId(@RequestParam String entryId) {
        log.info("按进站交易ID查询: entryId={}", entryId);
        AlipayPayLogVO response = alipayPayLogQueryService.selectByEntryId(entryId);
        log.info("按进站交易ID查询响应结果：{}", response != null ? response.getOrderNo() : "null");
        return response;
    }


    @GetMapping("/payLog/exitId")
    public AlipayPayLogVO exitId(@RequestParam String exitId) {
        log.info("按出站交易ID查询: exitId={}", exitId);
        AlipayPayLogVO response = alipayPayLogQueryService.selectByExitId(exitId);
        log.info("按出站交易ID查询响应结果：{}", response != null ? response.getOrderNo() : "null");
        return response;
    }


    @PostMapping("/payLog/queryByTravelRecord")
    public AlipayPayLogVO queryByTravelRecord(@RequestBody Map<String, String> params) {
        String thirdUserId = params.get("thirdUserId");
        String entryDate = params.get("entryDate");
        String cardNum = params.get("cardNum");
        log.info("按乘车记录查询: thirdUserId={}, entryDate={}, cardNum={}", thirdUserId, entryDate, cardNum);
        AlipayPayLogVO response = alipayPayLogQueryService.selectByTravelRecord(thirdUserId, entryDate, cardNum);
        log.info("按乘车记录查询响应结果：{}", response != null ? response.getOrderNo() : "null");
        return response;
    }

    private PageResult<AlipayPayLogVO> queryList(String action, String thirdUserId, String startTime, String endTime,
                                                 int offset, int limit, String debitRequestResult, String invoice) {
        log.info("{}: thirdUserId={}, startTime={}, endTime={}, offset={}, limit={}",
                action, thirdUserId, startTime, endTime, offset, limit);
        PageResult<AlipayPayLogVO> response = alipayPayLogQueryService.selectAlipayPayLogList(
                thirdUserId, startTime, endTime, offset, limit, debitRequestResult, invoice);
        log.info("{}响应结果: total={}, listSize={}", action,
                response != null ? response.getTotal() : "null",
                response != null && response.getList() != null ? response.getList().size() : "null");
        return response;
    }

    private static String stringValue(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value != null ? value.toString() : null;
    }

    private static int intValue(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        return value != null ? Integer.parseInt(value.toString()) : defaultValue;
    }
}
