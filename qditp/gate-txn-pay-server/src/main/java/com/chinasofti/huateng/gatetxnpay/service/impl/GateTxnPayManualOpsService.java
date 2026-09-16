package com.chinasofti.huateng.gatetxnpay.service.impl;

import com.chinasofti.huateng.common.response.ResultMapper;
import com.chinasofti.huateng.common.response.ResultVO;
import com.chinasofti.huateng.gatetxnpay.constant.DebitStatus;
import com.chinasofti.huateng.gatetxnpay.constant.GateTxnPayRetCode;
import com.chinasofti.huateng.gatetxnpay.entity.GateTxnPay;
import com.chinasofti.huateng.gatetxnpay.mapper.GateTxnPayMapper;
import com.chinasofti.huateng.gatetxnpay.model.page.BatchRefundOvertimeRequest;
import com.chinasofti.huateng.gatetxnpay.model.page.BatchRefundResult;
import com.chinasofti.huateng.gatetxnpay.model.page.GateTxnPayRefundRequest;
import com.chinasofti.huateng.gatetxnpay.paysign.PaySignInitiator;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 运营后台对**既有订单**的两个人工干预入口：重试免密扣款、发起退款。
 *
 * <p>与出站扣费（{@code requestPay}）拆开的理由是**变化理由不同**，不是行数：
 * 出站扣费跟着闸机报文（IF1A-01）与算价规则变，本类跟着运营流程与支付中心退款接口变；
 * 两者唯一的共同点是读同一张表。合在一处时 {@code GateTxnPayServiceImpl} 因为退款
 * 而必须持有 {@code PaySignClient}，于是那个类同时握着「出账口」「退款口」两个出向 RPC ——
 * 拆开后 <b>{@code GateTxnPayServiceImpl} 不再直接持有任何 rpc client</b>。</p>
 *
 * <p>两个方法的共同前置形状 <b>MUST</b> 保持：按 {@code orderNo} 回查 → 日票直接拒绝 →
 * <b>状态白名单</b>（{@link DebitStatus#isRetryable} / {@link DebitStatus#isRefundable}）。
 * <b>NEVER</b> 把白名单改成「非终态即可」（AGENTS.md §5.2）。</p>
 *
 * <p>本类 <b>NEVER</b> 加 {@code @Transactional}：退款分支内有支付中心 RPC，
 * 事务包住网络调用已经出过生产事故（AGENTS.md §5.2 的行锁放大）。</p>
 */
@Service
public class GateTxnPayManualOpsService {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayManualOpsService.class);
    private static final String DEFAULT_REFUND_REASON = "运营人工退款";

    private final GateTxnPayMapper gateTxnPayMapper;
    private final PaySignClient paySignClient;
    private final PaySignInitiator paySignInitiator;

    public GateTxnPayManualOpsService(GateTxnPayMapper gateTxnPayMapper,
                                     PaySignClient paySignClient,
                                     PaySignInitiator paySignInitiator) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.paySignClient = paySignClient;
        this.paySignInitiator = paySignInitiator;
    }

    /** 对支付失败 / 未支付的订单单独重试免密扣款。 */
    public GateTxnPayRespDTO retryPay(String orderNo) {
        GateTxnPayRespDTO response = new GateTxnPayRespDTO();
        if (!StringUtils.hasText(orderNo)) {
            return reject(response, "orderNo不能为空");
        }

        GateTxnPay order = gateTxnPayMapper.selectByOrderNo(orderNo.trim());
        if (order == null) {
            return reject(response, "未找到对应的过闸扣费订单");
        }
        if (isDailyTicket(order)) {
            return reject(response, "日票订单不支持重试支付");
        }
        if (!DebitStatus.isRetryable(order.getDebitStatus())) {
            return reject(response, "当前扣费状态不允许重试支付：" + order.getDebitStatus());
        }

        String nextStatus = paySignInitiator.retryAndConverge(order);

        response.setRetCode(GateTxnPayRetCode.SUCCESS);
        response.setRetMsg("成功");
        response.setOrderNo(order.getOrderNo());
        response.setPayStatus(nextStatus);
        return response;
    }

    /**
     * 运营人工退款。
     *
     * <p>本方法**只发起**退款，不改本地 {@code DEBIT_STATUS} —— 退款结果由支付中心回调
     * 走各自的链路收敛。<b>NEVER</b> 在这里顺手把订单改成某个「已退款」状态：
     * 那个状态在 {@link DebitStatus} 里不存在，写进去等于给状态机加了一个没人认识的值。</p>
     */
    public ResultVO<RequestRefundResult> requestRefund(String orderNo, GateTxnPayRefundRequest request) {
        if (!StringUtils.hasText(orderNo)) {
            return ResultMapper.illegalParams("orderNo不能为空");
        }
        if (request == null || request.getRefundAmount() == null) {
            return ResultMapper.illegalParams("refundAmount不能为空，单位为分");
        }

        GateTxnPay order = gateTxnPayMapper.selectByOrderNo(orderNo.trim());
        if (order == null) {
            return ResultMapper.error("未找到对应的过闸扣费订单");
        }
        if (isDailyTicket(order)) {
            return ResultMapper.error("日票订单不支持通过过闸扣费退款入口处理");
        }
        if (!DebitStatus.isRefundable(order.getDebitStatus())) {
            return ResultMapper.error("当前扣费状态不允许退款：" + order.getDebitStatus());
        }
        if (order.getTotalAmount() == null || order.getTotalAmount() <= 0) {
            return ResultMapper.error("订单扣费金额无效，不能退款");
        }
        int refundAmount = request.getRefundAmount();
        if (refundAmount <= 0 || refundAmount > order.getTotalAmount()) {
            return ResultMapper.illegalParams("退款金额须大于0且不超过订单扣费金额");
        }

        RequestRefundReqDTO refundRequest = new RequestRefundReqDTO();
        refundRequest.setOrderNo(order.getOrderNo());
        refundRequest.setRefundAmount(refundAmount);
        String refundReason = trimToNull(request.getRefundReason());
        refundRequest.setRefundReason(refundReason == null ? DEFAULT_REFUND_REASON : refundReason);
        RequestRefundResult refundResult = paySignClient.requestRefund(refundRequest);
        // 「没抛异常」不等于退款成功：这里 MUST 显式判 success（AGENTS.md §5.2）。
        if (refundResult == null) {
            return ResultMapper.error("支付退款服务未返回结果");
        }
        if (!Boolean.TRUE.equals(refundResult.getSuccess())) {
            return ResultMapper.error(StringUtils.hasText(refundResult.getRetMsg())
                    ? refundResult.getRetMsg() : "支付退款申请失败");
        }
        return ResultMapper.ok(refundResult);
    }

    private GateTxnPayRespDTO reject(GateTxnPayRespDTO response, String msg) {
        response.setRetCode(GateTxnPayRetCode.INVALID_PARAM);
        response.setRetMsg(msg);
        return response;
    }

    /**
     * 综管台批量退超时罚金：对圈出的订单逐单发起退款，金额为各自的 {@code OVERTIME_AMOUNT}。
     *
     * <p>每单仍走 {@link #requestRefund} 的单笔链路（日票拒退、状态白名单、金额上限全保留），
     * 单笔失败只记入明细、不抛异常阻断整批；本方法 <b>NEVER</b> 加 {@code @Transactional}
     * （内部含支付中心 RPC，与本类其它方法同一约束）。</p>
     *
     * <p>入参硬闸：orderNos 非空、去重后 ≤ {@link BatchRefundOvertimeRequest#MAX_BATCH_SIZE}。
     * 某笔查不到订单或 {@code OVERTIME_AMOUNT} 为空/≤0 时该笔记失败、继续下一笔。</p>
     */
    public ResultVO<BatchRefundResult> batchRefundOvertime(BatchRefundOvertimeRequest batchRequest) {
        if (batchRequest == null || batchRequest.getOrderNos() == null || batchRequest.getOrderNos().isEmpty()) {
            return ResultMapper.illegalParams("orderNos不能为空");
        }
        List<String> orderNos = batchRequest.getOrderNos().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (orderNos.isEmpty()) {
            return ResultMapper.illegalParams("orderNos不能为空");
        }
        if (orderNos.size() > BatchRefundOvertimeRequest.MAX_BATCH_SIZE) {
            return ResultMapper.illegalParams("单批最多 " + BatchRefundOvertimeRequest.MAX_BATCH_SIZE + " 笔，请分批提交");
        }
        String refundReason = trimToNull(batchRequest.getRefundReason());

        BatchRefundResult result = new BatchRefundResult();
        for (String orderNo : orderNos) {
            BatchRefundResult.ItemResult item = new BatchRefundResult.ItemResult();
            item.setOrderNo(orderNo);
            GateTxnPay order = gateTxnPayMapper.selectByOrderNo(orderNo);
            if (order == null) {
                item.setSuccess(false);
                item.setRetMsg("未找到对应的过闸扣费订单");
            } else if (order.getOvertimeAmount() == null || order.getOvertimeAmount() <= 0) {
                item.setSuccess(false);
                item.setRetMsg("订单无超时罚金，无需退");
            } else {
                item.setRefundAmount(order.getOvertimeAmount());
                GateTxnPayRefundRequest single = new GateTxnPayRefundRequest();
                single.setRefundAmount(order.getOvertimeAmount());
                single.setRefundReason(refundReason);
                ResultVO<RequestRefundResult> singleResult = requestRefund(orderNo, single);
                boolean ok = singleResult != null && ResultVO.SUCCESS_CODE.equals(singleResult.getCode());
                item.setSuccess(ok);
                item.setRetMsg(singleResult != null ? singleResult.getMsg() : "退款服务未返回结果");
            }
            if (item.isSuccess()) {
                result.setSuccessCount(result.getSuccessCount() + 1);
            } else {
                result.setFailCount(result.getFailCount() + 1);
                log.warn("批量退超时罚金单笔失败, orderNo={}, retMsg={}", orderNo, item.getRetMsg());
            }
            result.getItems().add(item);
        }
        result.setTotal(result.getItems().size());
        log.info("批量退超时罚金完成, total={}, success={}, fail={}",
                result.getTotal(), result.getSuccessCount(), result.getFailCount());
        return ResultMapper.ok(result);
    }

    private boolean isDailyTicket(GateTxnPay order) {
        return CardTypeCodeEnum.isDailyTicket(order.getCardType());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
