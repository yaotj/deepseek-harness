package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.model.page.GateTxnPayRefundRequest;
import com.chinasofti.huateng.gatetxnpay.service.GateTxnPayService;
import com.chinasofti.huateng.gatetxnpay.writer.GateTxnPayWriter;
import com.chinasofti.huateng.model.app.GatePayRequestDTO;
import com.chinasofti.huateng.model.app.RequestPayResult;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayFailedOrderRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayListDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class GateTxnPayServiceImpl implements GateTxnPayService {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayServiceImpl.class);
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final GateTxnPayMapper gateTxnPayMapper;
    private final GateTxnPayWriter gateTxnPayWriter;
    private final PaySignClient paySignClient;
    private final Executor paySignAsyncExecutor;
    private final String payScene;
    private final String industryType;
    private final String subject;
    private final String body;
    private final Long orderTimeoutSeconds;

    public GateTxnPayServiceImpl(
            GateTxnPayMapper gateTxnPayMapper,
            GateTxnPayWriter gateTxnPayWriter,
            PaySignClient paySignClient,
            @Value("${gate.pay.scene:AGM_GATE}") String payScene,
            @Value("${gate.pay.industry-type:1}") String industryType,
            @Value("${gate.pay.subject:地铁乘车扣费}") String subject,
            @Value("${gate.pay.body:地铁乘车费用}") String body,
            @Value("${gate.pay.order-timeout-seconds:60}") Long orderTimeoutSeconds,
            @org.springframework.beans.factory.annotation.Qualifier("paySignAsyncExecutor") Executor paySignAsyncExecutor) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.gateTxnPayWriter = gateTxnPayWriter;
        this.paySignClient = paySignClient;
        this.payScene = payScene;
        this.industryType = industryType;
        this.subject = subject;
        this.body = body;
        this.orderTimeoutSeconds = orderTimeoutSeconds;
        this.paySignAsyncExecutor = paySignAsyncExecutor;
    }

    @Override
    public GateTxnPayRespDTO requestPay(GateTxnPayReqDTO request) {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        String validMsg = validate(request);
        if (validMsg != null) {
            response.setRetCode("8001");
            response.setRetMsg(validMsg);
            return response;
        }

        GateTxnPay order = buildOrder(request);

        if (isDailyTicket(order) || order.getTotalAmount() <= 0) {
            String reason = isDailyTicket(order) ? "日票交易默认支付成功" : "免扣费交易默认支付成功";
            GateTxnPay savedOrder = gateTxnPayWriter.insertOrderAndUpdateStatus(order, "SUCCESS", reason);
            response.setRetCode("0000");
            response.setRetMsg("成功");
            response.setOrderNo(savedOrder.getOrderNo());
            response.setPayStatus("SUCCESS");
            log.info("过闸交易已入库并默认支付成功，不调用pay-sign, orderNo={}, cardId={}, cardType={}, totalAmount={}",
                    savedOrder.getOrderNo(), savedOrder.getCardId(), savedOrder.getCardType(), savedOrder.getTotalAmount());
            return response;
        }

        GateTxnPay savedOrder = gateTxnPayWriter.insertOrder(order);
        if (savedOrder == null || savedOrder.getOrderNo() == null) {
            response.setRetCode("8002");
            response.setRetMsg("订单入库失败");
            return response;
        }

        // 幂等保护：如果订单已存在且已触发过支付请求，不再重复触发异步调用
        // insertOrder 在唯一索引冲突时会返回已有订单，需判断是否已处理过
        if (!savedOrder.getOrderNo().equals(order.getOrderNo())) {
            log.warn("并发重复请求，订单已存在，跳过重复触发pay-sign, orderNo={}, existingStatus={}",
                    savedOrder.getOrderNo(), savedOrder.getDebitStatus());
            // 返回已有订单号，状态以实际为准
            response.setRetCode("0000");
            response.setRetMsg("成功");
            response.setOrderNo(savedOrder.getOrderNo());
            response.setPayStatus(savedOrder.getDebitStatus());
            return response;
        }

        // 异步调用 pay-sign，成功后回调更新状态
        paySignAsyncExecutor.execute(() -> asyncPaySign(savedOrder, request));

        response.setRetCode("0000");
        response.setRetMsg("成功");
        response.setOrderNo(savedOrder.getOrderNo());
        response.setPayStatus("PROCESSING");
        return response;
    }

    private void asyncPaySign(GateTxnPay order, GateTxnPayReqDTO request) {
        try {
            RequestPayResult payResult = requestPaySign(order, request);
            String nextStatus = isSuccess(payResult) ? "PROCESSING" : "RETRY";
            String reason = payResult == null ? "调用pay-sign失败" : payResult.getRetMsg();
            gateTxnPayWriter.updateOrderStatus(order, nextStatus, reason);
        } catch (Exception e) {
            log.error("异步调用pay-sign异常, orderNo={}", order.getOrderNo(), e);
            try {
                gateTxnPayWriter.updateOrderStatus(order, "RETRY", "异步调用pay-sign异常: " + e.getMessage());
            } catch (RuntimeException ex) {
                log.error("异步更新订单状态失败, orderNo={}", order.getOrderNo(), ex);
                throw ex;
            }
        }
    }

    @Override
    public GateTxnPayRespDTO retryPay(String orderNo) {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        if (!StringUtils.hasText(orderNo)) {
            response.setRetCode("8001");
            response.setRetMsg("orderNo不能为空");
            return response;
        }

        GateTxnPay order = gateTxnPayMapper.selectByOrderNo(orderNo.trim());
        if (order == null) {
            response.setRetCode("8001");
            response.setRetMsg("未找到对应的过闸扣费订单");
            return response;
        }
        if (isDailyTicket(order)) {
            response.setRetCode("8001");
            response.setRetMsg("日票订单不支持重试支付");
            return response;
        }
        if (!"RETRY".equals(order.getDebitStatus()) && !"INIT".equals(order.getDebitStatus())) {
            response.setRetCode("8001");
            response.setRetMsg("当前扣费状态不允许重试支付：" + order.getDebitStatus());
            return response;
        }

        RequestPayResult payResult = requestPaySign(order, null);
        String nextStatus = isSuccess(payResult) ? "PROCESSING" : "RETRY";
        gateTxnPayWriter.updateOrderStatus(order, nextStatus, payResult == null ? "重试调用pay-sign失败" : payResult.getRetMsg());

        response.setRetCode("0000");
        response.setRetMsg("成功");
        response.setOrderNo(order.getOrderNo());
        response.setPayStatus(nextStatus);
        return response;
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

        Map<String, Object> page = new LinkedHashMap<>();
        page.put("list", orders);
        page.put("total", gateTxnPayMapper.countOperationPage(
                normalizedOrderNo, normalizedCardId, normalizedThirdUserId, trimToNull(signChannelCode), trimToNull(cardType),
                trimToNull(debitStatus), normalizedStartDate, normalizedEndDate));
        return ResultMapper.ok(page);
    }

    @Override
    public ResultVO<RequestRefundResult> requestRefund(String orderNo, GateTxnPayRefundRequest request) {
        if (!StringUtils.hasText(orderNo)) {
            return ResultMapper.illegalParams("orderNo不能为空");
        }
        if (request == null || request.getRefundAmount() == null) {
            return ResultMapper.illegalParams("refundAmount不能为空，单位为分");
        }

        GateTxnPay order = gateTxnPayMapper.selectByOrderNo(orderNo.trim());
        if (order == null) {
            return ResultMapper.error("未找到对应的过闸扣费订单");
        }
        if (isDailyTicket(order)) {
            return ResultMapper.error("日票订单不支持通过过闸扣费退款入口处理");
        }
        if (!"SUCCESS".equals(order.getDebitStatus()) && !"PROCESSING".equals(order.getDebitStatus())) {
            return ResultMapper.error("当前扣费状态不允许退款：" + order.getDebitStatus());
        }
        if (order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            return ResultMapper.error("订单扣费金额无效，不能退款");
        }
        int refundAmount = request.getRefundAmount();
        if (refundAmount <= 0 || refundAmount > order.getTotalAmount()) {
            return ResultMapper.illegalParams("退款金额须大于0且不超过订单扣费金额");
        }

        RequestRefundReqDTO refundRequest = new RequestRefundReqDTO();
        refundRequest.setOrderNo(order.getOrderNo());
        refundRequest.setRefundAmount(refundAmount);
        String refundReason = trimToNull(request.getRefundReason());
        refundRequest.setRefundReason(refundReason == null ? "运营人工退款" : refundReason);
        RequestRefundResult refundResult = paySignClient.requestRefund(refundRequest);
        if (refundResult == null) {
            return ResultMapper.error("支付退款服务未返回结果");
        }
        if (!Boolean.TRUE.equals(refundResult.getSuccess())) {
            return ResultMapper.error(StringUtils.hasText(refundResult.getRetMsg()) ? refundResult.getRetMsg() : "支付退款申请失败");
        }
        return ResultMapper.ok(refundResult);
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
            response.setRetCode("8001");
            response.setRetMsg("未找到关联的过闸扣费订单");
        } else {
            response.setRetCode("0000");
            response.setOrderNo(order.getOrderNo());
        }
        return response;
    }

    /**
     * 只处理出站类交易扣费，进站交易只更新票卡状态，不进入扣款链路。
     */
    private String validate(GateTxnPayReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!"02".equals(request.getTrxType()) && !"03".equals(request.getTrxType())) {
            return "非出站扣费交易";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getHandleDateTime()) || request.getHandleDateTime().length() < 8) {
            return "handleDateTime不能为空且长度不能小于8";
        }
        return null;
    }

    /**
     * 根据闸机交易报文生成本地过闸扣费订单。
     *
     * <p>itpUserId 入库前转换为十进制 thirdUserId；进出站信息保持简单字段，
     * 后续查询或退款都通过 orderNo 关联支付明细。</p>
     */
    private GateTxnPay buildOrder(GateTxnPayReqDTO request) {
        GateTxnPay order = new GateTxnPay();
        order.setOrderNo(buildOrderNo(request));
        order.setDebitStatus("INIT");
        order.setThirdUserId(request.getItpUserId());
        order.setCardId(request.getCardId());
        order.setCardType(request.getCardType());
        order.setDeviceId(request.getDeviceId());
        order.setTrxType(request.getTrxType());
        order.setTicketTransSeq(request.getTicketTransSeq());
        order.setInStation(request.getLastHandleStationCode());
        order.setInTime(request.getLastHandleDateTime());
        order.setOutStation(request.getHandleStationCode());
        order.setOutTime(request.getHandleDateTime());
        order.setTxnDate(request.getHandleDateTime().substring(0, 8));
        order.setTrxAmount(parseAmount(request.getTrxAmount()));
        order.setOvertimeAmount(parseAmount(request.getOvertimeAmount()));
        order.setTotalAmount(order.getTrxAmount() + order.getOvertimeAmount());
        order.setIssueChannelCode(request.getIssueChannelCode());
        order.setSignChannelCode(request.getSignChannelCode());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        // 新增字段：由 ticket-server 透传
        order.setTicketStatus(request.getTicketStatus());
        order.setOrderExpType(request.getOrderExpType());
        order.setEntryStationName(request.getEntryStationName());
        order.setExitStationName(request.getExitStationName());
        order.setCompanionFlag(request.getCompanionFlag());
        order.setOfflineFlag(request.getOfflineFlag());
        order.setTicketCode(request.getTicketCode());
        order.setCountingTimes(request.getCountingTimes());
        order.setCountingFlag(request.getCountingFlag());
        order.setAttributableParty(request.getAttributableParty());
        order.setReceivingParty(request.getReceivingParty());
        // payChannelCode/discountFee/discountInfo 仅用于 pay-sign，不持久化到 GATE_TXN_PAY
        log.info("IF1A-01 构建 GateTxnPay 订单快照, cardId={}, orderNo={}, ticketStatus={}, orderExpType={}, offlineFlag={}, companionFlag={}, ticketCode={}, countingTimes={}, countingFlag={}, attributableParty={}, receivingParty={}, entryStationName={}, exitStationName={}",
                request.getCardId(), order.getOrderNo(), order.getTicketStatus(),
                order.getOrderExpType(), order.getOfflineFlag(),
                order.getCompanionFlag(), order.getTicketCode(), order.getCountingTimes(),
                order.getCountingFlag(), order.getAttributableParty(), order.getReceivingParty(),
                order.getEntryStationName(), order.getExitStationName());
        return order;
    }

    /**
     * 调用 pay-sign 发起免密扣款。
     *
     * <p>这里不决定签约渠道和签约流水号，pay-sign 会根据 thirdUserId/cardId/cardType
     * 去 account-server 查询 USER_ITP_REG_INFO 中的默认支付通道和 REQ_CONTRACT_NO。</p>
     */
    private RequestPayResult requestPaySign(GateTxnPay order, GateTxnPayReqDTO request) {
        GatePayRequestDTO payRequest = new GatePayRequestDTO();
        payRequest.setOrderNo(order.getOrderNo());
        payRequest.setScene(payScene);
        payRequest.setAmount(order.getTotalAmount());
        payRequest.setIndustryType(industryType);
        payRequest.setSubject(subject);
        payRequest.setBody(body);
        payRequest.setThirdUserId(order.getThirdUserId());
        payRequest.setCardId(order.getCardId());
        payRequest.setCardType(order.getCardType());
        payRequest.setOrderTimeOut(orderTimeoutSeconds);
        payRequest.setIndustryDetail(buildIndustryDetail(order));
        // 支付相关字段仅用于 pay-sign，不持久化到 GATE_TXN_PAY；重试时可能为 null
        if (request != null) {
            payRequest.setPaymentVendor(request.getPaymentVendor());
            payRequest.setRequestSignSeq(request.getRequestSignSeq());
            payRequest.setDiscountFee(request.getDiscountFee());
            payRequest.setDiscountInfo(request.getDiscountInfo());
        }
        log.info("调用pay-sign请求支付, 入参 orderNo={}, cardId={}, amount={}, paymentVendor={}, requestSignSeq={}, discountFee={}, discountInfo={}",
                order.getOrderNo(), order.getCardId(), order.getTotalAmount(),
                payRequest.getPaymentVendor(), payRequest.getRequestSignSeq(),
                payRequest.getDiscountFee(), payRequest.getDiscountInfo());
        RequestPayResult response = paySignClient.requestPay(payRequest);
        log.info("调用pay-sign请求支付, 返回={}", JSON.toJSONString(response));
        return response;
    }

    @Override
    public List<GateTxnPayListDTO> selectTransList(String thirdUserId, List<String> cardIdList, String cardType,
                                                    String startDate, String endDate, String ticketCode,
                                                    Integer offset, Integer limit) {
        List<GateTxnPay> records = gateTxnPayMapper.selectTransList(thirdUserId, cardIdList, cardType,
                startDate, endDate, ticketCode, offset, limit);
        return records.stream().map(this::toListDTO).toList();
    }

    @Override
    public int countTransList(String thirdUserId, List<String> cardIdList, String cardType,
                              String startDate, String endDate, String ticketCode) {
        return gateTxnPayMapper.countTransList(thirdUserId, cardIdList, cardType, startDate, endDate, ticketCode);
    }

    // ==================== IF8A-34 APP 订单详情 ====================

    @Override
    public GateTxnPayListDTO selectByOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            return null;
        }
        GateTxnPay record = gateTxnPayMapper.selectByOrderNo(orderNo.trim());
        return record != null ? toListDTO(record) : null;
    }

    // ==================== 解约扣费失败订单查询 ====================

    @Override
    public GateTxnPayFailedOrderRespDTO hasFailedOrder(String thirdUserId, String paymentVendor, LocalDateTime requestTime) {
        GateTxnPayFailedOrderRespDTO response = new GateTxnPayFailedOrderRespDTO();
        if (!StringUtils.hasText(thirdUserId) || !StringUtils.hasText(paymentVendor) || requestTime == null) {
            log.warn("查询扣费失败订单参数缺失, thirdUserId={}, paymentVendor={}, requestTime={}", thirdUserId, paymentVendor, requestTime);
            response.setHasFailedOrder(false);
            return response;
        }
        int count = gateTxnPayMapper.countFailedOrder(thirdUserId.trim(), paymentVendor.trim(), requestTime);
        response.setHasFailedOrder(count > 0);
        log.info("查询扣费失败订单完成, thirdUserId={}, paymentVendor={}, requestTime={}, count={}",
                thirdUserId, paymentVendor, requestTime, count);
        return response;
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
        dto.setTicketStatus(record.getTicketStatus());
        dto.setEntryStationName(record.getEntryStationName());
        dto.setExitStationName(record.getExitStationName());
        dto.setOrderExpType(record.getOrderExpType());
        dto.setCompanionFlag(record.getCompanionFlag());
        dto.setOfflineFlag(record.getOfflineFlag());
        dto.setTicketCode(record.getTicketCode());
        dto.setCountingTimes(record.getCountingTimes());
        dto.setCountingFlag(record.getCountingFlag());
        dto.setAttributableParty(record.getAttributableParty());
        dto.setReceivingParty(record.getReceivingParty());
        dto.setRemark(record.getRemark());
        dto.setCreateTime(record.getCreateTime());
        dto.setUpdateTime(record.getUpdateTime());
        return dto;
    }

    /**
     * 行业明细先存完整订单快照，便于支付中心侧排查交易来源。
     */
    private String buildIndustryDetail(GateTxnPay order) {
        return JSON.toJSONString(order);
    }

    private boolean isSuccess(RequestPayResult result) {
        return result != null && "0000".equals(result.getRetCode());
    }

    private boolean isDailyTicket(GateTxnPay order) {
        return CardTypeCodeEnum.isDailyTicket(order.getCardType());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean hasSearchScope(String orderNo, String cardId, String thirdUserId, String startDate, String endDate) {
        return orderNo != null || cardId != null || thirdUserId != null || (startDate != null && endDate != null);
    }

    private String buildOrderNo(GateTxnPayReqDTO request) {
        String time = LocalDateTime.now().format(ORDER_TIME_FORMATTER);
        String suffix = request.getCardId();
        if (suffix != null && suffix.length() > 6) {
            suffix = suffix.substring(suffix.length() - 6);
        }
        return "GT" + time + (suffix == null ? "" : suffix);
    }

    /**
     * 金额字段按分保存，空值按 0 处理。
     */
    private int parseAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }

}
