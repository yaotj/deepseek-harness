package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson.JSONObject;
import com.chinasofti.huateng.collectpay.common.PayCenterCommon;
import com.chinasofti.huateng.collectpay.config.PayCenterProperties;
import com.chinasofti.huateng.collectpay.constant.BomPayCodeEnum;
import com.chinasofti.huateng.collectpay.constant.ItpStatusEnum;
import com.chinasofti.huateng.collectpay.constant.PayCenterErrorCodeEnum;
import com.chinasofti.huateng.collectpay.constant.PayCenterStatusEnum;
import com.chinasofti.huateng.collectpay.entity.BomBusResult;
import com.chinasofti.huateng.collectpay.entity.BomNoCashOrder;
import com.chinasofti.huateng.collectpay.entity.BomRefundOrder;
import com.chinasofti.huateng.collectpay.mapper.BomBusResultMapper;
import com.chinasofti.huateng.collectpay.mapper.BomNoCashOrderMapper;
import com.chinasofti.huateng.collectpay.mapper.BomRefundOrderMapper;
import com.chinasofti.huateng.collectpay.model.request.PayCenterRequest;
import com.chinasofti.huateng.collectpay.model.request.bom.NotiBusResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestGenNoCashOrderReqDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestGetPayResultReqDTO;
import com.chinasofti.huateng.collectpay.model.request.bom.RequestPaymentReqDTO;
import com.chinasofti.huateng.collectpay.model.response.PayCenterResponse;
import com.chinasofti.huateng.collectpay.model.response.bom.BomOrderResult;
import com.chinasofti.huateng.collectpay.service.BomOrderService;
import com.chinasofti.huateng.collectpay.service.PayCenterService;
import com.chinasofti.huateng.collectpay.utils.DateUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * BOM非现金业务服务实现类。
 * 实现BOM非现金收款业务的核心业务逻辑，包括下单、支付、查询支付结果、业务操作结果通知等。
 */
@Service
@Slf4j
public class BomOrderServiceImpl implements BomOrderService {

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

    /**
     * BOM业务操作结果通知Mapper。
     */
    @Autowired
    private BomBusResultMapper bomBusResultMapper;

    /**
     * BOM退款订单Mapper。
     */
    @Autowired
    private BomRefundOrderMapper bomRefundOrderMapper;

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
        bomNoCashOrderMapper.insert(order);

        return BomOrderResult.successData(orderNo);
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
    public JSONObject requestPayment(RequestPaymentReqDTO request) {
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

        PayCenterRequest payCenterRequest = payCenterCommon.buildBomPayRequest(order.getOrderNo(),order.getTransAount(),"bom支付","地铁单程票",request.getPaymentCode(),request.getPaymentVendor());
        String bomPayUrl = payCenterProperties.getPayCenterPayUrl();
        log.info("4.拉码请求 bomPayUrl is {} , payCenterRequest is {}",bomPayUrl,payCenterRequest);

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
            log.info("5.支付中心返回成功");
            Map<String, Object> data = payResponse.getData();
            if (data != null) {
                String status = getStringFromData(data, "status");
                String paymentChannelCode = getStringFromData(data, "paymentVendor");

//                // 支付成功
//                if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {
                    log.info("查询到支付成功的结果");
                    uMap.put("status", ItpStatusEnum.SUCCESS.getCode());
                    uMap.put("msg", "支付成功");
                    uMap.put("paymentCode", request.getPaymentCode());
                    uMap.put("paymentVendor", request.getPaymentVendor());
                    uMap.put("channel", paymentChannelCode);
                    uMap.put("updateTime", DateUtils.getNowTime());
                    bomNoCashOrderMapper.updateByOrderNo(uMap);
                    return BomOrderResult.successPaymentResultWithMsg("SUCCESS", "支付成功", "支付成功");
//                }
//                // 支付失败
//                else if (PayCenterStatusEnum.FAILED.getCode().equals(status)) {
//                    log.info("查询到支付失败的结果");
//                    uMap.put("status", ItpStatusEnum.FAILED.getCode());
//                    uMap.put("msg", "支付失败");
//                    uMap.put("channel", paymentChannelCode);
//                    uMap.put("updateTime", DateUtils.getNowTime());
//                    bomNoCashOrderMapper.updateByOrderNo(uMap);
//                    return BomOrderResult.successPaymentResultWithMsg("FAILED", "支付失败", "支付失败");
//                }
            }
        }

