package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.model.page.OfflineCodeStatView;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayQueryService;
import com.chinasofti.huateng.model.app.CardUnsettledQueryRespDTO;
import com.chinasofti.huateng.model.app.QueryActiveCardsReqDTO;
import com.chinasofti.huateng.model.app.QueryActiveCardsResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.RequestUserAccInfoReqDTO;
import com.chinasofti.huateng.model.app.RequestUserAccInfoResult;
import com.chinasofti.huateng.model.app.TripDataDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
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
    /** ticket-server「未注册用户」码：卡无 QRCODE_STATUS，视为不在行程中（非查询失败）。 */
    private static final String TICKET_QR_CODE_NOT_FOUND = "8004";

    /** IF8A-35 统计窗口月数。 */
    @Value("${app.acc-info.query-months:3}")
    private int accInfoQueryMonths;

    private final GateTxnPayMapper gateTxnPayMapper;
    private final AccountClient accountClient;
    private final TicketClient ticketClient;

    public GateTxnPayQueryServiceImpl(GateTxnPayMapper gateTxnPayMapper,
                                      AccountClient accountClient,
                                      TicketClient ticketClient) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.accountClient = accountClient;
        this.ticketClient = ticketClient;
    }

    @Override
    public ResultVO<Map<String, Object>> page(String orderNo, String cardId, String thirdUserId, String signChannelCode,
                                               String cardType, String debitStatus, String issueChannelCode,
                                               String startDate, String endDate,
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
        String normalizedIssueChannelCode = trimToNull(issueChannelCode);
        List<GateTxnPay> orders = gateTxnPayMapper.selectOperationPage(
                normalizedOrderNo, normalizedCardId, normalizedThirdUserId, trimToNull(signChannelCode), trimToNull(cardType),
                trimToNull(debitStatus), normalizedIssueChannelCode, normalizedStartDate, normalizedEndDate,
                (currentPage - 1) * currentPageSize, currentPageSize);
        fillMissingStationNames(orders);

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", orders);
        page.put("total", gateTxnPayMapper.countOperationPage(
                normalizedOrderNo, normalizedCardId, normalizedThirdUserId, trimToNull(signChannelCode), trimToNull(cardType),
                trimToNull(debitStatus), normalizedIssueChannelCode, normalizedStartDate, normalizedEndDate));
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
                                                    String issueChannelCode, Integer offset, Integer limit) {
        List<GateTxnPay> records = gateTxnPayMapper.selectTransList(thirdUserId, cardIdList, cardType, cardTypeList,
                startDate, endDate, ticketCode, debitRequestResult, issueChannelCode, offset, limit);
        return records.stream().map(this::toListDTO).toList();
    }

    @Override
    public int countTransList(String thirdUserId, List<String> cardIdList, String cardType,
                              List<String> cardTypeList, String startDate, String endDate,
                              String ticketCode, String debitRequestResult, String issueChannelCode) {
        return gateTxnPayMapper.countTransList(thirdUserId, cardIdList, cardType, cardTypeList, startDate, endDate,
                ticketCode, debitRequestResult, issueChannelCode);
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

    @Override
    public ResultVO<Map<String, Object>> oneSidePage(String orderNo, String cardId, String thirdUserId,
                                                      String orderExpType, String startDate, String endDate,
                                                      Integer pageNum, Integer pageSize) {
        String normalizedOrderNo = trimToNull(orderNo);
        String normalizedCardId = trimToNull(cardId);
        String normalizedThirdUserId = trimToNull(thirdUserId);
        String normalizedOrderExpType = trimToNull(orderExpType);
        String normalizedStartDate = normalizeTxnDate(startDate);
        String normalizedEndDate = normalizeTxnDate(endDate);
        if (!hasSearchScope(normalizedOrderNo, normalizedCardId, normalizedThirdUserId,
                normalizedStartDate, normalizedEndDate)) {
            return ResultMapper.illegalParams("请填写订单号、逻辑卡号、第三方用户ID，或同时填写开始日期和结束日期");
        }
        int currentPage = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int currentPageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        List<GateTxnPay> orders = gateTxnPayMapper.selectOneSidePage(
                normalizedOrderNo, normalizedCardId, normalizedThirdUserId, normalizedOrderExpType,
                normalizedStartDate, normalizedEndDate, (currentPage - 1) * currentPageSize, currentPageSize);
        fillMissingStationNames(orders);
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", orders);
        page.put("total", gateTxnPayMapper.countOneSidePage(
                normalizedOrderNo, normalizedCardId, normalizedThirdUserId, normalizedOrderExpType,
                normalizedStartDate, normalizedEndDate));
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

        int inProgressCount = countInProgressTrips(thirdUserId);
        if (inProgressCount < 0) {
            log.error("IF8A-35 聚合进行中行程失败，fail-closed, thirdUserId={}", thirdUserId);
            response.setRetCode(RESULT_CODE_INVALID_PARAM);
            response.setRetMsg("查询用户账务信息失败");
            return response;
        }

        int unpaidCount = counted.getUnpaidCount() + inProgressCount;
        response.setRetCode(RESULT_CODE_SUCCESS);
        response.setRetMsg("成功");
        response.setUnpaidCount(unpaidCount);
        response.setFailureCount(counted.getFailureCount());
        log.info("IF8A-35 查询用户账务信息完成, thirdUserId={}, startDate={}, orderUnpaid={}, inProgress={}, unpaidCount={}, failureCount={}",
                thirdUserId, startDate, counted.getUnpaidCount(), inProgressCount, unpaidCount, counted.getFailureCount());
        return response;
    }

    /**
     * 统计该用户「已进站未出站」的票卡数，叠加到 IF8A-35 的 unpaidCount（ADR-D175）。
     *
     * <p>两个域都不能跨库 JOIN：账户域持有 thirdUserId → CARD_ID，行程域持有 CARD_ID → CODE_STATUS。
     * 编排两次只读 RPC：account-server 查有效卡列表 → ticket-server 逐卡查状态。
     *
     * @return {@code >=0} 进行中行程数；{@code -1} 表示查询未执行（fail-closed，调用方 MUST 拒绝）
     */
    private int countInProgressTrips(String thirdUserId) {
        QueryActiveCardsReqDTO cardsRequest = new QueryActiveCardsReqDTO();
        cardsRequest.setThirdUserId(thirdUserId);
        QueryActiveCardsResult cardsResult;
        try {
            cardsResult = accountClient.queryActiveCards(cardsRequest);
        } catch (RuntimeException e) {
            log.error("IF8A-35 查有效票卡列表异常, thirdUserId={}", thirdUserId, e);
            return -1;
        }
        if (cardsResult == null || !RESULT_CODE_SUCCESS.equals(cardsResult.getRetCode())) {
            log.error("IF8A-35 查有效票卡列表未成功, thirdUserId={}, retCode={}, retMsg={}",
                    thirdUserId, cardsResult == null ? null : cardsResult.getRetCode(),
                    cardsResult == null ? null : cardsResult.getRetMsg());
            return -1;
        }

        int inProgress = 0;
        for (String cardId : cardsResult.getCardIds()) {
            QueryStatusReqDTO statusRequest = new QueryStatusReqDTO();
            statusRequest.setThirdUserId(thirdUserId);
            statusRequest.setCardId(cardId);
            QueryStatusRespDTO statusResult;
            try {
                statusResult = ticketClient.queryQrCodeStatus(statusRequest);
            } catch (RuntimeException e) {
                log.error("IF8A-35 查乘车状态异常, thirdUserId={}, cardId={}", thirdUserId, cardId, e);
                return -1;
            }
            if (statusResult == null) {
                log.error("IF8A-35 查乘车状态返回空, thirdUserId={}, cardId={}", thirdUserId, cardId);
                return -1;
            }
            String statusCode = statusResult.getRetCode();
            if (TICKET_QR_CODE_NOT_FOUND.equals(statusCode)) {
                continue;
            }
            if (!RESULT_CODE_SUCCESS.equals(statusCode)) {
                log.error("IF8A-35 查乘车状态未成功, thirdUserId={}, cardId={}, retCode={}",
                        thirdUserId, cardId, statusCode);
                return -1;
            }
            QRCodeStatusEnum status = QRCodeStatusEnum.parseOrNull(statusResult.getStatus());
            if (status != null && isEnteredWithoutExit(status)) {
                inProgress++;
                log.info("IF8A-35 计入进行中行程, thirdUserId={}, cardId={}, status={}, gateInStation={}, gateInTime={}",
                        thirdUserId, cardId, status.getCode(), statusResult.getGateInStation(), statusResult.getGateInTime());
            }
        }
        return inProgress;
    }

    /**
     * 「已进站未出站」白名单，与 ticket-server 的 {@code SupplementStateRules.hasEnteredWithoutExit} 同源：
     * 开环 04 / 81（{@code isOpenLoop()}）+ 入站码更新 10。NEVER 写成「非闭环即可」的黑名单。
     */
    private boolean isEnteredWithoutExit(QRCodeStatusEnum status) {
        return status.isOpenLoop() || status == QRCodeStatusEnum.UPDATE_ENTRY;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
