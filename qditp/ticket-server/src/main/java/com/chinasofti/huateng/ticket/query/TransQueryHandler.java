package com.chinasofti.huateng.ticket.service.impl;

import com.chinasofti.huateng.model.paysign.PayTxnDetailDTO;
import com.chinasofti.huateng.model.app.RequestTransListResult;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.QueryTransListReqDTO;
import com.chinasofti.huateng.model.app.QueryPayTxnBatchReqDTO;
import com.chinasofti.huateng.model.app.TransRecordDTO;
import com.chinasofti.huateng.model.app.RequestPayTxnBatchResult;
import com.chinasofti.huateng.model.app.TripDataDTO;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * IF8A-05/IF8A-34/IF8A-41 交易记录查询处理。
 *
 * <p>负责：
 * <ul>
 *   <li>IF8A-05 交易记录列表查询</li>
 *   <li>IF8A-34 订单详情查询</li>
 *   <li>IF8A-41 账单统计查询</li>
 * </ul>
 */
@Component
public class TransQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(TransQueryHandler.class);

    @Autowired
    private QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    @Autowired
    private PaySignClient paySignClient;

    @Autowired
    private TransStationNameResolver stationNameResolver;

    @Autowired
    private TransMerchantResolver merchantResolver;

    @Autowired
    private GateTxnPayClient gateTxnPayClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * 查询交易记录列表 (IF8A-05)。
     *
     * <p>数据来源：GATE_TXN_PAY（进出站/商户/金额）+ PAY_TXN_DETAIL（支付明细）。</p>
     */
    public RequestTransListResult requestTransList(QueryTransListReqDTO request) {
        RequestTransListResult response = new RequestTransListResult();
        try {
            // ========== 1. 必填参数校验 ==========
            if (!StringUtils.hasText(request.getThirdUserId())) {
                response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("thirdUserId不能为空");
                return response;
            }

            // ========== 2. 分页参数处理 ==========
            int pageNumber = request.getPageNumber() != null && request.getPageNumber() > 0
                    ? request.getPageNumber() : 1;
            int pageSize = request.getPageSize() != null && request.getPageSize() > 0
                    ? request.getPageSize() : 10;
            pageSize = Math.min(pageSize, MAX_PAGE_SIZE);
            int offset = (pageNumber - 1) * pageSize;

            // ========== 3. 日期格式转换、校验并回写 ==========
            String startDate = normalizeDate(request.getStartDate());
            String endDate = normalizeDate(request.getEndDate());
            if (startDate != null && endDate != null && startDate.compareTo(endDate) > 0) {
                response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("开始日期不能大于结束日期");
                return response;
            }
            request.setStartDate(startDate);
            request.setEndDate(endDate);

            // ========== 4. 卡类型映射并回写 ==========
            if (StringUtils.hasText(request.getCardType())) {
                request.setCardType(CardTypeMapping.toIssueCardType(request.getCardType()));
            }

            // ========== 5. 多卡号解析并回写 ==========
            request.setCardIdList(parseCardIds(request.getCardId()));

            // ========== 6. 回写分页偏移，查询 GATE_TXN_PAY 分页数据（RPC） ==========
            request.setOffset(offset);
            request.setLimit(pageSize);
            List<GateTxnPayListDTO> gateRecords = gateTxnPayClient.requestTransList(request);
            int total = gateTxnPayClient.countTransList(request);
            int totalPage = (int) Math.ceil((double) total / pageSize);

            if (CollectionUtils.isEmpty(gateRecords)) {
                response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
                response.setPageNumber(String.valueOf(pageNumber));
                response.setPageSize(String.valueOf(pageSize));
                response.setTotalPage(String.valueOf(totalPage));
                response.setTicketTransRecord(Collections.emptyList());
                response.setSignType("00");
                response.setSign("");
                return response;
            }

            // ========== 7. 提取 orderNo，批量查询 PAY_TXN_DETAIL ==========
            List<String> orderNos = gateRecords.stream()
                    .map(GateTxnPayListDTO::getOrderNo)
                    .filter(Objects::nonNull)
                    .distinct()
                    .collect(Collectors.toList());

            Map<String, PayTxnDetailDTO> payDetailMap = Collections.emptyMap();
            if (!CollectionUtils.isEmpty(orderNos)) {
                QueryPayTxnBatchReqDTO batchReq = new QueryPayTxnBatchReqDTO();
                batchReq.setOrderNos(orderNos);
                RequestPayTxnBatchResult batchResult = paySignClient.queryPayTxnBatch(batchReq);
                List<PayTxnDetailDTO> payDetails = batchResult != null ? batchResult.getPayTxnDetailList() : null;
                if (!CollectionUtils.isEmpty(payDetails)) {
                    payDetailMap = payDetails.stream()
                            .collect(Collectors.toMap(PayTxnDetailDTO::getOrderNo, Function.identity(), (a, b) -> a));
                }
            }

            // ========== 8. 双源合并为 TransListEntry ==========
            List<TransListEntry> entries = new ArrayList<>(gateRecords.size());
            for (GateTxnPayListDTO gate : gateRecords) {
                TransListEntry entry = new TransListEntry();
                // GT 字段
                entry.setId(gate.getId());
                entry.setOrderNo(gate.getOrderNo());
                entry.setDebitStatus(gate.getDebitStatus());
                entry.setThirdUserId(gate.getThirdUserId());
                entry.setCardId(gate.getCardId());
                entry.setCardType(gate.getCardType());
                entry.setDeviceId(gate.getDeviceId());
                entry.setTrxType(gate.getTrxType());
                entry.setTicketTransSeq(gate.getTicketTransSeq());
                entry.setInStation(gate.getInStation());
                entry.setInTime(gate.getInTime());
                entry.setOutStation(gate.getOutStation());
                entry.setOutTime(gate.getOutTime());
                entry.setTxnDate(gate.getTxnDate());
                entry.setTrxAmount(gate.getTrxAmount());
                entry.setOvertimeAmount(gate.getOvertimeAmount());
                entry.setTotalAmount(gate.getTotalAmount());
                entry.setIssueChannelCode(gate.getIssueChannelCode());
                entry.setSignChannelCode(gate.getSignChannelCode());
                entry.setTicketStatus(gate.getTicketStatus());
                entry.setEntryStationName(gate.getEntryStationName());
                entry.setExitStationName(gate.getExitStationName());
                entry.setOrderExpType(gate.getOrderExpType());
                entry.setCompanionFlag(gate.getCompanionFlag());
                entry.setOfflineFlag(gate.getOfflineFlag());
                entry.setTicketCode(gate.getTicketCode());
                entry.setCountingTimes(gate.getCountingTimes());
                entry.setCountingFlag(gate.getCountingFlag());
                entry.setAttributableParty(gate.getAttributableParty());
                entry.setReceivingParty(gate.getReceivingParty());
                entry.setRemark(gate.getRemark());
                entry.setCreateTime(gate.getCreateTime());
                entry.setUpdateTime(gate.getUpdateTime());
                // PAY 字段
                PayTxnDetailDTO pay = payDetailMap.get(gate.getOrderNo());
                if (pay != null) {
                    entry.setPayType(pay.getPayType());
                    entry.setPayStatus(pay.getPayStatus());
                    entry.setPaymentVendor(pay.getPaymentVendor());
                    entry.setRequestSignSeq(pay.getRequestSignSeq());
                    entry.setAmount(pay.getAmount());
                    entry.setCashAmount(pay.getCashAmount());
                    entry.setCouponAmount(pay.getCouponAmount());
                    entry.setRefundStatus(pay.getRefundStatus());
                    entry.setRefundAmount(pay.getRefundAmount());
                    entry.setLastRefundTime(pay.getLastRefundTime());
                    entry.setMerchantOrderNo(pay.getMerchantOrderNo());
                    entry.setChannelOrderNo(pay.getChannelOrderNo());
                    entry.setPayUserId(pay.getPayUserId());
                    entry.setRequestCount(pay.getRequestCount());
                    entry.setNextRequestTime(pay.getNextRequestTime());
                    entry.setLastRequestTime(pay.getLastRequestTime());
                    entry.setFirstRequestTime(pay.getFirstRequestTime());
                    entry.setResponseTime(pay.getResponseTime());
                    entry.setPayTime(pay.getPayTime());
                    entry.setPayTxnDate(pay.getTxnDate());
                    entry.setPayCreateTime(pay.getCreateTime());
                    entry.setPayUpdateTime(pay.getUpdateTime());
                    entry.setPayDebitRequestResult(pay.getDebitRequestResult());
                    entry.setPayDiscountInfo(pay.getDiscountInfo());
                    entry.setPayDiscountFee(pay.getDiscountFee());
                }
                entries.add(entry);
            }

            // ========== 9. 组装应答记录（字段落库时已写入，无需二次 enrich） ==========
            List<TransRecordDTO> finalRecords = entries.stream()
                    .map(TransRecordAssembler::assemble)
                    .collect(Collectors.toList());

            response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
            response.setPageNumber(String.valueOf(pageNumber));
            response.setPageSize(String.valueOf(pageSize));
            response.setTotalPage(String.valueOf(totalPage));
            response.setTicketTransRecord(finalRecords);
            response.setSignType("00");
            response.setSign("");
        } catch (IllegalArgumentException e) {
            log.warn("IF8A-05 查询交易记录参数非法, request={}", request, e);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg(e.getMessage());
        } catch (Exception e) {
            log.error("IF8A-05 查询交易记录异常, request={}", request, e);
            response.setRetCode(TicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
        }
        return response;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 日期格式转换：yyyy-MM-dd -> yyyyMMdd，校验合法性。
     */
    private String normalizeDate(String dateStr) {
        if (!StringUtils.hasText(dateStr)) {
            return null;
        }
        try {
            LocalDate.parse(dateStr, DATE_FORMATTER);
            return dateStr.replace("-", "");
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式非法: " + dateStr + "，期望格式 yyyy-MM-dd");
        }
    }

    /**
     * 解析卡号列表。
     */
    private List<String> parseCardIds(String cardId) {
        if (!StringUtils.hasText(cardId)) {
            return Collections.emptyList();
        }
        return Arrays.stream(cardId.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 查询账单统计 (IF8A-41)。
     */
    public RequestTransStatisticsResult requestTransStatistics(RequestTransStatisticsReqDTO request) {
        RequestTransStatisticsResult response = new RequestTransStatisticsResult();
        try {
            if (request != null && StringUtils.hasText(request.getCardId())) {
                List<String> cardIdList = Arrays.stream(request.getCardId().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                request.setCardIdList(cardIdList);
            }

            RequestTransStatisticsResult result = qrCodeTxnDetailMapper.selectTransStatistics(request);
            if (result == null || result.getTripData() == null) {
                result = new RequestTransStatisticsResult();
                result.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
                result.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
                TripDataDTO tripData = new TripDataDTO();
                tripData.setTotalPrice("0.00");
                tripData.setTotalDebit("0.00");
                tripData.setTotalDiscount("0.00");
                tripData.setCount(0);
                result.setTripData(tripData);
            } else {
                result.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
                result.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
            }
            return result;
        } catch (Exception e) {
            log.error("IF8A-41 查询账单统计异常, request={}", request, e);
            response.setRetCode(TicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
            TripDataDTO tripData = new TripDataDTO();
            tripData.setTotalPrice("0.00");
            tripData.setTotalDebit("0.00");
            tripData.setTotalDiscount("0.00");
            tripData.setCount(0);
            response.setTripData(tripData);
        }
        return response;
    }

    /**
     * 获取订单详情 (IF8A-34)。
     *
     * <p>数据来源：GATE_TXN_PAY（进出站/商户/金额）+ PAY_TXN_DETAIL（支付明细）。</p>
     */
    public RequestTransDetailResult requestTransDetail(RequestTransDetailReqDTO request) {
        RequestTransDetailResult response = new RequestTransDetailResult();
        try {
            if (request == null || !StringUtils.hasText(request.getThirdUserId())
                    || !StringUtils.hasText(request.getOrderNo())) {
                response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("thirdUserId和orderNo不能为空");
                return response;
            }

            // ========== 1. 查询 GATE_TXN_PAY 主交易数据（RPC） ==========
            GateTxnPayListDTO gateRecord = gateTxnPayClient.queryByOrderNo(request.getOrderNo());
            if (gateRecord == null) {
                response.setRetCode(TicketErrorCodeEnum.NO_DATA.getCode());
                response.setRetMsg(TicketErrorCodeEnum.NO_DATA.getMsg());
                return response;
            }

            // 校验订单归属，防止越权查询
            if (!StringUtils.hasText(gateRecord.getThirdUserId())
                    || !gateRecord.getThirdUserId().equals(request.getThirdUserId())) {
                response.setRetCode(TicketErrorCodeEnum.NO_DATA.getCode());
                response.setRetMsg("订单不存在或不属于该用户");
                return response;
            }

            // ========== 2. 查询 PAY_TXN_DETAIL 支付明细（RPC） ==========
            Map<String, PayTxnDetailDTO> payDetailMap = Collections.emptyMap();
            QueryPayTxnBatchReqDTO batchReq = new QueryPayTxnBatchReqDTO();
            batchReq.setOrderNos(Collections.singletonList(request.getOrderNo()));
            RequestPayTxnBatchResult batchResult = paySignClient.queryPayTxnBatch(batchReq);
            List<PayTxnDetailDTO> payDetails = batchResult != null ? batchResult.getPayTxnDetailList() : null;
            if (!CollectionUtils.isEmpty(payDetails)) {
                payDetailMap = payDetails.stream()
                        .collect(Collectors.toMap(PayTxnDetailDTO::getOrderNo, Function.identity(), (a, b) -> a));
            }
            PayTxnDetailDTO payDetail = payDetailMap.get(request.getOrderNo());

            // ========== 3. 双源合并为 TransRecordDTO ==========
            TransRecordDTO record = new TransRecordDTO();
            // GT 字段
            record.setEntryStationName(gateRecord.getEntryStationName());
            record.setExitStationName(gateRecord.getExitStationName());
            record.setEntryDate(gateRecord.getInTime());
            record.setExitDate(gateRecord.getOutTime());
            // 支付相关字段：PaySign 无记录时使用 GT 兜底
            if (payDetail != null) {
                record.setPayAmount(String.valueOf(payDetail.getAmount()));
                record.setPayTradeOrderNo(payDetail.getMerchantOrderNo());
                record.setPayOrderNoDate(payDetail.getTxnDate());
                record.setDebitRequestResult(payDetail.getDebitRequestResult());
                // payChannelCode 响应字段，从 paymentVendor 转换而来（DB 列已删除）
                if (StringUtils.hasText(payDetail.getPaymentVendor())) {
                    record.setPayChannelCode(payDetail.getPaymentVendor());
                }
                record.setDiscountFee(payDetail.getDiscountFee());
                record.setDiscountInfo(payDetail.getDiscountInfo());
            } else {
                // PaySign 无记录，使用 GT 金额兜底，标记为支付中
                record.setPayAmount(String.valueOf(gateRecord.getTotalAmount()));
                record.setDebitRequestResult("PROCESSING");
            }
            record.setOrderExpType(gateRecord.getOrderExpType());
            record.setTradeOrderNo(gateRecord.getOrderNo());
            record.setCompanionFlag(gateRecord.getCompanionFlag());
            record.setCardNum(gateRecord.getCardId());
            record.setTicketCode(gateRecord.getTicketCode());
            record.setCountingTimes(gateRecord.getCountingTimes());
            record.setCountingFlag(gateRecord.getCountingFlag());
            record.setOfflineFlag(gateRecord.getOfflineFlag());
            record.setAttributableParty(gateRecord.getAttributableParty());
            record.setReceivingParty(gateRecord.getReceivingParty());

            // ========== 4. 站点名称转换 ==========
            enrichSingleStationNames(record);

            // ========== 5. 填充商户号 ==========
            resolveMerchantParties(record);

            response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
            response.setTicketTransRecord(record);
        } catch (Exception e) {
            log.error("IF8A-34 获取订单详情异常, request={}", request, e);
            response.setRetCode(TicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
        }
        return response;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 填充单条记录的站点名称（详情场景）。
     */
    private void enrichSingleStationNames(TransRecordDTO record) {
        if (StringUtils.hasText(record.getEntryStationName())) {
            Set<String> codes = new LinkedHashSet<>(Collections.singletonList(record.getEntryStationName()));
            Map<String, String> stationNameMap = stationNameResolver.resolveStationNames(codes);
            stationNameMap.getOrDefault(record.getEntryStationName(), record.getEntryStationName());
            record.setEntryStationName(
                    stationNameMap.getOrDefault(record.getEntryStationName(), record.getEntryStationName()));
        }
        if (StringUtils.hasText(record.getExitStationName())) {
            Set<String> codes = new LinkedHashSet<>(Collections.singletonList(record.getExitStationName()));
            Map<String, String> stationNameMap = stationNameResolver.resolveStationNames(codes);
            record.setExitStationName(
                    stationNameMap.getOrDefault(record.getExitStationName(), record.getExitStationName()));
        }
    }

    /**
     * 批量查询 GT 订单号。
     */
    private void enrichTradeOrderNos(List<TransRecordDTO> records) {
        // 交易记录详情接口已通过 orderNo 直接查询，无需再调用 gate-txn-pay-server
    }

    /**
     * 解析商户号。
     */
    private void resolveMerchantParties(TransRecordDTO record) {
        String rideDate = null;
        if (StringUtils.hasText(record.getEntryDate()) && record.getEntryDate().length() >= 8) {
            rideDate = record.getEntryDate().substring(0, 8);
        } else if (StringUtils.hasText(record.getExitDate()) && record.getExitDate().length() >= 8) {
            rideDate = record.getExitDate().substring(0, 8);
        }

        boolean useOld = merchantResolver.shouldUseOldMerchant(rideDate);
        if (useOld) {
            if (StringUtils.hasText(merchantResolver.getOldAttributableParty())) {
                record.setAttributableParty(merchantResolver.getOldAttributableParty());
            }
            if (StringUtils.hasText(merchantResolver.getOldReceivingParty())) {
                record.setReceivingParty(merchantResolver.getOldReceivingParty());
            }
        } else {
            if (StringUtils.hasText(merchantResolver.getNewAttributableParty())) {
                record.setAttributableParty(merchantResolver.getNewAttributableParty());
            }
            if (StringUtils.hasText(merchantResolver.getNewReceivingParty())) {
                record.setReceivingParty(merchantResolver.getNewReceivingParty());
            }
        }
    }

    /**
     * 合并进出站记录为一条完整订单。
     */
    private TransRecordDTO mergeTransRecord(List<TransRecordDTO> records) {
        TransRecordDTO merged = new TransRecordDTO();
        for (TransRecordDTO r : records) {
            // 合并公共字段（取非空值）
            setNonNull(merged::getPayAmount, () -> merged.setPayAmount(r.getPayAmount()));
            setNonNull(merged::getOrderExpType, () -> merged.setOrderExpType(r.getOrderExpType()));
            setNonNull(merged::getTradeOrderNo, () -> merged.setTradeOrderNo(r.getTradeOrderNo()));
            setNonNull(merged::getPayTradeOrderNo, () -> merged.setPayTradeOrderNo(r.getPayTradeOrderNo()));
            setNonNull(merged::getPayOrderNoDate, () -> merged.setPayOrderNoDate(r.getPayOrderNoDate()));
            setNonNull(merged::getDebitRequestResult, () -> merged.setDebitRequestResult(r.getDebitRequestResult()));
            setNonNull(merged::getCardNum, () -> merged.setCardNum(r.getCardNum()));
            // 根据进站/出站信息区分
            if (StringUtils.hasText(r.getEntryStationName())) {
                setNonNull(merged::getEntryStationName, () -> merged.setEntryStationName(r.getEntryStationName()));
                setNonNull(merged::getEntryDate, () -> merged.setEntryDate(r.getEntryDate()));
            }
            if (StringUtils.hasText(r.getExitStationName())) {
                setNonNull(merged::getExitStationName, () -> merged.setExitStationName(r.getExitStationName()));
                setNonNull(merged::getExitDate, () -> merged.setExitDate(r.getExitDate()));
            }
        }
        return merged;
    }

    private <T> void setNonNull(java.util.function.Supplier<T> getter, Runnable setter) {
        if (getter.get() == null) {
            setter.run();
        }
    }
}
