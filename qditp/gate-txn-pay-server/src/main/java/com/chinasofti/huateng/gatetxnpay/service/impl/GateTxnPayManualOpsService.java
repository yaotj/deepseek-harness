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
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundReqDTO;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripTxnRefundRespDTO;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundResult;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.rpc.alipay.paysign.AlipayPaySignClient;
import com.chinasofti.huateng.rpc.paySign.PaySignClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 运营后台对既有订单的两个人工干预入口：重试免密扣款、发起退款。
 *
 * <p><b>退款按渠道分流，判据与扣费侧同源（2026-09-20 新增）</b>：此前 {@link #requestRefund} 无条件调
 * {@code paySignClient.requestRefund}，而支付宝出行的原支付单落在 {@code ALIPAY_PAY_LOG}、
 * 根本不在 {@code PAY_TXN_DETAIL} 里，于是 {@code PayRefundRules.validateRefundPayTxn} 一律
 * 返「原支付订单不存在」——**支付宝出行的订单从上线起就退不了款**（实测该 orderNo 在
 * {@code PAY_TXN_DETAIL} 命中 0 行，全表 77 行 / 76 条 {@code GT%} 全是非支付宝单）。
 *
 * <p><b>分流键取 {@code ISSUE_CHANNEL_CODE}、NEVER 改成 {@code SIGN_CHANNEL_CODE}</b>：扣费发起侧
 * （{@code PaySignInitiator.converge}）用的就是 {@link IssueChannelCodeEnum#isAlipay}，退款必须与
 * 「当初是谁扣的」同源，否则会出现「支付宝扣的、却去支付中心退」这类跨域错投。两列在支付宝上
 * **当前确实同集**（2026-09-20 实测 `GATE_TXN_PAY` 分组：`07/07` 14 行，不存在一列 07 另一列非 07 的行），
 * 因此换键不改变今天的行为，但同源才是不会漂的那个。
 *
 * <p><b>支付宝分支的三条口径</b>：
 * <ul>
 *   <li>打的是**新端点** {@code /internal/alipay/payment/requestTxnRefund}（读
 *       {@code ALIPAY_PAY_TXN_DETAIL}、落 {@code ALIPAY_REFUND_TXN_DETAIL}），出向送
 *       {@code orderNo} + {@code refundAmount}（单位分）+ {@code refundReason}（落对端明细留痕、
 *       **不进支付中心 bizData**）。**旧端点 `/requestRefund` 读的 `ALIPAY_PAY_LOG` 已无写入方、
 *       对任何新订单都返 `9999 原支付记录不存在`**（2026-09-20 页面实测），它只留作回滚位，
 *       见 {@link AlipayTripTxnRefundReqDTO} 类注释；</li>
 *   <li>成功码是 {@code 0000}；应答比旧的多一个 {@code refundOrderNo}（对端生成的我方退款单号，
 *       只记日志便于去 {@code ALIPAY_REFUND_TXN_DETAIL} 与支付中心两侧对账）；</li>
 *   <li>{@link RequestRefundResult#getSuccess()} **MUST 显式 set** —— 调用点（含本类
 *       {@link #batchRefundOvertime}）判成功用的是 {@code Boolean.TRUE.equals(getSuccess())}，
 *       不 set 就等于恒失败。</li>
 * </ul>
 *
 * <p>{@link #batchRefundOvertime} 复用 {@link #requestRefund}，因此「批量退超时罚金」一并覆盖。
 */
@Service
public class GateTxnPayManualOpsService {
    private static final Logger log = LoggerFactory.getLogger(GateTxnPayManualOpsService.class);
    private static final String DEFAULT_REFUND_REASON = "运营人工退款";

    /** 支付宝出行退款申请的成功码，与 {@code FepAppErrorCodeEnum.SUCCESS} 同值。 */
    private static final String ALIPAY_RET_CODE_SUCCESS = "0000";

    private final GateTxnPayMapper gateTxnPayMapper;
    private final PaySignClient paySignClient;
    private final AlipayPaySignClient alipayPaySignClient;
    private final PaySignInitiator paySignInitiator;

    public GateTxnPayManualOpsService(GateTxnPayMapper gateTxnPayMapper,
                                     PaySignClient paySignClient,
                                     AlipayPaySignClient alipayPaySignClient,
                                     PaySignInitiator paySignInitiator) {
        this.gateTxnPayMapper = gateTxnPayMapper;
        this.paySignClient = paySignClient;
        this.alipayPaySignClient = alipayPaySignClient;
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

    /** 运营人工退款。 */
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

        if (IssueChannelCodeEnum.isAlipay(order.getIssueChannelCode())) {
            return requestAlipayTripRefund(order, refundAmount, trimToNull(request.getRefundReason()));
        }

        RequestRefundReqDTO refundRequest = new RequestRefundReqDTO();
        refundRequest.setOrderNo(order.getOrderNo());
        refundRequest.setRefundAmount(refundAmount);
        String refundReason = trimToNull(request.getRefundReason());
        refundRequest.setRefundReason(refundReason == null ? DEFAULT_REFUND_REASON : refundReason);
        RequestRefundResult refundResult = paySignClient.requestRefund(refundRequest);
        if (refundResult == null) {
            return ResultMapper.error("支付退款服务未返回结果");
        }
        if (!Boolean.TRUE.equals(refundResult.getSuccess())) {
            return ResultMapper.error(StringUtils.hasText(refundResult.getRetMsg())
                    ? refundResult.getRetMsg() : "支付退款申请失败");
        }
        return ResultMapper.ok(refundResult);
    }

    /**
     * 支付宝出行渠道的退款申请。
     *
     * <p><b>打的是新端点 {@code /internal/alipay/payment/requestTxnRefund}</b>（读
     * {@code ALIPAY_PAY_TXN_DETAIL}、落 {@code ALIPAY_REFUND_TXN_DETAIL}）。<b>NEVER 改回
     * {@code alipayTripRequestRefund}</b> 除非是回滚 —— 那条端点读的 {@code ALIPAY_PAY_LOG} 已无写入方，
     * 对任何新订单都返 {@code 9999 原支付记录不存在}（2026-09-20 页面实测：分流已生效、请求确实到了对端，
     * 卡在那一步）。回滚时两个镜像 MUST 同批换回。
     *
     * <p>新端点<b>接</b> {@code refundReason}：它落对端明细的 {@code REFUND_REASON} 列留痕，
     * 但对端**不会**把它送进支付中心 bizData（供方契约里没有这个键）。因此人工填的退款原因现在两边都留得住，
     * 而<b>本方法仍 NEVER 为了带它去动出网契约</b>。
     *
     * <p>成功判据是业务码 {@code 0000}；对端**不返回**支付中心的原始应答码，我方只认这一层。
     * {@code refundOrderNo} 是对端生成的我方退款单号，只记日志便于对账，<b>NEVER 拿它当幂等键重推</b>。
     */
    private ResultVO<RequestRefundResult> requestAlipayTripRefund(GateTxnPay order, int refundAmount,
                                                                 String refundReason) {
        AlipayTripTxnRefundReqDTO refundRequest = new AlipayTripTxnRefundReqDTO();
        refundRequest.setOrderNo(order.getOrderNo());
        refundRequest.setRefundAmount(String.valueOf(refundAmount));
        refundRequest.setRefundReason(refundReason == null ? DEFAULT_REFUND_REASON : refundReason);

        AlipayTripTxnRefundRespDTO response;
        try {
            response = alipayPaySignClient.alipayTripTxnRefund(refundRequest);
        } catch (Exception e) {
            log.error("调用alipay-pay-sign申请退款未获业务答复, orderNo={}, refundAmount={}",
                    order.getOrderNo(), refundAmount, e);
            return ResultMapper.error("支付宝出行退款服务未获答复，请稍后重试");
        }
        if (response == null) {
            log.error("调用alipay-pay-sign申请退款：应答为空, orderNo={}", order.getOrderNo());
            return ResultMapper.error("支付宝出行退款服务未返回结果");
        }

        boolean accepted = ALIPAY_RET_CODE_SUCCESS.equals(response.getRetCode());
        log.info("调用alipay-pay-sign申请退款完成, orderNo={}, refundAmount={}, retCode={}, retMsg={}, refundOrderNo={}",
                order.getOrderNo(), refundAmount, response.getRetCode(), response.getRetMsg(),
                response.getRefundOrderNo());
        if (!accepted) {
            return ResultMapper.error(StringUtils.hasText(response.getRetMsg())
                    ? response.getRetMsg() : "支付宝出行退款申请失败");
        }

        RequestRefundResult result = new RequestRefundResult();
        result.setSuccess(true);
        result.setRetCode(response.getRetCode());
        result.setRetMsg(response.getRetMsg());
        result.setOrderNo(order.getOrderNo());
        return ResultMapper.ok(result);
    }

    private GateTxnPayRespDTO reject(GateTxnPayRespDTO response, String msg) {
        response.setRetCode(GateTxnPayRetCode.INVALID_PARAM);
        response.setRetMsg(msg);
        return response;
    }

    /** 综管台批量退超时罚金：对圈出的订单逐单发起退款，金额为各自的 {@code OVERTIME_AMOUNT}。 */
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
