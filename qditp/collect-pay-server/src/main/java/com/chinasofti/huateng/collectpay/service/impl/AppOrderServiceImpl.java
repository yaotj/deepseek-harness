package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.common.ItpCommon;
import com.chinasofti.huateng.collectpay.common.PayCenterCommon;
import com.chinasofti.huateng.collectpay.config.PayCenterProperties;
import com.chinasofti.huateng.collectpay.constant.*;
import com.chinasofti.huateng.collectpay.entity.AppRefundOrder;
import com.chinasofti.huateng.collectpay.entity.TvmAppOrder;
import com.chinasofti.huateng.collectpay.mapper.*;
import com.chinasofti.huateng.model.app.ItpCommonRequest;
import com.chinasofti.huateng.collectpay.model.request.PayCenterRequest;
import com.chinasofti.huateng.collectpay.model.request.app.NoticeAppRefundDTO;
import com.chinasofti.huateng.collectpay.model.request.app.RequestOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.app.RequestPayInfoReqDTO;
import com.chinasofti.huateng.collectpay.model.request.app.RequestQueryActiveOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.APPRefundNotiResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.PayNoticeReqDTO;
import com.chinasofti.huateng.collectpay.model.request.tvm.RequestPayResultReqDTO;
import com.chinasofti.huateng.collectpay.model.response.PayCenterResponse;
import com.chinasofti.huateng.collectpay.model.response.app.AppActiveOrderModel;
import com.chinasofti.huateng.collectpay.model.response.app.AppOrderResult;
import com.chinasofti.huateng.collectpay.model.response.paycenter.PayCenterResult;
import com.chinasofti.huateng.collectpay.model.response.tvm.TvmOrderResult;
import com.chinasofti.huateng.collectpay.service.AppOrderService;
import com.chinasofti.huateng.collectpay.service.PayCenterService;
import com.chinasofti.huateng.collectpay.service.TvmCommonService;
import com.chinasofti.huateng.collectpay.utils.*;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * APP订单服务实现类。
 * 实现APP下单、支付、支付结果查询和支付结果通知等业务逻辑。
 */
@Service
@Slf4j
public class AppOrderServiceImpl implements AppOrderService {

    private static final String DATE_yyyyMMddHHmmss = "yyyyMMddHHmmss";

    @Autowired
    private TvmAppOrderMapper tvmAppOrderMapper;

    /** 订单号序列，与 BOM / TVM 侧共用 {@code ORDER_NO_SEQ}，保证跨渠道不重号。 */
    @Autowired
    private OrderSeqMapper orderSeqMapper;

    @Autowired
    private PayCenterService payCenterService;

    @Autowired
    private PayCenterProperties payCenterProperties;

    @Autowired
    private PayCenterCommon payCenterCommon;
    @Autowired
    TvmOrderPreMapper tvmOrderPreMapper;
    @Autowired
    AppRefundOrderMapper appRefundOrderMapper;
    @Autowired
    TvmCommonService tvmCommonService;
    @Autowired
    TvmNoticeAppMapper tvmNoticeAppMapper;
    @Autowired
    TvmMainTicketMapper tvmMainTicketMapper;
    @Autowired
    HttpUtils httpUtils;
    @Autowired
    Environment environment;
    @Resource(name = "tvmexecutor")
    ThreadPoolTaskExecutor executor;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JSONObject requestOrder(RequestOrderReqDTO request) {
        log.info("1.开始处理APP下单请求, request={}", request);

        if (!validateOrderRequest(request)) {
            log.info("2.参数校验失败");
            return AppOrderResult.fail("8003", "非法参数");
        }

        String now = DateUtils.getNowTime();
        String orderNo = generateOrderNo();
        log.info("3.生成订单号: {}", orderNo);

        TvmAppOrder order = buildTvmAppOrder(orderNo, request, now);
        log.info("4.构建订单信息: {}", order);
        // 保存支付订单前置信息
        tvmOrderPreMapper.insert(getTvmOrderPre(order, request.getDeviceId()));
        tvmAppOrderMapper.insert(order);
        log.info("5.订单保存成功");

        return AppOrderResult.successData(orderNo);
    }

