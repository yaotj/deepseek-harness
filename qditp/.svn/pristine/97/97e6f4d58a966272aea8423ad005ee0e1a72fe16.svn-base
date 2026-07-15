package com.chinasofti.huateng.fep.dev.service.impl;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.fep.dev.constant.FepDevErrorCodeEnum;
import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusReqDTO;
import com.chinasofti.huateng.fep.dev.model.RequestQrCodeStatusRespDTO;
import com.chinasofti.huateng.fep.dev.service.DevService;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfoDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRequestPayRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 设备请求处理服务默认实现。
 */
@Service
public class DevServiceImpl implements DevService {
    private static final Logger log = LoggerFactory.getLogger(DevServiceImpl.class);
    private static final DateTimeFormatter ORDER_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    @Autowired
    private TicketClient ticketClient;
    @Autowired
    private GateTxnPayClient gateTxnPayClient;
    @Autowired
    private AlipayPaySignClient alipayPaySignClient;
    @Autowired
    private AlipayAccountClient alipayAccountClient;

    @Override
    public RequestQrCodeStatusRespDTO requestQrCodeStatus(RequestQrCodeStatusReqDTO request) {
        RequestQrCodeStatusRespDTO response = new RequestQrCodeStatusRespDTO();
        response.setItpUserId(request == null ? null : request.getItpUserId());
        response.setCardId(request == null ? null : request.getCardId());

            if (request == null || !StringUtils.hasText(request.getCardId())) {
            response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("cardId不能为空");
            return response;
        }

        QueryStatusReqDTO ticketRequest = new QueryStatusReqDTO();
        ticketRequest.setCardId(request.getCardId());
        ticketRequest.setThirdUserId(request.getItpUserId());

        log.info("IF1A-04 调用 ticket-server 查询票卡状态, 入参={}", JSON.toJSONString(ticketRequest));
        QueryStatusRespDTO ticketResponse = ticketClient.queryQrCodeStatus(ticketRequest);
        log.info("IF1A-04 调用 ticket-server 查询票卡状态, 返回={}", JSON.toJSONString(ticketResponse));

        response.setRetCode(ticketResponse.getRetCode());
        response.setRetMsg(ticketResponse.getRetMsg());
        response.setItpUserId(request.getItpUserId());
        response.setCardId(request.getCardId());
        response.setLastTicketStatus(ticketResponse.getStatus());
        response.setLastHandleDateTime(ticketResponse.getLastTxnTime());
        return response;
    }

    @Override
    public NotifyVerifyResultRespDTO notifyVerifyResult(NotifyVerifyResultReqDTO request) {
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            response.setRetCode(FepDevErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("cardId不能为空");
            return response;
        }

        log.info("IF1A-01 调用 ticket-server 闸机检票通知, 入参={}", JSON.toJSONString(request));
        NotifyVerifyResultRespDTO ticketResponse = ticketClient.notifyVerifyResult(request);
        log.info("IF1A-01 调用 ticket-server 闸机检票通知, 返回={}", JSON.toJSONString(ticketResponse));
        if ("0000".equals(ticketResponse.getRetCode()) && shouldPay(request)) {
            String issueChannelCode = request.getIssueChannelCode();
            if("07".equals(issueChannelCode)) {
                requestAlipayTripPay(request);
            }else {
                requestGateTxnPay(request);
            }
        }
        return ticketResponse;
    }

