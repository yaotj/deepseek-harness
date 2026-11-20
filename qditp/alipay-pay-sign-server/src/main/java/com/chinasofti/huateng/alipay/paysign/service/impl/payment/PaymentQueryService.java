package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCallbackLog;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayCallbackLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTravelDetailDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.alipay.paysign.port.DebitSyncPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentQueryService {
    private static final Logger log = LoggerFactory.getLogger(PaymentQueryService.class);

    private static final String CALLBACK_TYPE_PAY = "PAY";

    private static final int MAX_PAY_CALLBACK_PUSH = 2;

    private static final String TRANS_STATUS_SUCCESS = "1";
    private static final String TRANS_STATUS_FAIL = "2";

    private static final String HANDLE_STATUS_PROCESSING = "PROCESSING";
    private static final String HANDLE_STATUS_SUCCESS = "SUCCESS";
    private static final String HANDLE_STATUS_FAIL = "FAIL";
    private static final String HANDLE_STATUS_MANUAL = "MANUAL";

    @Autowired
    private AlipayPayLogMapper alipayPayLogMapper;

    @Autowired
    private AlipayPayCallbackLogMapper alipayPayCallbackLogMapper;

    @Autowired
    private PayCenterPort payCenterPort;

    @Autowired
    private DebitSyncPort debitSyncPort;

    public AlipayTripPayQueryRespDTO payQuery(AlipayTripPayQueryReqDTO request) {
        AlipayTripPayQueryRespDTO response = new AlipayTripPayQueryRespDTO();
        log.info("收到支付查询: orderNo={}", request != null ? request.getOrderNo() : null);

        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }
        AlipayPayLog payLog = alipayPayLogMapper.selectByOrderNo(request.getOrderNo());
        if (payLog == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "订单不存在");
        }

        Map<String, Object> bizDataMap = new java.util.LinkedHashMap<>();
        bizDataMap.put("orderNo", request.getOrderNo());
        bizDataMap.put("cardIssueCode", "0007");
        if (StringUtils.hasText(payLog.getCardId())) {
            bizDataMap.put("cardNum", payLog.getCardId());
        }

        String channelAgreementNo = request.getChannelAgreementNo();
        if (!StringUtils.hasText(channelAgreementNo) && StringUtils.hasText(payLog.getIndustryDetail())) {
            try {
                JSONObject detailJson = JSON.parseObject(payLog.getIndustryDetail());
                channelAgreementNo = detailJson.getString("channelAgreementNo");
                log.info("支付宝出行-支付结果查询,从industryDetail解析channelAgreementNo={}", channelAgreementNo);
            } catch (Exception e) {
                log.warn("支付宝出行-支付结果查询,解析industryDetail失败, industryDetail={}", payLog.getIndustryDetail(), e);
            }
        }
        if (StringUtils.hasText(channelAgreementNo)) {
            bizDataMap.put("channelAgreementNo", channelAgreementNo);
        }

        log.info("支付宝出行-支付结果查询,调用支付中心查询接口,请求参数: {}", JSON.toJSONString(bizDataMap));
        PayCenterReply reply = payCenterPort.payQuery(bizDataMap);
        log.info("支付宝出行-支付结果查询,支付中心响应结果: code={}, msg={}, success={}, responseBody={}",
                reply.code(), reply.msg(), reply.success(), reply.rawBody());

        String payStatus = payLog.getPayStatus();
        String tradeNo = payLog.getTradeNo();
        String transTime = payLog.getTransTime();
        String payAmount = payLog.getPayAmount();
        String resultMsg = payLog.getResultMsg();

        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                String centerTradeNo = accepted.field("tradeNo");
                String centerPayAmount = accepted.field("totalAmount");
                String centerTransTime = accepted.field("paymentTime");
                String centerTransStatus = accepted.field("tradeStatus");

                log.info("支付宝出行-支付结果查询,支付中心解密后数据: retCode={}, retMsg={}, tradeNo={}, totalAmount={}, paymentTime={}, tradeStatus={}",
                        accepted.retCode(), accepted.retMsg(), centerTradeNo, centerPayAmount, centerTransTime, centerTransStatus);

                if (StringUtils.hasText(centerTradeNo)) {
                    tradeNo = centerTradeNo;
                }
                if (StringUtils.hasText(centerPayAmount)) {
                    payAmount = centerPayAmount;
                }
                if (StringUtils.hasText(centerTransTime)) {
                    transTime = centerTransTime;
                }

                if ("SUCCESS".equals(accepted.retCode())) {
                    payStatus = "SUCCESS";
                    resultMsg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "支付成功";
                } else {
                    payStatus = "FAIL";
                    resultMsg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "支付失败";
                }

                applyQueryResult(payLog.getOrderNo(), payStatus, tradeNo, transTime, payAmount, resultMsg);
            }
            case PayCenterReply.Rejected rejected -> log.error(
                    "支付宝出行-支付结果查询未拿到支付中心业务应答，本地支付流水保持原状、NEVER 落 FAIL, orderNo={}, code={}, msg={}, success={}",
                    request.getOrderNo(), rejected.code(), rejected.msg(), rejected.success());
            case PayCenterReply.NoAnswer noAnswer -> log.error(
                    "支付宝出行-支付结果查询调用支付中心无响应，本地支付流水保持原状、NEVER 落 FAIL, orderNo={}",
                    request.getOrderNo());
        }

        payLog = alipayPayLogMapper.selectByOrderNo(request.getOrderNo());
        log.info("支付宝出行-支付结果查询,更新后支付流水: orderNo={}, payStatus={}, tradeNo={}", request.getOrderNo(), payLog.getPayStatus(), payLog.getTradeNo());

        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("查询成功");
        response.setOutTradeNo(payLog.getOrderNo());
        response.setTradeStatus(payLog.getPayStatus());
        response.setTotalAmount(payLog.getPayAmount());
        response.setTradeNo(payLog.getTradeNo());
        response.setTradeDesc(payLog.getResultMsg());
        response.setPaymentTime(payLog.getTransTime());
        log.info("支付宝出行-支付结果查询,查询结果: {}", JSON.toJSONString(response));
        return response;
    }

    private void applyQueryResult(String orderNo, String payStatus, String tradeNo,
                                  String transTime, String payAmount, String resultMsg) {
        int affected = alipayPayLogMapper.updatePayQueryResultIfNotSuccess(
                orderNo, payStatus, tradeNo, transTime, payAmount,
                FepAppErrorCodeEnum.SUCCESS.getCode(), resultMsg);
        if (affected > 0) {
            log.info("支付宝出行-支付结果查询,本地支付流水已回写, orderNo={}, payStatus={}, tradeNo={}, payAmount={}",
                    orderNo, payStatus, tradeNo, payAmount);
            return;
        }
        AlipayPayLog current = alipayPayLogMapper.selectByOrderNo(orderNo);
        String currentStatus = current == null ? null : current.getPayStatus();
        if (payStatus.equals(currentStatus)) {
            log.info("支付宝出行-支付结果查询,本地已是目标状态，跳过回写, orderNo={}, payStatus={}", orderNo, currentStatus);
            return;
        }
        log.error("支付宝出行-支付结果查询与本地口径冲突，本地为终态、拒绝覆盖，MUST 人工到支付中心核对, orderNo={}, 本地PAY_STATUS={}, 支付中心给出={}, retMsg={}",
                orderNo, currentStatus, payStatus, resultMsg);
    }


    public AlipayCommonResponse handlePayNotify(AlipayTripPayNotifyReqDTO request) {
        log.info("接收到支付宝支付结果回调, 原始报文: {}", JSON.toJSONString(request));
        AlipayCommonResponse response = new AlipayCommonResponse();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            log.warn("支付宝支付回调报文为空或订单号为空");
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("参数异常：orderNo不能为空");
            return response;
        }

        String payStatus = mapTransStatus(request.getTransStatus());
        if (payStatus == null) {
            log.error("支付宝支付回调 transStatus 不在白名单内，拒绝处理、MUST 人工核对报文契约, orderNo={}, transStatus={}",
                    request.getOrderNo(), request.getTransStatus());
            recordCallback(request, HANDLE_STATUS_MANUAL, "transStatus 非法: " + request.getTransStatus());
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("参数异常：transStatus 取值非法");
            return response;
        }

        String callbackSeq = recordCallback(request, HANDLE_STATUS_PROCESSING, null);
        int pushCount = countPush(request.getOrderNo());

        log.info("支付宝支付回调,准备同步扣费订单状态, orderNo={}, transStatus={}, payStatus={}, channelVoucherId={}, transTime={}, transAmount={}, pushCount={}",
                request.getOrderNo(), request.getTransStatus(), payStatus,
                request.getChannelVoucherId(), request.getTransTime(), request.getTransAmount(), pushCount);

        if (!syncGateTxnPayStatus(request.getOrderNo(), payStatus)) {
            if (pushCount >= MAX_PAY_CALLBACK_PUSH) {
                giveUpRetry(request.getOrderNo(), pushCount, "扣费订单状态同步失败");
                response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
                response.setRetMsg("成功");
                return response;
            }
            updateHandleResult(callbackSeq, HANDLE_STATUS_FAIL, "扣费订单状态同步失败，待支付中心重推");
            response.setRetCode(FepAppErrorCodeEnum.FAIL.getCode());
            response.setRetMsg("扣费订单状态同步失败");
            return response;
        }

        updateHandleResult(callbackSeq, HANDLE_STATUS_SUCCESS, null);
        log.info("支付宝支付回调处理完成, orderNo={}, payStatus={}", request.getOrderNo(), payStatus);
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("成功");
        return response;
    }

    private String mapTransStatus(String transStatus) {
        if (TRANS_STATUS_SUCCESS.equals(transStatus)) {
            return "SUCCESS";
        }
        if (TRANS_STATUS_FAIL.equals(transStatus)) {
            return "FAIL";
        }
        return null;
    }


    private String recordCallback(AlipayTripPayNotifyReqDTO request, String handleStatus, String handleMsg) {
        String callbackSeq = UUID.randomUUID().toString().replaceAll("-", "");
        try {
            AlipayPayCallbackLog callbackLog = new AlipayPayCallbackLog();
            callbackLog.setCallbackSeq(callbackSeq);
            callbackLog.setOrderNo(request.getOrderNo());
            callbackLog.setCallbackType(CALLBACK_TYPE_PAY);
            callbackLog.setTransStatus(request.getTransStatus());
            callbackLog.setChannelVoucherId(request.getChannelVoucherId());
            callbackLog.setTransAmount(request.getTransAmount());
            callbackLog.setTransTime(request.getTransTime());
            callbackLog.setRawBody(JSON.toJSONString(request));
            callbackLog.setHandleStatus(handleStatus);
            callbackLog.setHandleMsg(truncate(handleMsg, 500));
            callbackLog.setCreateTime(LocalDateTime.now());
            callbackLog.setUpdateTime(LocalDateTime.now());
            alipayPayCallbackLogMapper.insert(callbackLog);
            return callbackSeq;
        } catch (Exception e) {
            log.error("支付宝支付回调凭据落库失败，本次处理继续, orderNo={}", request.getOrderNo(), e);
            return null;
        }
    }

    private int countPush(String orderNo) {
        try {
            return alipayPayCallbackLogMapper.countByOrderNo(orderNo, CALLBACK_TYPE_PAY);
        } catch (Exception e) {
            log.error("统计支付回调推送次数失败，本次不启用硬限次, orderNo={}", orderNo, e);
            return 0;
        }
    }

    private void updateHandleResult(String callbackSeq, String handleStatus, String handleMsg) {
        if (callbackSeq == null) {
            return;
        }
        try {
            alipayPayCallbackLogMapper.updateHandleResult(callbackSeq, handleStatus, truncate(handleMsg, 500));
        } catch (Exception e) {
            log.error("回写支付回调处理结果失败, callbackSeq={}, handleStatus={}", callbackSeq, handleStatus, e);
        }
    }

    private void giveUpRetry(String orderNo, int pushCount, String reason) {
        log.error("支付宝支付回调已推送{}次仍未处理成功，达到上限{}，放弃重推并返回0000，MUST 人工处理, orderNo={}, 原因={}",
                pushCount, MAX_PAY_CALLBACK_PUSH, orderNo, reason);
        try {
            alipayPayCallbackLogMapper.markManualByOrderNo(orderNo, CALLBACK_TYPE_PAY, truncate(reason, 500));
        } catch (Exception e) {
            log.error("标记支付回调需人工处理失败, orderNo={}", orderNo, e);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }


    private boolean syncGateTxnPayStatus(String orderNo, String payStatus) {
        RpcOutcome outcome = debitSyncPort.syncDebitStatus(orderNo, payStatus, "支付宝出行扣费结果回调");
        switch (outcome) {
            case RpcOutcome.Ok ok -> {
                log.info("扣费订单状态同步成功, orderNo={}, payStatus={}", orderNo, payStatus);
                return true;
            }
            case RpcOutcome.BizRejected rejected -> {
                log.error("扣费订单状态同步被业务拒绝，待支付中心重推, orderNo={}, payStatus={}, retCode={}, retMsg={}",
                        orderNo, payStatus, rejected.retCode(), rejected.retMsg());
                return false;
            }
            case RpcOutcome.Unreachable unreachable -> {
                log.error("扣费订单状态同步未获答复，待支付中心重推, orderNo={}, payStatus={}, cause={}",
                        orderNo, payStatus, unreachable.cause().getClass().getSimpleName());
                return false;
            }
        }
    }

    public AlipayTripFindTravelDetailRespDTO findTravelDetail(AlipayTripFindTravelDetailReqDTO request) {
        AlipayTripFindTravelDetailRespDTO response = new AlipayTripFindTravelDetailRespDTO();
        log.info("查询乘车记录详情: orderNo={}", request != null ? request.getOrderNo() : null);

        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }
        AlipayPayLog payLog = alipayPayLogMapper.selectByOrderNo(request.getOrderNo());
        if (payLog == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "未查询到支付流水");
        }

        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("查询成功");
        AlipayTripTravelDetailDTO detail = new AlipayTripTravelDetailDTO();
        detail.setPayOrderNoDate(payLog.getTransTime());
        detail.setPayChannelCode("ALIPAY");
        detail.setTradeOrderNo(payLog.getTradeNo());
        detail.setTotalAmount(payLog.getPayAmount());
        detail.setDebitRequestResult(mapPayStatusToDebitResult(payLog.getPayStatus()));
        response.setData(detail);
        log.info("查询乘车记录详情响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    private String mapPayStatusToDebitResult(String payStatus) {
        // IT-082 阶段三：对外四态 0=线上成功 / 1=线下补款人工成功（暂不产出）/ 空=支付中 / 其他=失败。
        // 入参只有 ALIPAY_PAY_LOG.PAY_STATUS；在途白名单与 trans-query / ticket-server 的
        // TransRecordAssembler.toAppDebitResult 一致，改一处 MUST 同批改那两处。
        // 「支付中」MUST 返回 null，NEVER 用 1 / 3 / 占位串（APP 把 1 当成功态）。
        String status = payStatus == null ? "" : payStatus.trim();
        if (status.isEmpty() || "CLOSED".equalsIgnoreCase(status)) {
            return "2";
        }
        if ("SUCCESS".equalsIgnoreCase(status)) {
            return "0";
        }
        if ("INIT".equalsIgnoreCase(status) || "PROCESSING".equalsIgnoreCase(status)
                || "RETRY".equalsIgnoreCase(status)) {
            return null;
        }
        return "2";
    }
}
