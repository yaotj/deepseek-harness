package com.chinasofti.huateng.ticket.alipay;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelDetailDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelRecordDTO;
import com.chinasofti.huateng.model.app.RequestStationNameReqDTO;
import com.chinasofti.huateng.model.app.RequestStationNameResult;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import com.chinasofti.huateng.ticket.station.StationNameResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.*;

/** 支付宝出行-行程查询处理。 */
@Component
public class AlipayTripHandler {

    private static final Logger log = LoggerFactory.getLogger(AlipayTripHandler.class);

    @Autowired
    private QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    @Autowired
    private StationNameResolver stationNameResolver;

    /** 查询支付宝出行乘车记录列表。 */
    public AlipayTripFindTravelListRespDTO alipayTripFindTravelList(AlipayTripFindTravelListReqDTO request) {
        AlipayTripFindTravelListRespDTO response = new AlipayTripFindTravelListRespDTO();
        try {
            int page = parsePage(request.getPage());
            int size = parseSize(request.getSize());
            int offset = page * size;

            String startDate = request.getStartDate();
            String endDate = request.getEndDate();
            if (startDate != null && !startDate.isEmpty()) {
                request.setStartDate(startDate.replace("-", ""));
            }
            if (endDate != null && !endDate.isEmpty()) {
                request.setEndDate(endDate.replace("-", ""));
            }

            log.info("支付宝出行-查询乘车记录列表, thirdUserId={}, startDate={}, endDate={}, page={}, size={}",
                    request.getThirdUserId(), startDate, endDate, page, size);

            List<AlipayTripTravelRecordDTO> records = qrCodeTxnDetailMapper.selectAlipayTravelList(
                    request.getThirdUserId(), startDate, endDate, offset, size);
            log.info("支付宝出行-查询乘车记录列表, selectAlipayTravelList完成, 条数={}", records != null ? records.size() : 0);

            int total = qrCodeTxnDetailMapper.countAlipayTravelList(
                    request.getThirdUserId(), startDate, endDate);
            log.info("支付宝出行-查询乘车记录列表, countAlipayTravelList完成, total={}", total);

            int totalPage = (int) Math.ceil((double) total / size);

            enrichStationNamesForAlipay(records);

            List<AlipayTripTravelRecordDTO> list = convertToAlipayDTOs(records);

            response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
            response.setPageNumber(page);
            response.setPageSize(size);
            response.setTotalPage(totalPage);
            response.setTotalCount(total);
            response.setTicketTransRecord(list);
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录异常, request={}", request, e);
            response.setRetCode(TicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
        }
        return response;
    }

    /** 查询支付宝出行乘车记录详情。 */
    public AlipayTripFindTravelDetailRespDTO alipayTripFindTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        AlipayTripFindTravelDetailRespDTO response = new AlipayTripFindTravelDetailRespDTO();
        try {
            log.info("支付宝出行-查询乘车记录详情, thirdUserId={}, orderNo={}",
                    request.getThirdUserId(), request.getOrderNo());

            AlipayTripTravelRecordDTO record = queryRecord(request);
            if (record == null) {
                log.warn("支付宝出行-查询乘车记录详情, 查询结果为空");
                response.setRetCode(TicketErrorCodeEnum.NO_DATA.getCode());
                response.setRetMsg(TicketErrorCodeEnum.NO_DATA.getMsg());
                return response;
            }

            log.info("支付宝出行-查询乘车记录详情, 查询成功, record={}", record);
            AlipayTripTravelRecordDTO dto = convertToAlipayDTO(record);

            enrichSingleStationNamesForAlipay(dto);

            fillResponse(response, dto);

            log.info("支付宝出行-查询乘车记录详情, 响应结果: entryStationName={}, entryDate={}, exitStationName={}, exitDate={}",
                    dto.getEntryStationName(), dto.getEntryDate(), dto.getExitStationName(), dto.getExitDate());
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录详情异常, request={}", request, e);
            response.setRetCode(TicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
        }
        return response;
    }

    private int parsePage(String pageStr) {
        int page = 0;
        if (StringUtils.hasText(pageStr)) {
            try {
                page = Integer.parseInt(pageStr);
                if (page < 0) page = 0;
            } catch (NumberFormatException e) {
                log.warn("page 参数格式错误: {}", pageStr);
            }
        }
        return page;
    }

    private int parseSize(String sizeStr) {
        int size = 10;
        if (StringUtils.hasText(sizeStr)) {
            try {
                size = Integer.parseInt(sizeStr);
                if (size <= 0) size = 10;
            } catch (NumberFormatException e) {
                log.warn("size 参数格式错误: {}", sizeStr);
            }
        }
        return size;
    }

    /**
     * 按 orderNo 查明细。
     *
     * <p>原先还有一支「按 thirdUserId + handleDateTime + trxType 查」，随 R6 表147 把请求契约收窄成
     * thirdUserId + orderNo 后已无入参来源，整支删除。NEVER 加回 —— 那三个字段在
     * {@code AlipayTripFindTravelDetailReqDTO} 里已不存在。
     */
    private AlipayTripTravelRecordDTO queryRecord(AlipayTripFindTravelDetailReqDTO request) {
        if (!StringUtils.hasText(request.getOrderNo())) {
            log.warn("支付宝出行-无有效查询条件: thirdUserId={}, orderNo 为空", request.getThirdUserId());
            return null;
        }
        log.info("支付宝出行-按订单号查询, orderNo={}", request.getOrderNo());
        return qrCodeTxnDetailMapper.selectAlipayTravelDetailByOrderNo(request.getOrderNo());
    }

    private void enrichStationNamesForAlipay(List<AlipayTripTravelRecordDTO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<String> entryCodes = new LinkedHashSet<>();
        Set<String> exitCodes = new LinkedHashSet<>();
        for (AlipayTripTravelRecordDTO record : records) {
            if (record.getEntryStationName() != null) {
                entryCodes.add(record.getEntryStationName());
            }
            if (record.getExitStationName() != null) {
                exitCodes.add(record.getExitStationName());
            }
        }
        Map<String, String> stationNameMap = stationNameResolver.resolveStationNames(
                stationNameResolver.collectStationCodes(entryCodes, exitCodes));

        for (AlipayTripTravelRecordDTO record : records) {
            Optional<String> entry = Optional.ofNullable(stationNameMap.get(record.getEntryStationName()));
            entry.ifPresent(record::setEntryStationName);
            Optional<String> exit = Optional.ofNullable(stationNameMap.get(record.getExitStationName()));
            exit.ifPresent(record::setExitStationName);
        }
    }

    private void enrichSingleStationNamesForAlipay(AlipayTripTravelRecordDTO dto) {
        if (dto == null) {
            return;
        }
        if (StringUtils.hasText(dto.getEntryStationName())) {
            Set<String> codes = new LinkedHashSet<>(Collections.singletonList(dto.getEntryStationName()));
            Map<String, String> map = stationNameResolver.resolveStationNames(codes);
            map.getOrDefault(dto.getEntryStationName(), dto.getEntryStationName());
            dto.setEntryStationName(
                    map.getOrDefault(dto.getEntryStationName(), dto.getEntryStationName()));
        }
        if (StringUtils.hasText(dto.getExitStationName())) {
            Set<String> codes = new LinkedHashSet<>(Collections.singletonList(dto.getExitStationName()));
            Map<String, String> map = stationNameResolver.resolveStationNames(codes);
            dto.setExitStationName(
                    map.getOrDefault(dto.getExitStationName(), dto.getExitStationName()));
        }
    }

    private List<AlipayTripTravelRecordDTO> convertToAlipayDTOs(List<AlipayTripTravelRecordDTO> records) {
        if (records == null) {
            return new ArrayList<>();
        }
        List<AlipayTripTravelRecordDTO> result = new ArrayList<>();
        for (AlipayTripTravelRecordDTO record : records) {
            result.add(convertToAlipayDTO(record));
        }
        return result;
    }

    private AlipayTripTravelRecordDTO convertToAlipayDTO(AlipayTripTravelRecordDTO record) {
        AlipayTripTravelRecordDTO dto = new AlipayTripTravelRecordDTO();
        dto.setEntryStationName(record.getEntryStationName());
        dto.setEntryDate(record.getEntryDate());
        dto.setExitStationName(record.getExitStationName());
        dto.setExitDate(record.getExitDate());
        dto.setPayAmount(record.getPayAmount());
        dto.setTotalAmount(record.getTotalAmount());
        dto.setOrderExpType(record.getOrderExpType() != null && !record.getOrderExpType().trim().isEmpty()
                ? record.getOrderExpType() : "0");
        dto.setTradeOrderNo(record.getTradeOrderNo());
        dto.setPayTradeOrderNo(record.getPayTradeOrderNo());
        dto.setPayOrderNoDate(record.getPayOrderNoDate());
        dto.setDebitRequestResult(record.getDebitRequestResult());
        // companionFlag / countingTimes / countingFlag 在本链路**恒为空**，且 NEVER 改成透传 record：
        // 本类的数据源是 QRCODE_TXN_DETAIL，`alipayTripTravelRecordResultMap` 里 companionFlag 映射的是
        // TRX_TYPE、ticketCode 映射的是 CARD_TYPE（错映射），countingTimes / countingFlag 压根没映射。
        // 透传等于把「交易类型」当成同行标志答给 APP。这三个字段的正确来源是 GATE_TXN_PAY，
        // 已在 trans-query-server 的 AlipayTravelQueryHandler 按那条口径实现（2026-09-18）。
        dto.setCompanionFlag("");
        dto.setCardNum(record.getCardNum());
        dto.setTicketCode(record.getTicketCode());
        dto.setCountingTimes(null);
        dto.setCountingFlag("");
        return dto;
    }

    private void fillResponse(AlipayTripFindTravelDetailRespDTO response, AlipayTripTravelRecordDTO dto) {
        response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
        // 业务字段 MUST 包在 data 里（ADR-D150：支付宝侧按 data 解析），NEVER 平铺回顶层。
        AlipayTripTravelDetailDTO detail = new AlipayTripTravelDetailDTO();
        detail.setEntryStationName(dto.getEntryStationName());
        detail.setEntryDate(dto.getEntryDate());
        detail.setExitStationName(dto.getExitStationName());
        detail.setExitDate(dto.getExitDate());
        detail.setPayAmount(dto.getPayAmount());
        detail.setTotalAmount(dto.getTotalAmount());
        detail.setOrderExpType(dto.getOrderExpType());
        detail.setTradeOrderNo(dto.getTradeOrderNo());
        detail.setPayTradeOrderNo(dto.getPayTradeOrderNo());
        detail.setPayOrderNoDate(dto.getPayOrderNoDate());
        detail.setDebitRequestResult(dto.getDebitRequestResult());
        detail.setCompanionFlag(dto.getCompanionFlag());
        detail.setCardNum(dto.getCardNum());
        detail.setTicketCode(dto.getTicketCode());
        detail.setCountingTimes(dto.getCountingTimes());
        detail.setCountingFlag(dto.getCountingFlag());
        response.setData(detail);
    }
}