    private void requestAlipayTripPay(NotifyVerifyResultReqDTO request) {
        try {
            AlipaySignInfoDTO signInfo = alipayPaySignClient.selectSignInfo(request.getItpUserId());
            if (signInfo == null || !StringUtils.hasText(signInfo.getReqContractNo())) {
                log.warn("支付宝用户未签约，无法扣费, itpUserId={}", request.getItpUserId());
                return;
            }

            AlipayUserInfoDTO userInfo = alipayAccountClient.selectByThirdUserId(request.getItpUserId());
            String phone = userInfo != null ? userInfo.getPhone() : null;

            String entryDeviceCode = null;
            try {
                entryDeviceCode = ticketClient.queryEntryDevice(request.getCardId());
            } catch (Exception e) {
                log.warn("查询进站设备异常, cardId={}", request.getCardId(), e);
            }

            AlipayTripRequestPayReqDTO payRequest = new AlipayTripRequestPayReqDTO();
            payRequest.setOrderNo(buildAlipayOrderNo(request.getCardId()));
            payRequest.setScene("TRIP");
            payRequest.setPaymentVendor("ALIPAY");
            payRequest.setAmount(parseAlipayAmount(request.getTrxAmount(), request.getOvertimeAmount()));
            payRequest.setIndustryType("1");
            payRequest.setSubject("地铁乘车扣费");
            payRequest.setBody("地铁乘车费用");
            payRequest.setRequestSignSeq(signInfo.getReqContractNo());
            payRequest.setThirdUserId(convertHexUserIdToDecimal(request.getItpUserId()));
            payRequest.setOrderTimeOut(60);
            payRequest.setIndustryDetail(buildAlipayIndustryDetail(request, signInfo.getChannelAgreementCode(), phone, entryDeviceCode));

            log.info("支付宝交通乘车码扣费请求, 入参={}", JSON.toJSONString(payRequest));
            AlipayTripRequestPayRespDTO payResponse = alipayPaySignClient.alipayTripRequestPay(payRequest);
            log.info("支付宝交通乘车码扣费响应, 返回={}", JSON.toJSONString(payResponse));
        } catch (Exception e) {
            log.error("支付宝交通乘车码扣费异常, cardId={}, trxType={}, handleDateTime={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(), e);
        }
    }

    private String buildAlipayOrderNo(String cardId) {
        String time = LocalDateTime.now().format(ORDER_TIME_FORMATTER);
        String suffix = cardId;
        if (suffix != null && suffix.length() > 6) {
            suffix = suffix.substring(suffix.length() - 6);
        }
        return "GT" + time + (suffix == null ? "" : suffix);
    }

    private Integer parseAlipayAmount(String trxAmount, String overtimeAmount) {
        int amount = parseAmount(trxAmount) + parseAmount(overtimeAmount);
        return amount;
    }

    private String buildAlipayIndustryDetail(NotifyVerifyResultReqDTO request, String channelAgreementCode, String phone, String entryDeviceCode) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("orderDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        detail.put("cardIssueCode", "0007");
        detail.put("cardNum", request.getCardId());
        detail.put("channelAgreementNo", channelAgreementCode);
        detail.put("tikcetTransSeq", request.getTicketTransSeq());
        detail.put("entryLineCode", request.getLastHandleStationCode());
        detail.put("entryId", request.getLastTicketStatus());
        detail.put("entryLineName", request.getLastHandleStationCode());
        detail.put("entryStationCode", request.getLastHandleStationCode());
        detail.put("entryStationName", request.getLastHandleStationCode());
        detail.put("entryDeviceCode", entryDeviceCode);
        detail.put("entryDate", convertHandleDateTime(request.getLastHandleDateTime()));
        detail.put("exitId", request.getTicketTransSeq());
        detail.put("exitLineCode", request.getHandleStationCode());
        detail.put("exitLineName", request.getHandleStationCode());
        detail.put("exitStationCode", request.getHandleStationCode());
        detail.put("exitStationName", request.getHandleStationCode());
        detail.put("exitDeviceCode", request.getDeviceId());
        detail.put("exitDate", convertHandleDateTime(request.getHandleDateTime()));
        detail.put("orderExpType", request.getTrxType());
        detail.put("fineAmount", request.getOvertimeAmount());
        detail.put("phone", phone);
        detail.put("cardType", request.getCardType());
        detail.put("ticketFlag", "3");
        return JSON.toJSONString(detail);
    }

    private String convertHandleDateTime(String handleDateTime) {
        if (handleDateTime == null || handleDateTime.length() < 14) {
            return handleDateTime;
        }
        return handleDateTime.substring(0, 4) + "-" +
                handleDateTime.substring(4, 6) + "-" +
                handleDateTime.substring(6, 8) + " " +
                handleDateTime.substring(8, 10) + ":" +
                handleDateTime.substring(10, 12) + ":" +
                handleDateTime.substring(12, 14);
    }

    private int parseAmount(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        return Integer.parseInt(value.trim());
    }

    private String convertHexUserIdToDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        try {
            return leftPadToEight(new BigInteger(value.trim(), 16).toString(10));
        } catch (Exception e) {
            return value;
        }
    }

    private String leftPadToEight(String value) {
        if (!StringUtils.hasText(value) || value.length() >= 8) {
            return value;
        }
        return "0".repeat(8 - value.length()) + value;
    }

    private boolean shouldPay(NotifyVerifyResultReqDTO request) {
        return request != null && ("02".equals(request.getTrxType()) || "03".equals(request.getTrxType()));
    }

    private void requestGateTxnPay(NotifyVerifyResultReqDTO request) {
        try {
            GateTxnPayReqDTO payRequest = new GateTxnPayReqDTO();
            payRequest.setDeviceId(request.getDeviceId());
            payRequest.setItpUserId(request.getItpUserId());
            payRequest.setTrxType(request.getTrxType());
            payRequest.setIssueChannelCode(request.getIssueChannelCode());
            payRequest.setSignChannelCode(request.getSignChannelCode());
            payRequest.setCardId(request.getCardId());
            payRequest.setCardType(request.getCardType());
            payRequest.setHandleDateTime(request.getHandleDateTime());
            payRequest.setHandleStationCode(request.getHandleStationCode());
            payRequest.setTrxAmount(request.getTrxAmount());
            payRequest.setOvertimeAmount(request.getOvertimeAmount());
            payRequest.setLastTicketStatus(request.getLastTicketStatus());
            payRequest.setHandleResultCode(request.getHandleResultCode());
            payRequest.setLastHandleStationCode(request.getLastHandleStationCode());
            payRequest.setLastHandleDateTime(request.getLastHandleDateTime());
            payRequest.setTicketTransSeq(request.getTicketTransSeq());
            payRequest.setReserve1(request.getReserve1());
            payRequest.setReserve2(request.getReserve2());
            log.info("IF1A-01 出站交易调用扣费交易服务, 入参={}", JSON.toJSONString(payRequest));
            GateTxnPayRespDTO payResponse = gateTxnPayClient.requestGateTxnPay(payRequest);
            log.info("IF1A-01 出站交易调用扣费交易服务, 返回={}", JSON.toJSONString(payResponse));
        } catch (Exception e) {
            log.error("IF1A-01 出站交易调用扣费交易服务异常, cardId={}, trxType={}, handleDateTime={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(), e);
        }
    }
}