        // 支付结果不明确，按处理中返回
        log.info("6.支付结果不明确");
        return BomOrderResult.successPaymentResultWithMsg("PROCESSING", "处理中", "处理中");
    }

    /**
     * IF8A-06 查询支付结果。
     * BOM轮询查询支付结果，ITP调用支付中心查询并返回支付状态。
     *
     * @param request 请求参数（包含订单号）
     * @return 响应结果，包含支付结果（SUCCESS/FAILED/PROCESSING）
     */
    @Override
    public JSONObject requestGetPayResult(RequestGetPayResultReqDTO request) {
        log.info("1.开始处理BOM查询支付结果, deviceId={}, request={}", request.getDeviceId(), request);

        // 查询订单信息
        BomNoCashOrder order = bomNoCashOrderMapper.selectByOrderNo(request.getOrderNo());
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
        PayCenterRequest queryPayRequest = payCenterCommon.buildQueryPayCenterRequest(request.getOrderNo());
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
                uMap.put("orderNo", request.getOrderNo());
                Map<String, Object> data = queryPayResponse.getData();
                if (data != null) {
                    String status = getStringFromData(data, "status");
                    String paymentChannelCode = getStringFromData(data, "paymentVendor");

                    // 支付成功
                    if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {
                        log.info("查询到支付成功的结果");
                        uMap.put("status", ItpStatusEnum.SUCCESS.getCode());
                        uMap.put("msg", "支付成功");
                        uMap.put("channel", paymentChannelCode);
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
    public JSONObject notiBusResult(NotiBusResultReqDTO request) {
        log.info("1.开始处理BOM业务操作结果通知, deviceId={}, request={}", request.getDeviceId(), request);

        // 查询订单信息
        BomNoCashOrder order = bomNoCashOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            return BomOrderResult.fail(BomPayCodeEnum.ORDER_NO_ERROR.getCode(), "订单号错误,没有找到匹配的订单");
        }

        // 生成通知ID
        String notifyId = generateNotifyId();
        String now = DateUtils.getNowTime();

        // 构建业务操作结果通知记录
        BomBusResult busResult = new BomBusResult();
        busResult.setNotifyId(notifyId);
        busResult.setOrderNo(request.getOrderNo());
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
            String refundOrderNo = generateRefundOrderNo();

            // 创建BOM退款订单记录，状态初始为退款中
            BomRefundOrder refundOrder = new BomRefundOrder();
            refundOrder.setRefundNo(refundOrderNo);
            refundOrder.setPayOrderNo(request.getOrderNo());
            refundOrder.setMerchantRefundNo(refundOrderNo);
            refundOrder.setRefundAmount(order.getTransAount());
            refundOrder.setRefundReason("业务操作失败");
            refundOrder.setRefundStatus("0"); // 0-退款中
            refundOrder.setRefundMsg("退款中");
            refundOrder.setCreateTime(now);
            refundOrder.setUpdateTime(now);
            bomRefundOrderMapper.insert(refundOrder);
            log.info("3.1.BOM退款订单记录已入库, refundOrderNo={}", refundOrderNo);

            // 构建退款请求并调用支付中心退款接口
            PayCenterRequest refundRequest = payCenterCommon.getRefundRequest(
                    refundOrderNo,
                    request.getOrderNo(),
                    "", // payCenterOrderNo，根据实际情况填写
                    Integer.parseInt(order.getTransAount())
            );
            String refundUrl = payCenterProperties.getPayCenterRefundUrl();
            log.info("4.调用支付中心退款接口, refundUrl={}, refundRequest={}", refundUrl, refundRequest);

            // 调用支付中心退款接口
            PayCenterResponse refundResponse = payCenterService.callPayCenter(refundUrl, refundRequest);
            log.info("5.支付中心退款响应 refundResponse={}", refundResponse);

            // 处理退款结果
            if (ObjectUtils.isEmpty(refundResponse)) {
                // 退款结果为空，通知状态设为退款失败
                log.info("6.退款结果为空，通知状态设为退款失败");
                // 更新退款订单状态
                updateBomRefundOrder(refundOrderNo, "2", "退款失败", now);
                // 更新通知记录状态
                updateBomBusResult(notifyId, refundOrderNo, now);
                // 更新原订单状态
//                updateBomOrder(request.getOrderNo(), ItpStatusEnum.FAILED.getCode(), "业务操作失败，退款失败", null, now);
            } else if (StringUtils.equals(refundResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
                // 退款成功
                log.info("6.退款成功");
                // 更新退款订单状态
                updateBomRefundOrder(refundOrderNo, "1", "退款成功", now);
                // 更新通知记录状态
                updateBomBusResult(notifyId,  refundOrderNo, now);
                // 更新原订单状态
                updateBomOrder(request.getOrderNo(), refundOrderNo, now);
            } else {
                // 退款失败
                log.info("6.退款失败");
                // 更新退款订单状态
                updateBomRefundOrder(refundOrderNo, "2", "退款失败", now);
                // 更新通知记录状态
                updateBomBusResult(notifyId,  refundOrderNo, now);
                // 更新原订单状态
                updateBomOrder(request.getOrderNo(), refundOrderNo, now);
            }

        }

        log.info("7.BOM业务操作结果通知处理完成");
        return BomOrderResult.success();
    }

    /**
     * 生成通知ID。
     * 格式：N + yyyyMMddHHmmss + 8位随机字符
     *
     * @return 通知ID
     */
    private String generateNotifyId() {
        return "N" + LocalDateTime.now().format(DATE_FORMATTER) + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 生成退款订单号。
     * 格式：R + yyyyMMddHHmmss + 8位随机字符
     *
     * @return 退款订单号
     */
    private String generateRefundOrderNo() {
        return "R" + LocalDateTime.now().format(DATE_FORMATTER) + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 更新BOM退款订单状态。
     *
     * @param refundNo   退款单号
     * @param status     退款状态
     * @param msg        状态描述
     * @param updateTime 更新时间
     */
    private void updateBomRefundOrder(String refundNo, String status, String msg, String updateTime) {
        Map<String, String> refundMap = new HashMap<>();
        refundMap.put("refundNo", refundNo);
        refundMap.put("refundStatus", status);
        refundMap.put("refundMsg", msg);
        refundMap.put("refundTime", "1".equals(status) ? updateTime : null); // 退款成功时记录退款时间
        refundMap.put("updateTime", updateTime);
        bomRefundOrderMapper.updateByRefundNo(refundMap);
    }

    /**
     * 更新BOM业务操作结果通知记录状态。
     *
     * @param notifyId      通知ID
     * @param status        通知状态
     * @param refundOrderNo 退款订单号
     * @param updateTime    更新时间
     */
    private void updateBomBusResult(String notifyId, String refundOrderNo, String updateTime) {
        Map<String, String> notifyMap = new HashMap<>();
        notifyMap.put("notifyId", notifyId);
        notifyMap.put("refundOrderNo", refundOrderNo);
        notifyMap.put("updateTime", updateTime);
        bomBusResultMapper.updateByNotifyId(notifyMap);
    }

    /**
     * 更新BOM非现金收款订单状态。
     *
     * @param orderNo       订单号
     * @param status        订单状态
     * @param msg           状态描述
     * @param rsv2          退款订单号（可为null）
     * @param updateTime    更新时间
     */
    private void updateBomOrder(String orderNo,  String rsv2, String updateTime) {
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

    /**
     * 生成订单号。
     * 格式：03 + yyyyMMddHHmmss + 8位随机字符
     * 03表示BOM渠道。
     *
     * @return 订单号
     */
    private String generateOrderNo() {
        return "03" + LocalDateTime.now().format(DATE_FORMATTER) + UUID.randomUUID().toString().substring(0, 8);
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
}