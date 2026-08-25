package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.common.PayCenterCommon;
import com.chinasofti.huateng.collectpay.common.UpdateDbMap;
import com.chinasofti.huateng.collectpay.config.PayCenterProperties;
import com.chinasofti.huateng.collectpay.constant.*;
import com.chinasofti.huateng.collectpay.constant.ProductType;
import com.chinasofti.huateng.collectpay.entity.*;

import com.chinasofti.huateng.collectpay.mapper.*;


import com.chinasofti.huateng.collectpay.model.request.PayCenterRequest;
import com.chinasofti.huateng.collectpay.model.request.bom.*;
import com.chinasofti.huateng.collectpay.model.request.tvm.*;
import com.chinasofti.huateng.collectpay.model.response.PayCenterResponse;
import com.chinasofti.huateng.collectpay.model.response.app.AppOrderResult;
import com.chinasofti.huateng.collectpay.model.response.bom.BomOrderResult;
import com.chinasofti.huateng.collectpay.model.response.paycenter.PayCenterResult;
import com.chinasofti.huateng.collectpay.model.response.tvm.RequestRefundRespDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.TvmOrderResult;
import com.chinasofti.huateng.collectpay.service.*;
import com.chinasofti.huateng.collectpay.utils.*;


import com.chinasofti.huateng.model.enums.DeviceTypeEnum;

import com.chinasofti.huateng.model.app.UpdateHceDataReqDTO;
import com.chinasofti.huateng.model.app.UpdateHceDataResult;
import com.chinasofti.huateng.model.enums.DeviceTypeEnum;

import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BOM非现金业务服务实现类。
 * 实现BOM非现金收款业务的核心业务逻辑，包括下单、支付、查询支付结果、业务操作结果通知等。
 */
@Service
@Slf4j
public class BomOrderServiceImpl implements BomOrderService {


//    private final static String BOM_SALE = DeviceTypeEnum.BOM.getCode();
//    private final static String BOM_PAY = "02";

    /**
     * 日期时间格式化器，格式：yyyyMMddHHmmss。
     * 用于生成订单号。
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * BOM非现金收款订单Mapper。
     */
    @Autowired
    private BomNoCashOrderMapper bomNoCashOrderMapper;
    @Autowired
    private BomSaleOrderMapper bomSaleOrderMapper;
    @Autowired
    private BomMainTicketMapper bomMainTicketMapper;
    @Autowired
    private BomSubTicketMapper bomSubTicketMapper;
    @Autowired
    private TvmSubTicketMapper tvmSubTicketMapper;
    @Autowired
    private TvmMainTicketMapper tvmMainTicketMapper;
    @Autowired
    private TvmOrderMapper tvmOrderMapper;
    @Autowired
    private RefundOrderMapper refundOrderMapper;
    @Autowired
    private TvmCommonService tvmCommonService;
    @Autowired
    private AppOrderService appOrderService;
    @Autowired
    private TvmAppOrderMapper tvmAppOrderMapper;
    @Resource(name = "tvmexecutor")
    ThreadPoolTaskExecutor executor;

    /**
     * BOM业务操作结果通知Mapper。
     */
    @Autowired
    private BomBusResultMapper bomBusResultMapper;
    @Autowired
    private TvmOrderPreMapper tvmOrderPreMapper;

    /**
     * BOM退款订单Mapper。
     */
    @Autowired
    private BomRefundOrderMapper bomRefundOrderMapper;
    @Autowired
    private Environment environment;

    /**
     * TicketClient。
     */
    @Autowired
    private com.chinasofti.huateng.rpc.ticket.TicketClient ticketClient;

    /**
     * AccountClient。
     */
    @Autowired
    private com.chinasofti.huateng.rpc.account.AccountClient accountClient;

    /**
     * BOM充值结果通知Mapper。
     */
    @Autowired
    private BomTopupResultMapper bomTopupResultMapper;

    /**
     * TVM充值订单Mapper。
     */
    @Autowired
    private TvmTopupOrderMapper tvmTopupOrderMapper;

    /**
     * 支付中心服务。
     * 用于调用支付中心的支付和查询接口。
     */

    @Autowired
    private PayCenterService payCenterService;

    /**
     * 支付中心配置属性。
     * 包含商户号、API版本、签名类型、字符集、私钥等配置信息。
     */
    @Autowired
    private PayCenterProperties payCenterProperties;

    @Autowired
    PayCenterCommon payCenterCommon;

    @Autowired
    private com.chinasofti.huateng.collectpay.mapper.OrderSeqMapper orderSeqMapper;

