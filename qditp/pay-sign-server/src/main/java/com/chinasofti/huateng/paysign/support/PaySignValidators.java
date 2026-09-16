package com.chinasofti.huateng.paysign.support;

import com.chinasofti.huateng.model.app.ReceiveSignResultReqDTO;
import com.chinasofti.huateng.model.app.RequestPayReqDTO;
import com.chinasofti.huateng.model.app.RequestRefundReqDTO;
import com.chinasofti.huateng.model.app.RequestSignInfoReqDTO;
import com.chinasofti.huateng.model.app.ReceiveTerminationResultReqDTO;
import com.chinasofti.huateng.model.app.RequestTerminationReqDTO;
import org.springframework.util.StringUtils;

/**
 * 入向报文的**必填字段校验**，返回第一条不满足的说明、全部通过返回 {@code null}（2026-09-15 拆分批次 2）。
 *
 * <p><b>由来</b>：7 个方法原是 {@code PaySignWorkflow} 的私有 {@code validateXxx}，逐字搬迁；
 * 方法名与参数列表原样保留、调用方改用 {@code import static}，因此**调用点一个字都没改**。
 *
 * <p><b>返回的字符串会原样进 APP 应答的 {@code retMsg}</b>，因此
 * <b>NEVER 改文案、NEVER 调整判断顺序</b> —— 顺序决定「同时缺两个字段时报哪一个」，
 * 而 APP 与联调方可能已按这些文案做了断言。要改 MUST 当成对外契约变更处理。
 *
 * <p><b>刻意留在业务类里没搬的是 {@code validateRefundPayTxn}</b>：它读 {@code PayTxnDetail} 的
 * 支付状态、算「可退金额 = 已付 − 已退」，还依赖 {@code resolvePaidAmount}，
 * 属**退款业务规则**而非报文校验。搬进本类等于把业务判断塞进 support 包，
 * <b>NEVER 因为名字都叫 validate 就一起搬</b>。
 *
 * <p>本类与 {@code PaySignResponses} 同属对 AGENTS.md §5.1「NEVER 主动创建新的工具类」的有意破例，
 * 理由同 {@code F2fDuplicateKey}（ADR-D84）：纯函数、零状态、零依赖，做成 Bean 只是徒增装配；
 * 而留在业务类里就没法单独测。<b>NEVER 往本类加任何 mapper / client 依赖</b>。
 */
public final class PaySignValidators {

    private PaySignValidators() {
    }

    /** IF8A-16 请求签约信息。 */
    public static String validateRequestSignInfo(RequestSignInfoReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getDisplayAccount())) {
            return "displayAccount不能为空";
        }
        if (!StringUtils.hasText(request.getPayChannelCode())) {
            return "payChannelCode不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        return null;
    }

    /** 签约查询类接口（IF8A-21 / IF8A-22）的公共参数。 */
    public static String validateContractQuery(String thirdUserId, String requestSignSeq, String paymentVendor) {
        if (!StringUtils.hasText(thirdUserId)) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(requestSignSeq)) {
            return "requestSignSeq不能为空";
        }
        if (!StringUtils.hasText(paymentVendor)) {
            return "paymentVendor不能为空";
        }
        return null;
    }

    /**
     * IF8A-06 请求解约。
     *
     * <p><b>cardId / cardType / paymentVendor 刻意非必填</b>：APP 报文里常常不带，
     * 由 {@code requestTermination} 用签约记录回填（那三列在 {@code APP_TERMINATION_REQUEST} 是 NOT NULL）。
     * <b>NEVER 在这里加上它们的必填校验</b>，会把能正常受理的解约申请挡掉。
     */
    public static String validateRequestTermination(RequestTerminationReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        return null;
    }

    /** IF8A-19 免密扣款。 */
    public static String validateRequestPay(RequestPayReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (!StringUtils.hasText(request.getScene())) {
            return "scene不能为空";
        }
        if (!StringUtils.hasText(request.getThirdUserId())) {
            return "thirdUserId不能为空";
        }
        if (request.getAmount() == null) {
            return "amount不能为空";
        }
        if (request.getAmount() < 0) {
            return "amount不能小于0";
        }
        if (!StringUtils.hasText(request.getIndustryType())) {
            return "industryType不能为空";
        }
        if (!StringUtils.hasText(request.getSubject())) {
            return "subject不能为空";
        }
        if (!StringUtils.hasText(request.getBody())) {
            return "body不能为空";
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return "cardId不能为空";
        }
        if (!StringUtils.hasText(request.getCardType())) {
            return "cardType不能为空";
        }
        return null;
    }

    /** 请求退款。金额上界（可退金额）不在这里判，见类注释。 */
    public static String validateRequestRefund(RequestRefundReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getOrderNo())) {
            return "orderNo不能为空";
        }
        if (request.getRefundAmount() == null) {
            return "refundAmount不能为空";
        }
        if (request.getRefundAmount() <= 0) {
            return "refundAmount必须大于0";
        }
        return null;
    }

    /**
     * 签约结果回调。
     *
     * <p><b>thirdUserId 刻意非必填</b>：支付平台回调常不带，由 {@code resolveThirdUserId} 从流水表补。
     */
    public static String validateReceiveSignResult(ReceiveSignResultReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        if (!StringUtils.hasText(request.getPaymentVendor())) {
            return "paymentVendor不能为空";
        }
        if (!StringUtils.hasText(request.getStatus())) {
            return "status不能为空";
        }
        return null;
    }

    /** 解约结果回调（IF8B-02）。thirdUserId / cardId / cardType 同样由调用方补齐，不在此必填。 */
    public static String validateReceiveTerminationResult(ReceiveTerminationResultReqDTO request) {
        if (request == null) {
            return "请求报文不能为空";
        }
        if (!StringUtils.hasText(request.getRequestSignSeq())) {
            return "requestSignSeq不能为空";
        }
        if (!StringUtils.hasText(request.getPaymentVendor())) {
            return "paymentVendor不能为空";
        }
        if (!StringUtils.hasText(request.getStatus())) {
            return "status不能为空";
        }
        return null;
    }
}
