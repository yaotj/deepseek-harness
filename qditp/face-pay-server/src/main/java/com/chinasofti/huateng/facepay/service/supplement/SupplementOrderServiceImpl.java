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

    private static final List<String> DEBIT_PENDING = List.of("INIT", "RETRY", "FAIL");

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
            log.warn("补款下单校验失败：部分原订单状态不可补款（非 INIT/RETRY/FAIL）, notPendingCount={}", notPending.size());
            return buildResp(CODE_STATUS_REJECTED, "部分订单已结清或状态不可补款", null, null, null);
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
        order.setPaymentVendor(expectedPaymentVendor);
        order.setSignChannelCode(expectedSignChannelCode);
        // SUPPLEMENT_ORDER.TXN_DATE 是 NOT NULL，取补款发生日（yyyyMMdd）
        order.setTxnDate(LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE));
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());

        SupplementOrderLocalWriter.PersistResult persistResult = localWriter.persist(order, orderNos, orderIndex);
        if (!persistResult.persisted()) {
            return buildResp(CODE_STATUS_REJECTED, "补款单已存在，请勿重复提交",
                    supplementOrderNo, null, totalAmount);
        }

        RpcOutcome outcome;
        try {
            outcome = payCenterFlow.preOrder(order);
        } catch (Exception e) {
            log.error("补款预下单异常, orderNo={}", supplementOrderNo, e);
            supplementOrderMapper.updatePayStatusFromPending(
                    supplementOrderNo, SUPPLEMENT_INIT, "预下单异常: " + truncate(e.getMessage()));
            return buildResp(CODE_INVALID_PARAM, "补款预下单异常", supplementOrderNo, SUPPLEMENT_INIT, totalAmount);
        }

        return switch (outcome) {
            case RpcOutcome.Ok ok -> buildResp(CODE_SUCCESS, "成功", supplementOrderNo,
                    SUPPLEMENT_PROCESSING, totalAmount);
            case RpcOutcome.BizRejected rejected -> {
                supplementOrderMapper.updatePayStatusFromPending(
                        supplementOrderNo, SUPPLEMENT_INIT,
                        "支付中心拒绝: " + truncate(rejected.retMsg()));
                yield buildResp(CODE_STATUS_REJECTED,
                        "支付中心拒绝: " + rejected.retMsg(),
                        supplementOrderNo, SUPPLEMENT_INIT, totalAmount);
            }
            case RpcOutcome.Unreachable unreachable -> {
                log.error("补款预下单支付中心不可达, orderNo={}", supplementOrderNo, unreachable.cause());
                yield buildResp(CODE_SUCCESS, "补款单已生成，待支付中心受理",
                        supplementOrderNo, SUPPLEMENT_INIT, totalAmount);
            }
        };
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

        // 通道由 IF8A-26 按原订单的 PAYMENT_VENDOR 定，支付参数也是按它向支付中心换的。
        // 换通道 MUST 重新走 IF8A-26 建新单，NEVER 在这里按 APP 传入的通道重下：
        // PROCESSING 时支付中心已挂待支付单，再下一次等于同一笔欠费有两份可付参数。
        String vendor = order.getPaymentVendor();
        String requested = request.getPayChannelCode();
        if (requested != null && !requested.isBlank() && vendor != null && !requested.equals(vendor)) {
            log.warn("补款单请求支付信息 通道与下单时不一致, orderNo={}, orderVendor={}, requested={}",
                    orderNo, vendor, requested);
            return AppResponses.failMessage("支付方式与下单时不一致，请重新发起补款");
        }

        if (SUPPLEMENT_INIT.equals(payStatus)) {
            // 落到这里说明下单那次支付中心不可达（IF8A-26 的 Unreachable 分支回 0000 + INIT），
            // 补一次预下单。updatePrepayResult 的 WHERE 只认 INIT，天然挡住重复预下单。
            RpcOutcome outcome;
            try {
                outcome = payCenterFlow.preOrder(order);
            } catch (Exception e) {
                log.error("补款单请求支付信息 预下单异常, orderNo={}", orderNo, e);
                return AppResponses.failMessage("支付中心暂时不可用，请稍后重试");
            }
            JSONObject rejected = switch (outcome) {
                case RpcOutcome.Ok ok -> null;
                case RpcOutcome.BizRejected biz ->
                        AppResponses.failMessage("获取支付信息失败[" + biz.retCode() + "]");
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

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 256 ? s : s.substring(0, 256);
    }
}
