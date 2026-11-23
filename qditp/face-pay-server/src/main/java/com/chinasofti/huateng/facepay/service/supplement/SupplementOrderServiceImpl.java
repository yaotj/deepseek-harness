package com.chinasofti.huateng.facepay.service.supplement;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.app.AppResponses;
import com.chinasofti.huateng.facepay.api.device.app.RequestPayInfoReqDTO;
import com.chinasofti.huateng.facepay.entity.GateTxnPay;
import com.chinasofti.huateng.facepay.entity.SupplementOrder;
import com.chinasofti.huateng.facepay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.facepay.mapper.SupplementOrderMapper;
import com.chinasofti.huateng.model.pay.SupplementOrderReqDTO;
import com.chinasofti.huateng.model.pay.SupplementOrderRespDTO;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SupplementOrderServiceImpl implements SupplementOrderService {

    private static final Logger log = LoggerFactory.getLogger(SupplementOrderServiceImpl.class);

    private static final String SUPPLEMENT_INIT = "INIT";
    private static final String SUPPLEMENT_PROCESSING = "PROCESSING";

    // 可补款的原订单状态白名单。
    // 2026-11-20 用户裁决：INIT 不可补款 —— 出站落单后订单先以 INIT 落库、随后由异步任务发往支付中心接，
    // 这段「发起中」窗口里钱可能已经在路上（接口对外也已回 payStatus=PROCESSING），此时让它可补款就是双付口子。
    // 卡在 INIT 的单也不需要人工补款自救：DebitRetryProcessor 的 scan（sys_job 220/255）每轮都扫
    // INIT/RETRY/FAIL（带 minAgeSeconds=60 的观察期），必然接管；`OFFLINE_FARE_PENDING`（离线码金额待重算）
    // 同样以 INIT 落库且金额为 0，一并挡在外面（自动重试与 requestPayFailOrder 早就排除了它，补款此前漏排除）。
    // 与 trans-query / gate-txn-pay「地铁未扣费订单」的 Debit_Result_Filter 白名单 MUST 同源：
    // 列表里能看到的每一笔都真的可补款，否则就是今天这个「列出来、点了必 8003」的死循环。
    private static final List<String> DEBIT_PENDING = List.of("RETRY", "FAIL");

    private static final DateTimeFormatter ORDER_NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private static final String CODE_SUCCESS = "0000";
    private static final String CODE_INVALID_PARAM = "8001";
    private static final String CODE_STATUS_REJECTED = "8003";

    private final SupplementOrderMapper supplementOrderMapper;
    private final GateTxnPayMapper gateTxnPayMapper;
    private final SupplementPayCenterFlow payCenterFlow;
    private final SupplementOrderLocalWriter localWriter;

    public SupplementOrderServiceImpl(SupplementOrderMapper supplementOrderMapper,
                                      GateTxnPayMapper gateTxnPayMapper,
                                      SupplementPayCenterFlow payCenterFlow,
                                      SupplementOrderLocalWriter localWriter) {
        this.supplementOrderMapper = supplementOrderMapper;
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.payCenterFlow = payCenterFlow;
        this.localWriter = localWriter;
    }

    @Override
    public SupplementOrderRespDTO requestPayOrder(SupplementOrderReqDTO request) {
        List<String> orderNos = deduplicate(request.getOrderNoList());
        if (orderNos.isEmpty()) {
            return buildResp(CODE_INVALID_PARAM, "orderNoList 不能为空", null, null, null);
        }
        if (orderNos.size() > 500) {
            return buildResp(CODE_INVALID_PARAM, "orderNoList 超过 500 笔上限", null, null, null);
        }

        List<GateTxnPay> gateTxnPays = gateTxnPayMapper.selectByOrderNos(orderNos);
        Map<String, GateTxnPay> orderIndex = new LinkedHashMap<>();
        for (GateTxnPay gtp : gateTxnPays) {
            orderIndex.put(gtp.getOrderNo(), gtp);
        }

        List<String> missing = new ArrayList<>();
        List<String> notPending = new ArrayList<>();
        List<String> ownerMismatch = new ArrayList<>();
        String expectedUserId = null;
        String expectedCardId = null;
        String expectedCardType = null;
        String expectedPaymentVendor = null;
        String expectedSignChannelCode = null;
        long totalAmount = 0L;

        for (String orderNo : orderNos) {
            GateTxnPay gtp = orderIndex.get(orderNo);
            if (gtp == null) {
                missing.add(orderNo);
                continue;
            }
            if (!DEBIT_PENDING.contains(gtp.getDebitStatus())) {
                notPending.add(orderNo);
                continue;
            }
            if (expectedUserId == null) {
                expectedUserId = gtp.getThirdUserId();
                expectedCardId = gtp.getCardId();
                expectedCardType = gtp.getCardType();
                expectedPaymentVendor = gtp.getPaymentVendor();
                expectedSignChannelCode = gtp.getSignChannelCode();
            } else {
                if (!equals(expectedUserId, gtp.getThirdUserId())
                        || !equals(expectedCardId, gtp.getCardId())) {
                    ownerMismatch.add(orderNo);
                }
            }
            totalAmount += gtp.getTotalAmount() != null ? gtp.getTotalAmount() : 0;
        }

        if (!missing.isEmpty()) {
            log.warn("补款下单校验失败：部分原订单不存在, missingCount={}", missing.size());
            return buildResp(CODE_INVALID_PARAM, "存在无效的原订单号", null, null, null);
        }
        if (!notPending.isEmpty()) {
            log.warn("补款下单校验失败：部分原订单状态不可补款（非 RETRY/FAIL：已结清或正在扣费中）, notPendingCount={}",
                    notPending.size());
            return buildResp(CODE_STATUS_REJECTED, "部分订单已结清或正在扣费中，暂不可补款", null, null, null);
        }
        if (!ownerMismatch.isEmpty()) {
            log.warn("补款下单校验失败：订单归属不一致, mismatchCount={}", ownerMismatch.size());
            return buildResp(CODE_INVALID_PARAM, "订单归属不一致", null, null, null);
        }

        if (request.getThirdUserId() != null && !equals(request.getThirdUserId(), expectedUserId)) {
            return buildResp(CODE_INVALID_PARAM, "thirdUserId 与原订单不一致", null, null, null);
        }
        if (request.getCardId() != null && !equals(request.getCardId(), expectedCardId)) {
            return buildResp(CODE_INVALID_PARAM, "cardId 与原订单不一致", null, null, null);
        }

        String supplementOrderNo = generateOrderNo(expectedCardId);
        SupplementOrder order = new SupplementOrder();
        order.setOrderNo(supplementOrderNo);
        order.setPayStatus(SUPPLEMENT_INIT);
        order.setThirdUserId(expectedUserId);
        order.setCardId(expectedCardId);
        order.setCardType(expectedCardType);
        order.setGoodsCode(request.getGoodsCode() != null ? request.getGoodsCode() : "001");
        order.setQuantity(1);
        order.setTotalAmount(totalAmount);
        order.setOrderCount(orderNos.size());
        // PAYMENT_VENDOR 只是「原支付方式」留痕（IT-014），NEVER 拿它发报：
        // 补款允许乘客自选渠道（设计要求），实际渠道由 IF8A-11 的 payChannelCode 决定，
        // 落在 PAY_CHANNEL_CODE。若拿这一列发报，乘客选的 04 会被顶成原单的 07（2026-11-20 实测）。
        order.setPaymentVendor(expectedPaymentVendor);
        order.setSignChannelCode(expectedSignChannelCode);
        order.setTxnDate(LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        // 建单不做业务防重（2026-11-20 裁决）：APP 侧本身没有防重逻辑（旧流程就是靠重复下单，
        // 只是重复的那几张不付费而已），补款是抢占式 —— 同一批欠费允许生成多张补款单，
        // 谁先付款谁结清：GATE_TXN_PAY 的 DEBIT_STATUS 条件更新 + 明细 SETTLED 抢占兜底，
        // 后到的重复支付由回调侧标「重复支付待退款」（IT-089）。
        // 因此这里 NEVER 再返回「补款单已存在，请勿重复提交」，也 NEVER 把生成的单号回给调用方
        // 却没落库（旧代码在拒绝分支里返回了一个库里不存在的 orderNo，APP 拿去调 IF8A-11 只会得到
        // 「订单号错误」）。
        supplementOrderNo = localWriter.persist(order, orderNos, orderIndex);

        // IF8A-26 只落单、不预下单：渠道此时还不知道（APP 在支付页才选），
        // 预下单 MUST 留到 IF8A-11 按乘客所选 payChannelCode 执行，见 requestPayInfo。
        log.info("补款单落单完成，等待 IF8A-11 按乘客所选渠道预下单, orderNo={}", supplementOrderNo);
        return buildResp(CODE_SUCCESS, "成功", supplementOrderNo, SUPPLEMENT_INIT, totalAmount);
    }

    @Override
    public JSONObject requestPayInfo(RequestPayInfoReqDTO request) {
        String orderNo = request.getOrderNo();
        SupplementOrder order = supplementOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            log.info("补款单请求支付信息 订单不存在, orderNo={}", orderNo);
            return AppResponses.failMessage("订单号错误");
        }
        String payStatus = order.getPayStatus();
        if (!SUPPLEMENT_INIT.equals(payStatus) && !SUPPLEMENT_PROCESSING.equals(payStatus)) {
            log.info("补款单请求支付信息 状态不可支付, orderNo={}, payStatus={}", orderNo, payStatus);
            return AppResponses.failMessage("SUCCESS".equals(payStatus)
                    ? "该补款单已支付成功" : "订单状态异常");
        }
        if (order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            log.error("补款单请求支付信息 金额非法, orderNo={}, amount={}", orderNo, order.getTotalAmount());
            return AppResponses.failMessage("订单金额异常，请联系工作人员");
        }

        // PAY_CHANNEL_CODE 才是本次实际预下单用的渠道（乘客选的）；PAYMENT_VENDOR 是原支付方式留痕。
        String orderedChannel = order.getPayChannelCode();
        String requested = request.getPayChannelCode();
        if (requested == null || requested.isBlank()) {
            log.warn("补款单请求支付信息 未传渠道, orderNo={}", orderNo);
            return AppResponses.failMessage("支付方式不能为空");
        }
        // APP 支付页允许乘客改支付方式（2026-11-20 用户确认：可以和原支付方式不一样，
        // 也可以和上一次已预下单的渠道不一样）。因此这里**不锁渠道**：
        // 同渠道走回放（避免重复下单），异渠道按新渠道重下（旧参数由支付中心同订单号覆盖）。
        boolean sameChannel = requested.equals(orderedChannel);
        boolean needPreOrder = SUPPLEMENT_INIT.equals(payStatus) || !sameChannel;
        if (SUPPLEMENT_PROCESSING.equals(payStatus) && !sameChannel) {
            log.info("补款单换渠道重下, orderNo={}, from={}, to={}", orderNo, orderedChannel, requested);
        }

        if (needPreOrder) {
            // 补款允许乘客自选渠道：预下单 MUST 用本次 IF8A-11 传入的 payChannelCode。
            RpcOutcome outcome;
            try {
                outcome = payCenterFlow.preOrder(order, requested);
            } catch (Exception e) {
                log.error("补款单请求支付信息 预下单异常, orderNo={}, vendor={}", orderNo, requested, e);
                return AppResponses.failMessage("支付中心暂时不可用，请稍后重试");
            }
            JSONObject rejected = switch (outcome) {
                case RpcOutcome.Ok ok -> null;
                case RpcOutcome.BizRejected biz -> isChannelSwitchFailure(biz.retCode())
                        ? AppResponses.failMessage("支付方式未能切换，请稍后重试或联系工作人员")
                        : AppResponses.failMessage("获取支付信息失败[" + biz.retCode() + "]");
                case RpcOutcome.Unreachable unreachable ->
                        AppResponses.failMessage("支付中心暂时不可用，请稍后重试");
            };
            if (rejected != null) {
                return rejected;
            }
            order = supplementOrderMapper.selectByOrderNo(orderNo);
        }

        String paymentInfo = order != null ? order.getPaymentInfo() : null;
        if (paymentInfo == null || paymentInfo.isBlank()) {
            log.error("补款单请求支付信息 支付参数缺失, orderNo={}, payStatus={}",
                    orderNo, order != null ? order.getPayStatus() : null);
            return AppResponses.failMessage("支付信息不可用，请重新发起补款");
        }
        String channel = order.getPayChannelCode() != null && !order.getPayChannelCode().isBlank()
                ? order.getPayChannelCode() : order.getPaymentVendor();
        log.info("补款单请求支付信息成功, orderNo={}, payChannelCode={}, merchantOrderNo={}",
                orderNo, channel, order.getMerchantOrderNo());
        return AppResponses.payInfo(channel, paymentInfo);
    }

    @Override
    public boolean isSupplementOrder(String orderNo) {
        try {
            return supplementOrderMapper.selectByOrderNo(orderNo) != null;
        } catch (Exception e) {
            log.error("查询补款单异常，降级返回 false, orderNo={}", orderNo, e);
            return false;
        }
    }

    @Override
    public int convergePendingOrders(int limit) {
        return payCenterFlow.convergePending(limit);
    }

    @Override
    public int closeTimeoutOrders(int timeoutMinutes, int limit) {
        List<SupplementOrder> orders = supplementOrderMapper.selectTimeoutPending(timeoutMinutes, limit);
        int closed = 0;
        for (SupplementOrder order : orders) {
            int updated = supplementOrderMapper.closeTimeoutOrder(order.getOrderNo(),
                    "超时未支付关单, timeoutMinutes=" + timeoutMinutes);
            if (updated > 0) {
                closed++;
                log.info("补款超时关单, orderNo={}", order.getOrderNo());
            }
        }
        return closed;
    }

    @Override
    public int closeStaleProcessingOrders(int timeoutMinutes, int limit) {
        List<SupplementOrder> orders = supplementOrderMapper.selectTimeoutProcessing(timeoutMinutes, limit);
        int closed = 0;
        for (SupplementOrder order : orders) {
            int updated = supplementOrderMapper.closeStaleProcessingOrder(order.getOrderNo(),
                    "PROCESSING 超时未支付关单, timeoutMinutes=" + timeoutMinutes);
            if (updated > 0) {
                closed++;
                log.info("补款 PROCESSING 超时关单, orderNo={}", order.getOrderNo());
            }
        }
        return closed;
    }

    /**
     * 换渠道失败的两种标记码（支付中心不认新渠道 / 本地没接住），对乘客给同一句人话。
     * 两种都不改库：失败时库里的渠道与支付参数仍是上一版可用的那一份。
     */
    private boolean isChannelSwitchFailure(String retCode) {
        return SupplementPayCenterFlow.CODE_CHANNEL_LOCKED.equals(retCode)
                || SupplementPayCenterFlow.CODE_CHANNEL_NOT_APPLIED.equals(retCode);
    }

    private List<String> deduplicate(List<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return List.of();
        }
        Set<String> dedup = new LinkedHashSet<>();
        for (String no : orderNos) {
            if (no != null && !no.isBlank()) {
                dedup.add(no);
            }
        }
        return new ArrayList<>(dedup);
    }

    private String generateOrderNo(String cardId) {
        String suffix = "";
        if (cardId != null && cardId.length() >= 6) {
            suffix = cardId.substring(cardId.length() - 6);
        } else if (cardId != null) {
            suffix = cardId;
        }
        return ORDER_NO_PREFIX + ORDER_NO_FMT.format(LocalDateTime.now()) + suffix;
    }

    private SupplementOrderRespDTO buildResp(String retCode, String retMsg,
                                             String orderNo, String payStatus, Long totalAmount) {
        SupplementOrderRespDTO resp = new SupplementOrderRespDTO();
        resp.setRetCode(retCode);
        resp.setRetMsg(retMsg);
        resp.setOrderNo(orderNo);
        resp.setPayStatus(payStatus);
        resp.setTotalAmount(totalAmount);
        return resp;
    }

    private static boolean equals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.equals(b);
    }
}
