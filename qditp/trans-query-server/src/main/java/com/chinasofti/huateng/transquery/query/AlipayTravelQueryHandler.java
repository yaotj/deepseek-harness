package com.chinasofti.huateng.transquery.query;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayTxnBriefDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelDetailDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelRecordDTO;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@Component
public class AlipayTravelQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(AlipayTravelQueryHandler.class);

    private static final String ALIPAY_ISSUE_CHANNEL_CODE = "07";

    private static final int DEFAULT_PAGE_SIZE = 10;


    private static final int MAX_PAGE_SIZE = 100;


    private static final String EMPTY_DISCOUNT = "";

    private static final int PAY_ORDER_NO_DATE_LENGTH = 14;


    private static final int QUERY_DATE_LENGTH = 8;

    private final AlipayPaySignClient alipayPaySignClient;
    private final GateTxnPayClient gateTxnPayClient;

    @Autowired
    public AlipayTravelQueryHandler(AlipayPaySignClient alipayPaySignClient, GateTxnPayClient gateTxnPayClient) {
        this.alipayPaySignClient = alipayPaySignClient;
        this.gateTxnPayClient = gateTxnPayClient;
    }

    public AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request) {
        log.info("支付宝出行-查询乘车记录列表,请求参数：{}", JSON.toJSONString(request));
        AlipayTripFindTravelListRespDTO response = new AlipayTripFindTravelListRespDTO();
        if (request == null || !StringUtils.hasText(request.getThirdUserId())) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("无效的参数");
            log.warn("支付宝出行-查询乘车记录列表,参数校验失败");
            return response;
        }
        String startDate;
        String endDate;
        try {
            startDate = normalizeQueryDate(request.getStartDate(), "startDate");
            endDate = normalizeQueryDate(request.getEndDate(), "endDate");
        } catch (IllegalArgumentException e) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("无效的参数");
            log.warn("支付宝出行-查询乘车记录列表,日期参数非法: {}", e.getMessage());
            return response;
        }
        try {
            int pageNum = parseInt(request.getPage(), 0, "page");
            int pageSize = parseInt(request.getSize(), DEFAULT_PAGE_SIZE, "size");
            if (pageSize <= 0) {
                pageSize = DEFAULT_PAGE_SIZE;
            }
            if (pageSize > MAX_PAGE_SIZE) {
                log.warn("支付宝出行-查询乘车记录列表,size 超过上界已钳制, 原值={}, 实际={}", pageSize, MAX_PAGE_SIZE);
                pageSize = MAX_PAGE_SIZE;
            }

            QueryTransListReqDTO gateRequest = new QueryTransListReqDTO();
            gateRequest.setThirdUserId(request.getThirdUserId());
            gateRequest.setStartDate(startDate);
            gateRequest.setEndDate(endDate);
            gateRequest.setDebitRequestResult(request.getDebitRequestResult());
            gateRequest.setIssueChannelCode(ALIPAY_ISSUE_CHANNEL_CODE);
            gateRequest.setOffset(resolveOffset(pageNum, pageSize));
            gateRequest.setLimit(pageSize);

            List<GateTxnPayListDTO> gateRecords = gateTxnPayClient.requestTransList(gateRequest);
            int total = gateTxnPayClient.countTransList(gateRequest);
            int totalPage = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
            log.info("支付宝出行-查询乘车记录列表,扣费订单分页结果, 本页={}条, total={}",
                    gateRecords == null ? 0 : gateRecords.size(), total);

            response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("成功");
            response.setPageNumber(pageNum);
            response.setPageSize(pageSize);
            response.setTotalPage(totalPage);
            response.setTotalCount(total);
            response.setTicketTransRecord(assembleRecords(gateRecords));
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录列表 异常", e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-查询乘车记录列表,响应结果：{}", JSON.toJSONString(response));
        return response;
    }


    private List<AlipayTripTravelRecordDTO> assembleRecords(List<GateTxnPayListDTO> gateRecords) {
        if (gateRecords == null || gateRecords.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, AlipayTripPayTxnBriefDTO> briefMap = queryPayTxnBriefs(gateRecords);
        List<AlipayTripTravelRecordDTO> records = new ArrayList<>(gateRecords.size());
        for (GateTxnPayListDTO gate : gateRecords) {
            records.add(buildTravelRecord(gate, briefMap.get(gate.getOrderNo())));
        }
        return records;
    }

    private Map<String, AlipayTripPayTxnBriefDTO> queryPayTxnBriefs(List<GateTxnPayListDTO> gateRecords) {
        Set<String> orderNos = new LinkedHashSet<>();
        for (GateTxnPayListDTO gate : gateRecords) {
            if (StringUtils.hasText(gate.getOrderNo())) {
                orderNos.add(gate.getOrderNo());
            }
        }
        if (orderNos.isEmpty()) {
            return new HashMap<>();
        }
        Map<String, AlipayTripPayTxnBriefDTO> briefMap = new HashMap<>();
        try {
            List<AlipayTripPayTxnBriefDTO> briefs = alipayPaySignClient.queryPayTxnBrief(new ArrayList<>(orderNos));
            if (briefs != null) {
                for (AlipayTripPayTxnBriefDTO brief : briefs) {
                    if (brief != null && StringUtils.hasText(brief.getOrderNo())) {
                        briefMap.put(brief.getOrderNo(), brief);
                    }
                }
            }
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录列表,批量补齐支付明细失败, 请求条数={}", orderNos.size(), e);
        }
        log.info("支付宝出行-查询乘车记录列表,批量补齐支付明细, 请求={}条, 命中={}条", orderNos.size(), briefMap.size());
        return briefMap;
    }


    private AlipayTripTravelRecordDTO buildTravelRecord(GateTxnPayListDTO gate, AlipayTripPayTxnBriefDTO brief) {
        AlipayTripTravelRecordDTO record = new AlipayTripTravelRecordDTO();
        record.setEntryStationName(gate.getEntryStationName());
        record.setEntryDate(gate.getInTime());
        record.setExitStationName(gate.getExitStationName());
        record.setExitDate(gate.getOutTime());
        record.setPayAmount(toStringOrNull(gate.getTrxAmount()));
        record.setTotalAmount(toStringOrNull(gate.getTotalAmount()));
        record.setOrderExpType(StringUtils.hasText(gate.getOrderExpType()) ? gate.getOrderExpType() : "0");
        record.setTradeOrderNo(gate.getOrderNo());
        record.setPayChannelCode(ALIPAY_ISSUE_CHANNEL_CODE);
        record.setCardNum(gate.getCardId());
        record.setCompanionFlag(gate.getCompanionFlag());
        record.setTicketCode(gate.getTicketCode());
        record.setCountingTimes(toStringOrNull(gate.getCountingTimes()));
        record.setCountingFlag(gate.getCountingFlag());
        record.setDiscountFee(EMPTY_DISCOUNT);
        record.setDiscountInfo(EMPTY_DISCOUNT);
        record.setDebitRequestResult(mapDebitStatusToResult(gate.getDebitStatus()));

        if (brief != null) {
            record.setPayTradeOrderNo(brief.getChannelOrderNo());
            record.setPayOrderNoDate(normalizePayOrderNoDate(brief.getTransTime()));
            record.setInvoice(brief.getInvoice());
        }
        return record;
    }


    private int resolveOffset(int pageNum, int pageSize) {
        long offset = (long) pageNum * pageSize;
        if (offset > Integer.MAX_VALUE) {
            log.warn("支付宝出行-查询乘车记录列表,offset 越界已钳制, page={}, size={}, offset={}",
                    pageNum, pageSize, offset);
            return Integer.MAX_VALUE;
        }
        return (int) offset;
    }

    private int parseInt(String value, int defaultValue, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed < 0 ? defaultValue : parsed;
        } catch (NumberFormatException e) {
            log.warn("{} 参数格式错误: {}", fieldName, value);
            return defaultValue;
        }
    }


    private String normalizeQueryDate(String date, String fieldName) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        String digits = date.replaceAll("\\D", "");
        if (digits.length() < QUERY_DATE_LENGTH) {
            throw new IllegalArgumentException(
                    fieldName + " 无法归一成 " + QUERY_DATE_LENGTH + " 位 yyyyMMdd: " + date);
        }
        String normalized = digits.substring(0, QUERY_DATE_LENGTH);
        if (!normalized.equals(date)) {
            log.info("支付宝出行-查询乘车记录列表,{} 已归一, 原值={}, 归一后={}", fieldName, date, normalized);
        }
        return normalized;
    }

    private String toStringOrNull(Integer value) {
        return value == null ? null : String.valueOf(value);
    }


    public AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        log.info("支付宝出行-查询乘车记录详情,请求参数：{}", JSON.toJSONString(request));
        AlipayTripFindTravelDetailRespDTO detailResp = new AlipayTripFindTravelDetailRespDTO();
        if (request == null || !StringUtils.hasText(request.getThirdUserId()) || !StringUtils.hasText(request.getOrderNo())) {
            detailResp.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            detailResp.setRetMsg("无效的参数");
            log.warn("支付宝出行-查询乘车记录详情,参数校验失败");
            return detailResp;
        }
        try {
            GateTxnPayListDTO order = gateTxnPayClient.queryByOrderNo(request.getOrderNo());
            if (order == null) {
                detailResp.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                detailResp.setRetMsg("支付订单不存在");
                log.warn("支付宝出行-查询乘车记录详情,扣费订单不存在, orderNo={}", request.getOrderNo());
                return detailResp;
            }

            JSONObject industryDetail = null;
            if (StringUtils.hasText(order.getIndustryDetail())) {
                try {
                    industryDetail = JSONObject.parseObject(order.getIndustryDetail());
                } catch (Exception e) {
                    log.warn("支付宝出行-查询乘车记录详情,行业明细解析失败, orderNo={}, industryDetail={}",
                            request.getOrderNo(), order.getIndustryDetail(), e);
                }
            }
            log.info("支付宝出行-查询乘车记录详情,扣费订单查询成功, orderNo={}, inStation={}, outStation={}, 有无行业明细={}",
                    request.getOrderNo(), order.getInStation(), order.getOutStation(), industryDetail != null);

            detailResp.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
            detailResp.setRetMsg("成功");

            AlipayTripTravelDetailDTO detail = new AlipayTripTravelDetailDTO();
            detail.setEntryStationName(order.getEntryStationName());
            detail.setEntryDate(order.getInTime());
            detail.setExitStationName(order.getExitStationName());
            detail.setExitDate(order.getOutTime());
            detail.setPayAmount(toStringOrNull(order.getTrxAmount()));
            detail.setTotalAmount(toStringOrNull(order.getTotalAmount()));
            detail.setOrderExpType(StringUtils.hasText(order.getOrderExpType()) ? order.getOrderExpType() : "0");

            detail.setTradeOrderNo(order.getOrderNo());
            detail.setDebitRequestResult(mapDebitStatusToResult(order.getDebitStatus()));
            detail.setCardNum(order.getCardId());
            detail.setPayChannelCode(ALIPAY_ISSUE_CHANNEL_CODE);

            detail.setCompanionFlag(order.getCompanionFlag());
            detail.setTicketCode(order.getTicketCode());
            detail.setCountingTimes(toStringOrNull(order.getCountingTimes()));
            detail.setCountingFlag(order.getCountingFlag());
            detail.setDiscountFee(EMPTY_DISCOUNT);
            detail.setDiscountInfo(EMPTY_DISCOUNT);

            AlipayTripPayTxnBriefDTO brief = queryPayTxnBrief(order.getOrderNo());
            if (brief != null) {
                detail.setPayTradeOrderNo(brief.getChannelOrderNo());
                detail.setPayOrderNoDate(normalizePayOrderNoDate(brief.getTransTime()));
                detail.setInvoice(brief.getInvoice());
            }
            detailResp.setData(detail);

            log.info("支付宝出行-查询乘车记录详情,响应结果：{}", JSON.toJSONString(detailResp));
            return detailResp;
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录详情异常, request={}", request, e);
            detailResp.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
            detailResp.setRetMsg("系统内部错误");
            log.info("支付宝出行-查询乘车记录详情,响应结果：{}", JSON.toJSONString(detailResp));
            return detailResp;
        }
    }

    private AlipayTripPayTxnBriefDTO queryPayTxnBrief(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        try {
            List<AlipayTripPayTxnBriefDTO> briefs = alipayPaySignClient.queryPayTxnBrief(List.of(orderNo));
            if (briefs == null || briefs.isEmpty()) {
                log.info("支付宝出行-查询乘车记录详情,未命中支付明细, orderNo={}", orderNo);
                return null;
            }
            return briefs.get(0);
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录详情,补齐支付明细失败, orderNo={}", orderNo, e);
            return null;
        }
    }


    private String normalizePayOrderNoDate(String transTime) {
        if (!StringUtils.hasText(transTime)) {
            return null;
        }
        String digits = transTime.replaceAll("\\D", "");
        if (digits.length() >= PAY_ORDER_NO_DATE_LENGTH) {
            return digits.substring(0, PAY_ORDER_NO_DATE_LENGTH);
        }
        log.warn("支付时间无法归一成 {} 位，原样返回, transTime={}", PAY_ORDER_NO_DATE_LENGTH, transTime);
        return transTime;
    }


    private String mapDebitStatusToResult(String debitStatus) {
        return "SUCCESS".equalsIgnoreCase(debitStatus) ? "0" : "1";
    }
}
