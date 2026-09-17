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

/** 运营后台对既有订单的两个人工干预入口：重试免密扣款、发起退款。 */
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
