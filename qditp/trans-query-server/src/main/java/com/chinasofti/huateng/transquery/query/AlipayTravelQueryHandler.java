package com.chinasofti.huateng.transquery.query;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespVO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelListRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelRecordDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 支付宝出行行程查询（乘车记录列表 + 乘车记录详情）。
 *
 * <p>本类沿用 {@link FepAppErrorCodeEnum} 而非本模块的 {@code TransQueryErrorCodeEnum}：
 * 这两条是支付宝渠道的对外契约，retCode 取值不允许因为换了宿主服务而改变。
 *
 * <p>列表与详情合在同一个类里，是因为 {@code buildDetailRequest} 与
 * {@code mapPayStatusToDebitResult} 两个私有方法被两条链路共用——拆成两个 Handler 等于留两份逐字副本。
 */
@Component
public class AlipayTravelQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(AlipayTravelQueryHandler.class);

    /** 进出站明细单次查询超时，与迁入前的实现保持一致。 */
    private static final long DETAIL_TIMEOUT_MILLIS = 500L;

    private final AlipayPaySignClient alipayPaySignClient;
    private final TicketClient ticketClient;
    private final GateTxnPayClient gateTxnPayClient;

    /** 构造注入（不是本模块其余 Handler 的字段注入写法）：单测要能直接 new 出来并塞 mock。 */
    @Autowired
    public AlipayTravelQueryHandler(AlipayPaySignClient alipayPaySignClient, TicketClient ticketClient,
                                    GateTxnPayClient gateTxnPayClient) {
        this.alipayPaySignClient = alipayPaySignClient;
        this.ticketClient = ticketClient;
        this.gateTxnPayClient = gateTxnPayClient;
    }

    /** 查询乘车记录列表。 */
    public AlipayTripFindTravelListRespDTO findTravelList(AlipayTripFindTravelListReqDTO request) {
        log.info("支付宝出行-查询乘车记录列表,请求参数：{}", JSON.toJSONString(request));
        AlipayTripFindTravelListRespDTO response = new AlipayTripFindTravelListRespDTO();
        if (request == null || !StringUtils.hasText(request.getThirdUserId())) {
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("无效的参数");
            log.warn("支付宝出行-查询乘车记录列表,参数校验失败");
            return response;
        }
        try {
            int pageNum = 0;
            if (StringUtils.hasText(request.getPage())) {
                try {
                    pageNum = Integer.parseInt(request.getPage());
                    if (pageNum < 0) {
                        pageNum = 0;
                    }
                } catch (NumberFormatException e) {
                    log.warn("page 参数格式错误: {}", request.getPage());
                }
            }
            int pageSize = 10;
            if (StringUtils.hasText(request.getSize())) {
                try {
                    pageSize = Integer.parseInt(request.getSize());
                    if (pageSize <= 0) {
                        pageSize = 10;
                    }
                } catch (NumberFormatException e) {
                    log.warn("size 参数格式错误: {}", request.getSize());
                }
            }

            Map<String, Object> payLogResult = alipayPaySignClient.selectAlipayPayLogListForTravel(
                    request.getThirdUserId(),
                    request.getStartDate(),
                    request.getEndDate(),
                    pageNum,
                    pageSize,
                    request.getDebitRequestResult(),
                    request.getInvoice()
            );
            if (payLogResult == null) {
                response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                response.setRetMsg("无有效的乘车记录");
                log.error("支付宝出行-查询乘车记录列表,alipay-pay-sign-server 返回空响应");
                return response;
            }
            log.info("支付宝出行-查询乘车记录列表,alipay-pay-sign-server 响应结果：{}", JSON.toJSONString(payLogResult));
            log.info("支付宝出行-查询乘车记录列表,alipay-pay-sign-server 原始响应 list={}", JSON.toJSONString(payLogResult.get("list")));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> payLogList = (List<Map<String, Object>>) payLogResult.get("list");
            int total = payLogResult.get("total") != null ? ((Number) payLogResult.get("total")).intValue() : 0;
            int totalPage = payLogResult.get("totalPage") != null ? ((Number) payLogResult.get("totalPage")).intValue() : 0;

            List<AlipayTripTravelRecordDTO> records = new ArrayList<>();
            if (payLogList != null) {
                for (Map<String, Object> payLog : payLogList) {
                    String entryId = (String) payLog.get("entryId");
                    String exitId = (String) payLog.get("exitId");
                    if (!StringUtils.hasText(entryId) || !StringUtils.hasText(exitId)) {
                        log.warn("支付宝出行-查询乘车记录列表,跳过异常数据,缺少进出站ID, orderNo={}, entryId={}, exitId={}",
                                payLog.get("orderNo"), entryId, exitId);
                        continue;
                    }
                    try {
                        records.add(buildTravelRecord(request.getThirdUserId(), payLog));
                    } catch (Exception e) {
                        log.error("支付宝出行-查询乘车记录列表,组装单条记录异常, payLog={}", JSON.toJSONString(payLog), e);
                    }
                }
            }

            response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg("成功");
            response.setPageNumber(pageNum);
            response.setPageSize(pageSize);
            response.setTotalPage(totalPage);
            response.setTotalCount(total);
            response.setTicketTransRecord(records);
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录列表 异常", e);
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("系统内部错误");
        }
        log.info("支付宝出行-查询乘车记录列表,响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    /** 查询乘车记录详情。 */
    public AlipayTripFindTravelDetailRespVO findTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        log.info("支付宝出行-查询乘车记录详情,请求参数：{}", JSON.toJSONString(request));
        AlipayTripFindTravelDetailRespDTO detailResp = new AlipayTripFindTravelDetailRespDTO();
        if (request == null || !StringUtils.hasText(request.getThirdUserId()) || !StringUtils.hasText(request.getOrderNo())) {
            detailResp.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            detailResp.setRetMsg("无效的参数");
            log.warn("支付宝出行-查询乘车记录详情,参数校验失败");
            return wrap(detailResp);
        }
        try {
            GateTxnPayListDTO order = gateTxnPayClient.queryByOrderNo(request.getOrderNo());
            if (order == null) {
                detailResp.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                detailResp.setRetMsg("支付订单不存在");
                log.warn("支付宝出行-查询乘车记录详情,扣费订单不存在, orderNo={}", request.getOrderNo());
                return wrap(detailResp);
            }

            JSONObject industryDetail = null;
            if (StringUtils.hasText(order.getIndustryDetail())) {
                try {
                    industryDetail = JSONObject.parseObject(order.getIndustryDetail());
                } catch (Exception e) {
                    log.warn("支付宝出行-查询乘车记录详情,行业明细解析失败, orderNo={}, industryDetail={}",
                            request.getOrderNo(), order.getIndustryDetail(), e);
                }
            } else {
                log.warn("支付宝出行-查询乘车记录详情,扣费订单无行业明细, orderNo={}", request.getOrderNo());
            }
            final String entryId = industryDetail == null ? null : industryDetail.getString("entryId");
            final String exitId = industryDetail == null ? null : industryDetail.getString("exitId");
            log.info("支付宝出行-查询乘车记录详情,扣费订单查询成功, orderNo={}, entryId={}, exitId={}",
                    request.getOrderNo(), entryId, exitId);

            CompletableFuture<AlipayTripFindTravelDetailRespDTO> entryFuture = null;
            CompletableFuture<AlipayTripFindTravelDetailRespDTO> exitFuture = null;

            if (StringUtils.hasText(entryId)) {
                AlipayTripFindTravelDetailReqDTO entryReq = buildDetailRequest(request.getThirdUserId(), entryId, order.getCardId());
                entryFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return ticketClient.alipayTripFindTravelDetail(entryReq);
                    } catch (Exception e) {
                        log.warn("支付宝出行-查询乘车记录详情,进站交易查询异常, entryId={}", entryId, e);
                        return null;
                    }
                });
            }

            if (StringUtils.hasText(exitId)) {
                AlipayTripFindTravelDetailReqDTO exitReq = buildDetailRequest(request.getThirdUserId(), exitId, order.getCardId());
                exitFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return ticketClient.alipayTripFindTravelDetail(exitReq);
                    } catch (Exception e) {
                        log.warn("支付宝出行-查询乘车记录详情,出站交易查询异常, exitId={}", exitId, e);
                        return null;
                    }
                });
            }

            AlipayTripFindTravelDetailRespDTO entryDetail = null;
            AlipayTripFindTravelDetailRespDTO exitDetail = null;

            try {
                if (entryFuture != null) {
                    entryDetail = entryFuture.get(DETAIL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.warn("支付宝出行-查询乘车记录详情,进站交易查询超时或异常, entryId={}", entryId, e);
            }

            try {
                if (exitFuture != null) {
                    exitDetail = exitFuture.get(DETAIL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                log.warn("支付宝出行-查询乘车记录详情,出站交易查询超时或异常, exitId={}", exitId, e);
            }

            if (entryDetail != null) {
                log.info("支付宝出行-查询乘车记录详情,进站交易查询结果, entryId={}, response={}", entryId, JSON.toJSONString(entryDetail));
            }
            if (exitDetail != null) {
                log.info("支付宝出行-查询乘车记录详情,出站交易查询结果, exitId={}, response={}", exitId, JSON.toJSONString(exitDetail));
            }

            boolean entrySuccess = isSuccess(entryDetail);
            boolean exitSuccess = isSuccess(exitDetail);

            if (entrySuccess) {
                detailResp.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
                detailResp.setRetMsg("成功");
                detailResp.setEntryStationName(entryDetail.getEntryStationName());
                detailResp.setEntryDate(entryDetail.getEntryDate());
            }

            if (exitSuccess) {
                // 出站查询返回的 response 中，entryStationName/entryDate 实际对应本次出站站点和时间；
                detailResp.setExitStationName(exitDetail.getEntryStationName());
                detailResp.setExitDate(exitDetail.getEntryDate());
                detailResp.setPayAmount(exitDetail.getPayAmount());
                detailResp.setTotalAmount(exitDetail.getTotalAmount());
                if (StringUtils.hasText(exitDetail.getOrderExpType())) {
                    detailResp.setOrderExpType(exitDetail.getOrderExpType());
                } else {
                    detailResp.setOrderExpType("0");
                }
            }

            detailResp.setTradeOrderNo(order.getOrderNo());
            // payTradeOrderNo（支付宝渠道流水号）与 invoice 在 GATE_TXN_PAY 里没有对应列：
            detailResp.setPayTradeOrderNo(null);
            detailResp.setInvoice(null);
            detailResp.setDebitRequestResult(mapPayStatusToDebitResult(order.getDebitStatus()));
            detailResp.setCardNum(order.getCardId());
            detailResp.setPayOrderNoDate(order.getOutTime());
            detailResp.setPayChannelCode("07");

            if (exitSuccess) {
                detailResp.setDiscountFee(exitDetail.getDiscountFee());
                detailResp.setDiscountInfo(null);
                detailResp.setCompanionFlag(exitDetail.getCompanionFlag());
                detailResp.setCountingTimes(exitDetail.getCountingTimes());
                detailResp.setCountingFlag("N");
            }

            if (!entrySuccess && !exitSuccess) {
                detailResp.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
                detailResp.setRetMsg("未查询到行程数据");
            }

            log.info("支付宝出行-查询乘车记录详情,响应结果：{}", JSON.toJSONString(detailResp));
            return wrap(detailResp);
        } catch (Exception e) {
            log.error("支付宝出行-查询乘车记录详情异常, request={}", request, e);
            detailResp.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
            detailResp.setRetMsg("系统内部错误");
            log.info("支付宝出行-查询乘车记录详情,响应结果：{}", JSON.toJSONString(detailResp));
            return wrap(detailResp);
        }
    }

    private boolean isSuccess(AlipayTripFindTravelDetailRespDTO detail) {
        return detail != null && FepAppErrorCodeEnum.SUCCESS.getCode().equals(detail.getRetCode());
    }

    private AlipayTripFindTravelDetailRespVO wrap(AlipayTripFindTravelDetailRespDTO data) {
        AlipayTripFindTravelDetailRespVO vo = new AlipayTripFindTravelDetailRespVO();
        vo.setRetCode(data.getRetCode());
        vo.setRetMsg(data.getRetMsg());
        vo.setData(data);
        return vo;
    }

    /** 把 {@code entryId} / {@code exitId}（形如 thirdUserId + handleDateTime + trxType）按长度切片还原成明细查询入参。 */
    private AlipayTripFindTravelDetailReqDTO buildDetailRequest(String thirdUserId, String transactionId, String cardId) {
        AlipayTripFindTravelDetailReqDTO req = new AlipayTripFindTravelDetailReqDTO();
        req.setThirdUserId(thirdUserId);
        log.info("支付宝出行-查询乘车记录详情,buildDetailRequest入参: thirdUserId={}, transactionId={}, cardId={}",
                thirdUserId, transactionId, cardId);
        if (StringUtils.hasText(transactionId) && transactionId.length() > thirdUserId.length() + 2) {
            String remain = transactionId.substring(thirdUserId.length());
            req.setHandleDateTime(remain.substring(0, remain.length() - 2));
            req.setTrxType(remain.substring(remain.length() - 2));
        }
        req.setCardId(cardId);
        log.info("支付宝出行-查询乘车记录详情,buildDetailRequest组装结果: thirdUserId={}, handleDateTime={}, trxType={}, cardId={}",
                req.getThirdUserId(), req.getHandleDateTime(), req.getTrxType(), req.getCardId());
        return req;
    }

    /** 扣款结果只有 {@code "0"}（成功）/ {@code "1"}（其余）两个取值，NEVER 放渠道文案。 */
    private String mapPayStatusToDebitResult(String payStatus) {
        return "SUCCESS".equalsIgnoreCase(payStatus) ? "0" : "1";
    }

    private AlipayTripTravelRecordDTO buildTravelRecord(String thirdUserId, Map<String, Object> payLog) {
        String orderNo = (String) payLog.get("orderNo");
        String entryId = (String) payLog.get("entryId");
        String exitId = (String) payLog.get("exitId");
        String channelOrderNo = (String) payLog.get("channelOrderNo");
        String payStatus = (String) payLog.get("payStatus");
        String payAmount = (String) payLog.get("payAmount");
        String transTime = (String) payLog.get("transTime");
        String cardId = (String) payLog.get("cardId");

        CompletableFuture<AlipayTripFindTravelDetailRespDTO> entryFuture = null;
        CompletableFuture<AlipayTripFindTravelDetailRespDTO> exitFuture = null;

        if (StringUtils.hasText(entryId)) {
            AlipayTripFindTravelDetailReqDTO entryReq = buildDetailRequest(thirdUserId, entryId, cardId);
            entryFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return ticketClient.alipayTripFindTravelDetail(entryReq);
                } catch (Exception e) {
                    log.warn("支付宝出行-查询乘车记录列表,进站交易查询异常, entryId={}", entryId, e);
                    return null;
                }
            });
        }

        if (StringUtils.hasText(exitId)) {
            AlipayTripFindTravelDetailReqDTO exitReq = buildDetailRequest(thirdUserId, exitId, cardId);
            exitFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return ticketClient.alipayTripFindTravelDetail(exitReq);
                } catch (Exception e) {
                    log.warn("支付宝出行-查询乘车记录列表,出站交易查询异常, exitId={}", exitId, e);
                    return null;
                }
            });
        }

        AlipayTripFindTravelDetailRespDTO entryDetail = null;
        AlipayTripFindTravelDetailRespDTO exitDetail = null;

        try {
            if (entryFuture != null) {
                entryDetail = entryFuture.get(DETAIL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.warn("支付宝出行-查询乘车记录列表,进站交易查询超时或异常, entryId={}", entryId, e);
        }

        try {
            if (exitFuture != null) {
                exitDetail = exitFuture.get(DETAIL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            log.warn("支付宝出行-查询乘车记录列表,出站交易查询超时或异常, exitId={}", exitId, e);
        }

        if (entryDetail != null) {
            log.info("支付宝出行-查询乘车记录列表,进站交易查询结果, entryId={}, response={}", entryId, JSON.toJSONString(entryDetail));
        }
        if (exitDetail != null) {
            log.info("支付宝出行-查询乘车记录列表,出站交易查询结果, exitId={}, response={}", exitId, JSON.toJSONString(exitDetail));
        }

        AlipayTripTravelRecordDTO record = new AlipayTripTravelRecordDTO();
        if (isSuccess(entryDetail)) {
            record.setEntryStationName(entryDetail.getEntryStationName());
            record.setEntryDate(entryDetail.getEntryDate());
        }
        if (isSuccess(exitDetail)) {
            // 出站查询返回的 response 中，entryStationName/entryDate 实际对应本次出站站点和时间
            record.setExitStationName(exitDetail.getEntryStationName());
            record.setExitDate(exitDetail.getEntryDate());
            record.setPayAmount(exitDetail.getPayAmount());
            record.setTotalAmount(exitDetail.getTotalAmount());
            if (StringUtils.hasText(exitDetail.getOrderExpType())) {
                record.setOrderExpType(exitDetail.getOrderExpType());
            } else {
                record.setOrderExpType("0");
            }
        }
        record.setTradeOrderNo(orderNo);
        record.setPayTradeOrderNo(channelOrderNo);
        record.setPayOrderNoDate(transTime);
        log.info("支付宝出行-查询乘车记录列表,组装记录, orderNo={}, transTime={}, payOrderNoDate={}", orderNo, transTime, record.getPayOrderNoDate());
        record.setPayChannelCode("07");
        record.setDebitRequestResult(mapPayStatusToDebitResult(payStatus));
        record.setInvoice((String) payLog.get("invoice"));
        record.setCardNum(cardId);
        record.setCountingFlag("N");
        record.setPayAmount(payAmount);

        return record;
    }
}
