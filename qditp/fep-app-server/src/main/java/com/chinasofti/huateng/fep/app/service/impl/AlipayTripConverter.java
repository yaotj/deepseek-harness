package com.chinasofti.huateng.fep.app.service.impl;

import com.chinasofti.huateng.model.alipaytrip.*;
import com.chinasofti.huateng.model.app.*;

/**
 * 支付宝出行 DTO 与 APP DTO 转换工具类。
 * <p>
 * 用于在现有 RPC 客户端中调用支付宝渠道方法时，进行参数转换。
 * </p>
 */
public class AlipayTripConverter {

    private AlipayTripConverter() {
    }

    /**
     * 支付宝出行-添加签约信息 → APP-请求签约信息
     */
    public static RequestSignInfoReqDTO toRequestSignInfoReqDTO(AlipayTripAddContractReqDTO request) {
        if (request == null) {
            return null;
        }
        RequestSignInfoReqDTO dto = new RequestSignInfoReqDTO();
        dto.setThirdUserId(request.getThirdUserId());
        dto.setPayChannelCode(request.getChannel());
        dto.setDisplayAccount(request.getChannelUserAccount());
        dto.setRequestSignSeq(request.getAgreementCode());
        return dto;
    }

    /**
     * APP-请求签约信息结果 → 支付宝出行-添加签约信息响应
     */
    public static AlipayTripAddContractRespDTO fromRequestSignInfoResult(RequestSignInfoResult result) {
        if (result == null) {
            return null;
        }
        AlipayTripAddContractRespDTO response = new AlipayTripAddContractRespDTO();
        response.setRetCode(result.getRetCode());
        response.setRetMsg(result.getRetMsg());
        return response;
    }

    /**
     * 支付宝出行-解约登记 → APP-请求解约
     */
    public static RequestTerminationReqDTO toRequestTerminationReqDTO(AlipayTripTerminateContractReqDTO request) {
        if (request == null) {
            return null;
        }
        RequestTerminationReqDTO dto = new RequestTerminationReqDTO();
        dto.setRequestSignSeq(request.getAgreementCode());
        return dto;
    }

    /**
     * APP-请求解约结果 → 支付宝出行-解约登记响应
     */
    public static AlipayTripTerminateContractRespDTO fromRequestTerminationResult(RequestTerminationResult result) {
        if (result == null) {
            return null;
        }
        AlipayTripTerminateContractRespDTO response = new AlipayTripTerminateContractRespDTO();
        response.setRetCode(result.getRetCode());
        response.setRetMsg(result.getRetMsg());
        return response;
    }

    /**
     * 支付宝出行-开卡申请 → APP-请求开户
     */
    public static RequestApplicationReqDTO toRequestApplicationReqDTO(AlipayTripRequestApplicationReqDTO request) {
        if (request == null) {
            return null;
        }
        RequestApplicationReqDTO dto = new RequestApplicationReqDTO();
        dto.setThirdUserId(request.getThirdUserId());
        dto.setCardType(request.getCardType());
        dto.setMsisdn(request.getMsisdn());
        dto.setCardIssueCode(request.getCardIssueCode());
        return dto;
    }

    /**
     * APP-请求开户结果 → 支付宝出行-开卡申请响应
     */
    public static AlipayTripRequestApplicationRespDTO fromRequestApplicationResult(RequestApplicationResult result) {
        if (result == null) {
            return null;
        }
        AlipayTripRequestApplicationRespDTO response = new AlipayTripRequestApplicationRespDTO();
        response.setRetCode(result.getRetCode());
        response.setRetMsg(result.getRetMsg());
        response.setCardId(result.getCardId());
        return response;
    }

    /**
     * 支付宝出行-查询乘车记录 → APP-请求查询交易记录
     */
    public static RequestTransListReqDTO toRequestTransListReqDTO(AlipayTripFindTravelListReqDTO request) {
        if (request == null) {
            return null;
        }
        RequestTransListReqDTO dto = new RequestTransListReqDTO();
        dto.setThirdUserId(request.getThirdUserId());
        if (request.getPage() != null) {
            dto.setPageNumber(Integer.parseInt(request.getPage()));
        }
        if (request.getSize() != null) {
            dto.setPageSize(Integer.parseInt(request.getSize()));
        }
        dto.setStartDate(request.getStartDate());
        dto.setEndDate(request.getEndDate());
        dto.setDebitRequestResult(request.getDebitRequestResult());
        return dto;
    }

