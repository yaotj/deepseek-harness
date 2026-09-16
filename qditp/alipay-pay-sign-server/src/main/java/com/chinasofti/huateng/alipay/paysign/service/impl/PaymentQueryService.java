package com.chinasofti.huateng.alipay.paysign.service.impl;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCallbackLog;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayCallbackLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripFindTravelDetailRespDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripPayNotifyReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.pay.GateTxnPaySyncStatusReqDTO;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.alipay.paysign.util.PayCenterClient;
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

    /**
     * 支付结果回调允许支付中心推送的总次数（首推 1 次 + 重推 1 次），与 pay-sign-server
     * 的 {@code MAX_PAY_CALLBACK_PUSH} 同口径。
     *
     * <p>⚠️ 支付中心的重推次数 / 间隔 / 上限**没有规格**（{@code docs/external/支付中心网关接口文档.md} §5
     * 只写「未收到成功响应会重试」）。这个 2 是与 pay-sign 对齐的经验值，
     * <b>MUST 向供方索取重试规格后再校准，NEVER 把日志观察值当契约</b>。</p>
     */
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
    private PayCenterClient payCenterClient;

    @Autowired
    private GateTxnPayClient gateTxnPayClient;

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
        com.chinasofti.huateng.alipay.paysign.model.response.PayCenterResponse payCenterResponse = payCenterClient.payQuery(bizDataMap);
        log.info("支付宝出行-支付结果查询,支付中心响应结果: code={}, msg={}, success={}, responseBody={}",
                payCenterResponse != null ? payCenterResponse.getCode() : "null",
                payCenterResponse != null ? payCenterResponse.getMsg() : "null",
                payCenterResponse != null ? payCenterResponse.getSuccess() : "null",
                payCenterResponse != null ? JSON.toJSONString(payCenterResponse) : "null");

        String payStatus = payLog.getPayStatus();
        String tradeNo = payLog.getTradeNo();
        String transTime = payLog.getTransTime();
        String payAmount = payLog.getPayAmount();
        String resultMsg = payLog.getResultMsg();

        if (payCenterResponse != null && (payCenterResponse.getCode() != null && payCenterResponse.getCode() == 200 || Boolean.TRUE.equals(payCenterResponse.getSuccess()))) {
            String centerTradeNo = payCenterClient.getStringFromData(payCenterResponse, "tradeNo");
            String centerPayAmount = payCenterClient.getStringFromData(payCenterResponse, "totalAmount");
            String centerTransTime = payCenterClient.getStringFromData(payCenterResponse, "paymentTime");
            String centerTransStatus = payCenterClient.getStringFromData(payCenterResponse, "tradeStatus");
            String dataRetCode = payCenterClient.getStringFromData(payCenterResponse, "retCode");
            if (dataRetCode == null) {
                dataRetCode = payCenterClient.getStringFromData(payCenterResponse, "returnCode");
            }
            String dataRetMsg = payCenterClient.getStringFromData(payCenterResponse, "retMsg");
            if (dataRetMsg == null) {
                dataRetMsg = payCenterClient.getStringFromData(payCenterResponse, "returnMsg");
            }

            log.info("支付宝出行-支付结果查询,支付中心解密后数据: retCode={}, retMsg={}, tradeNo={}, totalAmount={}, paymentTime={}, tradeStatus={}",
                    dataRetCode, dataRetMsg, centerTradeNo, centerPayAmount, centerTransTime, centerTransStatus);

            if (StringUtils.hasText(centerTradeNo)) {
                tradeNo = centerTradeNo;
            }
            if (StringUtils.hasText(centerPayAmount)) {
                payAmount = centerPayAmount;
            }
            if (StringUtils.hasText(centerTransTime)) {
                transTime = centerTransTime;
            }

            if ("SUCCESS".equals(dataRetCode)) {
                payStatus = "SUCCESS";
                resultMsg = StringUtils.hasText(dataRetMsg) ? dataRetMsg : "支付成功";
            } else {
                payStatus = "FAIL";
                resultMsg = StringUtils.hasText(dataRetMsg) ? dataRetMsg : "支付失败";
            }

            applyQueryResult(payLog.getOrderNo(), payStatus, tradeNo, transTime, payAmount, resultMsg);
        } else {
            log.error("支付宝出行-支付结果查询未拿到支付中心业务应答，本地支付流水保持原状、NEVER 落 FAIL, orderNo={}, code={}, msg={}, success={}",
                    request.getOrderNo(),
                    payCenterResponse != null ? payCenterResponse.getCode() : "null",
                    payCenterResponse != null ? payCenterResponse.getMsg() : "null",
                    payCenterResponse != null ? payCenterResponse.getSuccess() : "null");
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

    /**
     * 把支付中心给出的终态回写本地支付流水，带非终态白名单。
     *
     * <p>影响 0 行有两种含义，MUST 回读区分，NEVER 一律当成功：
     * 本地已是目标状态即幂等命中；本地已 SUCCESS 而支付中心说失败则是口径冲突，
     * 只打 ERROR 等人工去支付中心核对，<b>NEVER 把终态覆盖成 FAIL</b>。</p>
     */
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

    /**
     * 支付宝出行扣费结果回调。
     *
     * <p>扣费订单已收口到 gate-txn-pay-server，因此本方法 <b>NEVER 再读写 ALIPAY_PAY_LOG</b>，
     * 而是把结果同步给 {@code GATE_TXN_PAY.DEBIT_STATUS}（唯一权威）。业务幂等由下游按
     * 订单号 + 状态白名单保证。</p>
     *
     * <p><b>NEVER 给本方法加 {@code @Transactional}</b>：方法体内有 RPC，
     * 事务包住网络调用会把行锁持有时长拉成对端响应时长（AGENTS.md §5.2 已记录过生产事故）；
     * 无事务时每条 SQL 自动提交，{@code ALIPAY_PAY_CALLBACK_LOG} 那行凭据不会被回滚掉。</p>
     *
     * <p>形态对齐 pay-sign-server 的 {@code PaySignWorkflow.receivePayResult}：
     * <b>回调即入库</b>当凭据 → 按累计推送次数判是否已到上限 → 同步下游 →
     * 失败且未到上限就返非 0000 让支付中心重推、到上限则返 0000 停推并把该行置 MANUAL。
     * <b>NEVER 回退成「只返非 0000、库里不留痕」</b>：那样支付中心会无限重推，
     * 而我方除容器日志外没有任何可查的证据。</p>
     */
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
            recordCallback(request, null, HANDLE_STATUS_MANUAL, "transStatus 非法: " + request.getTransStatus());
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("参数异常：transStatus 取值非法");
            return response;
        }

        String callbackSeq = recordCallback(request, payStatus, HANDLE_STATUS_PROCESSING, null);
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

    /**
     * {@code transStatus} 白名单映射：只认契约里的 {@code 1} 成功 / {@code 2} 失败，其余返回 null。
     *
     * <p><b>NEVER 写成「等于 1 就成功、否则失败」</b>——那是黑名单式判定，会把新增取值、
     * 空值、乱码统统当成扣款失败，从而把一笔可能已成功的扣费同步成 FAIL
     * （AGENTS.md §5.2「状态机校验用白名单」）。</p>
     */
    private String mapTransStatus(String transStatus) {
        if (TRANS_STATUS_SUCCESS.equals(transStatus)) {
            return "SUCCESS";
        }
        if (TRANS_STATUS_FAIL.equals(transStatus)) {
            return "FAIL";
        }
        return null;
    }

    /**
     * 回调即入库，返回主键。落库自身失败只记日志、不打断处理：留痕失败不该让一笔已扣款的回调收不了口。
     */
    private String recordCallback(AlipayTripPayNotifyReqDTO request, String payStatus, String handleStatus, String handleMsg) {
        String callbackSeq = UUID.randomUUID().toString().replaceAll("-", "");
        try {
            AlipayPayCallbackLog callbackLog = new AlipayPayCallbackLog();
            callbackLog.setCallbackSeq(callbackSeq);
            callbackLog.setOrderNo(request.getOrderNo());
            callbackLog.setCallbackType(CALLBACK_TYPE_PAY);
            callbackLog.setTransStatus(request.getTransStatus());
            callbackLog.setPayStatus(payStatus);
            callbackLog.setChannelVoucherId(request.getChannelVoucherId());
            callbackLog.setTransAmount(request.getTransAmount());
            callbackLog.setTransTime(request.getTransTime());
            callbackLog.setCardNo(request.getCardNo());
            callbackLog.setRawBody(truncate(JSON.toJSONString(request), 4000));
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

    /**
     * 统计该订单 PAY 回调的累计推送次数（含本次）。
     *
     * <p>计数点在 insert 之后：本方法无事务，insert 已提交。取不到计数时返回 0，
     * 等于「不启用硬限次、保持原来的让上游重推」，<b>NEVER 因为计数失败就直接放行返 0000</b>。</p>
     */
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

    /**
     * 达到重推上限仍未处理成功：回 0000 让支付中心停推，同时把最后一条回调标成 MANUAL。
     *
     * <p>这是「不再自动重试」换「不再自我放大」的取舍，因此 MUST 同时打 ERROR 并留痕。
     * <b>运维 MUST 例行巡检 {@code HANDLE_STATUS='MANUAL'}</b>，否则失败会静默沉底。</p>
     */
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

    /**
     * 通知 gate-txn-pay-server 把 {@code GATE_TXN_PAY.DEBIT_STATUS} 收敛到终态。
     *
     * <p>与 pay-sign-server 的 {@code PaySignWorkflow.syncGateTxnPayStatus} 保持同一形态：
     * 显式判 {@code retCode=0000}，失败只记日志并交回调用方决定是否让上游重推。</p>
     *
     * @return true 表示远端已确认收敛（含幂等命中），false 表示需要支付中心重推
     */
    private boolean syncGateTxnPayStatus(String orderNo, String payStatus) {
        GateTxnPaySyncStatusReqDTO syncRequest = new GateTxnPaySyncStatusReqDTO();
        syncRequest.setOrderNo(orderNo);
        syncRequest.setPayStatus(payStatus);
        syncRequest.setRemark("支付宝出行扣费结果回调");
        try {
            GateTxnPayRespDTO syncResponse = gateTxnPayClient.syncDebitStatus(syncRequest);
            if (syncResponse == null || !"0000".equals(syncResponse.getRetCode())) {
                log.error("扣费订单状态同步失败，待支付中心重推, orderNo={}, payStatus={}, 返回={}",
                        orderNo, payStatus, syncResponse);
                return false;
            }
            log.info("扣费订单状态同步成功, orderNo={}, payStatus={}", orderNo, payStatus);
            return true;
        } catch (Exception e) {
            log.error("扣费订单状态同步异常，待支付中心重推, orderNo={}, payStatus={}", orderNo, payStatus, e);
            return false;
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
        response.setPayOrderNoDate(payLog.getTransTime());
        response.setPayChannelCode("ALIPAY");
        response.setTradeOrderNo(payLog.getTradeNo());
        response.setTotalAmount(payLog.getPayAmount());
        response.setDebitRequestResult(mapPayStatusToDebitResult(payLog.getPayStatus()));
        log.info("查询乘车记录详情响应结果：{}", JSON.toJSONString(response));
        return response;
    }

    /**
     * debitRequestResult 是对外契约字段，值域只有 "0"（扣费成功）与 "1"（未成功），
     * NEVER 往里塞渠道文案 —— 本方法上线前该字段填的是 ALIPAY_PAY_LOG.RESULT_MSG，
     * 支付宝侧按 0/1 解析，任何文案都被判成非 0 即失败，成功单也显示扣费未成功。
     * 同一套映射在 fep-alipay-server 的 AlipayQueryServiceImpl:236 / :416 各有一份
     * （私有方法同名），三处 MUST 保持一致；跨模块无法直接复用，见 AGENTS.md §5.1
     * 「NEVER 主动创建新的工具类」。
     */
    private String mapPayStatusToDebitResult(String payStatus) {
        return "SUCCESS".equalsIgnoreCase(payStatus) ? "0" : "1";
    }
}
