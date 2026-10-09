package com.chinasofti.huateng.alipay.paysign.service.impl.refund;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.config.PayCenterProperties;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.alipay.paysign.service.AlipayPayRefundService;
import com.chinasofti.huateng.alipay.paysign.service.impl.support.RefundAmountCalculator;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 退款申请编排（{@link AlipayPayRefundService} 的唯一实现）。
 *
 * <p>六步，顺序 MUST 不变：① 校验收口成 {@link RefundCommand} → ② 查原支付并校验「已支付成功」→
 * ③ <b>幂等短路</b>（同一原订单存在未收口的退款明细即拒绝）→ ④ 查生效签约拿渠道协议号 →
 * ⑤ 落明细并提交（{@link RefundLogRepository#openRefund}）→ ⑥ 出网 + 按应答回写明细与汇总。
 *
 * <p><b>③ 排在落库之前是本链路的资金防线</b>：上一笔结果未知（{@code PROCESSING}）时放行第二笔，
 * 等于对同一笔原支付发两次退款。<b>NEVER 把它挪到落库之后，也 NEVER 因为「反正金额会校验」就删掉</b>
 * —— 金额校验读的是 {@code ALIPAY_PAY_LOG} 的汇总列，而未收口那笔<b>还没进汇总</b>，拦不住。
 *
 * <p><b>三件套里的「sealed 结果」由既有 {@link PayCenterReply} 承担</b>（ADR-D131）：三分支穷尽
 * {@code switch}，少写一支即编译失败。<b>本方向 {@code Rejected} 与 {@code NoAnswer} 的处置相同</b>
 * （都保持 {@code PROCESSING}、只回写响应体留证），但仍各写一个 case ——
 * <b>那是「本方向的」结论，{@code requestPay} 对两者的处置不同，NEVER 因为这里能合并就去合并那处</b>。
 *
 * <p><b>NEVER 加 {@code @Transactional}</b>：本方法内有一次支付中心 HTTP 调用。事务包住它会让行锁
 * 持有时长等于对端响应时长，超过 Druid {@code remove-abandoned-timeout} 后连接被强杀、连「留证据」的
 * INSERT 一起回滚 —— pay-sign 侧 2026-08-26 已因此出过循环重推 8 分钟、单请求 287 秒的生产事故。
 *
 * <p><b>与旧实现互不调用</b>：{@code service.impl.payment.PaymentRefundService} 原样保留作回滚位，
 * 本类 NEVER 转发给它、它也 NEVER 反向调本类 —— 否则「线上跑的是哪一侧」无法判断。回滚只需把
 * {@code controller.internal.AlipayPaymentInternalController.requestRefund} 改回注
 * {@code AlipayTripPaymentService}。
 *
 * <p><b>已知缺口（本次未修）</b>：本端点无鉴权、无归属校验，按 AGENTS.md §5.2 本应有 ——
 * 它既改状态又出网发起真实退款。这是既有缺口、不是本次引入的，见控制器类注释。
 */
@Service
public class AlipayPayRefundServiceImpl implements AlipayPayRefundService {

    private static final Logger log = LoggerFactory.getLogger(AlipayPayRefundServiceImpl.class);

    private static final String CHANNEL_ALIPAY = "ALIPAY";
    /** 支付宝渠道的卡机构编号，出网报文用。 */
    private static final String CARD_ISSUE_CODE_ALIPAY = "0007";
    /** 原支付已成功才允许退款，白名单只有这一个值。 */
    private static final String PAY_STATUS_SUCCESS = "SUCCESS";
    /** 支付中心业务成功的 {@code retCode}。 */
    private static final String PAY_CENTER_SUCCESS = "SUCCESS";

    private final AlipaySignInfoMapper alipaySignInfoMapper;
    private final RefundAmountCalculator refundAmountCalculator;
    private final RefundLogRepository refundLogRepository;
    private final PayCenterPort payCenterPort;
    private final PayCenterProperties payCenterProperties;

    public AlipayPayRefundServiceImpl(AlipaySignInfoMapper alipaySignInfoMapper,
                                     RefundAmountCalculator refundAmountCalculator,
                                     RefundLogRepository refundLogRepository,
                                     PayCenterPort payCenterPort,
                                     PayCenterProperties payCenterProperties) {
        this.alipaySignInfoMapper = alipaySignInfoMapper;
        this.refundAmountCalculator = refundAmountCalculator;
        this.refundLogRepository = refundLogRepository;
        this.payCenterPort = payCenterPort;
        this.payCenterProperties = payCenterProperties;
    }

    @Override
    public AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request) {
        log.info("接收到支付宝退款申请报文: {}", JSON.toJSONString(request));
        RefundCommand command = RefundCommand.from(request);
        String orderNo = command.orderNo();

        AlipayPayLog payLog = loadSettledPayLog(orderNo);
        String cardNum = payLog.getCardId();
        rejectIfProcessingRefundExists(orderNo);

        String channelAgreementNo = resolveChannelAgreementNo(cardNum);
        String refundAmount = refundAmountCalculator.resolveRefundAmount(command.refundAmount(), payLog);
        refundAmountCalculator.validateRefundAmount(refundAmount, payLog);

        String refundOrderNo = "R" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);
        String refundSeq = refundLogRepository.openRefund(command, payLog, channelAgreementNo,
                refundAmount, refundOrderNo, JSON.toJSONString(request));

        return callPayCenter(command, cardNum, channelAgreementNo, refundAmount, refundOrderNo, refundSeq);
    }

    /**
     * 查原支付并校验「可退」。
     *
     * <p>三条校验的文案与迁移前逐字一致。<b>白名单只认 {@code SUCCESS}</b>（AGENTS.md §5.2：状态机用
     * 白名单不用黑名单）—— {@code PROCESSING} 的单子钱还没确定扣没扣，退它是凭空出账。
     */
    private AlipayPayLog loadSettledPayLog(String orderNo) {
        AlipayPayLog payLog = refundLogRepository.findPayLog(orderNo);
        if (payLog == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录不存在");
        }
        if (!PAY_STATUS_SUCCESS.equals(payLog.getPayStatus())) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付订单未支付成功");
        }
        if (!StringUtils.hasText(payLog.getCardId())) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录缺少逻辑卡号");
        }
        return payLog;
    }

    /** 幂等短路：同一原订单存在未收口（{@code PROCESSING}）的退款明细即拒绝新申请。 */
    private void rejectIfProcessingRefundExists(String orderNo) {
        int processingCount = refundLogRepository.countProcessing(orderNo);
        if (processingCount > 0) {
            log.error("该订单存在未收口的退款明细，拒绝重复退款，MUST 人工到支付中心核对上一笔结果, orderNo={}, processingCount={}",
                    orderNo, processingCount);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "该订单存在处理中的退款，请先确认上一笔结果");
        }
    }

    /**
     * 取渠道协议号。
     *
     * <p>查不到就拒绝、<b>NEVER 送空协议号出网</b> —— 支付中心侧认协议号定位扣款账户，空值会被它按
     * 参数错误拒掉，而我方这边明细已经落成 {@code PROCESSING}，白留一笔要人工核对的单子。
     */
    private String resolveChannelAgreementNo(String cardNum) {
        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByCardIdAndChannel(cardNum, CHANNEL_ALIPAY);
        String channelAgreementNo = signInfo != null ? signInfo.getChannelAgreementCode() : null;
        if (!StringUtils.hasText(channelAgreementNo)) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款申请未查到签约信息, cardNum=" + cardNum);
        }
        return channelAgreementNo;
    }

    /**
     * 组装 bizData 并出网。
     *
     * <p>七个键名是供方契约，<b>NEVER 改</b>（含 {@code cardNum} 这个与本模块内部用词不一致的键）。
     *
     * <p><b>{@code notifyUrl} 是契约 §3.1 的必填键</b>，支付中心只往「本次请求带的这个地址」推退款结果 ——
     * 此前一直没送，因此 {@code POST /api/payment/refundNotify} <b>从未收到过任何回调</b>。
     * 配置为空时只打 WARN、不送该键、<b>NEVER 阻断退款申请</b>：申请本身能成功，缺的只是终态回调，
     * 由退款回查补偿兜。
     */
    private AlipayTripRequestRefundRespDTO callPayCenter(RefundCommand command, String cardNum,
                                                        String channelAgreementNo, String refundAmount,
                                                        String refundOrderNo, String refundSeq) {
        String orderNo = command.orderNo();
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", orderNo);
        bizData.put("cardIssueCode", CARD_ISSUE_CODE_ALIPAY);
        bizData.put("cardNum", cardNum);
        bizData.put("channelAgreementNo", channelAgreementNo);
        bizData.put("refundAmount", refundAmount);
        bizData.put("refundOrderNo", refundOrderNo);
        String refundNotifyUrl = payCenterProperties.getRefundNotifyUrl();
        if (StringUtils.hasText(refundNotifyUrl)) {
            bizData.put("notifyUrl", refundNotifyUrl);
        } else {
            log.warn("pay.center.refund-notify-url 未配置，本次退款申请不送 notifyUrl，支付中心将无法回推退款结果，MUST 配置后重试, orderNo={}, refundOrderNo={}",
                    orderNo, refundOrderNo);
        }

        log.info("支付宝出行-退款申请,调用支付中心退款接口,请求参数: {}", JSON.toJSONString(bizData));
        PayCenterReply reply = payCenterPort.requestRefund(bizData);
        log.info("支付宝退款申请,支付中心响应结果: code={}, success={}, msg={}", reply.code(), reply.success(), reply.msg());

        AlipayTripRequestRefundRespDTO response = applyReply(reply, orderNo, refundOrderNo, refundSeq);
        log.info("支付宝退款申请完成, orderNo={}, refundOrderNo={}, cardNum={}, channelAgreementNo={}, refundAmount={}, retCode={}",
                orderNo, refundOrderNo, cardNum, channelAgreementNo, refundAmount, response.getRetCode());
        return response;
    }

    /**
     * 按支付中心应答回写明细与汇总。
     *
     * <p>三分支处置：
     * <ul>
     *   <li>{@code Accepted}：解开 data 看业务 {@code retCode}，{@code SUCCESS} 即终态 {@code SUCCESS}
     *       并<b>紧接着</b>刷汇总；非 {@code SUCCESS} 是拿到了业务拒绝，一次即终态 {@code FAIL}、
     *       <b>不刷汇总</b>（这笔钱没退出去）。</li>
     *   <li>{@code Rejected} / {@code NoAnswer}：<b>明细保持 {@code PROCESSING}</b>、只回写响应体留证。
     *       <b>NEVER 置 FAIL</b> —— 钱可能已经退了，落 FAIL 会让这笔单子被当成没退成、随后被人再退一次。</li>
     * </ul>
     */
    private AlipayTripRequestRefundRespDTO applyReply(PayCenterReply reply, String orderNo,
                                                     String refundOrderNo, String refundSeq) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                log.info("支付宝退款申请,支付中心解密后数据: retCode={}, retMsg={}", accepted.retCode(), accepted.retMsg());
                boolean refundSuccess = PAY_CENTER_SUCCESS.equals(accepted.retCode());
                String resultCode = refundSuccess
                        ? FepAppErrorCodeEnum.SUCCESS.getCode() : FepAppErrorCodeEnum.FAIL.getCode();
                String resultMsg = resolveResultMsg(accepted, refundSuccess);
                refundLogRepository.writeResult(refundSeq,
                        refundSuccess ? RefundLogRepository.REFUND_STATUS_SUCCESS : RefundLogRepository.REFUND_STATUS_FAIL,
                        resultCode, resultMsg, accepted.rawBody());
                if (refundSuccess) {
                    refundLogRepository.refreshSummary(orderNo, refundOrderNo);
                }
                return response(resultCode, resultMsg);
            }
            case PayCenterReply.Rejected rejected -> {
                return keepProcessing(orderNo, refundOrderNo, refundSeq, rejected.code(), rejected.msg(),
                        rejected.rawBody());
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                return keepProcessing(orderNo, refundOrderNo, refundSeq, noAnswer.code(), noAnswer.msg(),
                        noAnswer.rawBody());
            }
        }
    }

    /** 拿不到业务应答：明细保持 {@code PROCESSING}，只回写响应体留证，等人工核对。 */
    private AlipayTripRequestRefundRespDTO keepProcessing(String orderNo, String refundOrderNo, String refundSeq,
                                                         Integer code, String msg, String rawBody) {
        log.error("支付宝退款申请未拿到支付中心业务应答，退款结果未知、明细保持 PROCESSING，MUST 人工核对, orderNo={}, refundOrderNo={}, code={}, msg={}",
                orderNo, refundOrderNo, code, msg);
        refundLogRepository.writeResult(refundSeq, RefundLogRepository.REFUND_STATUS_PROCESSING,
                RefundLogRepository.RESULT_CODE_INIT, "退款结果未知，待人工核对", rawBody);
        return response(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode(), "退款结果未知，请稍后核对");
    }

    /** 文案兜底顺序与迁移前逐字一致：失败时 {@code retMsg} 空了才退到传输层 {@code msg}。 */
    private String resolveResultMsg(PayCenterReply.Accepted accepted, boolean refundSuccess) {
        if (StringUtils.hasText(accepted.retMsg())) {
            return accepted.retMsg();
        }
        if (refundSuccess) {
            return "退款成功";
        }
        return StringUtils.hasText(accepted.msg()) ? accepted.msg() : "退款失败";
    }

    private AlipayTripRequestRefundRespDTO response(String retCode, String retMsg) {
        AlipayTripRequestRefundRespDTO response = new AlipayTripRequestRefundRespDTO();
        response.setRetCode(retCode);
        response.setRetMsg(retMsg);
        return response;
    }
}
