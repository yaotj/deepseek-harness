package com.chinasofti.huateng.collectpay.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.collectpay.common.DeviceResponse;
import com.chinasofti.huateng.collectpay.common.PayCenterCommon;
import com.chinasofti.huateng.collectpay.common.UpdateDbMap;
import com.chinasofti.huateng.collectpay.config.PayCenterProperties;
import com.chinasofti.huateng.collectpay.constant.*;
import com.chinasofti.huateng.collectpay.entity.RefundOrder;
import com.chinasofti.huateng.collectpay.entity.TvmAppOrder;
import com.chinasofti.huateng.collectpay.entity.TvmTopupOrder;
import com.chinasofti.huateng.collectpay.mapper.RefundOrderMapper;
import com.chinasofti.huateng.collectpay.mapper.TvmOrderPreMapper;
import com.chinasofti.huateng.collectpay.mapper.TvmTopupOrderMapper;
import com.chinasofti.huateng.collectpay.model.request.PayCenterRequest;
import com.chinasofti.huateng.collectpay.model.request.tvm.*;
import com.chinasofti.huateng.collectpay.model.response.PayCenterResponse;
import com.chinasofti.huateng.collectpay.model.response.app.AppOrderResult;
import com.chinasofti.huateng.collectpay.model.response.paycenter.PayCenterResult;
import com.chinasofti.huateng.collectpay.model.response.tvm.RequestRefundRespDTO;
import com.chinasofti.huateng.collectpay.model.response.tvm.TvmOrderResult;
import com.chinasofti.huateng.collectpay.service.PayCenterService;
import com.chinasofti.huateng.collectpay.service.TvmCommonService;
import com.chinasofti.huateng.collectpay.service.TvmTopupService;
import com.chinasofti.huateng.collectpay.utils.DateUtils;
import com.chinasofti.huateng.collectpay.utils.OrderCommonUtils;
import com.chinasofti.huateng.collectpay.utils.OrderNoUtils;
import com.chinasofti.huateng.collectpay.utils.SignUtils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** TVM扫码充值服务实现。 */
@Slf4j
@Service
public class TvmTopupServiceImpl implements TvmTopupService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DB_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long DEFAULT_ORDER_TIMEOUT = 180;

    @Autowired
    private PayCenterProperties payCenterProperties;

    @Autowired
    private TvmTopupOrderMapper tvmTopupOrderMapper;
    @Autowired
    private TvmOrderPreMapper tvmOrderPreMapper;

    @Autowired
    private RefundOrderMapper refundOrderMapper;

    @Autowired
    private com.chinasofti.huateng.collectpay.mapper.OrderSeqMapper orderSeqMapper;

    @Autowired
    private PayCenterCommon payCenterCommon;
    @Autowired
    private PayCenterService payCenterService;
    @Autowired
    private TvmCommonService tvmCommonService;
    @Autowired
    SignUtils signUtils;
    @Autowired
    Environment environment;

    @Override
    public JSONObject requestTopup(RequestTopupReqDTO request) {
        log.info("1.开始处理请求充值下单, deviceId={}, request={}", request.getDeviceId(), request);

        // 生成订单号（充值订单以08开头）
        long seq = orderSeqMapper.nextval();
        String orderNo = OrderNoUtils.generateOrderNo(ProductType.tvmTopup, seq);
        log.info("2.生成充值订单号, orderNo={}", orderNo);

        // 创建充值订单
        TvmTopupOrder order = buildTopupOrder(orderNo, request);

        // 保存支付订单前置信息
        tvmOrderPreMapper.insert(getTvmOrderPre(order, request.getDeviceId()));
        tvmTopupOrderMapper.insert(order);
        log.info("3.保存充值订单到数据库, orderNo={}", orderNo);

        // 非数币渠道直接返回
        if (request.getPayType().equals("0")) {
            String sign = signUtils.getJhmSign(orderNo);
            String payUrl = payCenterProperties.getPayCenterJhmUrl() + "?orderNo=" + orderNo + "&sign=" + sign;
            Map<String, String> jmap = new HashMap<>();
            jmap.put("orderNo", orderNo);
            jmap.put("url", payUrl);
            tvmTopupOrderMapper.updateByOrderNo(jmap);
            return TvmOrderResult.successData(DeviceResponse.getLaMaSuccessRespose(orderNo, payUrl));
        }

        // 调用支付中心预下单
        String payTopupUrl = payCenterProperties.getPayCenterPayUrl();
        PayCenterRequest payTopupRequest = payCenterCommon.buildTvmPayRequest(order.getOrderNo(), order.getTransAmount(), order.getPayType(), "地铁票卡充值", "地铁票卡充值");
        log.info("4.充值请求 payTopupUrl is {} , payCenterRequest is {}", payTopupUrl, payTopupRequest);
        PayCenterResponse payTopupResponse = payCenterService.callPayCenter(payTopupUrl, payTopupRequest);

        // 默认为失败
        JSONObject result = TvmOrderResult.fail();
        Map<String, String> uMap = UpdateDbMap.getTopupUpdateFailDb(orderNo);

        if (ObjectUtils.isEmpty(payTopupResponse)) {
            log.info("6.支付中心返回结果为空,结束");
        } else {
            if (StringUtils.equals(payTopupResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
                Map<String, Object> data = payTopupResponse.getData();
                String payUrl = getStringFromData(data, "data");
                String payCenterOrderNo = getStringFromData(data, "orderNo");
                String payCenterChannelOrderNo = getStringFromData(data, "channelOrderNo");
                log.info("4.支付中心预下单成功, payUrl={}", payUrl);

                uMap = UpdateDbMap.getLaMaUpdateSuccessDb(orderNo, payCenterOrderNo, payCenterChannelOrderNo, payUrl);
                result = TvmOrderResult.successData(DeviceResponse.getTopupSuccessRespose(orderNo, payUrl));
            } else {
                log.info("7.支付中心返回业务数据失败");
            }
        }

        log.info("9.开始修改记录 uMap is {}", uMap);
        int i = tvmTopupOrderMapper.updateByOrderNo(uMap);
        log.info("10.修改结束 i is {}", i);

        return result;
    }


    private Map<String, Object> getTvmOrderPre(TvmTopupOrder order, String deviceId) {
        Map<String, Object> preMap = new HashMap<>();
        preMap.put("orderNo", order.getOrderNo());
        preMap.put("transAmount", order.getTransAmount());
        preMap.put("deviceId", deviceId);
        // 01-扫码购票  02-扫码充值
        preMap.put("transType", BusinessTypeEnum.TVM_SCAN_QR_RECHARGE.getCode());
        preMap.put("createTime", DateUtils.getNowTime());
        preMap.put("updateTime", "");
        return preMap;
    }

    @Override
    public JSONObject requestPayResult(RequestPayResultReqDTO request) {

        log.info("1.开始处理查询充值支付结果, deviceId={}, request={}", request.getDeviceId(), request);
        JSONObject result = new JSONObject();

        TvmTopupOrder payTopupOrderInfo = tvmTopupOrderMapper.selectByOrderNo(request.getOrderNo());
        if (payTopupOrderInfo == null) {
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(), "没有找到匹配的订单，请确认订单号是否正确");
        }

        // 如果已经是成功，直接返回
        if (ItpStatusEnum.SUCCESS.getCode().equals(payTopupOrderInfo.getStatus())) {
            log.info("2.数据库查询结果为支付成功，直接返回");
            return TvmOrderResult.successData(DeviceResponse.getPaySuccessResult(payTopupOrderInfo.getChannel()));
        }

        // 如果已经是失败，直接返回
        if (ItpStatusEnum.FAILED.getCode().equals(payTopupOrderInfo.getStatus())) {
            log.info("2.数据库查询结果为支付失败，直接返回");
            return TvmOrderResult.failData(DeviceResponse.getPayFailResult(payTopupOrderInfo.getChannel()));
        }
        // 如果已经是未支付，直接返回 未支付也是终态，轮询结束后没有支付则认为未支付
        if (ItpStatusEnum.UNPAID.getCode().equals(payTopupOrderInfo.getStatus())) {
            log.info("2.数据库查询结果为未支付，直接返回");
            return TvmOrderResult.failData(DeviceResponse.getPayFailResult(payTopupOrderInfo.getChannel()));
        }

        String tvmQueryUrl = payCenterProperties.getPayCenterQueryUrl();
        // 订单为已下单状态，需要向支付中心查询实际支付结果
        PayCenterRequest queryPayRequest = payCenterCommon.buildQueryPayCenterRequest(payTopupOrderInfo.getOrderNo());

        log.info("3.tvmQueryUrl is {} , queryPayRequest is {}", tvmQueryUrl, queryPayRequest);
        PayCenterResponse queryPayResponse = payCenterService.callPayCenter(tvmQueryUrl, queryPayRequest);

        log.info("queryPayResponse is {}", queryPayResponse);

        if (ObjectUtils.isEmpty(queryPayResponse)) {
            log.info("6.支付中心返回结果为空,结束");
        } else {

            if (StringUtils.equals(queryPayResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {

                Map<String, String> uMap = UpdateDbMap.getTopupUpdateFailDb(payTopupOrderInfo.getOrderNo());
                Map<String, Object> data = queryPayResponse.getData();
                if (data != null) {
                    String status = getStringFromData(data, "status");
                    String payCenterOrderNo = getStringFromData(data, "orderNo");
                    String channelOrderNo = getStringFromData(data, "channelOrderNo");
                    String paymentChannelCode = getStringFromData(data, "paymentVendor");

                    // 查询支付中心支付状态为支付成功
                    if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {

                        log.info("查询到支付成功的结果");
                        String aftAmount = String.valueOf(new BigDecimal(payTopupOrderInfo.getBeforeAmount()).add(new BigDecimal(payTopupOrderInfo.getTransAmount())));

                        uMap = UpdateDbMap.getTopupUpdateSuccessDb(payTopupOrderInfo.getOrderNo(), payCenterOrderNo, channelOrderNo, aftAmount, paymentChannelCode);
                        result = TvmOrderResult.successData(DeviceResponse.getPaySuccessResult(paymentChannelCode));

                    } else if (PayCenterStatusEnum.FAILED.getCode().equals(status)) {
                        log.info("查询到支付失败的结果");
                        uMap = UpdateDbMap.getTopupUpdateFailDb(payTopupOrderInfo.getOrderNo());
                        result = TvmOrderResult.successData(DeviceResponse.getPayFailResult(paymentChannelCode));
                    } else {
                        log.info("查询到不明确的结果，按已下单-支付中处理");
                        result = TvmOrderResult.successData(DeviceResponse.getPayIngResult(paymentChannelCode));
                        return result;
                    }
                    log.info("开始修改记录 uMap is {}", uMap);
                    int i = tvmTopupOrderMapper.updateByOrderNo(uMap);
                    log.info("修改结束 i is {}", i);
                    return result;
                }
            }

        }
        // 没有查询到支付结果，或结果为空，全部按照支付中-已下单返回
        return TvmOrderResult.successData(DeviceResponse.getPayIngResult(payTopupOrderInfo.getChannel()));
    }

    @Override
    public JSONObject refundTvmTopupNotTakeTickets() {
        log.info("开始查询购票但未取票的订单信息");

        Map<String, String> condition = new HashMap<>();
        condition.put("startTime", DateUtils.getTime(-1, "yyyy-MM-dd") + " 00:00:00");
        condition.put("endTime", DateUtils.getTime(-1, "yyyy-MM-dd") + " 23:59:59");

        log.info("condition is {}", condition);
        // 1.查询购票但未取票的订单信息
        List<TvmTopupOrder> tvmTopupOrders = tvmTopupOrderMapper.selectByCondition(condition);

        log.info(" tvmTopupOrders.size is {}", tvmTopupOrders.size());
        log.info("tvmTopupOrders is {}", tvmTopupOrders);

        if (tvmTopupOrders.size() == 0) {
            return AppOrderResult.success("无充值但未通知的订单信息,结束");
        }

        for (TvmTopupOrder tvmTopupOrder : tvmTopupOrders) {

            RequestRefundReqDTO dto = new RequestRefundReqDTO();
            dto.setOrderNo(tvmTopupOrder.getOrderNo());
            // 订单金额
            dto.setRefundAmt(tvmTopupOrder.getTransAmount());
            dto.setRefundReason("自动发起充值退款");
            log.info("定时任务 开始发起退款，dto is {}", dto);

            JSONObject refundResult = this.requestRefund(dto);
            log.info("订单 {} 退款结束 refundResult is {}", tvmTopupOrder.getOrderNo(), refundResult);
        }

        return AppOrderResult.success("tvm充值 订单 退款 结束");
    }

    @Override
    public JSONObject topupCardResultNoti(TopupCardResultNotiReqDTO request) {
        log.info("1.开始处理充值结果通知, deviceId={}, request={}", request.getDeviceId(), request);

        // 查询原充值订单
        TvmTopupOrder order = tvmTopupOrderMapper.selectByOrderNo(request.getOrderNo());
        if (order == null) {
            log.info("2.没有找到匹配的充值订单，orderNo={}", request.getOrderNo());
            return TvmOrderResult.fail(TvmPayCodeEnum.ORDER_NO_ERROR.getCode(), TvmPayCodeEnum.ORDER_NO_ERROR.getMsg());
        }

        Map<String, String> map = getNotiy(request);

        tvmTopupOrderMapper.insertNotiy(map);

        log.info("3.充值结果通知处理完成, orderNo={}", request.getOrderNo());
        return TvmOrderResult.success();
    }

    private Map<String, String> getNotiy(TopupCardResultNotiReqDTO request) {
        Map<String, String> map = new HashMap<>();
        map.put("orderNo", request.getOrderNo());
        map.put("ticketLogicNum", request.getTicketLogicNum());
        map.put("ticketPhysicsNum", request.getTicketPhysicsNum());
        map.put("transDate", request.getTransDate());
        map.put("transAmount", request.getTransAmount());
        map.put("afterAmount", request.getAfterAmount());
        map.put("topupStatus", "00");
        map.put("transType", "01");
        map.put("createTime", DateUtils.getNowTime());
        return map;
    }


    @Override
    public JSONObject topupCardFailNoti(TopupCardFailNotiReqDTO request) {
        log.info("1.开始处理充值失败通知, deviceId={}, request={}", request.getDeviceId(), request);

        String payOrderNo = request.getOrderNo();
        // 查询原充值订单
        TvmTopupOrder order = tvmTopupOrderMapper.selectByOrderNo(payOrderNo);
        if (order == null) {
            log.info("2.没有找到匹配的充值订单，orderNo={}", request.getOrderNo());
            return TvmOrderResult.fail(TvmPayCodeEnum.ORDER_NO_ERROR.getCode(), TvmPayCodeEnum.ORDER_NO_ERROR.getMsg());
        }

        Map<String, String> map = getFailNotiy(request);

        tvmTopupOrderMapper.insertFailtNotiy(map);

        // 如果充值失败，发起退款
        String topupStatus = request.getTopupStatus();
        if ("01".equals(topupStatus)) {
            log.info("3.充值失败，发起退款, orderNo={}", payOrderNo);
            int refundAmount = Integer.parseInt(order.getTransAmount());
            String refundNo = OrderCommonUtils.getRefundNo();
            boolean b = tvmCommonService.doRefund(BusinessTypeEnum.TVM_SCAN_QR_RECHARGE.getCode(), order.getOrderNo(), order.getPayCenterOrderNo(), refundAmount, refundNo);
            log.info("退款结束 refundNo is {}", refundNo);
            // 如果refundNo不为空，则证明退款结束 退款结果可以是成功的也可以是失败的
            if (b) {
                Map<String, String> updateMap = new LinkedHashMap<>();
                updateMap.put("orderNo", payOrderNo);
                updateMap.put("rsv2", refundNo);
                updateMap.put("updateTime", DateUtils.getNowTime());
                tvmTopupOrderMapper.updateByOrderNo(updateMap);
            }
        }

        log.info("4.充值失败通知处理完成, orderNo={}", payOrderNo);
        return TvmOrderResult.success();
    }

    private Map<String, String> getFailNotiy(TopupCardFailNotiReqDTO request) {
        Map<String, String> map = new HashMap<>();
        map.put("orderNo", request.getOrderNo());
        map.put("ticketLogicNum", request.getTicketLogicNum());
        map.put("ticketPhysicsNum", request.getTicketPhysicsNum());
        map.put("topupStatus", request.getTopupStatus());
        map.put("faultOccurDate", request.getFaultOccurDate());
        map.put("faultSlipSeq", request.getFaultSlipSeq());
        map.put("errorCode", request.getErrorCode());
        map.put("errorMessage", request.getErrorMessage());
        map.put("transType", "02");
        map.put("createTime", DateUtils.getNowTime());
        return map;
    }

    /** 构建充值订单实体。 */
    private TvmTopupOrder buildTopupOrder(String orderNo, RequestTopupReqDTO request) {
        TvmTopupOrder order = new TvmTopupOrder();
        order.setOrderNo(orderNo);
        order.setDeviceId(request.getDeviceId());
        order.setTicketLogicNum(request.getTicketLogicNum());
        order.setTicketPhysicsNum(request.getTicketPhysicsNum());
        order.setBeforeAmount(request.getBeforeAmount());
        order.setTransAmount(request.getTransAmount());
        order.setStatus(ItpStatusEnum.PAYING.getCode());
        order.setMsg(ItpStatusEnum.PAYING.getDesc());
        order.setPayType(request.getPayType());
        order.setCreateTime(LocalDateTime.now().format(DB_DATE_FORMATTER));
        order.setUpdateTime(LocalDateTime.now().format(DB_DATE_FORMATTER));
        return order;
    }


    /** 发起退款。 */
    private void doRefund(String orderNo, String payCenterOrderNo, int refundAmount) {
        try {

            long seq = orderSeqMapper.nextval();
            String refundNo = OrderNoUtils.generateRefundNo(seq);

            PayCenterRequest refundRequest = payCenterCommon.getRefundRequest(refundNo, orderNo, payCenterOrderNo, refundAmount);

            log.info("发起退款 refundRequest is {}", refundRequest);
            PayCenterResponse payCenterResponse = payCenterService.callPayCenter(payCenterProperties.getPayCenterRefundUrl(), refundRequest);

            RefundOrder refundOrder = new RefundOrder();
            refundOrder.setRefundNo(refundNo);
            refundOrder.setPayOrderNo(orderNo);
            refundOrder.setRefundAmount(refundAmount);
            refundOrder.setRefundReason("充值失败");
            refundOrder.setCreateTime(DateUtils.getNowTime());

            if (payCenterResponse != null && StringUtils.equals(payCenterResponse.getCode(), PayCenterErrorCodeEnum.SUCCESS.getCode())) {
                Map<String, Object> data = payCenterResponse.getData();
                refundOrder.setMerchantRefundNo(getStringFromData(data, "merchantRefundNo"));
                refundOrder.setChannelRefundNo(getStringFromData(data, "channelRefundNo"));
                refundOrder.setRefundTime(getStringFromData(data, "refundTime"));
                refundOrder.setRefundStatus(ItpStatusEnum.REFUND_SUCCESS.getCode()); // 1-退款成功
                refundOrder.setRefundMsg(ItpStatusEnum.REFUND_SUCCESS.getDesc()); // 1-退款成功
                log.info("充值退款成功, orderNo={}, refundNo={}", orderNo, refundNo);
            } else {
                String errorMsg = payCenterResponse != null ? payCenterResponse.getMsg() : "调用支付中心退款失败";
                refundOrder.setRefundStatus(ItpStatusEnum.REFUNDING_FAIL.getCode()); // 2-退款失败
                refundOrder.setRefundMsg(ItpStatusEnum.REFUNDING_FAIL.getDesc()); // 2-
                log.error("充值退款失败, orderNo={}, errorMsg={}", orderNo, errorMsg);
            }
            int insert = refundOrderMapper.insert(refundOrder);
            // 如果refundNo不为空，则证明退款结束 退款结果可以是成功的也可以是失败的
            log.info("退款解释 refundNo is {}", refundNo);
            if (!StringUtils.isEmpty(refundNo)) {
                Map<String, String> updateMap = new LinkedHashMap<>();
                updateMap.put("orderNo", orderNo);
                updateMap.put("rsv2", refundNo);
                tvmTopupOrderMapper.updateByOrderNo(updateMap);
            }

        } catch (Exception e) {
            log.error("发起退款异常, orderNo={}", orderNo, e);
        }
    }

    /** 调用支付中心接口。 */
    private PayCenterResponse callPayCenter(String path, PayCenterRequest request) {
        try {
            OkHttpClient client = new OkHttpClient();
            MediaType mediaType = MediaType.parse("application/json;charset=UTF-8");
            String jsonStr = JSON.toJSONString(request);
            RequestBody body = RequestBody.create(jsonStr, mediaType);

            Request httpRequest = new Request.Builder()
                    .url(payCenterProperties.getGatewayUrl() + path)
                    .post(body)
                    .build();

            Response response = client.newCall(httpRequest).execute();
            if (response.isSuccessful() && response.body() != null) {
                String responseBody = response.body().string();
                return JSON.parseObject(responseBody, PayCenterResponse.class);
            }
        } catch (IOException e) {
            log.error("调用支付中心接口异常, path={}", path, e);
        }
        return null;
    }

    /** 签名请求。 */
    private void signRequest(PayCenterRequest request) {
        try {
            String privateKeyStr = payCenterProperties.getPrivateKey();
            byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyStr);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

            StringBuilder sb = new StringBuilder();
            Map<String, String> params = new TreeMap<>();
            params.put("merchantNo", request.getMerchantNo());
            params.put("apiVersion", request.getApiVersion());
            params.put("signType", request.getSignType());
            params.put("charset", request.getCharset());
            params.put("bizData", request.getBizData());

            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (StringUtils.isNotEmpty(entry.getValue())) {
                    if (sb.length() > 0) {
                        sb.append("&");
                    }
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                }
            }

            Signature signature = Signature.getInstance("SHA1withRSA");
            signature.initSign(privateKey);
            signature.update(sb.toString().getBytes(StandardCharsets.UTF_8));
            byte[] signBytes = signature.sign();
            request.setSign(Base64.getEncoder().encodeToString(signBytes));

        } catch (Exception e) {
            log.error("签名请求异常", e);
        }
    }

    /** 从Map中获取字符串值。 */
    private String getStringFromData(Map<String, Object> data, String key) {
        if (data == null || !data.containsKey(key)) {
            return null;
        }
        Object value = data.get(key);
        return value != null ? value.toString() : null;
    }

    @Override
    public JSONObject requestPayOrderDetail(RequestPayResultReqDTO request) {

        String orderNo = request.getOrderNo();
        log.info("1.支付中心查询扫码充值订单详情,orderNo is {}", orderNo);

        // 查询订单信息
        TvmTopupOrder order = tvmTopupOrderMapper.selectByOrderNo(orderNo);
        log.info("2.支付中心查询扫码充值订单详情,order is {}", order);

        if (ObjectUtils.isEmpty(order)) {
            log.info("3.没有找到扫码充值匹配的订单，请确认订单号是否正确");
            return TvmOrderResult.fail(TvmPayCodeEnum.INVALID_PARAM.getCode(), "没有找到匹配的订单，请确认订单号是否正确");
        }

        // 组装返回结果
        JSONObject jsonObject = getPayCenterPayOrderDetailResult(order);
        log.info("3.返回结果 扫码充值 jsonObject is {}", jsonObject);

        return TvmOrderResult.successData(jsonObject);
    }

    private JSONObject getPayCenterPayOrderDetailResult(TvmTopupOrder order) {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("orderNo", order.getOrderNo());
        String deviceId = order.getDeviceId();
        String stationCode = "";
        if (!StringUtils.isEmpty(deviceId) && deviceId.length() > 4) {
            stationCode = deviceId.substring(0, 4);
        }
        // todo 需改为中文名
        jsonObject.put("singlePickupStationName", stationCode);
        jsonObject.put("singlePickupStationCode", stationCode);
        jsonObject.put("singleTicketNum", "1");
        jsonObject.put("singleTicketPrice", order.getTransAmount());
        jsonObject.put("totalTicketPrice", order.getTransAmount());
        jsonObject.put("regDate", order.getCreateTime().replace("-", "").replace(":", "").replace(" ", ""));
        String status = order.getStatus();

        String orderStatus = "";
        if (StringUtils.equals(status, ItpStatusEnum.PAYING.getCode())) {
            orderStatus = "1";
        } else if (StringUtils.equals(status, ItpStatusEnum.SUCCESS.getCode())) {
            orderStatus = "2";
            jsonObject.put("payDate", order.getUpdateTime().replace("-", "").replace(":", "").replace(" ", ""));
            // 说明发生了退款 先判断是否支付成功，只有支付成功后，才可以发起退款
            if (!StringUtils.isEmpty(order.getRsv2())) {
                log.info("该订单已发生退款");
                orderStatus = "7";
            }
        }
        jsonObject.put("orderStatus", orderStatus);
        jsonObject.put("subject", "一票通_单程票");
        jsonObject.put("body", "一票通_单程票");
        jsonObject.put("notifyUrl", environment.getProperty("pay.center.pay-notice"));
        return jsonObject;
    }

    @Override
    public JSONObject requestRefund(RequestRefundReqDTO request) {
        log.info("1.扫码充值 开始处理退款请求, deviceId={}, request={}", request.getDeviceId(), request);

        String payOrderNo = request.getOrderNo();
        if (request == null || !StringUtils.isNotBlank(payOrderNo)) {
            log.info("参数校验失败, orderNo为空");
            return RequestRefundRespDTO.fail("9999", "订单号不能为空");
        }

        // 查询订单信息
        TvmTopupOrder order = tvmTopupOrderMapper.selectByOrderNo(payOrderNo);
        if (order == null) {
            log.info("2.扫码充值 没有找到匹配的订单, orderNo={}", payOrderNo);
            return RequestRefundRespDTO.fail("9999", "订单号错误,没有找到匹配的订单");
        }

        // 订单已支付成功才退款
        if (!ItpStatusEnum.SUCCESS.getCode().equals(order.getStatus())) {
            log.info("2.扫码充值 订单状态不是支付成功, 不能退款, status={}", order.getStatus());
            return RequestRefundRespDTO.fail("9999", "订单状态不是支付成功,不能退款");
        }
        String refundNo = OrderCommonUtils.getRefundNo();
        boolean b = tvmCommonService.doRefund(BusinessTypeEnum.TVM_SCAN_QR_RECHARGE.getCode(), payOrderNo, order.getPayCenterOrderNo(), Integer.valueOf(request.getRefundAmt()), refundNo);

        // 如果refundNo不为空，则证明退款结束 退款结果可以是成功的也可以是失败的
        if (b) {
            Map<String, String> updateMap = new LinkedHashMap<>();
            updateMap.put("orderNo", payOrderNo);
            updateMap.put("rsv2", refundNo);
            updateMap.put("updateTime", DateUtils.getNowTime());
            tvmTopupOrderMapper.updateByOrderNo(updateMap);
        }

        log.info("扫码充值 退款结束");

        return TvmOrderResult.success();
    }


    @Override
    public JSONObject payNotice(PayNoticeReqDTO request) {

        log.info("开始处理 tvm扫码充值 支付结果通知 request is {}", request);

        // 查询订单信息
        TvmTopupOrder payTopupOrderInfo = tvmTopupOrderMapper.selectByOrderNo(request.getMerchantOrderNo());
        if (payTopupOrderInfo == null) {
            return PayCenterResult.fail(PayCenterErrorCodeEnum.ORDER_NOT_EXIST.getCode(), PayCenterErrorCodeEnum.ORDER_NOT_EXIST.getMsg());
        }

        // 如果已经是成功，直接返回
        if (ItpStatusEnum.SUCCESS.getCode().equals(payTopupOrderInfo.getStatus())) {
            log.info("2.支付结果通知 数据库查询结果为支付成功，直接返回");
            return PayCenterResult.success();
        }

        // 如果已经是失败，直接返回
        if (ItpStatusEnum.FAILED.getCode().equals(payTopupOrderInfo.getStatus())) {
            log.info("2.支付结果通知 数据库查询结果为支付失败，直接返回");
            return PayCenterResult.success();
        }


        Map<String, String> uMap = new HashMap<>();
        String status = request.getStatus();
        // itp订单号
        String orderNo = request.getMerchantOrderNo();
        // 支付中心订单号
        String payCenterOrderNo = request.getOrderNo();
        // 渠道订单号
        String channelOrderNo = request.getChannelOrderNo();
        String channel = request.getPaymentVendor();

        // 查询支付中心支付状态为支付成功
        // 查询支付中心支付状态为支付成功
        if (PayCenterStatusEnum.SUCCESS.getCode().equals(status)) {

            log.info("支付结果通知 支付成功 的结果");
            String aftAmount = String.valueOf(new BigDecimal(payTopupOrderInfo.getBeforeAmount()).add(new BigDecimal(payTopupOrderInfo.getTransAmount())));

            uMap = UpdateDbMap.getTopupUpdateSuccessDb(orderNo, payCenterOrderNo, channelOrderNo, aftAmount, channel);

        } else if (PayCenterStatusEnum.FAILED.getCode().equals(status)) {
            log.info("支付结果通知 支付失败 的结果");
            uMap = UpdateDbMap.getTopupUpdateFailDb(orderNo);
        } else {
            log.info("支付结果通知 查询到不明确的结果，按已下单-支付中处理");
            return PayCenterResult.fail();
        }
        log.info("支付结果通知 开始修改记录 uMap is {}", uMap);
        int i = tvmTopupOrderMapper.updateByOrderNo(uMap);
        log.info("支付结果通知 修改结束 i is {}", i);
        return PayCenterResult.success();
    }
}