    /**
     * APP-请求查询交易记录结果 → 支付宝出行-查询乘车记录响应
     */
    public static AlipayTripFindTravelListRespDTO fromRequestTransListResult(RequestTransListResult result) {
        if (result == null) {
            return null;
        }
        AlipayTripFindTravelListRespDTO response = new AlipayTripFindTravelListRespDTO();
        response.setRetCode(result.getRetCode());
        response.setRetMsg(result.getRetMsg());
        if (result.getPageNumber() != null) {
            response.setPageNumber(Integer.parseInt(result.getPageNumber()));
        }
        if (result.getPageSize() != null) {
            response.setPageSize(Integer.parseInt(result.getPageSize()));
        }
        if (result.getTotalPage() != null) {
            response.setTotalPage(Integer.parseInt(result.getTotalPage()));
        }
        if (result.getTicketTransRecord() != null) {
            response.setTicketTransRecord(
                    result.getTicketTransRecord().stream()
                            .map(AlipayTripConverter::convertToAlipayTripTravelRecord)
                            .collect(java.util.stream.Collectors.toList())
            );
        }
        return response;
    }

    private static AlipayTripTravelRecordDTO convertToAlipayTripTravelRecord(TransRecordDTO dto) {
        AlipayTripTravelRecordDTO record = new AlipayTripTravelRecordDTO();
        record.setEntryStationName(dto.getEntryStationName());
        record.setEntryDate(dto.getEntryDate());
        record.setExitStationName(dto.getExitStationName());
        record.setExitDate(dto.getExitDate());
        record.setPayAmount(dto.getPayAmount());
        record.setOrderExpType(dto.getOrderExpType());
        record.setTradeOrderNo(dto.getTradeOrderNo());
        record.setPayTradeOrderNo(dto.getPayTradeOrderNo());
        record.setPayOrderNoDate(dto.getPayOrderNoDate());
        record.setPayChannelCode(dto.getPayChannelCode());
        record.setDebitRequestResult(dto.getDebitRequestResult());
        if (dto.getDiscountFee() != null) {
            record.setDiscountFee(dto.getDiscountFee().toString());
        }
        record.setDiscountInfo(dto.getDiscountInfo());
        record.setCompanionFlag(dto.getCompanionFlag());
        record.setCardNum(dto.getCardNum());
        record.setTicketCode(dto.getTicketCode());
        if (dto.getCountingTimes() != null) {
            record.setCountingTimes(dto.getCountingTimes().toString());
        }
        record.setCountingFlag(dto.getCountingFlag());
        return record;
    }

    /**
     * 支付宝出行-查询乘车记录详情 → APP-查询用户上次行程
     */
    public static QueryUserItineraryReqDTO toQueryUserItineraryReqDTO(AlipayTripFindTravelDetailReqDTO request) {
        if (request == null) {
            return null;
        }
        QueryUserItineraryReqDTO dto = new QueryUserItineraryReqDTO();
        dto.setCardNum(request.getOrderNo());
        return dto;
    }

    /**
     * APP-查询用户上次行程结果 → 支付宝出行-查询乘车记录详情响应
     */
    public static AlipayTripFindTravelDetailRespDTO fromQueryUserItineraryResult(QueryUserItineraryResult result) {
        if (result == null) {
            return null;
        }
        AlipayTripFindTravelDetailRespDTO response = new AlipayTripFindTravelDetailRespDTO();
        response.setRetCode(result.getRetCode());
        response.setRetMsg(result.getRetMsg());
        return response;
    }

    /**
     * 支付宝出行-黑名单状态变更通知 → APP-查询黑名单
     */
    public static QueryBlackListReqDTO toQueryBlackListReqDTO(AlipayTripReceiveBlackListReqDTO request) {
        if (request == null) {
            return null;
        }
        QueryBlackListReqDTO dto = new QueryBlackListReqDTO();
        dto.setCardId(request.getCardId());
        return dto;
    }

    /**
     * APP-查询黑名单结果 → 支付宝出行-黑名单状态变更通知响应
     */
    public static AlipayTripReceiveBlackListRespDTO fromQueryBlackListResult(QueryBlackListResult result) {
        if (result == null) {
            return null;
        }
        AlipayTripReceiveBlackListRespDTO response = new AlipayTripReceiveBlackListRespDTO();
        response.setRetCode(result.getRetCode());
        response.setRetMsg(result.getRetMsg());
        return response;
    }
}