    private Map<String, Object> getTvmOrderPre(TvmAppOrder order, String deviceId) {
        Map<String, Object> preMap = new HashMap<>();
        preMap.put("orderNo", order.getOrderNo());
        preMap.put("transAmount", order.getTotalPrice());
        preMap.put("deviceId", deviceId);
        // 01-扫码购票  02-扫码充值
        preMap.put("transType", BusinessTypeEnum.TVM_SCAN_QR_TAKETICKET.getCode());
        preMap.put("createTime", DateUtils.getNowTime());
        preMap.put("updateTime", "");
        return preMap;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JSONObject requestPayInfo(RequestPayInfoReqDTO request) {
        log.info("1.开始处理APP请求支付信息, request={}", request);

        if (!validatePayInfoRequest(request)) {
            log.info("2.参数校验失败");
            return AppOrderResult.failMessage("非法参数");
        }

        TvmAppOrder order = tvmAppOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("2.订单不存在, orderNo={}", request.getOrderNo());
            return AppOrderResult.failMessage("订单号错误");
        }

        if (!ItpStatusEnum.PAYING.getCode().equals(order.getPayStatus())) {
            log.info("2.订单状态异常, payStatus={}", order.getPayStatus());
            return AppOrderResult.failMessage("订单状态异常");
        }

        log.info("查询到订单 order is {}", order);

        String paymentInfo = "";
        String signType = "00";
        String sign = "";
        String merchantOrderNo = "";

        BigDecimal totalAmount = new BigDecimal(order.getTicketPrice())
                .multiply(new BigDecimal(order.getTicketNum()));

        PayCenterRequest payCenterRequest = payCenterCommon.buildAppPayRequest(
                order.getOrderNo(),
                totalAmount.toString(),
                request.getPayChannelCode(),
                "APP单程票购票",
                "地铁单程票", request.getPayChannelCode()
        );

        String payUrl = payCenterProperties.getPayCenterPayUrl();
        log.info("3.调用支付中心预下单, payUrl={}, payCenterRequest={}", payUrl, payCenterRequest);

        PayCenterResponse payResponse = payCenterService.callPayCenter(payUrl, payCenterRequest);
        log.info("4.支付中心响应: {}", payResponse);

        if (!ObjectUtils.isEmpty(payResponse)
                && StringUtils.equals(payResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
            Map<String, Object> data = payResponse.getData();
            if (data != null) {
                paymentInfo = getStringFromData(data, "data");
                signType = "00";
                sign = "";
                merchantOrderNo = getStringFromData(data, "orderNo");
            }
        }

        Map<String, String> updateParams = new HashMap<>();
        updateParams.clear();
        updateParams.put("orderNo", request.getOrderNo());
        updateParams.put("requestPayFlag", "1");
        updateParams.put("merchantOrderNo", merchantOrderNo);
        updateParams.put("paymentInfo", paymentInfo);
        updateParams.put("updateTime", DateUtils.getNowTime());
        tvmAppOrderMapper.updateByOrderNo(updateParams);

        return AppOrderResult.successPayInfo(request.getPayChannelCode(), paymentInfo, signType, sign);
    }

    @Override
    public JSONObject requestPayResult(RequestPayResultReqDTO request) {
        log.info("1.开始处理APP支付结果查询, request={}", request);

        String orderNo = request.getOrderNo();
        if (!validatePayResultRequest(request)) {
            log.info("2.参数校验失败");
            return AppOrderResult.failMessage("非法参数");
        }

        TvmAppOrder order = tvmAppOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("2.订单不存在, orderNo={}", orderNo);
            return AppOrderResult.failMessage("订单号错误");
        }

        if (ItpStatusEnum.SUCCESS.getCode().equals(order.getPayStatus())) {
            log.info("2.数据库查询结果为支付成功，直接返回");
            return AppOrderResult.successPayResult(
                    order.getMerchantOrderNo(),
                    "SUCCESS",
                    order.getPayAmount(),
                    order.getPayTime()
            );
        }

        if (ItpStatusEnum.FAILED.getCode().equals(order.getPayStatus())) {
            log.info("2.数据库查询结果为支付失败，直接返回");
            return AppOrderResult.successPayResult(
                    order.getMerchantOrderNo(),
                    "FAIL",
                    order.getPayAmount(),
                    order.getPayTime()
            );
        }

        String queryUrl = payCenterProperties.getPayCenterQueryUrl();
        PayCenterRequest queryRequest = payCenterCommon.buildQueryPayCenterRequest(order.getOrderNo());
        log.info("3.调用支付中心查询支付结果, queryUrl={}, queryRequest={}", queryUrl, queryRequest);

        PayCenterResponse queryResponse = payCenterService.callPayCenter(queryUrl, queryRequest);
        log.info("4.支付中心查询响应: {}", queryResponse);

        String nowTime = DateUtils.getNowTime();

        if (!ObjectUtils.isEmpty(queryResponse)
                && StringUtils.equals(queryResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
            Map<String, Object> data = queryResponse.getData();
            if (data != null) {
                String status = getStringFromData(data, "status");
                String payCenterChannelOrderNo = getStringFromData(data, "channelOrderNo");
                String payCenterOrderNo = getStringFromData(data, "payCenterOrderNo");
                String payAmount = getStringFromData(data, "amount");
                String payTime = getStringFromData(data, "payTime");
                String payChannelCode = TransforUtils.getStringFromData(data, "paymentVendor");

                if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {
                    log.info("5.查询到支付成功");
                    Map<String, String> updateParams = new HashMap<>();
                    updateParams.put("orderNo", orderNo);
                    updateParams.put("payStatus", ItpStatusEnum.SUCCESS.getCode());
                    updateParams.put("msg", ItpStatusEnum.SUCCESS.getDesc());
                    updateParams.put("payCenterOrderNo", payCenterOrderNo);
                    updateParams.put("payCenterChannelOrderNo", payCenterChannelOrderNo);
                    updateParams.put("payChannelCode", payChannelCode);
                    updateParams.put("payAmount", payAmount);
                    updateParams.put("payTime", payTime);
                    updateParams.put("updateTime", nowTime);
                    tvmAppOrderMapper.updateByOrderNo(updateParams);


                    return AppOrderResult.successPayResult(payCenterChannelOrderNo, AppStatusEnum.PAY_SUCCESS.getCode(), payAmount, DateUtils.getNowTimeByFormat(DATE_yyyyMMddHHmmss));
                } else if ("3".equals(status)) {
                    log.info("5.查询到支付失败");
                    Map<String, String> updateParams = new HashMap<>();
                    updateParams.put("orderNo", orderNo);
                    updateParams.put("payStatus", ItpStatusEnum.FAILED.getCode());
                    updateParams.put("msg", ItpStatusEnum.FAILED.getDesc());
                    updateParams.put("payCenterOrderNo", payCenterOrderNo);
                    updateParams.put("payCenterChannelOrderNo", payCenterChannelOrderNo);
                    updateParams.put("payAmount", payAmount);
//                    updateParams.put("payDate", DateUtils.getNowTime());
                    updateParams.put("updateTime", nowTime);
                    tvmAppOrderMapper.updateByOrderNo(updateParams);

                    return AppOrderResult.successPayResult(payCenterChannelOrderNo, AppStatusEnum.PAY_FAIL.getCode(), payAmount, "");
                }
            }
        }

        log.info("5.支付结果不明确，返回支付中");
        return AppOrderResult.fail("8999", "支付中");
    }

    /**
     *
     * 通知 app
     */
//    @Override
    public JSONObject receivePaymentResult(JSONObject request) {
        log.info("1.开始处理APP支付结果通知, request={}", request);

        String orderNo = request.getString("orderNo");
        String payResult = request.getString("payResult");

        if (StringUtils.isEmpty(orderNo)) {
            log.info("2.订单号为空");
            return AppOrderResult.fail("8006", "订单号错误");
        }

        TvmAppOrder order = tvmAppOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("2.订单不存在, orderNo={}", orderNo);
            return AppOrderResult.fail("8006", "订单号错误");
        }

        Map<String, String> updateParams = new HashMap<>();
        updateParams.put("orderNo", orderNo);
        updateParams.put("updateTime", DateUtils.getNowTime());

        if ("SUCCESS".equals(payResult)) {
            log.info("3.支付成功，更新订单状态");
            updateParams.put("payStatus", ItpStatusEnum.SUCCESS.getCode());
            updateParams.put("tradeNo", request.getString("tradeNo"));
            updateParams.put("payAmount", request.getString("payAmount"));
            updateParams.put("payDate", request.getString("payDate"));
            updateParams.put("voucher", request.getString("voucher"));
        } else {
            log.info("3.支付失败，更新订单状态");
            updateParams.put("payStatus", ItpStatusEnum.FAILED.getCode());
            updateParams.put("tradeNo", request.getString("tradeNo"));
            updateParams.put("payAmount", request.getString("payAmount"));
            updateParams.put("payDate", request.getString("payDate"));
        }

        tvmAppOrderMapper.updateByOrderNo(updateParams);

        return AppOrderResult.success();
    }

