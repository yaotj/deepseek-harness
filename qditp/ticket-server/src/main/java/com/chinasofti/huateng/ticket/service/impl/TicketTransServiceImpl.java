package com.chinasofti.huateng.ticket.service.impl;

import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelRecordDTO;
import com.chinasofti.huateng.model.app.CardTypeMapping;
import com.chinasofti.huateng.model.app.RequestTransDetailReqDTO;
import com.chinasofti.huateng.model.app.RequestTransDetailResult;
import com.chinasofti.huateng.model.app.RequestTransStatisticsReqDTO;
import com.chinasofti.huateng.model.app.RequestTransStatisticsResult;
import com.chinasofti.huateng.model.app.TripDataDTO;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import com.chinasofti.huateng.ticket.model.app.RequestTransListReqDTO;
import com.chinasofti.huateng.ticket.model.app.RequestTransListResult;
import com.chinasofti.huateng.ticket.model.app.TransRecordDTO;
import com.chinasofti.huateng.ticket.service.TicketTransService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TicketTransServiceImpl implements TicketTransService {
    private static final Logger log = LoggerFactory.getLogger(TicketTransServiceImpl.class);

    @Autowired
    private QRCodeTxnDetailMapper qrCodeTxnDetailMapper;

    @Autowired
    private com.chinasofti.huateng.rpc.para.ParaClient paraClient;

    @Autowired
    private com.chinasofti.huateng.rpc.account.AccountClient accountClient;

    @Autowired
    private com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient dailyTicketClient;

    /** 商户号变更日期，格式yyyyMMdd，此日期之前的订单使用城交商户 */
    @org.springframework.beans.factory.annotation.Value("${app.trans.merchant-change-date:}")
    private String merchantChangeDate;

    /** 城交商户号（变更日期前使用） */
    @org.springframework.beans.factory.annotation.Value("${app.trans.old-attributable-party:}")
    private String oldAttributableParty;

    @org.springframework.beans.factory.annotation.Value("${app.trans.old-receiving-party:}")
    private String oldReceivingParty;

    /** 新商户号（变更日期后使用） */
    @org.springframework.beans.factory.annotation.Value("${app.trans.new-attributable-party:}")
    private String newAttributableParty;

    @org.springframework.beans.factory.annotation.Value("${app.trans.new-receiving-party:}")
    private String newReceivingParty;

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

            if (request.getCardId() != null && !request.getCardId().isEmpty()) {
                List<String> cardIdList = Arrays.stream(request.getCardId().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
                request.setCardIdList(cardIdList);
            }

            request.setOffset(offset);
            request.setLimit(pageSize);

            List<TransRecordDTO> records = qrCodeTxnDetailMapper.selectTransList(request);
            int total = qrCodeTxnDetailMapper.countTransList(request);
            int totalPage = (int) Math.ceil((double) total / pageSize);

            if (records != null) {
                // 收集所有站点编码，批量查询站名
                java.util.Set<String> stationCodes = new java.util.LinkedHashSet<>();
                for (TransRecordDTO record : records) {
                    if (record.getEntryStationName() != null && !record.getEntryStationName().isEmpty()) {
                        stationCodes.add(record.getEntryStationName());
                    }
                    if (record.getExitStationName() != null && !record.getExitStationName().isEmpty()) {
                        stationCodes.add(record.getExitStationName());
                    }
                    if (record.getDiscountInfo() == null) {
                        record.setDiscountInfo("[]");
                    }
                }
                java.util.Map<String, String> stationNameMap = new java.util.HashMap<>();
                for (String code : stationCodes) {
                    try {
                        com.chinasofti.huateng.model.app.RequestStationNameReqDTO req = new com.chinasofti.huateng.model.app.RequestStationNameReqDTO();
                        req.setStationCode(code);
                        com.chinasofti.huateng.model.app.RequestStationNameResult result = paraClient.requestStationName(req);
                        if (result != null && result.getStationName() != null) {
                            stationNameMap.put(code, result.getStationName());
                        }
                    } catch (Exception e) {
                        log.warn("查询站点中文名失败, stationCode={}", code, e);
                    }
                }
                // 站点编码替换为站名
                for (TransRecordDTO record : records) {
                    if (record.getEntryStationName() != null) {
                        String cn = stationNameMap.get(record.getEntryStationName());
                        if (cn != null) {
                            record.setEntryStationName(cn);
                        }
                    }
                    if (record.getExitStationName() != null) {
                        String cn = stationNameMap.get(record.getExitStationName());
                        if (cn != null) {
                            record.setExitStationName(cn);
                        }
                    }
                }
            }

            response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
            response.setPageNumber(String.valueOf(pageNumber));
            response.setPageSize(String.valueOf(pageSize));
            response.setTotalPage(String.valueOf(totalPage));
            response.setTicketTransRecord(records);
            response.setSignType("00");
            response.setSign("");
        } catch (Exception e) {
            log.error("IF8A-05 查询交易记录异常, request={}", request, e);
            response.setRetCode(TicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
        }
        return response;
    }

    @Override
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

    @Override
    public RequestTransDetailResult requestTransDetail(RequestTransDetailReqDTO request) {
        RequestTransDetailResult response = new RequestTransDetailResult();
        try {
            if (request == null || !StringUtils.hasText(request.getThirdUserId()) || !StringUtils.hasText(request.getOrderNo())) {
                response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("thirdUserId和orderNo不能为空");
                return response;
            }

            com.chinasofti.huateng.model.app.TransRecordDTO record = qrCodeTxnDetailMapper.selectTransDetail(request.getThirdUserId(), request.getOrderNo());
            if (record == null) {
                response.setRetCode(TicketErrorCodeEnum.NO_DATA.getCode());
                response.setRetMsg(TicketErrorCodeEnum.NO_DATA.getMsg());
                return response;
            }

            // 站点编码转中文站名
            if (StringUtils.hasText(record.getEntryStationName())) {
                try {
                    com.chinasofti.huateng.model.app.RequestStationNameReqDTO req = new com.chinasofti.huateng.model.app.RequestStationNameReqDTO();
                    req.setStationCode(record.getEntryStationName());
                    com.chinasofti.huateng.model.app.RequestStationNameResult result = paraClient.requestStationName(req);
                    if (result != null && StringUtils.hasText(result.getStationName())) {
                        record.setEntryStationName(result.getStationName());
                    }
                } catch (Exception e) {
                    log.warn("查询进站中文名失败, stationCode={}", record.getEntryStationName(), e);
                }
            }
            if (StringUtils.hasText(record.getExitStationName())) {
                try {
                    com.chinasofti.huateng.model.app.RequestStationNameReqDTO req = new com.chinasofti.huateng.model.app.RequestStationNameReqDTO();
                    req.setStationCode(record.getExitStationName());
                    com.chinasofti.huateng.model.app.RequestStationNameResult result = paraClient.requestStationName(req);
                    if (result != null && StringUtils.hasText(result.getStationName())) {
                        record.setExitStationName(result.getStationName());
                    }
                } catch (Exception e) {
                    log.warn("查询出站中文名失败, stationCode={}", record.getExitStationName(), e);
                }
            }

            if (record.getDiscountInfo() == null) {
                record.setDiscountInfo("[]");
            }

            // 查询 companionFlag 和 countingFlag（跨服务调用 account-server）
            if (StringUtils.hasText(record.getCardNum())) {
                try {
                    com.chinasofti.huateng.model.app.QueryUserInfoResult userInfo = accountClient.queryCardTypeByCardId(record.getCardNum());
                    if (userInfo != null && "0000".equals(userInfo.getRetCode())) {
                        if (StringUtils.hasText(userInfo.getCompanionFlag())) {
                            record.setCompanionFlag(userInfo.getCompanionFlag());
                        }
                        // 根据 itpCardType 判断是否为计次票：02=计次票
                        if ("02".equals(userInfo.getItpCardType())) {
                            record.setCountingFlag("Y");
                        } else {
                            record.setCountingFlag("N");
                        }
                    }
                } catch (Exception e) {
                    log.warn("IF8A-34 查询companionFlag/countingFlag失败, cardId={}", record.getCardNum(), e);
                }
            }

            // 查询日票票号和计次票扣减次数（跨服务调用 daily-ticket-server）
            try {
                com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO dailyTicketReq = new com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO();
                dailyTicketReq.setOrderNo(request.getOrderNo());
                com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult dailyTicketResult = dailyTicketClient.queryDailyTicketInfo(dailyTicketReq);
                if (dailyTicketResult != null && "0000".equals(dailyTicketResult.getRetCode())) {
                    if (StringUtils.hasText(dailyTicketResult.getTicketCode())) {
                        record.setTicketCode(dailyTicketResult.getTicketCode());
                    }
                    if (dailyTicketResult.getActualTimes() != null) {
                        record.setCountingTimes(dailyTicketResult.getActualTimes());
                    }
                }
            } catch (Exception e) {
                log.warn("IF8A-34 查询日票信息失败, orderNo={}", request.getOrderNo(), e);
            }

            // 填充应收商户和实收商户（根据变更日期判断使用城交或新商户）
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

    @Override
    public AlipayTripFindTravelListRespDTO alipayTripFindTravelList(AlipayTripFindTravelListReqDTO request) {
        AlipayTripFindTravelListRespDTO response = new AlipayTripFindTravelListRespDTO();
        try {
            int page = 0;
            if (StringUtils.hasText(request.getPage())) {
                try {
                    page = Integer.parseInt(request.getPage());
                    if (page < 0) page = 0;
                } catch (NumberFormatException e) {
                    log.warn("page 参数格式错误: {}", request.getPage());
                }
            }
            int size = 10;
            if (StringUtils.hasText(request.getSize())) {
                try {
                    size = Integer.parseInt(request.getSize());
                    if (size <= 0) size = 10;
                } catch (NumberFormatException e) {
                    log.warn("size 参数格式错误: {}", request.getSize());
                }
            }
            int offset = page * size;

            String startDate = request.getStartDate();
            String endDate = request.getEndDate();
            if (startDate != null && !startDate.isEmpty()) {
                request.setStartDate(startDate.replace("-", ""));
            }
            if (endDate != null && !endDate.isEmpty()) {
                request.setEndDate(endDate.replace("-", ""));
            }

            log.info("支付宝出行-查询乘车记录列表,请求参数: thirdUserId={}, startDate={}, endDate={}, debitRequestResult={}, page={}, size={}, offset={}",
                    request.getThirdUserId(), request.getStartDate(), request.getEndDate(), request.getDebitRequestResult(), page, size, offset);

            List<AlipayTripTravelRecordDTO> records = qrCodeTxnDetailMapper.selectAlipayTravelList(
                    request.getThirdUserId(), startDate, endDate, request.getDebitRequestResult(), offset, size);
            log.info("支付宝出行-查询乘车记录列表,selectAlipayTravelList执行完成,结果条数={}", records != null ? records.size() : 0);

            int total = qrCodeTxnDetailMapper.countAlipayTravelList(
                    request.getThirdUserId(), startDate, endDate, request.getDebitRequestResult());
            log.info("支付宝出行-查询乘车记录列表,countAlipayTravelList执行完成,total={}", total);

            int totalPage = (int) Math.ceil((double) total / size);

            // 站点编码转中文站名
            java.util.Set<String> stationCodes = new java.util.LinkedHashSet<>();
            if (records != null) {
                for (AlipayTripTravelRecordDTO record : records) {
                    if (record.getEntryStationName() != null) {
                        stationCodes.add(record.getEntryStationName());
                    }
                    if (record.getExitStationName() != null) {
                        stationCodes.add(record.getExitStationName());
                    }
                }
            }
            java.util.Map<String, String> stationNameMap = new java.util.HashMap<>();
            for (String code : stationCodes) {
                if (code != null && !code.isEmpty()) {
                    try {
                        com.chinasofti.huateng.model.app.RequestStationNameReqDTO req = new com.chinasofti.huateng.model.app.RequestStationNameReqDTO();
                        req.setStationCode(code);
                        com.chinasofti.huateng.model.app.RequestStationNameResult result = paraClient.requestStationName(req);
                        if (result != null && result.getStationName() != null) {
                            stationNameMap.put(code, result.getStationName());
                        }
                    } catch (Exception e) {
                        log.warn("查询站点中文名失败, stationCode={}", code, e);
                    }
                }
            }

            List<AlipayTripTravelRecordDTO> list = new ArrayList<>();
            if (records != null) {
                for (AlipayTripTravelRecordDTO record : records) {
                    AlipayTripTravelRecordDTO dto = convertToAlipayTripTravelRecordDTO(record);
                    // 进站站点编码转中文
                    if (dto.getEntryStationName() != null) {
                        String cn = stationNameMap.get(dto.getEntryStationName());
                        if (cn != null) {
                            dto.setEntryStationName(cn);
                        }
                    }
                    // 出站站点编码转中文
                    if (dto.getExitStationName() != null) {
                        String cn = stationNameMap.get(dto.getExitStationName());
                        if (cn != null) {
                            dto.setExitStationName(cn);
                        }
                    }
                    list.add(dto);
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
            log.info("支付宝出行-查询乘车记录详情,请求参数: thirdUserId={}, handleDateTime={}, trxType={}, orderNo={}, cardId={}",
                    request.getThirdUserId(), request.getHandleDateTime(), request.getTrxType(), request.getOrderNo(), request.getCardId());

            AlipayTripTravelRecordDTO record;
            String queryType;
            if (StringUtils.hasText(request.getHandleDateTime()) && StringUtils.hasText(request.getTrxType())) {
                queryType = "byUserAndDateTime";
                log.info("支付宝出行-查询乘车记录详情,执行查询: {}, thirdUserId={}, handleDateTime={}, trxType={}",
                        queryType, request.getThirdUserId(), request.getHandleDateTime(), request.getTrxType());
                record = qrCodeTxnDetailMapper.selectAlipayTravelDetailByUserAndDateTime(
                        request.getThirdUserId(),
                        request.getHandleDateTime(),
                        request.getTrxType());
            } else if (StringUtils.hasText(request.getOrderNo())) {
                queryType = "byOrderNo";
                log.info("支付宝出行-查询乘车记录详情,执行查询: {}, orderNo={}", queryType, request.getOrderNo());
                record = qrCodeTxnDetailMapper.selectAlipayTravelDetailByOrderNo(request.getOrderNo());
            } else {
                queryType = "none";
                log.warn("支付宝出行-查询乘车记录详情,无有效查询条件: thirdUserId={}, handleDateTime={}, trxType={}, orderNo={}",
                        request.getThirdUserId(), request.getHandleDateTime(), request.getTrxType(), request.getOrderNo());
                record = null;
            }

            if (record == null) {
                log.warn("支付宝出行-查询乘车记录详情,查询结果为空, queryType={}, thirdUserId={}, handleDateTime={}, trxType={}, orderNo={}",
                        queryType, request.getThirdUserId(), request.getHandleDateTime(), request.getTrxType(), request.getOrderNo());
                response.setRetCode(TicketErrorCodeEnum.NO_DATA.getCode());
                response.setRetMsg(TicketErrorCodeEnum.NO_DATA.getMsg());
                return response;
            }

            log.info("支付宝出行-查询乘车记录详情,查询成功, queryType={}, record={}", queryType, record);
            response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
            AlipayTripTravelRecordDTO dto = convertToAlipayTripTravelRecordDTO(record);

            // 进站站点编码转中文
            if (dto.getEntryStationName() != null) {
                try {
                    com.chinasofti.huateng.model.app.RequestStationNameReqDTO req = new com.chinasofti.huateng.model.app.RequestStationNameReqDTO();
                    req.setStationCode(dto.getEntryStationName());
                    com.chinasofti.huateng.model.app.RequestStationNameResult result = paraClient.requestStationName(req);
                    if (result != null && result.getStationName() != null) {
                        dto.setEntryStationName(result.getStationName());
                    }
                } catch (Exception e) {
                    log.warn("查询进站中文名失败, stationCode={}", dto.getEntryStationName(), e);
                }
            }

            // 出站站点编码转中文
            if (dto.getExitStationName() != null) {
                try {
                    com.chinasofti.huateng.model.app.RequestStationNameReqDTO req = new com.chinasofti.huateng.model.app.RequestStationNameReqDTO();
                    req.setStationCode(dto.getExitStationName());
                    com.chinasofti.huateng.model.app.RequestStationNameResult result = paraClient.requestStationName(req);
                    if (result != null && result.getStationName() != null) {
                        dto.setExitStationName(result.getStationName());
                    }
                } catch (Exception e) {
                    log.warn("查询出站中文名失败, stationCode={}", dto.getExitStationName(), e);
                }
            }

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

            log.info("支付宝出行-查询乘车记录详情,响应结果: entryStationName={}, entryDate={}, exitStationName={}, exitDate={}, orderExpType={}",
                    dto.getEntryStationName(), dto.getEntryDate(), dto.getExitStationName(), dto.getExitDate(), dto.getOrderExpType());
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
        dto.setOrderExpType(record.getOrderExpType() != null && !record.getOrderExpType().trim().isEmpty() ? record.getOrderExpType() : "0");
        dto.setTradeOrderNo(record.getTradeOrderNo());
        dto.setPayTradeOrderNo(record.getPayTradeOrderNo());
        dto.setPayOrderNoDate(record.getPayOrderNoDate());
        dto.setPayChannelCode(record.getPayChannelCode());
        dto.setDebitRequestResult(record.getDebitRequestResult());
        dto.setDiscountFee(record.getDiscountFee());
        dto.setDiscountInfo("[]");
        dto.setCompanionFlag("");
        dto.setCardNum(record.getCardNum());
        dto.setTicketCode(record.getTicketCode());
        dto.setCountingTimes(null);
        dto.setCountingFlag("");
        return dto;
    }

    /**
     * 根据变更日期判断使用城交商户或新商户。
     * <p>
     * 规则：
     * - 订单时间 < 变更日期 → 城交商户
     * - 订单时间 >= 变更日期 → 新商户
     * - 单边账(1,2,3,4)或补站订单 + 乘车日期 < 变更日期 → 城交商户
     * </p>
     */
    private void resolveMerchantParties(com.chinasofti.huateng.model.app.TransRecordDTO record) {
        if (!StringUtils.hasText(merchantChangeDate)) {
            // 未配置变更日期，优先使用新商户
            if (StringUtils.hasText(newAttributableParty)) {
                record.setAttributableParty(newAttributableParty);
            }
            if (StringUtils.hasText(newReceivingParty)) {
                record.setReceivingParty(newReceivingParty);
            }
            return;
        }

        // 取乘车日期：优先用进站时间，其次用出站时间
        String rideDate = null;
        if (StringUtils.hasText(record.getEntryDate()) && record.getEntryDate().length() >= 8) {
            rideDate = record.getEntryDate().substring(0, 8);
        } else if (StringUtils.hasText(record.getExitDate()) && record.getExitDate().length() >= 8) {
            rideDate = record.getExitDate().substring(0, 8);
        }

        boolean useOldMerchant = false;
        if (rideDate != null && rideDate.compareTo(merchantChangeDate) < 0) {
            // 乘车日期早于变更日期
            useOldMerchant = true;
        }

        // 单边账(1,2,3,4)或补站订单，乘车日期早于变更日期 → 城交
        // 非单边/补站订单，订单时间早于变更日期 → 城交（上面已判断）
        // 其他情况 → 新商户

        if (useOldMerchant) {
            if (StringUtils.hasText(oldAttributableParty)) {
                record.setAttributableParty(oldAttributableParty);
            }
            if (StringUtils.hasText(oldReceivingParty)) {
                record.setReceivingParty(oldReceivingParty);
            }
        } else {
            if (StringUtils.hasText(newAttributableParty)) {
                record.setAttributableParty(newAttributableParty);
            }
            if (StringUtils.hasText(newReceivingParty)) {
                record.setReceivingParty(newReceivingParty);
            }
        }
    }
}
