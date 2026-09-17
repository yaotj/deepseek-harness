package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.model.page.OfflineCodeStatView;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayQueryService;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.app.TripDataDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** `GATE_TXN_PAY` 只读查询侧实现。 */
@Service
public class GateTxnPayQueryServiceImpl implements GateTxnPayQueryService {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayQueryServiceImpl.class);
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    /** 未结清扣费订单查询：查询已成功执行。 */
    private static final String RESULT_CODE_SUCCESS = GateTxnPayRetCode.SUCCESS;
    /** 未结清扣费订单查询：参数缺失，查询未执行。 */
    private static final String RESULT_CODE_INVALID_PARAM = GateTxnPayRetCode.QUERY_FAILED;
    /** IF8A-35 统计窗口默认月数，配置非法时回退到该值。 */
    private static final int DEFAULT_ACC_INFO_QUERY_MONTHS = 3;
    /** IF8A-35 时间下限格式。 */
    private static final DateTimeFormatter ACC_INFO_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** IF8A-35 统计窗口月数。 */
    @Value("${app.acc-info.query-months:3}")
    private int accInfoQueryMonths;

    private final GateTxnPayMapper gateTxnPayMapper;

    public GateTxnPayQueryServiceImpl(GateTxnPayMapper gateTxnPayMapper) {
        this.gateTxnPayMapper = gateTxnPayMapper;
    }

    @Override
    public ResultVO<Map<String, Object>> page(String orderNo, String cardId, String thirdUserId, String signChannelCode,
                                               String cardType, String debitStatus, String startDate, String endDate,
                                               Integer pageNum, Integer pageSize) {
        String normalizedOrderNo = trimToNull(orderNo);
        String normalizedCardId = trimToNull(cardId);
        String normalizedThirdUserId = trimToNull(thirdUserId);
        String normalizedStartDate = trimToNull(startDate);
        String normalizedEndDate = trimToNull(endDate);
        if (!hasSearchScope(normalizedOrderNo, normalizedCardId, normalizedThirdUserId, normalizedStartDate, normalizedEndDate)) {
            return ResultMapper.illegalParams("请填写订单号、逻辑卡号、第三方用户ID，或同时填写开始日期和结束日期");
        }
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentPageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        List<GateTxnPay> orders = gateTxnPayMapper.selectOperationPage(
                normalizedOrderNo, normalizedCardId, normalizedThirdUserId, trimToNull(signChannelCode), trimToNull(cardType),
                trimToNull(debitStatus), normalizedStartDate, normalizedEndDate, (currentPage - 1) * currentPageSize, currentPageSize);
        fillMissingStationNames(orders);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", orders);
        page.put("total", gateTxnPayMapper.countOperationPage(
                normalizedOrderNo, normalizedCardId, normalizedThirdUserId, trimToNull(signChannelCode), trimToNull(cardType),
                trimToNull(debitStatus), normalizedStartDate, normalizedEndDate));
        return ResultMapper.ok(page);
    }

    @Override
    public GateTxnPayRespDTO queryOrderByBizKey(GateTxnPayReqDTO request) {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        GateTxnPay order = gateTxnPayMapper.selectByBizKeyForQuery(
                request.getCardId(), request.getTrxType(), request.getHandleDateTime(),
                request.getTicketTransSeq(), request.getDeviceId(),
                request.getHandleDateTime() != null && request.getHandleDateTime().length() >= 8
                        ? request.getHandleDateTime().substring(0, 8) : null);
        if (order == null) {
            response.setRetCode(GateTxnPayRetCode.INVALID_PARAM);
            response.setRetMsg("未找到关联的过闸扣费订单");
        } else {
            response.setRetCode(RESULT_CODE_SUCCESS);
            response.setOrderNo(order.getOrderNo());
        }
        return response;
    }

    @Override
    public List<GateTxnPayListDTO> selectTransList(String thirdUserId, List<String> cardIdList, String cardType,
                                                    List<String> cardTypeList, String startDate, String endDate,
                                                    String ticketCode, String debitRequestResult,
                                                    Integer offset, Integer limit) {
        List<GateTxnPay> records = gateTxnPayMapper.selectTransList(thirdUserId, cardIdList, cardType, cardTypeList,
                startDate, endDate, ticketCode, debitRequestResult, offset, limit);
        return records.stream().map(this::toListDTO).toList();
    }

    @Override
    public int countTransList(String thirdUserId, List<String> cardIdList, String cardType,
                              List<String> cardTypeList, String startDate, String endDate,
                              String ticketCode, String debitRequestResult) {
        return gateTxnPayMapper.countTransList(thirdUserId, cardIdList, cardType, cardTypeList, startDate, endDate,
                ticketCode, debitRequestResult);
    }

    /**
     * 统计口径全部落在 SQL 里（见 {@code GateTxnPayMapper.xml#selectTransStatistics}），本方法只做零行兜底：{@code COUNT(1)} 恒有一行。
     */
    @Override
    public RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request) {
        RequestTransStatisticsResult result = new RequestTransStatisticsResult();
        TripDataDTO tripData = request == null ? null : gateTxnPayMapper.selectTransStatistics(request);
        if (tripData == null) {
            tripData = new TripDataDTO();
            tripData.setCount(0);
        }
        if (tripData.getCount() == null) {
            tripData.setCount(0);
        }
        tripData.setTotalPrice(zeroIfBlank(tripData.getTotalPrice()));
        tripData.setTotalDebit(zeroIfBlank(tripData.getTotalDebit()));
        tripData.setTotalDiscount(zeroIfBlank(tripData.getTotalDiscount()));
        tripData.setTotalOvertime(zeroIfBlank(tripData.getTotalOvertime()));
        result.setRetCode(RESULT_CODE_SUCCESS);
        result.setRetMsg("成功");
        result.setTripData(tripData);
        log.info("IF8A-41 账单统计完成, thirdUserId={}, cardTypeList={}, startDate={}, endDate={}, tripData={}",
                request == null ? null : request.getThirdUserId(),
                request == null ? null : request.getCardTypeList(),
                request == null ? null : request.getStartDate(),
                request == null ? null : request.getEndDate(), tripData);
        return result;
    }

    private String zeroIfBlank(String amount) {
        return StringUtils.hasText(amount) ? amount : "0.00";
    }

    @Override
    public GateTxnPayListDTO selectByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        GateTxnPay record = gateTxnPayMapper.selectByOrderNo(orderNo.trim());
        return record != null ? toListDTO(record) : null;
    }

    @Override
    public GateTxnPayFailedOrderRespDTO hasFailedOrder(String thirdUserId, String paymentVendor, LocalDateTime requestTime) {
        GateTxnPayFailedOrderRespDTO response = new GateTxnPayFailedOrderRespDTO();
        if (!StringUtils.hasText(thirdUserId) || !StringUtils.hasText(paymentVendor)) {
            log.warn("查询未结清扣费订单参数缺失, thirdUserId={}, paymentVendor={}", thirdUserId, paymentVendor);
            response.setResultCode(RESULT_CODE_INVALID_PARAM);
            response.setResultMsg("查询未结清扣费订单参数缺失");
            response.setHasFailedOrder(true);
            return response;
        }
        int count = gateTxnPayMapper.countFailedOrder(thirdUserId.trim(), paymentVendor.trim(), requestTime);
        response.setResultCode(RESULT_CODE_SUCCESS);
        response.setResultMsg("成功");
        response.setHasFailedOrder(count > 0);
        log.info("查询未结清扣费订单完成, thirdUserId={}, paymentVendor={}, requestTime={}, count={}",
                thirdUserId, paymentVendor, requestTime, count);
        return response;
    }

    @Override
    public CardUnsettledQueryRespDTO hasUnsettledOrderByCard(String cardId) {
        CardUnsettledQueryRespDTO response = new CardUnsettledQueryRespDTO();
        if (!StringUtils.hasText(cardId)) {
            log.warn("按卡查询未结清扣费订单参数缺失, cardId={}", cardId);
            response.setResultCode(RESULT_CODE_INVALID_PARAM);
            response.setResultMsg("按卡查询未结清扣费订单参数缺失");
            response.setHasUnsettled(true);
            return response;
        }

        int count = gateTxnPayMapper.countUnsettledOrderByCardId(cardId.trim());
        response.setResultCode(RESULT_CODE_SUCCESS);
        response.setResultMsg("成功");
        response.setHasUnsettled(count > 0);
        log.info("按卡查询未结清扣费订单完成, cardId={}, count={}", cardId, count);
        return response;
    }

    @Override
    public ResultVO<Map<String, Object>> offlineStats(String startDate, String endDate) {
        String start = normalizeTxnDate(startDate);
        String end = normalizeTxnDate(endDate);
        if (start == null || end == null || start.compareTo(end) > 0) {
            return ResultMapper.illegalParams("请同时填写正确的开始日期和结束日期（yyyy-MM-dd，闭区间）");
        }
        List<OfflineCodeStatView> stations = gateTxnPayMapper.countOfflineByStationGroup(start, end);
        if (stations == null) {
            stations = new ArrayList<>();
        }
        fillStationNames(stations);
        OfflineCodeStatView summary = gateTxnPayMapper.countOfflineSummary(start, end);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", stations);
        result.put("summary", summary);
        return ResultMapper.ok(result);
    }

    @Override
    public ResultVO<Map<String, Object>> overtimeRefundablePage(String stationCode, String startDate, String endDate,
                                                                Integer pageNum, Integer pageSize) {
        String start = normalizeTxnDate(startDate);
        String end = normalizeTxnDate(endDate);
        if (start == null || end == null || start.compareTo(end) > 0) {
            return ResultMapper.illegalParams("请同时填写正确的开始日期和结束日期（yyyy-MM-dd，闭区间）");
        }
        String station = trimToNull(stationCode);
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentPageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        List<GateTxnPay> orders = gateTxnPayMapper.selectOvertimeRefundablePage(
                station, start, end, (currentPage - 1) * currentPageSize, currentPageSize);
        fillMissingStationNames(orders);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", orders);
        page.put("total", gateTxnPayMapper.countOvertimeRefundable(station, start, end));
        return ResultMapper.ok(page);
    }

    /** 页面日期窗（yyyy-MM-dd 或 yyyyMMdd）换算成 TXN_DATE 的存储格式 yyyyMMdd，非法输入返回 null。 */
    private String normalizeTxnDate(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        String digits = trimmed.replace("-", "");
        return digits.matches("\\d{8}") ? digits : null;
    }

    /** 统计行只带车站编码，中文名经 STATION_INFO 批量回填，查不到的编码原样展示。 */
    private void fillStationNames(List<OfflineCodeStatView> stations) {
        Set<String> codes = new HashSet<>();
        for (OfflineCodeStatView row : stations) {
            if (StringUtils.hasText(row.getStationCode())) {
                codes.add(row.getStationCode().trim());
            }
        }
        if (codes.isEmpty()) {
            return;
        }
        Map<String, String> nameByCode = new HashMap<>();
        for (Map<String, Object> row : gateTxnPayMapper.selectStationNames(new ArrayList<>(codes))) {
            Object code = row.get("STATION_CODE");
            Object name = row.get("STATION_NAME");
            if (code != null && name != null) {
                nameByCode.put(String.valueOf(code), String.valueOf(name));
            }
        }
        for (OfflineCodeStatView row : stations) {
            if (StringUtils.hasText(row.getStationCode())) {
                row.setStationName(nameByCode.get(row.getStationCode().trim()));
            }
        }
    }

    private GateTxnPayListDTO toListDTO(GateTxnPay record) {
        GateTxnPayListDTO dto = new GateTxnPayListDTO();
        dto.setId(record.getId());
        dto.setOrderNo(record.getOrderNo());
        dto.setDebitStatus(record.getDebitStatus());
        dto.setThirdUserId(record.getThirdUserId());
        dto.setCardId(record.getCardId());
        dto.setCardType(record.getCardType());
        dto.setDeviceId(record.getDeviceId());
        dto.setTrxType(record.getTrxType());
        dto.setTicketTransSeq(record.getTicketTransSeq());
        dto.setInStation(record.getInStation());
        dto.setInTime(record.getInTime());
        dto.setOutStation(record.getOutStation());
        dto.setOutTime(record.getOutTime());
        dto.setTxnDate(record.getTxnDate());
        dto.setTrxAmount(record.getTrxAmount());
        dto.setOvertimeAmount(record.getOvertimeAmount());
        dto.setTotalAmount(record.getTotalAmount());
        dto.setIssueChannelCode(record.getIssueChannelCode());
        dto.setSignChannelCode(record.getSignChannelCode());
        dto.setPaymentVendor(record.getPaymentVendor());
        dto.setTransferFlag(record.getTransferFlag());
        dto.setCumulativeType(record.getCumulativeType());
        dto.setOriginalFare(record.getOriginalFare());
        dto.setWalletTotalAmt(record.getWalletTotalAmt());
        dto.setDiscountLevelAmt(record.getDiscountLevelAmt());
        dto.setDiscountRate(record.getDiscountRate());
        dto.setExpectedGateAmount(record.getExpectedGateAmount());
        dto.setDiscountCalcStatus(record.getDiscountCalcStatus());
        dto.setDiscountCalcMsg(record.getDiscountCalcMsg());
        dto.setTicketStatus(record.getTicketStatus());
        dto.setEntryStationName(record.getEntryStationName());
        dto.setExitStationName(record.getExitStationName());
        dto.setOrderExpType(record.getOrderExpType());
        dto.setCompanionFlag(record.getCompanionFlag());
        dto.setOfflineFlag(record.getOfflineFlag());
        dto.setTicketCode(record.getTicketCode());
        dto.setCountingTimes(record.getCountingTimes() != null ? record.getCountingTimes() : 0);
        dto.setCountingFlag(StringUtils.hasText(record.getCountingFlag()) ? record.getCountingFlag() : "N");
        dto.setAttributableParty(record.getAttributableParty());
        dto.setReceivingParty(record.getReceivingParty());
        dto.setIndustryDetail(record.getIndustryDetail());
        dto.setRemark(record.getRemark());
        dto.setCreateTime(record.getCreateTime());
        dto.setUpdateTime(record.getUpdateTime());
        return dto;
    }

    private boolean hasSearchScope(String orderNo, String cardId, String thirdUserId, String startDate, String endDate) {
        return orderNo != null || cardId != null || thirdUserId != null || (startDate != null && endDate != null);
    }

    /**
     * 运营端分页展示用：历史行的 ENTRY_STATION_NAME / EXIT_STATION_NAME 可能为 null，按进出站编码批量查 STATION_INFO 补齐，查不到的保持 null，由前端回退显示编码。
     */
    private void fillMissingStationNames(List<GateTxnPay> orders) {
        Set<String> codes = new HashSet<>();
        for (GateTxnPay order : orders) {
            if (!StringUtils.hasText(order.getEntryStationName()) && StringUtils.hasText(order.getInStation())) {
                codes.add(order.getInStation().trim());
            }
            if (!StringUtils.hasText(order.getExitStationName()) && StringUtils.hasText(order.getOutStation())) {
                codes.add(order.getOutStation().trim());
            }
        }
        if (codes.isEmpty()) {
            return;
        }
        Map<String, String> nameByCode = new HashMap<>();
        for (Map<String, Object> row : gateTxnPayMapper.selectStationNames(new ArrayList<>(codes))) {
            Object code = row.get("STATION_CODE");
            Object name = row.get("STATION_NAME");
            if (code != null && name != null) {
                nameByCode.put(String.valueOf(code), String.valueOf(name));
            }
        }
        for (GateTxnPay order : orders) {
            if (!StringUtils.hasText(order.getEntryStationName()) && StringUtils.hasText(order.getInStation())) {
                order.setEntryStationName(nameByCode.get(order.getInStation().trim()));
            }
            if (!StringUtils.hasText(order.getExitStationName()) && StringUtils.hasText(order.getOutStation())) {
                order.setExitStationName(nameByCode.get(order.getOutStation().trim()));
            }
        }
    }

    @Override
    public RequestUserAccInfoResult requestUserAccInfo(RequestUserAccInfoReqDTO request) {
        RequestUserAccInfoResult response = new RequestUserAccInfoResult();
        String thirdUserId = request != null ? trimToNull(request.getThirdUserId()) : null;
        if (thirdUserId == null) {
            log.warn("IF8A-35 查询用户账务信息参数缺失, request={}", request);
            response.setRetCode(RESULT_CODE_INVALID_PARAM);
            response.setRetMsg("thirdUserId不能为空");
            return response;
        }

        int months = accInfoQueryMonths > 0 ? accInfoQueryMonths : DEFAULT_ACC_INFO_QUERY_MONTHS;
        String startDate = LocalDate.now().minusMonths(months).format(ACC_INFO_DATE_FORMATTER);

        RequestUserAccInfoResult counted = gateTxnPayMapper.countUserAccInfo(thirdUserId, startDate);
        if (counted == null) {
            log.error("IF8A-35 统计结果为空，MUST 人工核对 mapper 映射, thirdUserId={}, startDate={}",
                    thirdUserId, startDate);
            response.setRetCode(RESULT_CODE_INVALID_PARAM);
            response.setRetMsg("查询用户账务信息失败");
            return response;
        }

        response.setRetCode(RESULT_CODE_SUCCESS);
        response.setRetMsg("成功");
        response.setUnpaidCount(counted.getUnpaidCount());
        response.setFailureCount(counted.getFailureCount());
        log.info("IF8A-35 查询用户账务信息完成, thirdUserId={}, startDate={}, unpaidCount={}, failureCount={}",
                thirdUserId, startDate, counted.getUnpaidCount(), counted.getFailureCount());
        return response;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