    private boolean validateOrderRequest(RequestOrderReqDTO request) {
        if (request == null) {
            return false;
        }
        return StringUtils.isNotBlank(request.getUserId())
                && StringUtils.isNotBlank(request.getEntryStationCode())
                && StringUtils.isNotBlank(request.getExitStationCode())
                && StringUtils.isNotBlank(request.getTicketPrice())
                && StringUtils.isNotBlank(request.getSingelTicketNum())
                && StringUtils.isNotBlank(request.getSingleTicketType());
    }

    private boolean validatePayInfoRequest(RequestPayInfoReqDTO request) {
        if (request == null) {
            return false;
        }
        return StringUtils.isNotBlank(request.getOrderNo())
                && StringUtils.isNotBlank(request.getPayChannelCode());
    }

    private boolean validatePayResultRequest(RequestPayResultReqDTO request) {
        if (request == null) {
            return false;
        }
        return StringUtils.isNotBlank(request.getUserId())
                && StringUtils.isNotBlank(request.getOrderNo());
    }

    private TvmAppOrder buildTvmAppOrder(String orderNo, RequestOrderReqDTO request, String now) {
        TvmAppOrder order = new TvmAppOrder();
        order.setOrderNo(orderNo);
        order.setUserId(request.getUserId());
        order.setInStationCode(request.getEntryStationCode());
        order.setOutStationCode(request.getExitStationCode());
        order.setTicketPrice(request.getTicketPrice());
        order.setTicketNum(request.getSingelTicketNum());
        String totalPrice = String.valueOf(new BigDecimal(request.getTicketPrice()).multiply(new BigDecimal(request.getSingelTicketNum())));
        order.setTotalPrice(totalPrice);
        order.setTicketType(request.getSingleTicketType());
        order.setPayStatus(ItpStatusEnum.PAYING.getCode());
        order.setMsg(ItpStatusEnum.PAYING.getDesc());
        order.setRequestPayFlag("0");
        order.setActivateFlag(ActivateFlagEnum.ACTIVATE_INIT.getCode());
        order.setPayChannelCode("");
//        order.setMerchantOrderNo("");
        order.setPayAmount(totalPrice);
        order.setPayTime("");
        order.setPaymentInfo("");
        order.setCreateTime(now);
        order.setUpdateTime("");
        order.setRsv1("");
        order.setRsv2("");
        return order;
    }

    /**
     * 生成 APP 单程票订单号，20 位：ProductType(2) + yyyyMMddHHmmss(14) + 序列(4)。
     *
     * <p>2026-09-11 由「00 + 时间 + UUID 前 8 位」的 24 位改为 20 位，与 BOM
     * （{@code BomOrderServiceImpl.generateOrderNo}）和新服务 face-pay 完全同口径。前缀仍是
     * {@code ProductType.ordinaryTicket = "00"}，与改动前的 APP 订单号一致，历史 24 位订单不受影响。</p>
     *
     * <p>长度由 {@code ORDER_NO_SEQ} 保证：实测 {@code MAX_VALUE=9999} + {@code CYCLE=Y}，
     * 序列值永远 ≤4 位，因此总长恒为 20。**NEVER 把该序列改成不循环或放大上限**，否则订单号会超过 20 位。</p>
     */
    private String generateOrderNo() {
        long seq = orderSeqMapper.nextval();
        return OrderNoUtils.generateOrderNo(ProductType.ordinaryTicket, seq);
    }

