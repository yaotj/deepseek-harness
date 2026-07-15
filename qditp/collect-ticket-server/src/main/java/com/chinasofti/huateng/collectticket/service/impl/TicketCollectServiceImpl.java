package com.chinasofti.huateng.collectticket.service.impl;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.collectticket.client.CollectPayClient;
import com.chinasofti.huateng.collectticket.constant.CollectTicketErrorCodeEnum;
import com.chinasofti.huateng.collectticket.entity.TicketCollectInfo;
import com.chinasofti.huateng.collectticket.entity.TicketCollectLogDetail;
import com.chinasofti.huateng.collectticket.entity.TicketCollectLogs;
import com.chinasofti.huateng.collectticket.mapper.TicketCollectInfoMapper;
import com.chinasofti.huateng.collectticket.mapper.TicketCollectLogDetailMapper;
import com.chinasofti.huateng.collectticket.mapper.TicketCollectLogsMapper;
import com.chinasofti.huateng.collectticket.model.request.CancelTicketCollectOrderReqDTO;
import com.chinasofti.huateng.collectticket.model.request.CreateTicketCollectOrderReqDTO;
import com.chinasofti.huateng.collectticket.model.request.QueryTicketCollectOrderReqDTO;
import com.chinasofti.huateng.collectticket.model.request.RequestPaymentInfoReqDTO;
import com.chinasofti.huateng.collectticket.model.request.RequestTicketPriceByStationReqDTO;
import com.chinasofti.huateng.collectticket.model.request.TicketCollectNotifyReqDTO;
import com.chinasofti.huateng.collectticket.model.request.TicketCollectNotifyReqDTO.TicketDetail;
import com.chinasofti.huateng.collectticket.model.request.TicketCollectPayNotifyReqDTO;
import com.chinasofti.huateng.collectticket.model.response.CancelTicketCollectOrderRespDTO;
import com.chinasofti.huateng.collectticket.model.response.CreateTicketCollectOrderRespDTO;
import com.chinasofti.huateng.collectticket.model.response.QueryTicketCollectOrderRespDTO;
import com.chinasofti.huateng.collectticket.model.response.RequestBuySingleTicketMaxNumRespDTO;
import com.chinasofti.huateng.collectticket.model.response.RequestPaymentInfoRespDTO;
import com.chinasofti.huateng.collectticket.model.response.RequestTicketPriceByStationRespDTO;
import com.chinasofti.huateng.collectticket.model.response.TicketCollectNotifyRespDTO;
import com.chinasofti.huateng.collectticket.model.response.TicketCollectPayNotifyRespDTO;
import com.chinasofti.huateng.collectticket.service.TicketCollectService;
import com.chinasofti.huateng.collectticket.util.PaySignUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class TicketCollectServiceImpl implements TicketCollectService {
    private static final Logger log = LoggerFactory.getLogger(TicketCollectServiceImpl.class);
    private static final DateTimeFormatter QRCODE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter PAY_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final int ORDER_STATUS_UNPAID = 0;
    private static final int ORDER_STATUS_PAID = 100;

    private static final int COLLECT_STATUS_NOT_COLLECTED = 0;
    private static final int COLLECT_STATUS_SUCCESS = 100;

    private static final int PAY_RESULT_UNPAID = 0;
    private static final int PAY_RESULT_PROCESSING = 1;
    private static final int PAY_RESULT_SUCCESS = 100;

    private static final int QRCODE_EXPIRE_SECONDS = 300;
    private static final String ERROR_CODE_QRCODE_EXPIRED = "2101";

    @Value("${collect.ticket.pay.callback-url:}")
    private String payCallbackUrl;

    @Value("${collect.ticket.buy-single-ticket-max-num:10}")
    private Integer buySingleTicketMaxNum;

    @Value("${collect.ticket.default-ticket-price:0}")
    private Integer defaultTicketPrice;

    @Autowired
    private TicketCollectInfoMapper ticketCollectInfoMapper;
    @Autowired
    private TicketCollectLogsMapper ticketCollectLogsMapper;
    @Autowired
    private TicketCollectLogDetailMapper ticketCollectLogDetailMapper;
    @Autowired
    private CollectPayClient collectPayClient;
    @Autowired
    private PaySignUtils paySignUtils;

    @Override
    public RequestBuySingleTicketMaxNumRespDTO requestBuySinlgeTicketMaxNum() {
        RequestBuySingleTicketMaxNumRespDTO response = new RequestBuySingleTicketMaxNumRespDTO();
        response.setRetCode(CollectTicketErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(CollectTicketErrorCodeEnum.SUCCESS.getMsg());
        response.setBuySinlgeTicketMaxNum(String.valueOf(buySingleTicketMaxNum));
        return response;
    }

    @Override
    public RequestTicketPriceByStationRespDTO requestTicketPriceByStation(RequestTicketPriceByStationReqDTO request) {
        RequestTicketPriceByStationRespDTO response = new RequestTicketPriceByStationRespDTO();
        if (request == null
                || !StringUtils.hasText(request.getEntryStationCode())
                || !StringUtils.hasText(request.getExitStationCode())) {
            response.setRetCode(CollectTicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg(CollectTicketErrorCodeEnum.INVALID_PARAM.getMsg());
            return response;
        }

        response.setRetCode(CollectTicketErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(CollectTicketErrorCodeEnum.SUCCESS.getMsg());
        response.setTicketPrice(String.valueOf(resolveTicketPrice(request)));
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestPaymentInfoRespDTO requestPaymentInfo(RequestPaymentInfoReqDTO request) {
        RequestPaymentInfoRespDTO response = new RequestPaymentInfoRespDTO();
        try {
            String validMsg = validateRequestPaymentInfo(request);
            if (validMsg != null) {
                response.setRetCode(CollectTicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                return response;
            }

            TicketCollectInfo collectInfo = ticketCollectInfoMapper.selectByOrderNo(request.getOrderNo());
            if (collectInfo == null) {
                response.setRetCode(CollectTicketErrorCodeEnum.ORDER_NOT_EXIST.getCode());
                response.setRetMsg(CollectTicketErrorCodeEnum.ORDER_NOT_EXIST.getMsg());
                return response;
            }
            if (collectInfo.getOrderStatus() != null && collectInfo.getOrderStatus() == ORDER_STATUS_PAID) {
                response.setRetCode(CollectTicketErrorCodeEnum.ORDER_STATUS_ERROR.getCode());
                response.setRetMsg(CollectTicketErrorCodeEnum.ORDER_STATUS_ERROR.getMsg());
                return response;
            }

            JSONObject payRequest = new JSONObject();
            payRequest.put("orderNo", collectInfo.getOrderNo());
            payRequest.put("scene", request.getChannelType());
            payRequest.put("paymentVendor", request.getPayChannelCode());
            payRequest.put("amount", calculateOrderAmount(collectInfo));
            payRequest.put("industryType", "1");
            payRequest.put("subject", "取票订单");
            payRequest.put("body", "单程票取票支付");
            payRequest.put("thirdUserId", collectInfo.getUserId());
            payRequest.put("remark", "IF8A-11 requestPaymentInfo");
            if (StringUtils.hasText(payCallbackUrl)) {
                payRequest.put("notifyUrl", payCallbackUrl);
            }

            JSONObject payResponse = collectPayClient.requestPayForJson(payRequest);
            if (payResponse == null) {
                response.setRetCode(CollectTicketErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE.getCode());
                response.setRetMsg(CollectTicketErrorCodeEnum.SERVICE_PROVIDER_UNAVAILABLE.getMsg());
                return response;
            }

            collectInfo.setPayChannelCode(request.getPayChannelCode());
            collectInfo.setChannelType(request.getChannelType());
            collectInfo.setPayResult(PAY_RESULT_PROCESSING);
            ticketCollectInfoMapper.updateByOrderNo(collectInfo);

            response.setRetCode(stringOrDefault(payResponse.getString("retCode"), CollectTicketErrorCodeEnum.SUCCESS.getCode()));
            response.setRetMsg(stringOrDefault(payResponse.getString("retMsg"), CollectTicketErrorCodeEnum.SUCCESS.getMsg()));
            response.setPayChannelCode(request.getPayChannelCode());
            response.setPaymentInfo(payResponse.getString("data"));
            response.setSignType(payResponse.getString("signType"));
            response.setSign(payResponse.getString("sign"));
            return response;
        } catch (Exception e) {
            log.error("处理请求支付异常", e);
            response.setRetCode(CollectTicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(CollectTicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateTicketCollectOrderRespDTO createTicketCollectOrder(CreateTicketCollectOrderReqDTO request) {
        try {
            log.info("开始处理创建取票订单, request={}", request);
            CreateTicketCollectOrderRespDTO response = new CreateTicketCollectOrderRespDTO();

            String validMsg = validateCreateOrder(request);
            if (validMsg != null) {
                response.setRetCode(CollectTicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                return response;
            }

            String orderNo = generateOrderNo();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime qrcodeGenDate = now;
            String randomFact = generateRandomFact();

            TicketCollectInfo collectInfo = buildCollectInfo(request, orderNo, now, qrcodeGenDate, randomFact);
            ticketCollectInfoMapper.insert(collectInfo);

            TicketCollectLogs collectLogs = buildCollectLogs(request, orderNo, now, qrcodeGenDate, randomFact);
            ticketCollectLogsMapper.insert(collectLogs);

            response.setRetCode(CollectTicketErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(CollectTicketErrorCodeEnum.SUCCESS.getMsg());
            response.setOrderNo(orderNo);
            response.setOrderStatus(collectInfo.getOrderStatus());
            response.setTicketPrice(collectInfo.getTicketPrice());
            response.setQrcodeGenDate(qrcodeGenDate.format(QRCODE_DATE_FORMATTER));
            response.setRandomFact(randomFact);
            response.setSingleTicketType(collectInfo.getSingleTicketType());
            return response;
        } catch (Exception e) {
            log.error("处理创建取票订单异常", e);
            CreateTicketCollectOrderRespDTO response = new CreateTicketCollectOrderRespDTO();
            response.setRetCode(CollectTicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(CollectTicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    public QueryTicketCollectOrderRespDTO queryTicketCollectOrder(QueryTicketCollectOrderReqDTO request) {
        try {
            log.info("开始处理查询取票订单, request={}", request);
            QueryTicketCollectOrderRespDTO response = new QueryTicketCollectOrderRespDTO();

            String validMsg = validateQueryOrder(request);
            if (validMsg != null) {
                response.setRetCode(CollectTicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                return response;
            }

            TicketCollectInfo collectInfo = findCollectInfo(request);
            if (collectInfo == null) {
                response.setRetCode(CollectTicketErrorCodeEnum.ORDER_NOT_EXIST.getCode());
                response.setRetMsg(CollectTicketErrorCodeEnum.ORDER_NOT_EXIST.getMsg());
                return response;
            }

            if (collectInfo.getQrcodeGenDate() != null) {
                long expireSeconds = Duration.between(collectInfo.getQrcodeGenDate(), LocalDateTime.now()).getSeconds();
                if (expireSeconds > QRCODE_EXPIRE_SECONDS && collectInfo.getOrderStatus() == ORDER_STATUS_UNPAID) {
                    collectInfo.setOrderStatus(99);
                    response.setErrorCode(ERROR_CODE_QRCODE_EXPIRED);
                    response.setErrorMessage("二维码已超时，请重新下单");
                }
            }

            TicketCollectLogs collectLogs = ticketCollectLogsMapper.selectByOrderNo(collectInfo.getOrderNo());
            response.setRetCode(CollectTicketErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(CollectTicketErrorCodeEnum.SUCCESS.getMsg());
            response.setOrderNo(collectInfo.getOrderNo());
            response.setUserId(collectInfo.getUserId());
            response.setEntryStationCode(collectInfo.getEntryStationCode());
            response.setExitStationCode(collectInfo.getExitStationCode());
            response.setTicketPrice(collectInfo.getTicketPrice());
            response.setSingelTicketNum(collectInfo.getSingelTicketNum());
            response.setChannelCode(collectInfo.getChannelCode());
            response.setOrderStatus(collectInfo.getOrderStatus());
            response.setCollectStatus(collectInfo.getCollectStatus());
            response.setDeviceId(collectInfo.getDeviceId());
            if (collectInfo.getQrcodeGenDate() != null) {
                response.setQrcodeGenDate(collectInfo.getQrcodeGenDate().format(QRCODE_DATE_FORMATTER));
            }
            response.setRandomFact(collectInfo.getRandomFact());
            response.setPayResult(collectInfo.getPayResult());
            response.setPayAmount(collectInfo.getPayAmount());
            if (collectInfo.getPayDate() != null) {
                response.setPayDate(collectInfo.getPayDate().format(PAY_DATE_FORMATTER));
            }
            if (collectLogs != null) {
                response.setActualTakeTicketNum(collectLogs.getActualTakeTicketNum());
                if (!StringUtils.hasText(response.getErrorCode())) {
                    response.setErrorCode(collectLogs.getErrorCode());
                    response.setErrorMessage(collectLogs.getErrorMessage());
                }
            }
            return response;
        } catch (Exception e) {
            log.error("处理查询取票订单异常", e);
            QueryTicketCollectOrderRespDTO response = new QueryTicketCollectOrderRespDTO();
            response.setRetCode(CollectTicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(CollectTicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketCollectNotifyRespDTO ticketCollectNotify(TicketCollectNotifyReqDTO request) {
        try {
            log.info("开始处理取票订单通知, request={}", request);
            TicketCollectNotifyRespDTO response = new TicketCollectNotifyRespDTO();

            String validMsg = validateCollectNotify(request);
            if (validMsg != null) {
                response.setRetCode(CollectTicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                return response;
            }

            TicketCollectInfo collectInfo = ticketCollectInfoMapper.selectByOrderNo(request.getOrderNo());
            if (collectInfo == null) {
                response.setRetCode(CollectTicketErrorCodeEnum.ORDER_NOT_EXIST.getCode());
                response.setRetMsg(CollectTicketErrorCodeEnum.ORDER_NOT_EXIST.getMsg());
                return response;
            }
            if (collectInfo.getCollectStatus() != null && collectInfo.getCollectStatus() == COLLECT_STATUS_SUCCESS) {
                response.setRetCode(CollectTicketErrorCodeEnum.COLLECT_ALREADY_DONE.getCode());
                response.setRetMsg(CollectTicketErrorCodeEnum.COLLECT_ALREADY_DONE.getMsg());
                return response;
            }

            LocalDateTime now = LocalDateTime.now();
            collectInfo.setCollectTms(now);
            collectInfo.setCollectStatus(request.getCollectStatus());
            collectInfo.setDeviceId(request.getDeviceId());
            ticketCollectInfoMapper.updateByOrderNo(collectInfo);

            TicketCollectLogs collectLogs = ticketCollectLogsMapper.selectByOrderNo(request.getOrderNo());
            if (collectLogs != null) {
                collectLogs.setCollectTms(now);
                collectLogs.setCollectStatus(request.getCollectStatus());
                collectLogs.setDeviceId(request.getDeviceId());
                collectLogs.setActualTakeTicketNum(request.getActualTakeTicketNum());
                collectLogs.setFaultSlipSeq(request.getFaultSlipSeq());
                collectLogs.setErrorCode(request.getErrorCode());
                collectLogs.setErrorMessage(request.getErrorMessage());
                ticketCollectLogsMapper.updateByOrderNo(collectLogs);
            }

            if (request.getCollectStatus() != null && request.getCollectStatus() == COLLECT_STATUS_SUCCESS
                    && request.getTicketDetails() != null && !request.getTicketDetails().isEmpty()) {
                saveTicketDetails(request.getOrderNo(), request.getActualTakeTicketNum(), request.getTicketDetails());
            }

            response.setRetCode(CollectTicketErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(CollectTicketErrorCodeEnum.SUCCESS.getMsg());
            return response;
        } catch (Exception e) {
            log.error("处理取票订单通知异常", e);
            TicketCollectNotifyRespDTO response = new TicketCollectNotifyRespDTO();
            response.setRetCode(CollectTicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(CollectTicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CancelTicketCollectOrderRespDTO cancelTicketCollectOrder(CancelTicketCollectOrderReqDTO request) {
        try {
            log.info("开始处理取消取票订单, request={}", request);
            CancelTicketCollectOrderRespDTO response = new CancelTicketCollectOrderRespDTO();

            String validMsg = validateCancelOrder(request);
            if (validMsg != null) {
                response.setRetCode(CollectTicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                return response;
            }

            TicketCollectInfo collectInfo = ticketCollectInfoMapper.selectByOrderNo(request.getOrderNo());
            if (collectInfo == null) {
                response.setRetCode(CollectTicketErrorCodeEnum.ORDER_NOT_EXIST.getCode());
                response.setRetMsg(CollectTicketErrorCodeEnum.ORDER_NOT_EXIST.getMsg());
                return response;
            }
            if (collectInfo.getOrderStatus() != null && collectInfo.getOrderStatus() == ORDER_STATUS_PAID) {
                response.setRetCode(CollectTicketErrorCodeEnum.ORDER_STATUS_ERROR.getCode());
                response.setRetMsg(CollectTicketErrorCodeEnum.ORDER_STATUS_ERROR.getMsg());
                return response;
            }
            if (collectInfo.getCollectStatus() != null && collectInfo.getCollectStatus() == COLLECT_STATUS_SUCCESS) {
                response.setRetCode(CollectTicketErrorCodeEnum.COLLECT_NOT_ALLOWED.getCode());
                response.setRetMsg(CollectTicketErrorCodeEnum.COLLECT_NOT_ALLOWED.getMsg());
                return response;
            }

            collectInfo.setOrderStatus(ORDER_STATUS_UNPAID);
            ticketCollectInfoMapper.updateByOrderNo(collectInfo);

            response.setRetCode(CollectTicketErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(CollectTicketErrorCodeEnum.SUCCESS.getMsg());
            response.setOrderNo(collectInfo.getOrderNo());
            response.setOrderStatus(collectInfo.getOrderStatus());
            return response;
        } catch (Exception e) {
            log.error("处理取消取票订单异常", e);
            CancelTicketCollectOrderRespDTO response = new CancelTicketCollectOrderRespDTO();
            response.setRetCode(CollectTicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(CollectTicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TicketCollectPayNotifyRespDTO ticketCollectPayNotify(TicketCollectPayNotifyReqDTO request) {
        try {
            log.info("开始处理取票订单支付结果通知, request={}", request);
            TicketCollectPayNotifyRespDTO response = new TicketCollectPayNotifyRespDTO();

            String validMsg = validatePayNotify(request);
            if (validMsg != null) {
                response.setRetCode(CollectTicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg(validMsg);
                return response;
            }
            if (!verifyPayNotifySign(request)) {
                response.setRetCode(CollectTicketErrorCodeEnum.SIGN_ERROR.getCode());
                response.setRetMsg("签名验证失败");
                return response;
            }

            TicketCollectInfo collectInfo = ticketCollectInfoMapper.selectByOrderNo(request.getOrderNo());
            if (collectInfo == null) {
                response.setRetCode(CollectTicketErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg(CollectTicketErrorCodeEnum.SUCCESS.getMsg());
                return response;
            }

            if ("SUCCESS".equalsIgnoreCase(request.getStatus())) {
                collectInfo.setOrderStatus(ORDER_STATUS_PAID);
                collectInfo.setPayResult(PAY_RESULT_SUCCESS);
                collectInfo.setPayAmount(request.getTotalAmount());
                collectInfo.setPayDate(parsePayTime(request.getPayTime()));
                collectInfo.setPayChannelCode(request.getPaymentVendor());
                collectInfo.setTradeNo(request.getChannelOrderNo());
            } else {
                collectInfo.setOrderStatus(ORDER_STATUS_UNPAID);
                collectInfo.setPayResult(PAY_RESULT_UNPAID);
            }
            ticketCollectInfoMapper.updateByOrderNo(collectInfo);

            TicketCollectLogs collectLogs = ticketCollectLogsMapper.selectByOrderNo(request.getOrderNo());
            if (collectLogs != null) {
                collectLogs.setOrderStatus(collectInfo.getOrderStatus());
                ticketCollectLogsMapper.updateByOrderNo(collectLogs);
            }

            notifyPayResultToPayServer(collectInfo);

            response.setRetCode(CollectTicketErrorCodeEnum.SUCCESS.getCode());
            response.setRetMsg(CollectTicketErrorCodeEnum.SUCCESS.getMsg());
            return response;
        } catch (Exception e) {
            log.error("处理取票订单支付结果通知异常", e);
            TicketCollectPayNotifyRespDTO response = new TicketCollectPayNotifyRespDTO();
            response.setRetCode(CollectTicketErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg(CollectTicketErrorCodeEnum.SYSTEM_ERROR.getMsg());
            return response;
        }
    }

    private boolean verifyPayNotifySign(TicketCollectPayNotifyReqDTO request) {
        Map<String, String> params = new HashMap<>();
        params.put("orderNo", request.getOrderNo());
        params.put("merchantOrderNo", request.getMerchantOrderNo());
        params.put("channelOrderNo", request.getChannelOrderNo());
        params.put("status", request.getStatus());
        params.put("payTime", request.getPayTime());
        if (request.getTotalAmount() != null) {
            params.put("totalAmount", request.getTotalAmount().toString());
        }
        if (request.getCashAmount() != null) {
            params.put("cashAmount", request.getCashAmount().toString());
        }
        if (request.getCouponAmount() != null) {
            params.put("couponAmount", request.getCouponAmount().toString());
        }
        if (StringUtils.hasText(request.getPayUserId())) {
            params.put("payUserId", request.getPayUserId());
        }
        if (StringUtils.hasText(request.getPaymentVendor())) {
            params.put("paymentVendor", request.getPaymentVendor());
        }
        return paySignUtils.verify(params, request.getSign());
    }

    private void notifyPayResultToPayServer(TicketCollectInfo collectInfo) {
        try {
            TicketCollectPayNotifyReqDTO notifyRequest = new TicketCollectPayNotifyReqDTO();
            notifyRequest.setOrderNo(collectInfo.getOrderNo());
            notifyRequest.setChannelOrderNo(collectInfo.getTradeNo());
            notifyRequest.setStatus(collectInfo.getOrderStatus() == ORDER_STATUS_PAID ? "SUCCESS" : "FAIL");
            notifyRequest.setPayTime(collectInfo.getPayDate() != null ? collectInfo.getPayDate().format(PAY_DATE_FORMATTER) : null);
            notifyRequest.setTotalAmount(collectInfo.getPayAmount());
            notifyRequest.setPaymentVendor(collectInfo.getPayChannelCode());
            collectPayClient.notifyPayResult(notifyRequest);
        } catch (Exception e) {
            log.error("通知支付服务支付结果失败, orderNo={}", collectInfo.getOrderNo(), e);
        }
    }

    private LocalDateTime parsePayTime(String payTime) {
        if (!StringUtils.hasText(payTime)) {
            return null;
        }
        try {
            return LocalDateTime.parse(payTime, PAY_DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("解析支付时间失败, payTime={}", payTime);
            return null;
        }
    }

    private String validateCreateOrder(CreateTicketCollectOrderReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getUserId())) {
            return "userId不能为空";
        }
        if (!StringUtils.hasText(request.getEntryStationCode())) {
            return "entryStationCode不能为空";
        }
        if (!StringUtils.hasText(request.getExitStationCode())) {
            return "exitStationCode不能为空";
        }
        if (request.getTicketPrice() == null || request.getTicketPrice() < 0) {
            return "ticketPrice不能为空";
        }
        if (request.getSingelTicketNum() == null || request.getSingelTicketNum() <= 0) {
            return "singelTicketNum必须大于0";
        }
        if (!StringUtils.hasText(request.getChannelCode())) {
            return "channelCode不能为空";
        }
        return null;
    }

    private String validateQueryOrder(QueryTicketCollectOrderReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo()) && !StringUtils.hasText(request.getUserId())) {
            return "orderNo和userId至少填一个";
        }
        return null;
    }

    private String validateCollectNotify(TicketCollectNotifyReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (request.getCollectStatus() == null) {
            return "collectStatus不能为空";
        }
        return null;
    }

    private String validateCancelOrder(CancelTicketCollectOrderReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        return null;
    }

    private String validatePayNotify(TicketCollectPayNotifyReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (!StringUtils.hasText(request.getStatus())) {
            return "status不能为空";
        }
        return null;
    }

    private String validateRequestPaymentInfo(RequestPaymentInfoReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (!StringUtils.hasText(request.getPayChannelCode())) {
            return "payChannelCode不能为空";
        }
        if (!StringUtils.hasText(request.getChannelType())) {
            return "channelType不能为空";
        }
        return null;
    }

    private TicketCollectInfo findCollectInfo(QueryTicketCollectOrderReqDTO request) {
        if (StringUtils.hasText(request.getOrderNo())) {
            return ticketCollectInfoMapper.selectByOrderNo(request.getOrderNo());
        }
        if (StringUtils.hasText(request.getUserId())) {
            var list = ticketCollectInfoMapper.selectByUserId(request.getUserId());
            if (list != null && !list.isEmpty()) {
                return list.get(0);
            }
        }
        return null;
    }

    private TicketCollectInfo buildCollectInfo(CreateTicketCollectOrderReqDTO request, String orderNo,
                                               LocalDateTime now, LocalDateTime qrcodeGenDate, String randomFact) {
        TicketCollectInfo info = new TicketCollectInfo();
        info.setOrderNo(orderNo);
        info.setUserId(request.getUserId());
        info.setEntryStationCode(request.getEntryStationCode());
        info.setExitStationCode(request.getExitStationCode());
        info.setTicketPrice(request.getTicketPrice());
        info.setSingelTicketNum(request.getSingelTicketNum());
        info.setSingleTicketType(request.getSingleTicketType() != null ? request.getSingleTicketType() : 0);
        info.setChannelCode(request.getChannelCode());
        info.setOrderStatus(ORDER_STATUS_UNPAID);
        info.setCreateTms(now);
        info.setQrcodeGenDate(qrcodeGenDate);
        info.setRandomFact(randomFact);
        info.setCollectStatus(COLLECT_STATUS_NOT_COLLECTED);
        info.setChannelType(request.getChannelType());
        info.setPayResult(PAY_RESULT_UNPAID);
        return info;
    }

    private TicketCollectLogs buildCollectLogs(CreateTicketCollectOrderReqDTO request, String orderNo,
                                               LocalDateTime now, LocalDateTime qrcodeGenDate, String randomFact) {
        TicketCollectLogs logs = new TicketCollectLogs();
        logs.setOrderNo(orderNo);
        logs.setUserId(request.getUserId());
        logs.setEntryStationCode(request.getEntryStationCode());
        logs.setExitStationCode(request.getExitStationCode());
        logs.setTicketPrice(request.getTicketPrice());
        logs.setSingelTicketNum(request.getSingelTicketNum());
        logs.setSingleTicketType(request.getSingleTicketType() != null ? request.getSingleTicketType() : 0);
        logs.setChannelCode(request.getChannelCode());
        logs.setOrderStatus(ORDER_STATUS_UNPAID);
        logs.setCreateTms(now);
        logs.setCollectStatus(COLLECT_STATUS_NOT_COLLECTED);
        logs.setDeviceId(request.getChannelCode());
        logs.setQrcodeGenDate(qrcodeGenDate);
        logs.setRandomFact(randomFact);
        return logs;
    }

    private void saveTicketDetails(String orderNo, Integer actualTakeTicketNum, java.util.List<TicketDetail> ticketDetails) {
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        for (TicketDetail detail : ticketDetails) {
            TicketCollectLogDetail logDetail = new TicketCollectLogDetail();
            logDetail.setOrderNo(orderNo);
            logDetail.setActualTakeTicketNum(actualTakeTicketNum);
            logDetail.setInOrderNo(detail.getInOrderNo());
            if (StringUtils.hasText(detail.getTakeTicketDate())) {
                try {
                    logDetail.setTakeTickeDate(LocalDateTime.parse(detail.getTakeTicketDate(), dateFormatter));
                } catch (Exception e) {
                    log.warn("解析取票时间失败, date={}", detail.getTakeTicketDate());
                }
            }
            logDetail.setTicketLogicNum(detail.getTicketLogicNum());
            if (StringUtils.hasText(detail.getTransDate())) {
                try {
                    logDetail.setTransDate(LocalDateTime.parse(detail.getTransDate(), dateFormatter));
                } catch (Exception e) {
                    log.warn("解析交易时间失败, date={}", detail.getTransDate());
                }
            }
            logDetail.setTransAmount(detail.getTransAmount());
            ticketCollectLogDetailMapper.insert(logDetail);
        }
    }

    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateRandomFact() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private int resolveTicketPrice(RequestTicketPriceByStationReqDTO request) {
        if (request.getEntryStationCode().equals(request.getExitStationCode())) {
            return 0;
        }
        return defaultTicketPrice;
    }

    private int calculateOrderAmount(TicketCollectInfo collectInfo) {
        int ticketPrice = collectInfo.getTicketPrice() == null ? 0 : collectInfo.getTicketPrice();
        int ticketNum = collectInfo.getSingelTicketNum() == null || collectInfo.getSingelTicketNum() <= 0
                ? 1 : collectInfo.getSingelTicketNum();
        return ticketPrice * ticketNum;
    }

    private String stringOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }
}