    /**
     * IF8A-04 请求非现金收款下单。
     * BOM向ITP平台发起非现金收款订单请求，ITP生成订单并返回订单号。
     *
     * @param request 请求参数（包含公共参数deviceId等）
     * @return 响应结果，包含订单号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JSONObject requestGenNoCashOrder(RequestGenNoCashOrderReqDTO request) {
        log.info("1.开始处理BOM非现金收款下单, deviceId={}, request={}", request.getDeviceId(), request);

        String now = DateUtils.getNowTime();
        String orderNo = generateOrderNo();
        log.info("2.当前时间={}, 生成订单号={}", now, orderNo);

        BomNoCashOrder order = buildBomNoCashOrder(orderNo, request, now);
        log.info("3.构建BOM订单信息={}", order);
        int iBom = bomNoCashOrderMapper.insert(order);
        int iPre = tvmOrderPreMapper.insert(getBomOrderPayPre(order, request.getDeviceId()));
        if (iBom == 1 && iPre == 1) {
            return BomOrderResult.successData(orderNo);
        } else {
            return BomOrderResult.fail(BomPayCodeEnum.FAIL.getCode(), BomPayCodeEnum.FAIL.getMsg());
        }
    }

    @Override
    public JSONObject requestBomSaleOrder(RequestGenSjtOrderReqDTO request) {
        log.info("1.开始处理BOM售票下单, deviceId={}, request={}", request.getDeviceId(), request);

        String now = DateUtils.getNowTime();
        String orderNo = generateOrderNo();
        log.info("2.当前时间={}, 生成订单号={}", now, orderNo);

        BomNoCashOrder order = buildBomSaleOrder(orderNo, request, now);
        log.info("3.构建BOM订单信息={}", order);


        int iPre = tvmOrderPreMapper.insert(getBomOrderPayPre(order, request.getDeviceId()));
        int iBomTikcetInfo = bomSaleOrderMapper.insertBomSaleTicketInfo(getIBomTikcetInfo(request, orderNo));
        int iBom = bomNoCashOrderMapper.insertBomSale(order);

        log.info("iBom is {} iBomTikcetInfo is {} iPre is{}", iBom, iBomTikcetInfo, iPre);
        if (iBom == 1 && iPre == 1) {

            return BomOrderResult.successData(orderNo);
        } else {
            return BomOrderResult.fail(BomPayCodeEnum.FAIL.getCode(), BomPayCodeEnum.FAIL.getMsg());
        }

    }

    private Map<String, String> getIBomTikcetInfo(RequestGenSjtOrderReqDTO request, String orderNo) {
        Map<String, String> map = new HashMap<>();
        map.put("orderNo", orderNo);
        map.put("deviceId", request.getDeviceId());
        map.put("entryStationCode", request.getEntryStationCode());
        map.put("exitStationCode", request.getExitStationCode());
        map.put("ticketPrice", request.getTicketPrice());
        map.put("singelTicketNum", request.getSingelTicketNum());
        String totalPrice = String.valueOf(new BigDecimal(request.getTicketPrice()).multiply(new BigDecimal(request.getSingelTicketNum())));
        map.put("totalPrice", totalPrice);
        map.put("singleTicketType", request.getSingleTicketType());
        map.put("payType", request.getPayType());
        map.put("createTime", DateUtils.getNowTime());
        return map;
    }

    private Map<String, Object> getBomOrderPayPre(BomNoCashOrder order, String deviceId) {
        Map<String, Object> preMap = new HashMap<>();
        preMap.put("orderNo", order.getOrderNo());
        preMap.put("transAmount", order.getTransAount());
        preMap.put("deviceId", deviceId);
        preMap.put("transType", BusinessTypeEnum.BOM_SCANED_PAY.getCode());
        preMap.put("createTime", DateUtils.getNowTime());
        preMap.put("updateTime", "");
        return preMap;
    }

    /**
     * IF8A-05 扫码支付。
     * BOM扫描用户付款码后，向ITP平台发起支付请求，ITP调用支付中心完成支付。
     *
     * @param request 请求参数（包含订单号、付款码等）
     * @return 响应结果，包含支付结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JSONObject requestPayment(com.chinasofti.huateng.collectpay.model.request.bom.RequestPaymentReqDTO request) {
        log.info("1.开始处理BOM扫码支付, deviceId={}, request={}", request.getDeviceId(), request);

        // 查询订单信息
        BomNoCashOrder order = bomNoCashOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return BomOrderResult.fail(BomPayCodeEnum.ORDER_NO_ERROR.getCode(), "订单号错误,没有找到匹配的订单");
        }

        // 订单已支付成功，直接返回
        if (ItpStatusEnum.SUCCESS.getCode().equals(order.getStatus())) {
            log.info("订单已支付成功");
            return BomOrderResult.successPaymentResultWithMsg("SUCCESS", "支付成功", order.getMsg());
        }

        // 订单已支付失败，直接返回
        if (ItpStatusEnum.FAILED.getCode().equals(order.getStatus())) {
            log.info("订单已支付失败");
            return BomOrderResult.successPaymentResultWithMsg("FAILED", "支付失败", order.getMsg());
        }

        // 构建支付中心扫码支付请求
        PayCenterRequest payCenterRequest = payCenterCommon.buildBomPayRequest(order.getOrderNo(), order.getTransAount(), "bom支付", "地铁单程票", request.getPaymentCode(), request.getPaymentVendor());
        String bomPayUrl = payCenterProperties.getPayCenterPayUrl();
        log.info("4.bom支付请求 bomPayUrl is {} , payCenterRequest is {}", bomPayUrl, payCenterRequest);

        // 调用支付中心接口
        PayCenterResponse payResponse = payCenterService.callPayCenter(bomPayUrl, payCenterRequest);
        log.info("3.支付中心响应 payResponse={}", payResponse);

        Map<String, String> uMap = new HashMap<>();
        uMap.put("orderNo", request.getOrderNo());

        // 支付中心返回结果为空
        if (ObjectUtils.isEmpty(payResponse)) {
            log.info("4.支付中心返回结果为空");
            return BomOrderResult.successPaymentResultWithMsg("FAILED", "支付中心返回结果为空", "支付中心返回结果为空");
        }

        // 支付中心返回成功
        if (StringUtils.equals(payResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
            log.info("5.支付中心返回成功,开始轮询查询支付结果");

            // 轮询查询支付结果
            JSONObject bomPayResult = getBomPayResult(request.getOrderNo());
            if (StringUtils.equals(bomPayResult.get("retCode").toString(), BomPayCodeEnum.SUCCESS.getCode())) {
                return BomOrderResult.successPaymentResultWithMsg("SUCCESS", "支付成功", "支付成功");
            } else {
                return BomOrderResult.fail();
            }
        } else {
            // 返回非200 直接认为支付结果失败
            log.info("支付失败的结果");
            uMap.put("status", ItpStatusEnum.FAILED.getCode());
            uMap.put("msg", "支付失败");
            uMap.put("updateTime", DateUtils.getNowTime());
            bomNoCashOrderMapper.updateByOrderNo(uMap);
            return BomOrderResult.successPaymentResultWithMsg("FAILED", "支付失败", "支付失败");
        }
        // 支付结果不明确，按处理中返回
//        log.info("6.支付结果不明确");
//        return BomOrderResult.successPaymentResultWithMsg("PROCESSING", "处理中", "处理中");
    }

    private JSONObject getBomPayResult(String payOrderNo) {
        log.info("1.______轮询开始...");
        Integer timeOut = Integer.valueOf(environment.getProperty("bom.payTimeOut"));
        Integer payTimeInterval = Integer.valueOf(environment.getProperty("bom.payTimeInterval"));
        // 第min秒
        int min = 0;
        int b = 0;
        try {
            while (min < timeOut) {
                log.info("第 {} 次轮询,order_no is {}，refundId is {}", ++b, payOrderNo);
                JSONObject jsonObject = requestGetPayResult(payOrderNo);
                String retCode = String.valueOf(jsonObject.get("retCode"));
                String retMsg = String.valueOf(jsonObject.get("retMsg"));
                log.info("2.______轮询查询交易状态 retCode is {} retMsg is {} ", retCode, retMsg);
                min = min + payTimeInterval;
                log.info("3.______min is {}", min);
                // 如果查询结果异常,直接返回
                if (StringUtils.equals(retCode, BomPayCodeEnum.SUCCESS.getCode())) {
                    log.info("4.______结果为成功 返回");
                    return jsonObject;
                }
                log.info("4.______交易中，继续轮询");
                Thread.sleep(payTimeInterval * 1000);
            }

        } catch (Exception e) {
            log.error("2.______查询结果异常 e is {}", e);
            return BomOrderResult.fail();
        }
        return BomOrderResult.fail();
    }

    /**
     * IF8A-06 查询支付结果。
     * BOM轮询查询支付结果，ITP调用支付中心查询并返回支付状态。
     *
     * @return 响应结果，包含支付结果（SUCCESS/FAILED/PROCESSING）
     */
    @Override
    public JSONObject requestGetPayResult(String payOrderNo) {
        log.info("1.开始处理BOM查询支付结果,  payOrderNo={}", payOrderNo);

        // 查询订单信息
        BomNoCashOrder order = bomNoCashOrderMapper.selectByOrderNo(payOrderNo);
        if (order == null) {
            return BomOrderResult.fail(BomPayCodeEnum.ORDER_NO_ERROR.getCode(), "订单号错误,没有找到匹配的订单");
        }

        // 订单已支付成功，直接返回
        if (ItpStatusEnum.SUCCESS.getCode().equals(order.getStatus())) {
            log.info("2.数据库查询结果为支付成功，直接返回");
            return BomOrderResult.successPaymentResultWithMsg("SUCCESS", "支付成功", order.getMsg());
        }

        // 订单已支付失败，直接返回
        if (ItpStatusEnum.FAILED.getCode().equals(order.getStatus())) {
            log.info("2.数据库查询结果为支付失败，直接返回");
            return BomOrderResult.successPaymentResultWithMsg("FAILED", "支付失败", order.getMsg());
        }

        // 构建支付中心查询请求
        PayCenterRequest queryPayRequest = payCenterCommon.buildQueryPayCenterRequest(payOrderNo);
        String tvmQueryUrl = payCenterProperties.getPayCenterQueryUrl();
        PayCenterResponse queryPayResponse = payCenterService.callPayCenter(tvmQueryUrl, queryPayRequest);

        log.info("支付中心查询响应 queryPayResponse={}", queryPayResponse);

        // 支付中心返回结果为空
        if (ObjectUtils.isEmpty(queryPayResponse)) {
            log.info("3.支付中心返回结果为空");
        } else {
            // 支付中心返回成功
            if (StringUtils.equals(queryPayResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
                Map<String, String> uMap = new HashMap<>();
                uMap.put("orderNo", payOrderNo);
                Map<String, Object> data = queryPayResponse.getData();
                if (data != null) {
                    String status = TransforUtils.getStringFromData(data, "status");
                    String paymentChannelCode = TransforUtils.getStringFromData(data, "paymentVendor");
                    String payCenterOrderNo = TransforUtils.getStringFromData(data, "orderNo");
                    String payCenterChannelOrderNo = TransforUtils.getStringFromData(data, "channelOrderNo");

                    // 支付成功
                    if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {
                        log.info("查询到支付成功的结果");
                        uMap.put("status", ItpStatusEnum.SUCCESS.getCode());
                        uMap.put("msg", "支付成功");
                        uMap.put("channel", paymentChannelCode);
                        uMap.put("payCenterOrderNo", payCenterOrderNo);
                        uMap.put("payCenterChannelOrderNo", payCenterChannelOrderNo);
                        uMap.put("updateTime", DateUtils.getNowTime());
                        bomNoCashOrderMapper.updateByOrderNo(uMap);
                        return BomOrderResult.successPaymentResultWithMsg("SUCCESS", "支付成功", "支付成功");
                    }
                    // 支付失败
                    else if (PayCenterStatusEnum.FAILED.getCode().equals(status)) {
                        log.info("查询到支付失败的结果");
                        uMap.put("status", ItpStatusEnum.FAILED.getCode());
                        uMap.put("msg", "支付失败");
                        uMap.put("channel", paymentChannelCode);
                        uMap.put("updateTime", DateUtils.getNowTime());
                        bomNoCashOrderMapper.updateByOrderNo(uMap);
                        return BomOrderResult.successPaymentResultWithMsg("FAILED", "支付失败", "支付失败");
                    }
                }
            }
        }

        log.info("支付结果不明确，按处理中返回");
        // 支付结果不明确，按处理中返回
        return BomOrderResult.successPaymentResultWithMsg("PROCESSING", "处理中", "处理中");
    }

    /**
     * IF2A-08 业务操作结果通知。
     * BOM业务操作完成后，向ITP平台通知操作结果。
     * 处理逻辑：
     * 1. 生成通知记录并入库（状态为待处理）
     * 2. 如果optResult=SUCCESS，更新通知状态为处理成功，更新原订单状态
     * 3. 如果optResult=FAILED，入库通知记录，发起退款请求，根据退款结果更新通知状态，并将原支付记录的rsv2修改为退款订单号
     *
     * @param request 请求参数（包含订单号、操作结果等）
     * @return 响应结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JSONObject notiBusResult(NotiBusResultReqDTO request, String transType) {
        log.info("1.开始处理BOM业务操作结果通知, deviceId={}, request={}", request.getDeviceId(), request);

        String payOrderNo = request.getOrderNo();
        // 查询订单信息
        BomNoCashOrder order = bomNoCashOrderMapper.selectByOrderNo(payOrderNo);
        if (order == null) {
            return BomOrderResult.fail(BomPayCodeEnum.ORDER_NO_ERROR.getCode(), "订单号错误,没有找到匹配的订单");
        }

        // 生成通知ID
        String notifyId = generateNotifyId();
        String now = DateUtils.getNowTime();

        // 构建业务操作结果通知记录
        BomBusResult busResult = new BomBusResult();
        busResult.setNotifyId(notifyId);
        busResult.setOrderNo(payOrderNo);
        busResult.setDeviceId(request.getDeviceId());
        busResult.setOptResult(request.getOptResult());
        busResult.setStatus("0"); // 初始状态：已通知待处理
        busResult.setCreateTime(now);
        busResult.setUpdateTime(now);

        // 插入业务操作结果通知记录
        bomBusResultMapper.insert(busResult);
        log.info("2.业务操作结果通知记录已入库, notifyId={}", notifyId);

        // 根据操作结果进行处理
        if ("SUCCESS".equals(request.getOptResult())) {
            // 业务操作成功，更新通知状态为处理成功
            log.info("3.业务操作成功");

        } else if ("FAILED".equals(request.getOptResult())) {
            // 业务操作失败，发起退款
            log.info("3.业务操作失败，发起退款");

            String refundNo = generateRefundOrderNo();
            // 退款金额
            String refundAmount = order.getTransAount();

            // 业务通知失败发起退款
            BaseResult baseResult = this.doRefund(refundNo, now, request.getOrderNo(), refundAmount);
            log.info("5.退款结束 baseResult={}", baseResult);

            // 退款发起成功，首先更新通知记录状态
            this.updateBomBusResult(notifyId, refundNo, DateUtils.getNowTime());
        }

        log.info("7.BOM业务操作结果通知处理完成");
        return BomOrderResult.success();
    }

    private void getRefundResult(String payOrderNo, String refundNo) {

        executor.execute(new Runnable() {
            @Override
            public void run() {
                BaseResult baseResult = tvmCommonService.getPayCenterRefundResult(refundNo);
                log.info("退款查询业务处理结束，baseResult is {}", baseResult);
                if (baseResult.getErrorCode() == BaseResult.SUCCESS) {

                    JSONObject refundResultInfo = JSONObject.parseObject(baseResult.getData().toString());
                    String refundStatus = refundResultInfo.get("status").toString();
                    String refundTime = refundResultInfo.get("refundTime").toString();
                    boolean b = dealBomRefundResult(payOrderNo, refundNo, refundStatus, refundTime);
                    log.info("app处理退款业务逻辑结束 b is {}", b);
                }
            }
        });

    }

    private boolean dealBomRefundResult(String payOrderNo, String refundNo, String refundStatus, String refundTime) {

        boolean b = false;
        String nowTime = DateUtils.getNowTime();

        if (StringUtils.equals(refundStatus, PayCenterRefundStatusEnum.REFUND_SUCCESS.getCode())) {
            log.info("查询到退款成功的结果");

            // 更新退款订单状态
            updateBomRefundOrder(refundNo, ItpStatusEnum.REFUND_SUCCESS.getCode(), ItpStatusEnum.REFUND_SUCCESS.getDesc(), nowTime);
            // 更新原订单状态
            updateBomOrder(payOrderNo, refundNo, nowTime);

            b = true;

        } else if (StringUtils.equals(refundStatus, PayCenterRefundStatusEnum.REFUNDING_FAIL.getCode())) {
            log.info("查询到退款状态为 退款失败");
            // 更新退款订单状态
            updateBomRefundOrder(refundNo, ItpStatusEnum.REFUNDING_FAIL.getCode(), ItpStatusEnum.REFUNDING_FAIL.getDesc(), nowTime);
            // 更新原订单状态
            updateBomOrder(payOrderNo, refundNo, nowTime);
            b = true;
        } else {
            log.info("查询到退款状态为 退款中/不明确 不做处理");
        }
        return b;
    }

    // 外加一层，用于处理bom操作请求了tvm的接口的问题
    @Override
    public JSONObject notiBomSaleResult(NotiTakeTicketFailResultReqDTO request) {


        return null;
    }

    //
//    private String getTransAmountByTransType(String transType,){
//
//        String trasnAmount = "";
//        if(StringUtils.equals(BOM_SALE,transType)){
//            trasnAmount =
//        }
//    }

    private BaseResult doRefund(String refundNo, String now, String payOrderNo, String refundAmount) {

        boolean b = false;
        // 创建BOM退款订单记录，状态初始为退款中
        BomRefundOrder refundOrder = new BomRefundOrder();
        refundOrder.setRefundNo(refundNo);
        refundOrder.setPayOrderNo(payOrderNo);
        refundOrder.setMerchantRefundNo(refundNo);
        refundOrder.setRefundAmount(refundAmount);
        refundOrder.setRefundReason("业务操作失败");
        refundOrder.setRefundStatus("0"); // 0-退款中
        refundOrder.setRefundMsg("退款中");
        refundOrder.setCreateTime(now);
        refundOrder.setUpdateTime(now);
        bomRefundOrderMapper.insert(refundOrder);
        log.info("3.1.BOM退款订单记录已入库, refundNo={}", refundNo);

        // 构建退款请求并调用支付中心退款接口
        PayCenterRequest refundRequest = payCenterCommon.getRefundRequest(
                refundNo,
                payOrderNo,
                "", // payCenterOrderNo，根据实际情况填写
                Integer.parseInt(refundAmount)
        );
        String refundUrl = payCenterProperties.getPayCenterRefundUrl();
        log.info("4.调用支付中心退款接口, refundUrl={}, refundRequest={}", refundUrl, refundRequest);

        // 调用支付中心退款接口
        PayCenterResponse refundResponse = payCenterService.callPayCenter(refundUrl, refundRequest);

        // 处理退款结果
        if (ObjectUtils.isEmpty(refundResponse)) {
            // 退款结果为空，通知状态设为退款失败
            log.info("6.退款结果为空，不做处理");
        } else if (StringUtils.equals(refundResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
            // 退款成功
            log.info("6.bom发起退款成功");

            // 新开线程查询退款状态
            this.getRefundResult(payOrderNo,refundNo);

            return BaseResult.success();

        } else {
            // 退款失败
            log.info("6.退款发起失败");
            // 更新退款订单状态
            updateBomRefundOrder(refundNo, ItpStatusEnum.REFUNDING_FAIL.getCode(), ItpStatusEnum.REFUNDING_FAIL.getDesc(),now);
            // 更新原订单状态
            updateBomOrder(payOrderNo, refundNo, now);
        }

        return BaseResult.error();
    }

    /**
     * 充值结果通知。
     * BOM充值操作完成后，向ITP平台通知充值结果。
     * 处理逻辑：
     * 1. 生成通知记录并入库（状态为待处理）
     * 2. 如果topupStatus=01，更新通知状态为处理成功，更新原订单状态为成功
     * 3. 如果topupStatus=02，更新通知状态为处理失败，更新原订单状态为失败
     *
     * @param request 请求参数（包含订单号、充值状态等）
     * @return 响应结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public JSONObject notiTopupResult(NotiTopupResultReqDTO request) {
        log.info("1.开始处理BOM充值结果通知, deviceId={}, request={}", request.getDeviceId(), request);
        JSONObject result = BomOrderResult.success();

        // 查询TVM充值订单
        TvmTopupOrder order = tvmTopupOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.warn("2.TVM充值订单不存在, orderNo={}", request.getOrderNo());
            return BomOrderResult.fail("8999", "充值订单不存在");
        }
        log.info("2.TVM充值订单查询成功, orderNo={}, order={}", request.getOrderNo(), order);

        // 保存充值结果通知
        Map<String, String> topupResultMap = new HashMap<>();
        topupResultMap.put("orderNo", request.getOrderNo());
        topupResultMap.put("ticketLogicNum", order.getTicketLogicNum());
        topupResultMap.put("ticketPhysicsNum", order.getTicketPhysicsNum());
        topupResultMap.put("transDate", DateUtils.getNowTime());
        topupResultMap.put("transAmount", order.getTransAmount());
        topupResultMap.put("afterAmount", order.getTransAmount());
        topupResultMap.put("topupStatus", request.getTopupStatus());
        topupResultMap.put("transType", "01");

        bomTopupResultMapper.insertNotiy(topupResultMap);
        log.info("2.充值结果通知已入库, orderNo={}", request.getOrderNo());

        // 充值失败，发起退款
        if ("01".equals(request.getTopupStatus())) {
            log.info("3.充值失败，发起退款, orderNo={}, orderStatus={}, transAmount={}, payCenterOrderNo={}",
                    request.getOrderNo(), order.getStatus(), order.getTransAmount(), order.getPayCenterOrderNo());
            long seq = orderSeqMapper.nextval();
            String refundOrderNo = OrderNoUtils.generateRefundNo(seq);

            try {
                // 更新TVM充值订单状态为退款中
                Map<String, String> updateMap = new HashMap<>();
                updateMap.put("orderNo", request.getOrderNo());
                updateMap.put("status", "2"); // 2-支付失败
                updateMap.put("rsv2", refundOrderNo);
                updateMap.put("msg", "充值失败，退款中");
                updateMap.put("updateTime", DateUtils.getNowTime());
                tvmTopupOrderMapper.updateByOrderNo(updateMap);
                log.info("3.1.TVM充值订单状态已更新为退款中, orderNo={}, refundOrderNo={}", request.getOrderNo(), refundOrderNo);

                // 构建退款请求并调用支付中心退款接口
                PayCenterRequest refundRequest = payCenterCommon.getRefundRequest(
                        refundOrderNo,
                        request.getOrderNo(),
                        order.getPayCenterOrderNo(),
                        Integer.parseInt(order.getTransAmount())
                );
                String refundUrl = payCenterProperties.getPayCenterRefundUrl();
                String refundRequestJson = JSON.toJSONString(refundRequest);
                log.info("4.调用支付中心退款接口, refundUrl={}, refundOrderNo={}, refundRequestJson={}", refundUrl, refundOrderNo, refundRequestJson);

                PayCenterResponse refundResponse = payCenterService.callPayCenter(refundUrl, refundRequest);
                log.info("5.支付中心退款响应, refundOrderNo={}, refundResponse={}", refundOrderNo, refundResponse);

                // 创建退款记录
                RefundOrder refundOrder = new RefundOrder();
                refundOrder.setRefundNo(refundOrderNo);
                refundOrder.setPayOrderNo(request.getOrderNo());
                refundOrder.setMerchantRefundNo(refundOrderNo);
                refundOrder.setRefundAmount(Integer.parseInt(order.getTransAmount()));
                refundOrder.setRefundReason("充值失败");

                // 处理退款结果：支付中心响应200才是退款成功，其他都是退款失败
                if (ObjectUtils.isEmpty(refundResponse)) {
                    refundOrder.setRefundStatus(ItpStatusEnum.REFUNDING_FAIL.getCode());
                    refundOrder.setRefundMsg(ItpStatusEnum.REFUNDING_FAIL.getDesc());
                    log.warn("6.退款结果为空，通知状态设为退款失败, orderNo={}, refundOrderNo={}", request.getOrderNo(), refundOrderNo);

                    // 退款失败，更新TVM订单状态
                    Map<String, String> failUpdateMap = new HashMap<>();
                    failUpdateMap.put("orderNo", request.getOrderNo());
                    failUpdateMap.put("status", "2");
                    failUpdateMap.put("rsv2", refundOrderNo);
                    failUpdateMap.put("msg", "充值失败，退款失败：" + ItpStatusEnum.REFUNDING_FAIL.getDesc());
                    failUpdateMap.put("updateTime", DateUtils.getNowTime());
                    tvmTopupOrderMapper.updateByOrderNo(failUpdateMap);

                    result = BomOrderResult.fail("8999", "退款失败：" + ItpStatusEnum.REFUNDING_FAIL.getDesc());
                } else if (StringUtils.equals(refundResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
                    Map<String, Object> data = refundResponse.getData();
                    refundOrder.setMerchantRefundNo(getStringFromData(data, "merchantRefundNo"));
                    refundOrder.setChannelRefundNo(getStringFromData(data, "channelRefundNo"));
                    refundOrder.setRefundTime(getStringFromData(data, "refundTime"));
                    refundOrder.setRefundStatus(ItpStatusEnum.REFUND_SUCCESS.getCode());
                    refundOrder.setRefundMsg(ItpStatusEnum.REFUND_SUCCESS.getDesc());
                    log.info("6.退款成功, orderNo={}, refundOrderNo={}, merchantRefundNo={}, channelRefundNo={}, refundTime={}",
                            request.getOrderNo(), refundOrderNo,
                            refundOrder.getMerchantRefundNo(), refundOrder.getChannelRefundNo(), refundOrder.getRefundTime());
                } else {
                    String errorMsg = ObjectUtils.isEmpty(refundResponse) ? "调用支付中心退款失败" : refundResponse.getMsg();
                    refundOrder.setRefundStatus(ItpStatusEnum.REFUNDING_FAIL.getCode());
                    refundOrder.setRefundMsg(ItpStatusEnum.REFUNDING_FAIL.getDesc());
                    log.warn("6.退款失败, orderNo={}, refundOrderNo={}, errorCode={}, errorMsg={}",
                            request.getOrderNo(), refundOrderNo, refundResponse != null ? refundResponse.getCode() : "null", errorMsg);

                    // 退款失败，更新TVM订单状态并回滚原因
                    Map<String, String> failUpdateMap = new HashMap<>();
                    failUpdateMap.put("orderNo", request.getOrderNo());
                    failUpdateMap.put("status", "2");
                    failUpdateMap.put("rsv2", refundOrderNo);
                    failUpdateMap.put("msg", "充值失败，退款失败：" + errorMsg);
                    failUpdateMap.put("updateTime", DateUtils.getNowTime());
                    tvmTopupOrderMapper.updateByOrderNo(failUpdateMap);

                    result = BomOrderResult.fail("8999", "退款失败：" + errorMsg);
                }
                refundOrder.setCreateTime(DateUtils.getNowTime());
                refundOrderMapper.insert(refundOrder);
                log.info("7.退款记录已入库, refundNo={}, payOrderNo={}, refundAmount={}, refundStatus={}",
                        refundOrderNo, request.getOrderNo(), refundOrder.getRefundAmount(), refundOrder.getRefundStatus());

            } catch (Exception e) {
                log.error("发起退款异常, orderNo={}, refundOrderNo={}", request.getOrderNo(), refundOrderNo, e);
                // 异常时也创建退款记录
                RefundOrder refundOrder = new RefundOrder();
                refundOrder.setRefundNo(refundOrderNo);
                refundOrder.setPayOrderNo(request.getOrderNo());
                refundOrder.setMerchantRefundNo(refundOrderNo);
                refundOrder.setRefundAmount(Integer.parseInt(order.getTransAmount()));
                refundOrder.setRefundReason("充值失败");
                refundOrder.setRefundStatus(ItpStatusEnum.REFUNDING_FAIL.getCode());
                refundOrder.setRefundMsg("退款异常：" + e.getMessage());
                refundOrder.setCreateTime(DateUtils.getNowTime());
                refundOrderMapper.insert(refundOrder);
                log.error("8.退款异常记录已入库, refundNo={}, payOrderNo={}", refundOrderNo, request.getOrderNo());

                // 异常时更新TVM订单状态
                Map<String, String> failUpdateMap = new HashMap<>();
                failUpdateMap.put("orderNo", request.getOrderNo());
                failUpdateMap.put("status", "2");
                failUpdateMap.put("rsv2", refundOrderNo);
                failUpdateMap.put("msg", "充值失败，退款异常：" + e.getMessage());
                failUpdateMap.put("updateTime", DateUtils.getNowTime());
                tvmTopupOrderMapper.updateByOrderNo(failUpdateMap);

                result = BomOrderResult.fail("8999", "退款异常：" + e.getMessage());
            }
        } else if ("00".equals(request.getTopupStatus())) {
            // 充值成功
            log.info("3.充值成功");
            // 更新TVM订单状态为成功
            Map<String, String> updateMap = new HashMap<>();
            updateMap.put("orderNo", request.getOrderNo());
            updateMap.put("status", "1"); // 1-支付成功
            updateMap.put("updateTime", DateUtils.getNowTime());
            tvmTopupOrderMapper.updateByOrderNo(updateMap);
        } else {
            // 未知状态，按失败处理
            log.info("3.充值状态未知, topupStatus={}", request.getTopupStatus());
            Map<String, String> updateMap = new HashMap<>();
            updateMap.put("orderNo", request.getOrderNo());
            updateMap.put("status", "2"); // 2-支付失败
            updateMap.put("updateTime", DateUtils.getNowTime());
            tvmTopupOrderMapper.updateByOrderNo(updateMap);
        }

        log.info("7.BOM充值结果通知处理完成");
        return result;
    }

    /**
     * 生成退款订单号。
     *
     * @return 退款订单号
     */
    private String generateRefundOrderNo() {
        long seq = orderSeqMapper.nextval();
        return OrderNoUtils.generateRefundNo(seq);
    }

    /**
     * 从Map中获取字符串值。
     *
     * @param data Map数据
     * @param key  键名
     * @return 字符串值，不存在返回null
     */
    private String getStringFromData(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 更新BOM退款订单状态。
     *
     * @param refundNo   退款单号
     * @param status     退款状态
     * @param msg        状态描述
     * @param updateTime 更新时间
     */
    private int updateBomRefundOrder(String refundNo, String status, String msg, String updateTime) {
        Map<String, String> refundMap = new HashMap<>();
        refundMap.put("refundNo", refundNo);
        refundMap.put("refundStatus", status);
        refundMap.put("refundMsg", msg);
        refundMap.put("refundTime", "1".equals(status) ? updateTime : null); // 退款成功时记录退款时间
        refundMap.put("updateTime", updateTime);
        return bomRefundOrderMapper.updateByRefundNo(refundMap);
    }

    /**
     * 更新BOM业务操作结果通知记录状态。
     *
     * @param notifyId      通知ID
     * @param refundOrderNo 退款订单号
     * @param updateTime    更新时间
     */
    private int updateBomBusResult(String notifyId, String refundOrderNo, String updateTime) {
        Map<String, String> notifyMap = new HashMap<>();
        notifyMap.put("notifyId", notifyId);
        notifyMap.put("refundOrderNo", refundOrderNo);
        notifyMap.put("updateTime", updateTime);
        return bomBusResultMapper.updateByNotifyId(notifyMap);
    }

    /**
     * 更新BOM非现金收款订单状态。
     */
    private void updateBomOrder(String orderNo, String rsv2, String updateTime) {
        Map<String, String> orderMap = new HashMap<>();
        orderMap.put("orderNo", orderNo);
        if (rsv2 != null) {
            orderMap.put("rsv2", rsv2);
        }
        orderMap.put("updateTime", updateTime);
        bomNoCashOrderMapper.updateByOrderNo(orderMap);
    }

    /**
     * 构建BOM非现金收款订单实体对象。
     *
     * @param orderNo 订单号
     * @param request 请求参数
     * @param now     当前时间
     * @return 订单实体对象
     */
    private BomNoCashOrder buildBomNoCashOrder(String orderNo, RequestGenNoCashOrderReqDTO request, String now) {
        BomNoCashOrder order = new BomNoCashOrder();
        order.setOrderNo(orderNo);
        order.setDeviceId(request.getDeviceId());
        order.setTransType(request.getTransType());
        order.setAdminTransType(request.getAdminTransType());
        order.setOperaterId(request.getOperaterId());
        order.setShiftId(request.getShiftId());
        order.setCardId(request.getCardId());
        order.setTransAount(request.getTransAount());
        order.setBomOptSeq(request.getBomOptSeq());
        order.setStatus(ItpStatusEnum.PAYING.getCode()); // 初始状态为支付中
        order.setMsg(ItpStatusEnum.PAYING.getDesc()); // 初始状态描述
        order.setCreateTime(now);
        order.setUpdateTime(now);
        return order;
    }

    // bom发售订单信息
    private BomNoCashOrder buildBomSaleOrder(String orderNo, RequestGenSjtOrderReqDTO request, String now) {
        BomNoCashOrder order = new BomNoCashOrder();
        order.setOrderNo(orderNo);
        order.setDeviceId(request.getDeviceId());
        order.setTransType("01");
        order.setTransAount(getTotalPrice(request.getTicketPrice(), request.getSingelTicketNum()));
        order.setStatus(ItpStatusEnum.PAYING.getCode()); // 初始状态为支付中
        order.setMsg(ItpStatusEnum.PAYING.getDesc()); // 初始状态描述
        order.setCreateTime(now);
        order.setUpdateTime(now);
        return order;
    }

    private String getTotalPrice(String ticketPrice, String ticketNum) {
        BigDecimal price = new BigDecimal(ticketPrice);
        BigDecimal num = new BigDecimal(ticketNum);
        String totalPrice = price.multiply(num).toString();
        log.info("bom发售 totalPrice is {}", totalPrice);
        return totalPrice;
    }

    /**
     * 生成订单号。
     *
     * @return 订单号
     */
    private String generateOrderNo() {
        long seq = orderSeqMapper.nextval();
        return OrderNoUtils.generateOrderNo(ProductType.ordinaryTicket, seq);
    }

    /**
     * 生成通知ID。
     */
//    private String getStringFromData(Map<String, Object> data, String key) {
//        Object value = data.get(key);
//        return value != null ? value.toString() : null;
//    }
    @Override
    public JSONObject payNotice(PayNoticeReqDTO request) {
        // 查询订单信息
        log.info("bom支付结果通知 request is {}", request);
        BomNoCashOrder order = bomNoCashOrderMapper.selectByOrderNo(request.getMerchantOrderNo());
        if (order == null) {
            log.info("支付结果通知 订单不存在, orderNo={}", request.getOrderNo());
            return PayCenterResult.fail(PayCenterErrorCodeEnum.ORDER_NOT_EXIST.getCode(), PayCenterErrorCodeEnum.ORDER_NOT_EXIST.getMsg());
        }

        // 订单已支付成功，直接返回
        if (ItpStatusEnum.SUCCESS.getCode().equals(order.getStatus())) {
            log.info("支付结果通知 订单已支付成功 不做处理 返回成功");
            return PayCenterResult.success();
        }

        // 订单已支付失败，直接返回
        if (ItpStatusEnum.FAILED.getCode().equals(order.getStatus())) {
            log.info("支付结果通知 订单已支付失败 返回成功");
            return PayCenterResult.success();
        }

        log.info("开始处理 tvm扫码购票 支付结果通知 request is {}", request);
        Map<String, String> uMap = new HashMap<>();
        String status = request.getStatus();
        // itp订单号
        String orderNo = request.getMerchantOrderNo();
        // 支付中心订单号
        String payCenterOrderNo = request.getOrderNo();
        // 渠道订单号
        String channelOrderNo = request.getChannelOrderNo();

        String paymentVendor = request.getPaymentVendor();

        // 查询支付中心支付状态为支付成功
        if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {

            log.info("支付结果通知 支付成功 的结果");
            uMap = UpdateDbMap.getQueryUpdateSuccessDb(orderNo, payCenterOrderNo, channelOrderNo, paymentVendor);

        } else if (PayCenterStatusEnum.FAILED.getCode().equals(status)) {
            log.info("支付结果通知 支付失败 的结果");
            uMap = UpdateDbMap.getQueryUpdateFailDb(request.getOrderNo());
        } else {
            log.info("支付结果通知 不明确的结果，按已下单-支付中处理");
            return PayCenterResult.fail();
        }
        log.info("支付结果通知 开始修改记录 uMap is {}", uMap);
        int i = bomNoCashOrderMapper.updateByOrderNo(uMap);
        log.info("支付结果通知 修改结束 i is {}", i);
        return PayCenterResult.success();

    }

    private String generateNotifyId() {
        long seq = orderSeqMapper.nextval();
        return OrderNoUtils.generateOrderNo(ProductType.ordinaryTicket, seq);
    }

    /**
     * IF5A-01 请求票卡分析。
     * 后付费二维码票分析。
     *
     * @param request 请求参数（包含发行方代码、手机号、逻辑卡号、更新区域类型等）
     * @return 响应结果，包含票卡分析结果
     */
    @Override
    public JSONObject requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request) {
        log.info("IF5A-01 请求票卡分析, request={}", request);
        com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO rpcRequest = new com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO();
        rpcRequest.setProviderId(request.getProviderId());
        rpcRequest.setMsisdn(request.getMsisdn());
        rpcRequest.setCardId(request.getCardId());
        rpcRequest.setUpdateType(request.getUpdateType());
        log.info("IF5A-01 透传ticket-server参数, rpcRequest={}", rpcRequest);

        RequestCardDataAnalyseRespDTO rpcResponse = ticketClient.requestCardDataAnalyse(rpcRequest);
        if (rpcResponse == null) {
            log.warn("IF5A-01 票卡分析响应为空");
            return BomOrderResult.fail("8999", "票卡分析失败");
        }

        if (!"0000".equals(rpcResponse.getRetCode())) {
            return BomOrderResult.fail(rpcResponse.getRetCode(), rpcResponse.getRetMsg());
        }

        JSONObject result = BomOrderResult.success();
        result.put("providerId", rpcResponse.getProviderId());
        result.put("cardIssueDate", rpcResponse.getCardIssueDate());
        result.put("msisdn", rpcResponse.getMsisdn());
        result.put("cardId", rpcResponse.getCardId());
        result.put("cardStatus", rpcResponse.getCardStatus());
        result.put("lastLineCode", rpcResponse.getLastLineCode());
        result.put("lastStationCode", rpcResponse.getLastStationCode());
        result.put("lastUpdateDate", rpcResponse.getLastUpdateDate());
        result.put("lastTransAmout", rpcResponse.getLastTransAmout());
        result.put("lastTikcetTransSeq", rpcResponse.getLastTicketTransSeq());
        result.put("adviceOpt", rpcResponse.getAdviceOpt());
        result.put("managerCode", rpcResponse.getManagerCode());
        result.put("transAmount", rpcResponse.getTransAmount());
        return result;
    }

    /**
     * IF5A-03 请求票卡更新。
     *
     * @param request 请求参数（包含逻辑卡号、更新区域类型、建议操作类型、操作员编码、补站站点、更新时间、交易金额等）
     * @return 响应结果，包含最新行业数据
     */
    @Override
    public JSONObject requestCardDataUpdate(RequestCardDataUpdateReqDTO request) {
        log.info("IF5A-03 请求票卡更新, request={}", request);
        com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO rpcRequest = new com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO();
        rpcRequest.setCardId(request.getCardId());
        rpcRequest.setUpdateType(request.getUpdateType());
        rpcRequest.setAdviceOpt(request.getAdviceOpt());
        rpcRequest.setOperaterId(request.getOperaterId());
        rpcRequest.setUpdateStationCode(request.getUpdateStationCode());
        rpcRequest.setOptDate(request.getOptDate());
        rpcRequest.setTransAmount(request.getTransAmount());
        log.info("IF5A-03 透传ticket-server参数, rpcRequest={}", rpcRequest);

        RequestCardDataUpdateRespDTO rpcResponse = ticketClient.requestUpdateCardData(rpcRequest);
        if (rpcResponse == null) {
            log.warn("IF5A-03 票卡更新响应为空");
            return BomOrderResult.fail("8999", "票卡更新失败");
        }

        if (!"0000".equals(rpcResponse.getRetCode())) {
            return BomOrderResult.fail(rpcResponse.getRetCode(), rpcResponse.getRetMsg());
        }

        JSONObject result = BomOrderResult.success();
        result.put("cardData", rpcResponse.getCardData());
        return result;
    }


    @Override
    public JSONObject notiTakeTicketResult(NotiTakeTicketResultReqDTO request) {
        log.info("1.开始处理出票结果通知, deviceId={}, request={}", request.getDeviceId(), request);

        String payOrderNo = request.getOrderNo();

        // 这里指的是bom的订单
        TvmPayPreOrder payPreOrder = tvmOrderPreMapper.selectByOrderNo(payOrderNo);
        if (org.springframework.util.ObjectUtils.isEmpty(payPreOrder) || StringUtils.isEmpty(payPreOrder.getTransType()) || !StringUtils.equals(payPreOrder.getTransType(), BusinessTypeEnum.BOM_SCANED_PAY.getCode())) {
            return TvmOrderResult.failMessage( "没有找到匹配的订单，请确认订单号是否正确");
        }
        // 业务类型
        String transType = payPreOrder.getTransType();

        String businessType = transType;
        log.info("bom出票结果通知");
        BomSaleOrder bomSaleOrderTicketInfo = bomSaleOrderMapper.selectByOrderNo(request.getOrderNo());

        int buyNum = bomSaleOrderTicketInfo.getTicketNum();

        int actualNum = Integer.parseInt(request.getActualTakeTicketNum());

        // 保存出票主记录
        BomMainTicket mainTicket = new BomMainTicket();
        mainTicket.setId(bomMainTicketMapper.getBomMainTicketSeq());
        mainTicket.setOrderNo(request.getOrderNo());
        mainTicket.setActualTakeTicketNum(actualNum);
        mainTicket.setBuyTicketNum(buyNum);
        mainTicket.setTakeTickeDate(request.getTakeTickeDate());
        mainTicket.setBusinessType(businessType);
        mainTicket.setNotifyType("0"); // 0-出票结果通知
        mainTicket.setCreateTime(DateUtils.getNowTime());
        log.info("3.开始保存出票主记录, mainTicket={}", mainTicket);
        bomMainTicketMapper.insert(mainTicket);

        log.info("request.getTicketList() is {}", request.getTicketList());
        // 保存出票明细记录
        if (request.getTicketList() != null && !request.getTicketList().isEmpty()) {
            log.info("4.开始保存出票明细记录, 数量={}", request.getTicketList().size());
            for (NotiTakeTicketResultReqDTO.TicketInfo ticketInfo : request.getTicketList()) {
                BomSubTicket subTicket = new BomSubTicket();
                subTicket.setMainTicketId(mainTicket.getId());
                subTicket.setTicketLogicNum(ticketInfo.getTicketLogicNum());
                subTicket.setTransDate(ticketInfo.getTransDate());
                subTicket.setTransAmount(ticketInfo.getTransAmount());
                subTicket.setCreateTime(DateUtils.getNowTime());
                bomSubTicketMapper.insert(subTicket);
            }
        }

//        // 如果购票数量大于实际出票数量，发起退款
//        int refundAmount = handleRefund(payOrderNo, ticketPrice, buyNum, actualNum);
//        log.info("5.出票结果通知处理完成, orderNo={}, buyNum={}, actualNum={}, refundAmount={}",
//                request.getOrderNo(), buyNum, actualNum, refundAmount);

        // 该接口由tvm发起，返回给tvm
        return TvmOrderResult.success();
    }


    @Override
    public JSONObject notiTakeTicketFailResult(NotiTakeTicketFailResultReqDTO request) {
        log.info("1.开始处理Bom出票故障通知, deviceId={}, request={}", request.getDeviceId(), request);
        String payOrderNo = request.getOrderNo();

        // todo 保存故障记录

        TvmPayPreOrder payPreOrder = tvmOrderPreMapper.selectByOrderNo(payOrderNo);
        if (org.springframework.util.ObjectUtils.isEmpty(payPreOrder) || StringUtils.isEmpty(payPreOrder.getTransType()) || !StringUtils.equals(payPreOrder.getTransType(), BusinessTypeEnum.BOM_SCANED_PAY.getCode())) {
            return TvmOrderResult.failMessage( "没有找到匹配的订单，请确认订单号是否正确");
        }

        int buyNum = 0;
        String ticketPrice = "";

        BomSaleOrder bomSaleOrderTicketInfo = bomSaleOrderMapper.selectByOrderNo(request.getOrderNo());
        buyNum = bomSaleOrderTicketInfo.getTicketNum();
        ticketPrice = bomSaleOrderTicketInfo.getTicketPrice();

        int actualNum = 0;
        if (StringUtils.isNotEmpty(request.getActualTakeTicketNum())) {
            actualNum = Integer.parseInt(request.getActualTakeTicketNum());
        }

        // 保存出票故障主记录
        BomMainTicket mainTicket = new BomMainTicket();
        mainTicket.setId(bomMainTicketMapper.getBomMainTicketSeq());
        mainTicket.setOrderNo(request.getOrderNo());
        mainTicket.setActualTakeTicketNum(actualNum);
        mainTicket.setBuyTicketNum(buyNum);
        mainTicket.setTakeTickeDate(request.getFaultOccurDate());
        mainTicket.setNotifyType("1"); // 1-出票故障通知
        mainTicket.setFaultSlipSeq(request.getFaultSlipSeq());
        mainTicket.setErrorCode(request.getErrorCode());
        mainTicket.setErrorMessage(request.getErrorMessage());
        mainTicket.setCreateTime(DateUtils.getNowTime());
        log.info("3.开始保存出票故障主记录, mainTicket={}", mainTicket);
        bomMainTicketMapper.insert(mainTicket);

        // 保存出票明细记录（如果有）
        if (request.getTicketList() != null && !request.getTicketList().isEmpty()) {
            log.info("4.开始保存出票明细记录, 数量={}", request.getTicketList().size());
            for (NotiTakeTicketFailResultReqDTO.TicketInfo ticketInfo : request.getTicketList()) {
                BomSubTicket subTicket = new BomSubTicket();
                subTicket.setMainTicketId(mainTicket.getId());
                subTicket.setTicketLogicNum(ticketInfo.getTicketLogicNum());
                subTicket.setTransDate(ticketInfo.getTransDate());
                subTicket.setTransAmount(ticketInfo.getTransAmount());
                subTicket.setCreateTime(DateUtils.getNowTime());
                bomSubTicketMapper.insert(subTicket);
            }
        }

        // 如果购票数量大于实际出票数量，发起退款 退款时发送的是支付中心的订单号
        // 如果购票数量大于实际出票数量，发起退款
        int refundAmount = handleRefund(payOrderNo, ticketPrice, buyNum, actualNum);
        log.info("5.出票结果通知处理完成, orderNo={}, buyNum={}, actualNum={}, refundAmount={}",
                request.getOrderNo(), buyNum, actualNum, refundAmount);

        return TvmOrderResult.success();
    }

    private int handleRefund(String payOrderNo, String ticketPrice, int buyNum, int actualNum) {
        log.info("开始判断是否需要退款处理");
        if (buyNum > actualNum) {
            int refundNum = buyNum - actualNum;
            BigDecimal price = new BigDecimal(ticketPrice);
            BigDecimal refundAmount = price.multiply(new BigDecimal(refundNum));
            log.info("购票数量大于实际出票数量，发起退款, orderNo={}, buyNum={}, actualNum={}, refundAmount={}",
                    payOrderNo, buyNum, actualNum, refundAmount);


            String refundNo = generateRefundOrderNo();

            String now = DateUtils.getNowTime();
            BaseResult baseResult = this.doRefund(refundNo, now, payOrderNo, String.valueOf(refundAmount));

            log.info("退款结束 baseResult is {}", baseResult);

            return refundAmount.intValue();
        } else {
            log.info("出票数量和购买数量相等，不发起退款");
        }
        return 0;
    }

    @Override
    public JSONObject requestOrderTResult(RequestOrderResultReqDTO request) {

        log.info("开始处理");
        Map<String, String> condition = new HashMap<>();
        condition.put("ticketLogicNum", request.getTicketLogicNum());
        condition.put("transDate", request.getTransDate());

        // 1.根据逻辑卡号查询对应的出票信息
        SubTicket subTicket = tvmSubTicketMapper.selectSubTickettByCondition(condition);
        log.info("subTicket is {}", subTicket);
        if (ObjectUtils.isEmpty(subTicket)) {
            log.info("无对应的出票子信息");
            return BomOrderResult.failMessage("没有查找到出票信息");
        }

        JSONObject orderResult = new JSONObject();
        // 如果是tvm的订单，则执行tvm的表、逻辑
        if (StringUtils.equals(subTicket.getBusinessType(), "tvm")) {
            log.info("当前票卡是tvm订单");
            // todo 判断当前订单是扫码购票还是扫码取票，然后查不同的表

            // 2.根据出票子信息的mainid查询主出票信息
            TvmMainTicket tvmMainTicket = tvmMainTicketMapper.selectById(Long.valueOf(subTicket.getMainTicketId()));
            log.info("tvmMainTicket is {}", tvmMainTicket);
            if (ObjectUtils.isEmpty(tvmMainTicket)) {
                log.info("无对应的出票主信息");
                return BomOrderResult.failMessage("没有查找到出票主信息");
            }

            String payOrderNo = tvmMainTicket.getOrderNo();
            if (StringUtils.equals(tvmMainTicket.getBusinessType(), BusinessTypeEnum.TVM_SCAN_QR_BUYTICKET.getCode())) {
                orderResult = getTvmOrderInfo(payOrderNo);
            } else {
                orderResult = getTvmAppOrderInfo(payOrderNo);
            }

        } else if (StringUtils.equals(subTicket.getBusinessType(), "bom")) {
            log.info("当前票卡是bom订单");
            orderResult = getBomOrderInfo(subTicket);
        }

        log.info("orderResult is {}", orderResult);

        if (!StringUtils.equals(String.valueOf(orderResult.get("retCode")), BomPayCodeEnum.SUCCESS.getCode())) {
            log.info("查询结果失败 orderResult is{}", orderResult);
            return orderResult;
        }

        JSONObject result = new JSONObject();
        String status = orderResult.getString("status");
        String orderNo = orderResult.getString("orderNo");
        String ticketPrice = orderResult.getString("ticketPrice");
        String channel = orderResult.getString("channel");
        if (StringUtils.equals(status, ItpStatusEnum.SUCCESS.getCode())) {
            result.put("paymentResult", PayCenterStatusEnum.SUCCESS.getCode());
            result.put("paymentResultDesc", PayCenterStatusEnum.SUCCESS.getDesc());
        } else if (StringUtils.equals(status, ItpStatusEnum.FAILED.getCode())) {
            result.put("paymentResult", PayCenterStatusEnum.FAILED.getCode());
            result.put("paymentResultDesc", PayCenterStatusEnum.FAILED.getDesc());
        } else {
            result.put("paymentResult", PayCenterStatusEnum.UNPAID.getCode());
            result.put("paymentResultDesc", PayCenterStatusEnum.UNPAID.getDesc());
        }
        result.put("orderNo", orderNo);
        result.put("transDate", subTicket.getTransDate());
        result.put("transAmount", ticketPrice);
        result.put("paymentChannelCode", channel);

        return BomOrderResult.successData(result);
    }

    private JSONObject getTvmOrderInfo(String payOrderNo) {

        // 3.根据出票主信息中的订单号查询支付信息
        TvmPayOrder tvmPayOrder = tvmOrderMapper.selectByOrderNo(payOrderNo);
        log.info("tvmPayOrder is {}", tvmPayOrder);
        if (ObjectUtils.isEmpty(tvmPayOrder)) {
            log.info("无对应的订单信息");
            return BomOrderResult.failMessage("无对应的订单信息");
        }

        // 判断该订单是否发起过退款，这部分代码用于后续追溯，实际业务用不到
        if (StringUtils.isEmpty(tvmPayOrder.getRsv2())) {
            // 查询已退款总金额
            String refundTotalAmt = refundOrderMapper.selectRefundTotalAmtByPayOderNo(tvmPayOrder.getOrderNo());
            String totalPrice = tvmPayOrder.getTotalPrice();
            log.info("该订单总金额是 {}  已退款总金额是 {}", totalPrice, refundTotalAmt);

        }

        JSONObject result = new JSONObject();
        result.put("status", tvmPayOrder.getStatus());
        result.put("orderNo", tvmPayOrder.getOrderNo());
        result.put("ticketPrice", tvmPayOrder.getTicketPrice());
        result.put("channel", tvmPayOrder.getChannel());

        return BomOrderResult.successData(result);
    }

    private JSONObject getTvmAppOrderInfo(String payOrderNo) {

        // 3.根据出票主信息中的订单号查询支付信息
        TvmAppOrder tvmAppOrder = tvmAppOrderMapper.selectByOrderNo(payOrderNo);
        log.info("tvmAppOrder is {}", tvmAppOrder);
        if (ObjectUtils.isEmpty(tvmAppOrder)) {
            log.info("无对应的订单信息");
            return BomOrderResult.failMessage("无对应的订单信息");
        }

        // 判断该订单是否发起过退款，这部分代码用于后续追溯，实际业务用不到
        if (StringUtils.isEmpty(tvmAppOrder.getRsv2())) {
            // 查询已退款总金额
            String refundTotalAmt = refundOrderMapper.selectRefundTotalAmtByPayOderNo(tvmAppOrder.getOrderNo());
            String totalPrice = tvmAppOrder.getTotalPrice();
            log.info("该订单总金额是 {}  已退款总金额是 {}", totalPrice, refundTotalAmt);

        }

        JSONObject result = new JSONObject();
        result.put("status", tvmAppOrder.getPayStatus());
        result.put("orderNo", tvmAppOrder.getOrderNo());
        result.put("ticketPrice", tvmAppOrder.getTicketPrice());
        result.put("channel", tvmAppOrder.getPayChannelCode());

        return BomOrderResult.successData(result);
    }

    private JSONObject getBomOrderInfo(SubTicket subTicket) {
        // 2.根据出票子信息的mainid查询主出票信息
        BomMainTicket bomMainTicket = bomMainTicketMapper.selectById(Long.valueOf(subTicket.getMainTicketId()));
        log.info("bomMainTicket is {}", bomMainTicket);
        if (ObjectUtils.isEmpty(bomMainTicket)) {
            log.info("无对应的出票主信息");
            return BomOrderResult.failMessage("没有查找到出票主信息");
        }

        String payNrderNo = bomMainTicket.getOrderNo();

        // 3.根据出票主信息中的订单号查询支付信息
        BomSaleOrder bomPayOrder = bomSaleOrderMapper.selectByOrderNo(payNrderNo);
        log.info("bomPayOrder is {}", bomPayOrder);
        if (ObjectUtils.isEmpty(bomPayOrder)) {
            log.info("无对应的订单信息");
            return BomOrderResult.failMessage("无对应的订单信息");
        }

        BomNoCashOrder bomNoCashOrder = bomNoCashOrderMapper.selectByOrderNo(payNrderNo);

        // 判断该订单是否发起过退款，这部分代码用于后续追溯，实际业务用不到
        if (StringUtils.isEmpty(bomNoCashOrder.getRsv2())) {
            // 查询已退款总金额
            String refundTotalAmt = refundOrderMapper.selectRefundTotalAmtByPayOderNo(bomNoCashOrder.getOrderNo());
            String totalPrice = String.valueOf(bomPayOrder.getTotalPrice());
            log.info("该订单总金额是 {}  已退款总金额是 {}", totalPrice, refundTotalAmt);

        }

        JSONObject result = new JSONObject();
        result.put("status", bomNoCashOrder.getStatus());
        result.put("orderNo", bomPayOrder.getOrderNo());
        result.put("ticketPrice", bomPayOrder.getTicketPrice());
        result.put("channel", bomNoCashOrder.getChannel());
        return BomOrderResult.successData(result);
    }

//    @Override
//    public JSONObject requestTicketTRefund(RequestTicketRefundReqDTO request) {
//        log.info("开始退款");
//        String refundOrderNo = generateRefundOrderNo();
//        String now = DateUtils.getNowTime();
//
//        request.setRefundNo(refundOrderNo);
//        int i = bomNoCashOrderMapper.insertTicketRefund(request);
//        log.info("保存退款请求记录信息结束 i is {}", i);
//        if (i > 0) {
//            PayCenterResponse refundResponse = this.doRefund(refundOrderNo, now, request.getOrderNo(), request.getTransAmount());
//            log.info("5.支付中心退款响应  refundResponse={}", refundResponse);
//            // 处理退款结果
//            if (ObjectUtils.isEmpty(refundResponse)) {
//                // 退款结果为空，通知状态设为退款失败
//                log.info("6.退款结果为空， 通知状态设为退款失败");
//                // 更新退款订单状态
//                updateBomRefundOrder(refundOrderNo, "2", "退款失败", now);
//                return BomOrderResult.fail();
//            } else if (StringUtils.equals(refundResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
//                // 退款成功
//                log.info("6.退款成功 ");
//                // 更新退款订单状态
//                updateBomRefundOrder(refundOrderNo, "1", "退款成功", now);
//                // 更新通知记录状态
//                updateBomOrder(request.getOrderNo(), refundOrderNo, now);
//
//                return BomOrderResult.success();
//            } else {
//                // 退款失败
//                log.info("6.退款失败 ");
//                // 更新退款订单状态
//                updateBomRefundOrder(refundOrderNo, "2", "退款失败", now);
//                // 更新原订单状态
//                updateBomOrder(request.getOrderNo(), refundOrderNo, now);
//                return BomOrderResult.fail();
//            }
//        } else {
//            log.info("保存请求退款信息失败");
//            return BomOrderResult.fail();
//        }
//    }

    @Override
    public JSONObject requestTicketTRefund(RequestTicketRefundReqDTO request) {
        log.info("开始退款");

        String payOrderNo = request.getOrderNo();
        int i = bomNoCashOrderMapper.insertTicketRefundRecord(request);

        log.info("保存退款请求记录信息结束 i is {}", i);
        if (i > 0) {

            TvmPayPreOrder payPreOrder = tvmOrderPreMapper.selectByOrderNo(payOrderNo);

            JSONObject jsonObject = new JSONObject();

            if (StringUtils.equals(payPreOrder.getTransType(), BusinessTypeEnum.TVM_SCAN_QR_BUYTICKET.getCode())) {
                log.info("单程票发起退款 业务类型为 tvm扫码购票");
                // 扫码购票
                jsonObject = tvmBuyRefund(request);
            } else if (StringUtils.equals(payPreOrder.getTransType(), BusinessTypeEnum.BOM_SCANED_PAY.getCode())) {
                log.info("单程票发起退款 业务类型为 bom订单");
                // bom订单
                jsonObject = bomRefund(request);
            } else if (StringUtils.equals(payPreOrder.getTransType(), BusinessTypeEnum.TVM_SCAN_QR_TAKETICKET.getCode())) {
                log.info("单程票发起退款 业务类型为 扫码取票");
                // 扫码取票
                jsonObject = tvmTakeRefund(request);
            } else {
                log.info("非法业务类型的订单");
                return BomOrderResult.failMessage("非法业务类型的订单");
            }

            if (StringUtils.equals(jsonObject.get("retCode").toString(), "0000")) {
                log.info("退款成功");
                String refundNo = String.valueOf(jsonObject.get("refundNo"));

                Map<String, String> upMap = new HashMap<>();
                upMap.put("rsv2", refundNo);
                upMap.put("ticketLogicNum", request.getTicketLogicNum());

                log.info("upMap is {}", upMap);

                // 如果是bom订单，则修改bom sub表对应的票卡记录的rsv2字段，其余类型都按tvm处理
                if (StringUtils.equals(payPreOrder.getTransType(), BusinessTypeEnum.BOM_SCANED_PAY.getCode())) {
                    log.info("开始修改bom sub 表");
                    int i1 = bomSubTicketMapper.updateByTicketLogicNum(upMap);
                    log.info("修改bom sub 表结束 i is {}", i1);
                } else {
                    log.info("开始修改tvm sub 表");
                    int i1 = tvmSubTicketMapper.updateByTicketLogicNum(upMap);
                    log.info("修改tvm sub 表结束 i is {}", i1);
                }
                log.info("退款成功 结束");
                return BomOrderResult.success();
            } else {
                log.info("退款失败");
                return BomOrderResult.fail();
            }

        } else {
            log.info("保存请求退款信息失败");
            return BomOrderResult.fail();
        }


    }

    private JSONObject tvmBuyRefund(RequestTicketRefundReqDTO request) {

        String payOrderNo = request.getOrderNo();
        // 查询订单信息
        TvmPayOrder order = tvmOrderMapper.selectByOrderNo(payOrderNo);
        if (order == null) {
            log.info("2.没有找到匹配的订单, orderNo={}", payOrderNo);
            return BomOrderResult.fail("9999", "订单号错误,没有找到匹配的订单");
        }

        // 订单已支付成功才退款
        if (!ItpStatusEnum.SUCCESS.getCode().equals(order.getStatus())) {
            log.info("2.订单状态不是支付成功, 不能退款, status={}", order.getStatus());
            return BomOrderResult.fail("9999", "订单状态不是支付成功,不能退款");
        }

        String refundNo = OrderCommonUtils.getRefundNo();
        boolean b = tvmCommonService.doRefund(BusinessTypeEnum.TVM_SCAN_QR_BUYTICKET.getCode(), payOrderNo, order.getPayCenterOrderNo(), Integer.valueOf(request.getTransAmount()), refundNo);
        // 如果refundNo不为空，则证明退款结束 退款结果可以是成功的也可以是失败的
        log.info("退款解释 refundNo is {}", refundNo);
        if (b) {
            // 6. 更新原支付订单的rsv2字段（退款记录ID），不修改原支付状态
            Map<String, String> upMap = new HashMap<>();
            upMap.put("orderNo", order.getOrderNo());
            upMap.put("rsv2", refundNo);
            upMap.put("updateTime", DateUtils.getNowTime());
            tvmOrderMapper.updateByOrderNo(upMap);
        }
        log.info("退款结束");
        JSONObject result = new JSONObject();
        result.put("refundNo", refundNo);
        return BomOrderResult.successData(result);

    }

    // bom发起的退款，如果是app下单，扫码取票出来的票，发起退款后要通知app退款结果
    private JSONObject tvmTakeRefund(RequestTicketRefundReqDTO request) {
        String refundOrderNo = generateRefundOrderNo();
        log.info("refundOrderNo is {}", refundOrderNo);
        JSONObject result = appOrderService.doRefund(request.getOrderNo(), request.getTransAmount(), refundOrderNo, BusinessTypeEnum.TVM_SCAN_QR_TAKETICKET.getCode());
        result.put("refundNo", refundOrderNo);
        return BomOrderResult.successData(result);
    }

    private JSONObject bomRefund(RequestTicketRefundReqDTO request) {

        String payOrderNo = request.getOrderNo();

        String now = DateUtils.getNowTime();

        String refundNo = generateRefundOrderNo();

        BaseResult baseResult = this.doRefund(refundNo, now, payOrderNo, request.getTransAmount());
        log.info("5.退款结束 baseResult={}", baseResult);

        if(baseResult.getErrorCode()==BaseResult.SUCCESS){
            JSONObject result = new JSONObject();
            result.put("refundNo", refundNo);
            return BomOrderResult.successData(result);
        }else {
            return BomOrderResult.fail();
        }
    }


    /**
     * IF5A-09 HCE票卡更新结果通知。
     * BOM更新HCE票数据后，向ITP平台通知更新结果。
     * 保存通知记录到TBL_BOM_BUS_RESULT表，状态设为处理成功。
     *
     * @param request 请求参数（包含卡号、HCE数据、操作类型等）
     * @return 响应结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public JSONObject notiUpdateHceData(NotiUpdateHceDataReqDTO request) {
        log.info("1.开始处理IF5A-09 HCE票卡更新结果通知, deviceId={}, request={}", request.getDeviceId(), request);

        // 参数校验
        if (request == null || StringUtils.isEmpty(request.getCardId())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "cardId不能为空");
        }
        if (StringUtils.isEmpty(request.getHceData())) {
            return BomOrderResult.fail(BomPayCodeEnum.INVALID_PARAM.getCode(), "hceData不能为空");
        }

        // 生成通知ID
        String notifyId = generateNotifyId();
        String now = DateUtils.getNowTime();

        // 构建通知记录
        BomBusResult busResult = new BomBusResult();
        busResult.setNotifyId(notifyId);
        busResult.setOrderNo(request.getCardId());
        busResult.setDeviceId(request.getDeviceId());
        busResult.setOptResult("SUCCESS");
        busResult.setStatus("1"); // 1-处理成功
        busResult.setCreateTime(now);
        busResult.setUpdateTime(now);
        busResult.setRsv1(request.getAdviceOpt());
        busResult.setRsv2(request.getHceData());

        // 插入通知记录
        bomBusResultMapper.insert(busResult);
        log.info("2.HCE票卡更新结果通知记录已入库, notifyId={}, cardId={}", notifyId, request.getCardId());

        // 调用账户系统回写 HCE 数据
        UpdateHceDataReqDTO hceDataReq = new UpdateHceDataReqDTO();
        hceDataReq.setCardId(request.getCardId());
        hceDataReq.setHceData(request.getHceData());
        try {
            UpdateHceDataResult hceResult = accountClient.updateHceData(hceDataReq);
            if (hceResult == null || !"0000".equals(hceResult.getRetCode())) {
                log.warn("3.HCE数据回写失败, cardId={}, result={}", request.getCardId(), hceResult);
                busResult.setStatus("3"); // 3-处理失败
                busResult.setUpdateTime(DateUtils.getNowTime());
                Map<String, String> resultMap = new HashMap<>();
                resultMap.put("notifyId", notifyId);
                resultMap.put("optResult", "FAILED");
                resultMap.put("updateTime", DateUtils.getNowTime());
                bomBusResultMapper.updateByNotifyId(resultMap);
            } else {
                log.info("3.HCE数据回写成功, cardId={}", request.getCardId());
            }
        } catch (Exception e) {
            log.error("3.HCE数据回写异常, cardId={}, error={}", request.getCardId(), e.getMessage(), e);
            busResult.setStatus("3");
            busResult.setUpdateTime(DateUtils.getNowTime());
            Map<String, String> resultMap = new HashMap<>();
            resultMap.put("notifyId", notifyId);
            resultMap.put("optResult", "FAILED");
            resultMap.put("updateTime", DateUtils.getNowTime());
            bomBusResultMapper.updateByNotifyId(resultMap);
        }

        log.info("4.IF5A-09 HCE票卡更新结果通知处理完成, notifyId={}", notifyId);
        return BomOrderResult.success();
    }

}