    private String getStringFromData(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    @Override
    public JSONObject payNotice(PayNoticeReqDTO request) {

        log.info("接收到 app下单 支付结果通知 request is {}", request);

        TvmAppOrder order = tvmAppOrderMapper.selectByOrderNo(request.getMerchantOrderNo());
        log.info("app下单 order is {}", order);
        if (order == null) {
            log.info("2.app下单 支付结果通知 订单不存在, orderNo={}", request.getOrderNo());
            return PayCenterResult.fail(PayCenterErrorCodeEnum.ORDER_NOT_EXIST.getCode(), PayCenterErrorCodeEnum.ORDER_NOT_EXIST.getMsg());
        }

        if (ItpStatusEnum.SUCCESS.getCode().equals(order.getPayStatus())) {
            log.info("2.app下单 支付结果通知 数据库查询结果为支付成功，直接返回");
            return PayCenterResult.success();
        }

        if (ItpStatusEnum.FAILED.getCode().equals(order.getPayStatus())) {
            log.info("2.app下单 支付结果通知 数据库查询结果为支付失败，直接返回");
            return PayCenterResult.success();
        }

        log.info("当前订单数据库没有确定的支付结果，开始修改数据库状态");

        Map<String, String> uMap = new HashMap<>();
        String status = request.getStatus();
        // itp订单号
        String orderNo = request.getMerchantOrderNo();
        // 支付中心订单号
        String payCenterOrderNo = request.getOrderNo();
        // 渠道订单号
        String payCenterChannelOrderNo = request.getChannelOrderNo();
        String payAmount = request.getTotalAmount();
        String payTime = request.getPayTime();
        String payChannelCode = request.getPaymentVendor();

        if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {
            log.info("5.app下单 支付结果通知  支付成功");
            Map<String, String> updateParams = new HashMap<>();
            updateParams.put("orderNo", orderNo);
            updateParams.put("payStatus", ItpStatusEnum.SUCCESS.getCode());
            updateParams.put("msg", ItpStatusEnum.SUCCESS.getDesc());
            updateParams.put("payCenterOrderNo", payCenterOrderNo);
            updateParams.put("payCenterChannelOrderNo", payCenterChannelOrderNo);
            updateParams.put("payAmount", payAmount);
            updateParams.put("payTime", payTime);
            updateParams.put("updateTime", DateUtils.getNowTime());
            updateParams.put("payChannelCode", payChannelCode);
            tvmAppOrderMapper.updateByOrderNo(updateParams);

            // todo 通知app支付结果

            return PayCenterResult.success();
        } else if (PayCenterStatusEnum.FAILED.getCode().equals(status)) {
            log.info("5.app下单 支付结果通知  支付失败");
            Map<String, String> updateParams = new HashMap<>();
            updateParams.put("orderNo", orderNo);
            updateParams.put("payStatus", ItpStatusEnum.FAILED.getCode());
            updateParams.put("msg", ItpStatusEnum.FAILED.getDesc());
            updateParams.put("payCenterOrderNo", payCenterOrderNo);
            updateParams.put("payCenterChannelOrderNo", payCenterChannelOrderNo);
            updateParams.put("payAmount", payAmount);
            updateParams.put("payDate", DateUtils.getNowTime());
            updateParams.put("updateTime", DateUtils.getNowTime());
            tvmAppOrderMapper.updateByOrderNo(updateParams);

            // todo 通知app支付结果

            return PayCenterResult.success();
        } else {
            log.info("app下单 支付结果通知 支付状态不明确，不做处理");
            return PayCenterResult.fail();
        }
    }

    @Override
    public JSONObject refundAppNotTakeTickets() {

        log.info("开始查询购票但未取票的订单信息");

        Map<String, String> condition = new HashMap<>();
        condition.put("startTime", DateUtils.getTime(-1, "yyyy-MM-dd") + " 00:00:00");
        condition.put("endTime", DateUtils.getTime(-1, "yyyy-MM-dd") + " 23:59:59");

        log.info("condition is {}", condition);
        // 1.查询购票但未取票的订单信息
        List<TvmAppOrder> tvmAppOrders = tvmAppOrderMapper.selectByCondition(condition);

        log.info(" tvmAppOrders.size is {}",tvmAppOrders.size());
        log.info("tvmAppOrders is {}",tvmAppOrders);

        if(tvmAppOrders.size()==0){
            return AppOrderResult.success("无购票但未取票的订单信息,结束");
        }

        for (TvmAppOrder tvmAppOrder : tvmAppOrders) {

            RequestPayResultReqDTO dto = new RequestPayResultReqDTO();
            dto.setOrderNo(tvmAppOrder.getOrderNo());

            log.info("定时任务 开始发起退款，dto is {}",dto);

            // 发起退款
            JSONObject refundResult = requestRefundTicket(dto);
            log.info("订单 {} 退款结束 refundResult is {}",tvmAppOrder.getOrderNo(),refundResult);
        }

        return AppOrderResult.success("app 支付成功未取票订单 退款 结束");
    }

    @Override
    public JSONObject requestRefundTicket(RequestPayResultReqDTO request) {

        log.info("1.app订单开始退款 request is {}", request);
        String payOrderNo = request.getOrderNo();
        TvmAppOrder appOrder = tvmAppOrderMapper.selectByOrderNo(payOrderNo);
        log.info("2.appOrder is {}", appOrder);
        if (ObjectUtils.isEmpty(appOrder)) {
            return AppOrderResult.failMessage("该订单无支付记录，不可退款");
        }
        String refundAmount = appOrder.getPayAmount();

        log.info("开始发起退款");

        return this.doRefund(payOrderNo, refundAmount, OrderCommonUtils.getRefundNo(), BusinessTypeEnum.APP_REFUND.getCode());
    }

    /**
     * 按指定金额退款（补退部分退款的剩余额度）。契约与约束见
     * {@link com.chinasofti.huateng.collectpay.service.AppOrderService#refundByAmount}。
     *
     * <p>三道校验全部在调支付中心**之前**完成，被拒时不落退款单、不发网络请求：
     * 订单存在且 {@code PAY_STATUS='1'}（白名单，NEVER 写成「非失败即可退」）、
     * 金额为正整数、金额不超过可退余额。</p>
     */
    @Override
    public JSONObject refundByAmount(String payOrderNo, int refundAmount) {
        log.info("1.app订单按指定金额退款 payOrderNo={}, refundAmount={}", payOrderNo, refundAmount);
        if (refundAmount <= 0) {
            log.info("参数校验失败，退款金额必须为正整数, refundAmount={}", refundAmount);
            return AppOrderResult.failMessage("退款金额必须为正整数");
        }
        TvmAppOrder appOrder = tvmAppOrderMapper.selectByOrderNo(payOrderNo);
        if (ObjectUtils.isEmpty(appOrder)) {
            return AppOrderResult.failMessage("该订单无支付记录，不可退款");
        }
        if (!StringUtils.equals(ItpStatusEnum.SUCCESS.getCode(), appOrder.getPayStatus())) {
            log.info("2.仅支付成功的订单可退款, payOrderNo={}, payStatus={}", payOrderNo, appOrder.getPayStatus());
            return AppOrderResult.failMessage("仅支付成功的订单可以退款");
        }

        long paidAmount;
        try {
            paidAmount = Long.parseLong(appOrder.getPayAmount().trim());
        } catch (RuntimeException e) {
            log.error("订单支付金额格式异常，不可退款, payOrderNo={}, payAmount={}", payOrderNo, appOrder.getPayAmount());
            return AppOrderResult.failMessage("订单支付金额格式异常，不可退款");
        }
        long refundedAmount = appRefundOrderMapper.sumSuccessRefundAmount(payOrderNo);
        long refundableAmount = paidAmount - refundedAmount;
        log.info("3.可退余额核算, payOrderNo={}, 支付={}, 已成功退款={}, 可退={}, 本次申请={}",
                payOrderNo, paidAmount, refundedAmount, refundableAmount, refundAmount);
        if (refundAmount > refundableAmount) {
            log.warn("退款金额超过可退余额被拒, payOrderNo={}, 可退={}, 本次申请={}",
                    payOrderNo, refundableAmount, refundAmount);
            return AppOrderResult.failMessage("退款金额超过可退余额，可退" + refundableAmount + "分");
        }

        log.info("开始发起指定金额退款");
        return this.doRefund(payOrderNo, String.valueOf(refundAmount),
                OrderCommonUtils.getRefundNo(), BusinessTypeEnum.APP_REFUND.getCode());
    }

    @Override
    public JSONObject doRefund(String payOrderNo, String refundAmount, String refundNo, String businessType) {
        try {

            log.info("payOrderNo is {}   refundAmount is {} refundNo is {}", payOrderNo, refundAmount, refundNo);

            JSONObject result = new JSONObject();
            result.put("orderNo", payOrderNo);
            result.put("refundType", "00");
            result.put("refundDate", getNowDate());
            result.put("refundAmount", refundAmount);
            String appRefundNoticeUrl = environment.getProperty("pay.center.app-refund-notice-url");
            log.info("appRefundNoticeUrl is {}", appRefundNoticeUrl);
            result.put("notifyUrl", appRefundNoticeUrl);

            String now = DateUtils.getNowTime();

            // 2. 保存退款记录
            AppRefundOrder refundOrder = new AppRefundOrder();
            refundOrder.setRefundNo(refundNo);
            refundOrder.setPayOrderNo(payOrderNo);
            refundOrder.setMerchantRefundNo(refundNo);
            refundOrder.setRefundAmount(refundAmount);
            refundOrder.setRefundReason("业务操作失败");
            refundOrder.setRefundStatus(ItpStatusEnum.REFUND_ING.getCode()); // 0-退款中
            refundOrder.setRefundMsg(ItpStatusEnum.REFUND_ING.getDesc());
            refundOrder.setCreateTime(now);
            refundOrder.setUpdateTime(now);
            appRefundOrderMapper.insert(refundOrder);

            PayCenterRequest payCenterRefundRequest = payCenterCommon.getRefundRequest(refundNo, payOrderNo, "", Integer.valueOf(refundAmount));

            log.info("退款开始 payCenterRefundRequest is {}", payCenterRefundRequest);

            // 3. 调用支付中心退款接口
            PayCenterResponse payCenterResponse = payCenterService.callPayCenter(payCenterProperties.getPayCenterRefundUrl(), payCenterRefundRequest);

            log.info("退款结束 payCenterResponse is {}", payCenterResponse);
            log.info("3.1.app退款订单记录已入库, refundOrderNo={}", refundNo);
            if (payCenterResponse != null && StringUtils.equals(payCenterResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
                log.info("退款返回成功, payOrderNo={}, refundNo={}, refundAmount={}", payOrderNo, refundNo, refundAmount);

                result.put("refundResult", "PROCESSING");
                result.put("refundResultDesc", "退款进行中");

                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        BaseResult baseResult = tvmCommonService.getPayCenterRefundResult(refundNo);
                        log.info("退款查询业务处理结束，baseResult is {}", baseResult);
                        if (baseResult.getErrorCode() == BaseResult.SUCCESS) {

                            JSONObject refundResultInfo = JSONObject.parseObject(baseResult.getData().toString());
                            String refundStatus = refundResultInfo.get("status").toString();
                            String refundTime = refundResultInfo.get("refundTime").toString();
                            String refundDate = toAppRefundDate(refundTime);
                            boolean b = dealAppRefundResult(payOrderNo, refundNo, refundStatus, refundTime);
                            log.info("app处理退款业务逻辑结束 b is {}", b);

                            // 查询到退款终态后 MUST 通知一次 app：先落通知记录（status=0），再同步 push，
                            // push 成功置 1、失败置 2 交给 NoticeAppTask 重试。
                            // NEVER 再用 businessType 把 APP 主动退款排除在外 —— 2026-08-27 生产事故：
                            // APP 退款走 BusinessTypeEnum.APP_REFUND（requestRefundTicket 传入），而这里原来只放行
                            // TVM_SCAN_QR_TAKETICKET，导致订单 00202608271248044c519a98 退款已成功、
                            // TBL_APP_ORDER_REFUND.REFUND_STATUS=1，但 TBL_NOTICE_APP_REFUND_RECORD 零条、
                            // NoticeAppTask 每轮扫到 size=0，APP 永远停在「退款进行中」。
                            if (b) {
                                // 通知报文与落库都用给 app 的取值域（SUCCESS / FAIL），不要用 ItpStatusEnum 的 1 / 2
                                String appRefundResult = StringUtils.equals(refundStatus, PayCenterRefundStatusEnum.REFUND_SUCCESS.getCode())
                                        ? AppStatusEnum.REFUND_SUCCESS.getCode()
                                        : AppStatusEnum.REFUND_FAIL.getCode();
                                // APP 主动退款的 refundType 与 doRefund 给 app 的应答保持一致（00），TVM 故障退款仍为 01
                                String refundType = StringUtils.equals(businessType, BusinessTypeEnum.APP_REFUND.getCode()) ? "00" : "01";
                                int i = saveNoticeAppRefundResultRecord(payOrderNo, refundAmount, refundType, appRefundResult, refundDate);
                                log.info("保存通知app退款记录结束 i is {}", i);
                                // 这里retryTimes写死为1，因为明确这里是第一次发送
                                boolean b1 = noticeAppRefundResult(payOrderNo, appRefundResult, refundDate, refundAmount, "1");
                                log.info("通知app退款结束 b1 is {}", b1);
                            }
                        }
                    }
                });

                // 退款发起成功不修改退款状态，在支付中心发起退款通知并退款成功时再修改 receiveRefundResult接口
                return AppOrderResult.successData(result);

            } else {
                Map<String, String> upRefundOrder = new HashMap<>();
                upRefundOrder.put("refundNo", refundNo);
                upRefundOrder.put("refundStatus", ItpStatusEnum.REFUNDING_FAIL.getCode());
                upRefundOrder.put("refundMsg", ItpStatusEnum.REFUNDING_FAIL.getDesc());
                upRefundOrder.put("updateTime", now);

                int iRefund = appRefundOrderMapper.updateByRefundNo(upRefundOrder);
                log.info("退款失败，修改退款订单结束 i is {}", iRefund);

                result.put("refundResult", "FAIL");
                result.put("refundResultDesc", "退款失败");

                return AppOrderResult.successData(result);
            }

        } catch (Exception e) {
            log.error("发起退款异常, payOrderNo={}", payOrderNo);
            log.error("发起退款异常, e is {}", e);
            return AppOrderResult.failMessage("请求异常");
        }
    }

    private int saveNoticeAppRefundResultRecord(String orderNo, String refundAmount, String refundType, String refundResult, String refundDate) {

        Map<String, String> saveMap = new HashMap<>();
        saveMap.put("orderNo", orderNo);
        saveMap.put("refundType", refundType);
        // MUST 存给 app 的取值域（SUCCESS / FAIL）：NoticeAppTask 重试时会把这个值原样再推一次，
        // 存 ItpStatusEnum 的 "1" / "2" 会让重试报文与首次报文取值不一致。
        // 同时 NEVER 写死成功 —— dealAppRefundResult 在退款失败分支同样返回 true。
        saveMap.put("refundResult", refundResult);
        saveMap.put("refundResultDesc", refundResultDesc(refundResult));
        // MUST 存与首次通知报文完全一致的 yyyyMMddHHmmss 退款时间：NoticeAppTask:110 重试时
        // 直接把本列的值原样再推一次，这里存 DateUtils.getNowTimeByFormat("yyyyMMdd") 会让
        // 重试报文的 refundDate 变成 8 位当天日期，APP 侧同样解析失败。
        saveMap.put("refundDate", refundDate);
        saveMap.put("refundAmount", refundAmount);
        saveMap.put("status", ItpCommon.NOTICE_INIT);
        saveMap.put("createTime", DateUtils.getNowTime());
        saveMap.put("retryTimes", "0");
        int i = tvmNoticeAppMapper.insertRefundNotice(saveMap);
        log.info("通知app记录保存成功 i is {}", i);
        return i;
    }

    /**
     * 把支付中心返回的退款时间归一化为 APP 要求的 {@code yyyyMMddHHmmss}。
     *
     * <p>NEVER 再用 {@code refundTime.substring(0, 8)} —— 2026-08-27 生产事故：支付中心返回的
     * 退款时间是 ISO 形态 {@code 2026-08-27T12:55:57}，截前 8 位得到 {@code "2026-08-"}，
     * APP 侧解析 refundDate 直接失败（订单 0020260827131329f68e95f4）。</p>
     *
     * <p>这里剥掉所有非数字字符再取前 14 位（{@code 2026-08-27T12:55:57} → {@code 20260827125557}），
     * 对 {@code yyyyMMddHHmmss} 与 {@code yyyy-MM-dd HH:mm:ss} 两种形态都成立。</p>
     */
    private String toAppRefundDate(String refundTime) {
        String digits = refundTime == null ? "" : refundTime.replaceAll("\\D", "");
        if (digits.length() >= 14) {
            return digits.substring(0, 14);
        }
        log.warn("退款时间格式无法识别，用当前时间兜底, refundTime={}", refundTime);
        return DateUtils.getNowTimeByFormat("yyyyMMddHHmmss");
    }

    private String refundResultDesc(String appRefundResult) {
        if (StringUtils.equals(appRefundResult, AppStatusEnum.REFUND_SUCCESS.getCode())) {
            return AppStatusEnum.REFUND_SUCCESS.getDesc();
        }
        if (StringUtils.equals(appRefundResult, AppStatusEnum.REFUND_FAIL.getCode())) {
            return AppStatusEnum.REFUND_FAIL.getDesc();
        }
        return AppStatusEnum.REFUND_ING.getDesc();
    }


    @Override
    // 扫码取票业务 通知app退款结果
    public boolean noticeAppRefundResult(String payOrderNo, String refundResult, String refundDate, String refundAmount, String retryTimes) {

        boolean b = false;
        log.info("开始通知app退款结果");
        String refundResultDesc = refundResultDesc(refundResult);
        ItpCommonRequest<NoticeAppRefundDTO> request = payCenterCommon.buildNoticeAppRefundResultRequest(payOrderNo, refundResult, refundResultDesc, refundDate, refundAmount);

        log.info("开始通知app退款 request is {}", request.toString());
        String noticeAppRefundResultUrl = environment.getProperty("pay.center.notice-app-refundresult-url");
        log.info("noticeAppRefundResultUrl is {}", noticeAppRefundResultUrl);

        Map<String, Object> upMap = new HashMap<>();
        upMap.put("orderNo", payOrderNo);
        upMap.put("updateTime", DateUtils.getNowTime());
        // 此处是第一次推送，所以写死为1
        upMap.put("retryTimes", retryTimes);

        // 推送与解析 MUST 兜住异常：本方法在 tvmexecutor 线程里跑，抛出去没人接，
        // 通知记录会卡在 status=0 且 retryTimes 不递增，NoticeAppTask 反复扫到同一条。
        String retCode = null;
        try {
            String httpResult = httpUtils.doPostFormData(noticeAppRefundResultUrl, request);
            log.info("请求通知app退款结束 httpResult is {}", httpResult);
            JSONObject httpResultJson = (JSONObject) JSONObject.parse(httpResult);
            retCode = String.valueOf(httpResultJson.get("retCode"));
            log.info("请求通知app退款结束 retCode is {}", retCode);
        } catch (Exception e) {
            log.error("通知app退款结果异常, payOrderNo={}", payOrderNo, e);
        }

        // 如果收到成功则修改数据库
        if (StringUtils.equals(AppCodeEnum.SUCCESS.getCode(), retCode)) {
            log.info("通知成功，修改通知记录状态为成功");
            upMap.put("status", ItpCommon.NOTICE_SUCCESS);
            b = true;
        } else {
            log.info("通知失败，修改通知状态为失败");
            upMap.put("status", ItpCommon.NOTICE_FAIL);
        }

        int i = tvmNoticeAppMapper.updateRefundNoticeByOrderNo(upMap);
        log.info("修改通知记录状态结束 i is {}", i);
        return b;
    }

    private String getNowDate() {
        return new SimpleDateFormat("yyyyMMdd").format(new Date());
    }

    public static String getRefundTime(String refundTime) {

        Date date = null;
        try {
            date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(refundTime);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }

        String result = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);

        return result;
    }

    private boolean dealAppRefundResult(String payOrderNo, String refundNo, String refundStatus, String refundTime) {

        boolean b = false;
        String nowTime = DateUtils.getNowTime();
        if (StringUtils.equals(refundStatus, PayCenterRefundStatusEnum.REFUND_SUCCESS.getCode())) {
            log.info("查询到app退款成功的结果");

            Map<String, String> upRefundOrder = new HashMap<>();
            upRefundOrder.put("refundNo", refundNo);
            upRefundOrder.put("refundStatus", ItpStatusEnum.REFUND_SUCCESS.getCode());
            upRefundOrder.put("refundMsg", ItpStatusEnum.REFUND_SUCCESS.getDesc());
            upRefundOrder.put("refundTime", refundTime);
            upRefundOrder.put("updateTime", nowTime);

            int iRefund = appRefundOrderMapper.updateByRefundNo(upRefundOrder);
            log.info("app退款成功，修改退款订单结束 i is {}", iRefund);

            Map<String, String> upPayMap = new HashMap<>();
            upPayMap.put("orderNo", payOrderNo);
            upPayMap.put("rsv2", refundNo);
            upPayMap.put("updateTime", nowTime);
            int iPay = tvmAppOrderMapper.updateByOrderNo(upPayMap);
            log.info("退款成功，修改原支付订单结束 i is {}", iPay);

            // 返回itp的退款成功码
            b = true;

        } else if (StringUtils.equals(refundStatus, PayCenterRefundStatusEnum.REFUNDING_FAIL.getCode())) {
            log.info("查询到app退款状态为 退款失败");
            Map<String, String> upRefundOrder = new HashMap<>();
            upRefundOrder.put("refundNo", refundNo);
            upRefundOrder.put("refundStatus", ItpStatusEnum.REFUNDING_FAIL.getCode());
            upRefundOrder.put("refundMsg", ItpStatusEnum.REFUNDING_FAIL.getDesc());
            upRefundOrder.put("updateTime", nowTime);

            int iRefund = appRefundOrderMapper.updateByRefundNo(upRefundOrder);
            log.info("app退款失败，修改退款订单结束 i is {}", iRefund);

            b = true;
        } else {
            log.info("查询到app退款状态为 退款中/不明确 不做处理");
        }
        return b;
    }


    @Override
    public JSONObject requestRefundTicketResult(RequestPayResultReqDTO request) {

        log.info("app开始查询退款订单");

        String refundRrderNo = request.getOrderNo();

        AppRefundOrder appRefundOrder = appRefundOrderMapper.selectByRefundNo(refundRrderNo);

        if (ObjectUtils.isEmpty(appRefundOrder)) {
            return AppOrderResult.failMessage("没有找到对应退款记录");
        }

        JSONObject result = new JSONObject();
        result.put("orderNo", refundRrderNo);
        result.put("refundType", "00");
        result.put("refundDate", getNowDate());
        result.put("refundAmount", appRefundOrder.getRefundAmount());

        if (StringUtils.equals(appRefundOrder.getRefundStatus(), ItpStatusEnum.REFUND_SUCCESS.getCode())) {
            result.put("refundResult", AppStatusEnum.REFUND_SUCCESS.getCode());
            result.put("refundResultDesc", AppStatusEnum.REFUND_SUCCESS.getDesc());
            return AppOrderResult.successData(result);
        } else if (StringUtils.equals(appRefundOrder.getRefundStatus(), ItpStatusEnum.REFUND_ING.getCode())) {

            // 查询支付平台的退款结果
            PayCenterResponse payCenterResponse = tvmCommonService.queryRefundResult(refundRrderNo);

            if (payCenterResponse != null && StringUtils.equals(payCenterResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {

                Map<String, Object> data = payCenterResponse.getData();
                String refundResult = getStringFromData(data, "refundResult");
                String refundTime = getStringFromData(data, "refundDate");

                log.info("开始处理退款结果");
                // 处理退款结果
                String itpStatus = dealRefundResult(appRefundOrder.getPayOrderNo(), refundRrderNo, refundResult, refundTime);

                log.info("退款结果处理结束 itpStatus is {}", itpStatus);

                if (StringUtils.equals(itpStatus, ItpStatusEnum.REFUND_SUCCESS.getCode())) {
                    result.put("refundResult", AppStatusEnum.REFUND_SUCCESS.getCode());
                    result.put("refundResultDesc", AppStatusEnum.REFUND_SUCCESS.getDesc());
                    return AppOrderResult.successData(result);
                } else if (StringUtils.equals(itpStatus, ItpStatusEnum.REFUNDING_FAIL.getCode())) {
                    result.put("refundResult", AppStatusEnum.REFUND_FAIL.getCode());
                    result.put("refundResultDesc", AppStatusEnum.REFUND_FAIL.getDesc());
                    return AppOrderResult.successData(result);
                } else {
                    result.put("refundResult", AppStatusEnum.REFUND_ING.getCode());
                    result.put("refundResultDesc", AppStatusEnum.REFUND_ING.getDesc());
                    return AppOrderResult.successData(result);
                }

            } else {
                log.info("查询失败，不做处理");
            }
        } else {
            result.put("refundResult", AppStatusEnum.REFUND_FAIL.getCode());
            result.put("refundResultDesc", AppStatusEnum.REFUND_FAIL.getDesc());
            return AppOrderResult.successData(result);
        }
        result.put("refundResult", AppStatusEnum.REFUND_FAIL.getCode());
        result.put("refundResultDesc", AppStatusEnum.REFUND_FAIL.getDesc());
        return AppOrderResult.failData(result);
    }


    @Override
    public JSONObject requestPreActiveOrderList(RequestQueryActiveOrderReqDTO request) {

        log.info("service 开始查询激活订单 request is {}", request);

        // 01-青岛地铁
        if (StringUtils.equals(request.getAppType(), "01")) {

            log.info("青岛地铁查询");

            List<AppActiveOrderModel> list = new ArrayList<>();
            List<TvmAppOrder> tvmAppOrders = tvmAppOrderMapper.selectOrderLsByUserId(request.getUserId(), ActivateFlagEnum.ACTIVATE_ED.getCode());

            log.info("tvmAppOrders.size is {}", tvmAppOrders);
            for (TvmAppOrder order : tvmAppOrders) {

                AppActiveOrderModel model = new AppActiveOrderModel();
                model.setOrderNo(order.getOrderNo());
                model.setEntryStationCode(order.getInStationCode());
                model.setExitStationCode(order.getOutStationCode());
                model.setTicketPrice(order.getTicketPrice());
                model.setSingelTicketNum(order.getTicketNum());
                model.setSingleTicketType(order.getTicketType());
                model.setOrderDate(order.getCreateTime().replace("-", "").replace(" ", "").replace(":", ""));
                model.setPayDate(order.getPayTime());
                list.add(model);
            }

            log.info("组装查询结果 list.size is {}", tvmAppOrders);

            JSONObject result = new JSONObject();
            result.put("orderList", list);
            return AppOrderResult.successData(result);

        } else {

            log.info("非青岛地铁查询，结束");

            return AppOrderResult.fail(AppCodeEnum.FAIL.getCode(), "appType有误，请输入正确的值");
        }

    }

    @Override
    public JSONObject receiveRefundResult(APPRefundNotiResultReqDTO request) {

        // 退款状态
        String refundNo = request.getRefundNo();
        String refundResult = request.getRefundResult();
        String refundTime = request.getRefundDate();

        log.info("当前退款订单号为 {} 退款状态为 {}", refundNo, refundResult);

        AppRefundOrder appRefundOrder = appRefundOrderMapper.selectByRefundNo(refundNo);
        if (ObjectUtils.isEmpty(appRefundOrder)) {
            log.info("根据订单号查询退款订单为空");
            return AppOrderResult.fail(AppCodeEnum.FAIL.getCode(), "订单号错误");
        }

        // itp数据库的退款状态
        String dbRefundStatus = appRefundOrder.getRefundStatus();
        if (StringUtils.equals(dbRefundStatus, ItpStatusEnum.REFUND_SUCCESS.getCode())) {
            log.info("数据库查询到 明确的退款成功，直接返回");
            return AppOrderResult.success();
        }
        if (StringUtils.equals(dbRefundStatus, ItpStatusEnum.REFUNDING_FAIL.getCode())) {
            log.info("数据库查询到 退款失败结果，直接返回");
            return AppOrderResult.success();
        }

        log.info("开始处理业务");
        String itpStatus = dealRefundResult(appRefundOrder.getPayOrderNo(), refundNo, refundResult, refundTime);
        log.info("业务处理结束 itpStatus is {}", itpStatus);

        if (StringUtils.equals(itpStatus, ItpStatusEnum.REFUND_SUCCESS.getCode())) {
            log.info("退款成功 处理结束 ");
            return AppOrderResult.success();
        } else if (StringUtils.equals(itpStatus, ItpStatusEnum.REFUNDING_FAIL.getCode())) {
            log.info("退款失败 处理结束 ");
            return AppOrderResult.success();
        } else {
            log.info("退款中 处理结束 ");
            return AppOrderResult.success();
        }
    }

    private String dealRefundResult(String payOrderNo, String refundNo, String refundResult, String refundTime) {

        String nowTime = DateUtils.getNowTime();
        if (StringUtils.equals(refundResult, PayCenterRefundStatusEnum.REFUND_SUCCESS.getCode())) {
            log.info("查询到退款成功的结果");


            Map<String, String> upRefundOrder = new HashMap<>();
            upRefundOrder.put("refundNo", refundNo);
            upRefundOrder.put("refundStatus", ItpStatusEnum.REFUND_SUCCESS.getCode());
            upRefundOrder.put("refundMsg", ItpStatusEnum.REFUND_SUCCESS.getDesc());
            upRefundOrder.put("refundTime", refundTime);
            upRefundOrder.put("updateTime", nowTime);

            int iRefund = appRefundOrderMapper.updateByRefundNo(upRefundOrder);
            log.info("退款成功，修改退款订单结束 i is {}", iRefund);

            Map<String, String> upPayMap = new HashMap<>();
            upPayMap.put("orderNo", payOrderNo);
            upPayMap.put("rsv2", refundNo);
            upPayMap.put("updateTime", nowTime);
            int iPay = tvmAppOrderMapper.updateByOrderNo(upPayMap);
            log.info("退款成功，修改原支付订单结束 i is {}", iPay);

            // 返回itp的退款成功码
            return ItpStatusEnum.REFUND_SUCCESS.getCode();

        } else if (StringUtils.equals(refundResult, PayCenterRefundStatusEnum.REFUNDING_FAIL.getCode())) {
            log.info("查询到退款状态为 退款失败");
            Map<String, String> upRefundOrder = new HashMap<>();
            upRefundOrder.put("refundNo", refundNo);
            upRefundOrder.put("refundStatus", ItpStatusEnum.REFUNDING_FAIL.getCode());
            upRefundOrder.put("refundMsg", ItpStatusEnum.REFUNDING_FAIL.getDesc());
            upRefundOrder.put("updateTime", nowTime);

            int iRefund = appRefundOrderMapper.updateByRefundNo(upRefundOrder);
            log.info("退款失败，修改退款订单结束 i is {}", iRefund);

            return ItpStatusEnum.REFUNDING_FAIL.getCode();
        } else {
            log.info("查询到退款状态为 退款中/不明确 不做处理");

            return ItpStatusEnum.REFUND_ING.getCode();
        }
    }
}