package com.chinasofti.huateng.ticket.service.impl;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelRecordDTO;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import com.chinasofti.huateng.ticket.model.app.RequestTransListReqDTO;
import com.chinasofti.huateng.ticket.model.app.RequestTransListResult;
import com.chinasofti.huateng.ticket.model.app.TransRecordDTO;
import com.chinasofti.huateng.ticket.service.TicketTransService;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TicketTransServiceImpl implements TicketTransService {
    private static final Logger log = LoggerFactory.getLogger(TicketTransServiceImpl.class);

    @Autowired
    private QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    @Override
    public RequestTransListResult requestTransList(RequestTransListReqDTO request) {
        RequestTransListResult response = new RequestTransListResult();
        try {
            int pageNumber = request.getPageNumber() != null && request.getPageNumber() > 0 ? request.getPageNumber() : 1;
            int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 10;
            int offset = (pageNumber - 1) * pageSize;

            String startDate = request.getStartDate();
            String endDate = request.getEndDate();
            if (startDate != null && !startDate.isEmpty()) {
                request.setStartDate(startDate.replace("-", ""));
            }
            if (endDate != null && !endDate.isEmpty()) {
                request.setEndDate(endDate.replace("-", ""));
            }

            if (request.getCardType() != null && !request.getCardType().isEmpty()) {
                request.setCardType(CardTypeMapping.toIssueCardType(request.getCardType()));
            }

            request.setOffset(offset);
            request.setLimit(pageSize);

            List<TransRecordDTO> records = qrCodeTxnDetailMapper.selectTransList(request);
            int total = qrCodeTxnDetailMapper.countTransList(request);
            int totalPage = (int) Math.ceil((double) total / pageSize);

            if (records != null) {
                for (TransRecordDTO record : records) {
                    if (record.getDiscountInfo() == null) {
                        record.setDiscountInfo("[]");
                    }
                }
            }

            response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
            response.setPageNumber(String.valueOf(pageNumber));
            response.setPageSize(String.valueOf(pageSize));
            response.setTotalPage(String.valueOf(totalPage));
            response.setTicketTransRecord(records);
        } catch (Exception e) {
            log.error("IF8A-05 查询交易记录异常, request={}", request, e);
            response.setRetCode(TicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
        }
        return response;
    }

    @Override
    public AlipayTripFindTravelListRespDTO alipayTripFindTravelList(AlipayTripFindTravelListReqDTO request) {
        AlipayTripFindTravelListRespDTO response = new AlipayTripFindTravelListRespDTO();
        try {
            int page = request.getPage() != null ? Integer.parseInt(request.getPage()) : 0;
            int size = request.getSize() != null ? Integer.parseInt(request.getSize()) : 10;
            if (page < 0) page = 0;
            if (size <= 0) size = 10;
            int offset = page * size;

            String startDate = request.getStartDate();
            String endDate = request.getEndDate();
            if (startDate != null && !startDate.isEmpty()) {
                request.setStartDate(startDate.replace("-", ""));
            }
            if (endDate != null && !endDate.isEmpty()) {
                request.setEndDate(endDate.replace("-", ""));
            }

            List<AlipayTripTravelRecordDTO> records = qrCodeTxnDetailMapper.selectAlipayTravelList(
                    request.getThirdUserId(), startDate, endDate, offset, size);
            int total = qrCodeTxnDetailMapper.countAlipayTravelList(
                    request.getThirdUserId(), startDate, endDate);
            int totalPage = (int) Math.ceil((double) total / size);

            List<AlipayTripTravelRecordDTO> list = new ArrayList<>();
            if (records != null) {
                for (AlipayTripTravelRecordDTO record : records) {
                    list.add(convertToAlipayTripTravelRecordDTO(record));
                }
            }

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

    @Override
    public AlipayTripFindTravelDetailRespDTO alipayTripFindTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        AlipayTripFindTravelDetailRespDTO response = new AlipayTripFindTravelDetailRespDTO();
        try {
            AlipayTripTravelRecordDTO record = qrCodeTxnDetailMapper.selectAlipayTravelDetail(
                    request.getThirdUserId(), request.getOrderNo(), null);
            if (record == null) {
                response.setRetCode(TicketErrorCodeEnum.NO_DATA.getCode());
                response.setRetMsg(TicketErrorCodeEnum.NO_DATA.getMsg());
                return response;
            }
            response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
            AlipayTripTravelRecordDTO dto = convertToAlipayTripTravelRecordDTO(record);
            response.setEntryStationName(dto.getEntryStationName());
            response.setEntryDate(dto.getEntryDate());
            response.setExitStationName(dto.getExitStationName());
            response.setExitDate(dto.getExitDate());
            response.setPayAmount(dto.getPayAmount());
            response.setTotalAmount(dto.getTotalAmount());
            response.setOrderExpType(dto.getOrderExpType());
            response.setTradeOrderNo(dto.getTradeOrderNo());
            response.setPayTradeOrderNo(dto.getPayTradeOrderNo());
            response.setPayOrderNoDate(dto.getPayOrderNoDate());
            response.setPayChannelCode(dto.getPayChannelCode());
            response.setDebitRequestResult(dto.getDebitRequestResult());
            response.setDiscountFee(dto.getDiscountFee());
            response.setDiscountInfo(dto.getDiscountInfo());
            response.setCompanionFlag(dto.getCompanionFlag());
            response.setCardNum(dto.getCardNum());
            response.setTicketCode(dto.getTicketCode());
            response.setCountingTimes(dto.getCountingTimes());
            response.setCountingFlag(dto.getCountingFlag());
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录详情异常, request={}", request, e);
            response.setRetCode(TicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
        }
        return response;
    }

    private AlipayTripTravelRecordDTO convertToAlipayTripTravelRecordDTO(AlipayTripTravelRecordDTO record) {
        AlipayTripTravelRecordDTO dto = new AlipayTripTravelRecordDTO();
        dto.setEntryStationName(record.getEntryStationName());
        dto.setEntryDate(record.getEntryDate());
        dto.setExitStationName(record.getExitStationName());
        dto.setExitDate(record.getExitDate());
        dto.setPayAmount(record.getPayAmount());
        dto.setTotalAmount(record.getTotalAmount());
        dto.setOrderExpType("");
        dto.setTradeOrderNo(record.getTradeOrderNo());
        dto.setPayTradeOrderNo(record.getPayTradeOrderNo());
        dto.setPayOrderNoDate(record.getPayOrderNoDate());
        dto.setPayChannelCode(record.getPayChannelCode());
        dto.setDebitRequestResult("");
        dto.setDiscountFee(record.getDiscountFee());
        dto.setDiscountInfo("[]");
        dto.setCompanionFlag("");
        dto.setCardNum(record.getCardNum());
        dto.setTicketCode(record.getTicketCode());
        dto.setCountingTimes(null);
        dto.setCountingFlag("");
        return dto;
    }
}
