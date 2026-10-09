package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.chinasofti.huateng.alipay.paysign.service.impl.support.RefundAmountCalculator;

import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayCallbackLog;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayLog;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayRefundLog;
import com.chinasofti.huateng.model.alipaytrip.AlipaySignInfo;
import com.chinasofti.huateng.model.alipaytrip.AlipayTripRefundNotifyReqDTO;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayCallbackLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayRefundLogMapper;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipaySignInfoMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripRequestRefundReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripRequestRefundRespDTO;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import com.chinasofti.huateng.common.response.AlipayCommonResponse;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentRefundService {
    private static final Logger log = LoggerFactory.getLogger(PaymentRefundService.class);
    private static final String CHANNEL_ALIPAY = "ALIPAY";

    /** 退款明细状态：调支付中心之前落 PROCESSING，业务应答到达后收口成 SUCCESS / FAIL。 */
    private static final String REFUND_STATUS_PROCESSING = "PROCESSING";
    private static final String REFUND_STATUS_SUCCESS = "SUCCESS";
    private static final String REFUND_STATUS_FAIL = "FAIL";
    private static final String RESULT_CODE_INIT = "INIT";

    /** 回调凭据类型：与 PaymentQueryService 的 'PAY' 同列不同值，用于把两个方向的回调分开计数。 */
    private static final String CALLBACK_TYPE_REFUND = "REFUND";
    /** 凭据处置状态：本轮只落证据、不回写业务，故恒为 PENDING，收口逻辑接线时再推进。 */
    private static final String HANDLE_STATUS_PENDING = "PENDING";

    @Autowired
    private AlipayPayLogMapper alipayPayLogMapper;

    @Autowired
    private AlipayPayCallbackLogMapper alipayPayCallbackLogMapper;

    @Autowired
    private AlipayRefundLogMapper alipayRefundLogMapper;

    @Autowired
    private AlipaySignInfoMapper alipaySignInfoMapper;

    @Autowired
    private RefundAmountCalculator refundAmountCalculator;

    @Autowired
    private PayCenterPort payCenterPort;

    /**
     * 支付宝出行退款申请。
     *
     * <p><b>2026-09-18 起本方法已是零调用方、只作回滚位</b>（迁移第 9 条）：
     * {@code POST /internal/alipay/payment/requestRefund} 已切到
     * {@code service.AlipayPayRefundService}（实现 {@code service.impl.refund.AlipayPayRefundServiceImpl}）。
     * 新实现搬的就是本方法的行为 —— 表、状态取值、响应文案、出网 bizData 键名逐条对齐，
     * <b>NEVER 在这里新增能力，也 NEVER 只改一侧</b>：两份并存期间任何行为调整都会让「线上跑的是哪一侧」
     * 无从判断。回滚只需把 {@code AlipayPaymentInternalController} 改回注 {@code AlipayTripPaymentService}。
     *
     * <p>NEVER 给本方法加 {@code @Transactional}（2026-09-14 移除，事务内出网会把行锁持有时长
     * 拉成对端响应时长）；链路约束与三分支收口见 {@code docs/business/alipay-channel.md}。</p>
     */
    public AlipayTripRequestRefundRespDTO requestRefund(AlipayTripRequestRefundReqDTO request) {
        AlipayTripRequestRefundRespDTO response = new AlipayTripRequestRefundRespDTO();
        log.info("接收到支付宝退款申请报文: {}", JSON.toJSONString(request));

        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }

        AlipayPayLog payLog = alipayPayLogMapper.selectByOrderNo(request.getOrderNo());
        if (payLog == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录不存在");
        }

        if (!"SUCCESS".equals(payLog.getPayStatus())) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付订单未支付成功");
        }

        String cardNum = payLog.getCardId();
        if (!StringUtils.hasText(cardNum)) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "原支付记录缺少逻辑卡号");
        }

        // 幂等短路：同一原订单存在未收口（PROCESSING）的退款明细即拒绝新申请。
        int processingCount = alipayRefundLogMapper.countByOrderNoAndStatus(request.getOrderNo(), REFUND_STATUS_PROCESSING);
        if (processingCount > 0) {
            log.error("该订单存在未收口的退款明细，拒绝重复退款，MUST 人工到支付中心核对上一笔结果, orderNo={}, processingCount={}",
                    request.getOrderNo(), processingCount);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "该订单存在处理中的退款，请先确认上一笔结果");
        }

        AlipaySignInfo signInfo = alipaySignInfoMapper.selectByCardIdAndChannel(cardNum, CHANNEL_ALIPAY);
        String channelAgreementNo = signInfo != null ? signInfo.getChannelAgreementCode() : null;
        if (!StringUtils.hasText(channelAgreementNo)) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款申请未查到签约信息, cardNum=" + cardNum);
        }

        String refundAmount = refundAmountCalculator.resolveRefundAmount(request.getRefundAmount(), payLog);
        refundAmountCalculator.validateRefundAmount(refundAmount, payLog);

        String refundOrderNo = "R" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 8);

        AlipayRefundLog refundLog = new AlipayRefundLog();
        refundLog.setRefundSeq(UUID.randomUUID().toString().replaceAll("-", ""));
        refundLog.setThirdUserId(payLog.getThirdUserId());
        refundLog.setCardId(payLog.getCardId());
        refundLog.setOrderNo(request.getOrderNo());
        refundLog.setRefundAmount(refundAmount);
        refundLog.setRefundStatus(REFUND_STATUS_PROCESSING);
        refundLog.setCardIssueCode("0007");
        refundLog.setChannelAgreementNo(channelAgreementNo);
        refundLog.setRefundOrderNo(refundOrderNo);
        refundLog.setRequestBody(JSON.toJSONString(request));
        refundLog.setResponseBody("");
        refundLog.setResultCode(RESULT_CODE_INIT);
        refundLog.setResultMsg("退款处理中");
        refundLog.setDeleteFlag("0");
        refundLog.setVersion("1");
        refundLog.setCreateTime(LocalDateTime.now());
        refundLog.setUpdateTime(LocalDateTime.now());
        try {
            alipayRefundLogMapper.insert(refundLog);
        } catch (Exception e) {
            // UK_ARL_REFUND_ORDER_NO 竞态兜底，沿 getCause 链判定完整性冲突。
            if (!isIntegrityViolation(e)) {
                throw e;
            }
            log.warn("退款明细插入命中唯一索引，判定为重复提交, orderNo={}, refundOrderNo={}", request.getOrderNo(), refundOrderNo);
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "退款申请重复提交");
        }

        Map<String, Object> bizDataMap = new java.util.LinkedHashMap<>();
        bizDataMap.put("orderNo", request.getOrderNo());
        bizDataMap.put("cardIssueCode", "0007");
        bizDataMap.put("cardNum", cardNum);
        bizDataMap.put("channelAgreementNo", channelAgreementNo);
        bizDataMap.put("refundAmount", refundAmount);
        bizDataMap.put("refundOrderNo", refundOrderNo);

        log.info("支付宝出行-退款申请,调用支付中心退款接口,请求参数: {}", JSON.toJSONString(bizDataMap));
        PayCenterReply reply = payCenterPort.requestRefund(bizDataMap);
        log.info("支付宝退款申请,支付中心响应结果: code={}, success={}, msg={}",
                reply.code(), reply.success(), reply.msg());

        if (!(reply instanceof PayCenterReply.Accepted accepted)) {
            // 拿不到业务应答：明细保持 PROCESSING，只回写响应体留证。
            // Rejected 与 NoAnswer 在本方向的处置相同 —— 但那是**本方向的**结论，
            // requestPay 对两者的处置不同，NEVER 因为这里能合并就去合并那处。
            log.error("支付宝退款申请未拿到支付中心业务应答，退款结果未知、明细保持 PROCESSING，MUST 人工核对, orderNo={}, refundOrderNo={}, code={}, msg={}",
                    request.getOrderNo(), refundOrderNo, reply.code(), reply.msg());
            alipayRefundLogMapper.updateRefundStatus(
                    refundLog.getRefundSeq(),
                    REFUND_STATUS_PROCESSING,
                    RESULT_CODE_INIT,
                    "退款结果未知，待人工核对",
                    reply.rawBody(),
                    LocalDateTime.now()
            );
            response.setRetCode(FepAppErrorCodeEnum.SYSTEM_ERROR.getCode());
            response.setRetMsg("退款结果未知，请稍后核对");
            return response;
        }

        // 与 requestPay / payQuery 同一套判定：HTTP 与网关层通了之后，还要解开 data 看业务 retCode。
        log.info("支付宝退款申请,支付中心解密后数据: retCode={}, retMsg={}", accepted.retCode(), accepted.retMsg());

        boolean refundSuccess = "SUCCESS".equals(accepted.retCode());
        String resultCode = refundSuccess ? FepAppErrorCodeEnum.SUCCESS.getCode() : FepAppErrorCodeEnum.FAIL.getCode();
        String resultMsg;
        if (refundSuccess) {
            resultMsg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg() : "退款成功";
        } else {
            resultMsg = StringUtils.hasText(accepted.retMsg()) ? accepted.retMsg()
                    : (StringUtils.hasText(accepted.msg()) ? accepted.msg() : "退款失败");
        }

        alipayRefundLogMapper.updateRefundStatus(
                refundLog.getRefundSeq(),
                refundSuccess ? REFUND_STATUS_SUCCESS : REFUND_STATUS_FAIL,
                resultCode,
                resultMsg,
                accepted.rawBody(),
                LocalDateTime.now()
        );

        if (refundSuccess) {
            // 汇总 MUST 在明细置为 SUCCESS 之后执行：SQL 是按 ALIPAY_REFUND_LOG 重算的。
            int summaryAffected = alipayPayLogMapper.updateRefundSummary(request.getOrderNo());
            if (summaryAffected == 0) {
                log.error("退款汇总回写未命中原支付订单，MUST 人工核对 ALIPAY_PAY_LOG 与 ALIPAY_REFUND_LOG, orderNo={}, refundOrderNo={}",
                        request.getOrderNo(), refundOrderNo);
            }
        }

        response.setRetCode(resultCode);
        response.setRetMsg(resultMsg);
        log.info("支付宝退款申请完成, orderNo={}, refundOrderNo={}, cardNum={}, channelAgreementNo={}, refundAmount={}, status={}",
                request.getOrderNo(), refundOrderNo, cardNum, channelAgreementNo, refundAmount, refundSuccess ? "SUCCESS" : "FAIL");
        return response;
    }

    /**
     * 契约 §5.2 退款结果回调 —— 当前只落回调凭据，退款明细与汇总的回写尚未接线。
     *
     * <p><b>2026-09-18 起本方法已是零调用方、只作回滚位</b>：{@code POST /api/payment/refundNotify} 已切到
     * {@code service.AlipayPayCallbackService}（实现 {@code service.impl.callback.AlipayPayCallbackServiceImpl}）。
     * 本方法行为一行未改，<b>NEVER 在这里新增能力</b>；回滚只需把 {@code PayCenterCallbackController}
     * 改回注 {@code AlipayTripPaymentService}。</p>
     *
     * <p>刻意做成这个形态：契约要求「返回通用响应表示接收成功，否则重试」，而退款收口现在走的是
     * {@code compensateRefundQuery} 主动回查那条路（见 pay-sign 域同名链路）。若这里返非 0000，
     * 支付中心会一直重推一笔我方本就不打算按回调收口的退款；若这里就地回写，又会与回查那条形成
     * 两个写入方、彼此覆盖。因此先只留证据 + 收下，回写在接线回查/回调优先级之后再补。</p>
     *
     * <p>NEVER 给本方法加 {@code @Transactional}：与 {@code requestRefund} 同理，且回滚会把
     * 唯一的证据行一起丢掉。</p>
     */
    public AlipayCommonResponse handleRefundNotify(AlipayTripRefundNotifyReqDTO request) {
        log.info("接收到支付宝退款结果回调, 原始报文: {}", JSON.toJSONString(request));
        AlipayCommonResponse response = new AlipayCommonResponse();
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            log.warn("支付宝退款回调报文为空或订单号为空");
            response.setRetCode(FepAppErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("参数异常：orderNo不能为空");
            return response;
        }

        recordRefundCallback(request);

        log.info("支付宝退款回调已留证据，业务回写未接线, orderNo={}, refundNo={}, outRefundNo={}, refundResult={}",
                request.getOrderNo(), request.getRefundNo(), request.getOutRefundNo(), request.getRefundResult());
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("成功");
        return response;
    }

    /**
     * 退款回调即入库。落库自身失败只记日志、不打断处理：留痕失败不该让一笔回调收不了口。
     *
     * <p>三个退款列的取值口径 NEVER 改：{@code REFUND_ORDER_NO} 取 {@code outRefundNo}（商户退款流水号，
     * 与 {@code ALIPAY_REFUND_LOG.REFUND_ORDER_NO} 同源），<b>不是 {@code refundNo}</b> —— 后者是支付中心侧
     * 自己的号，拿它关联会串单，它只在 {@code RAW_BODY} 里留证。缺这三列时一笔订单的多次退款在本表分不清彼此。</p>
     *
     * <p>不对两个字符串列做截断：{@code REFUND_ORDER_NO} 是我方生成的号（64 字符）、{@code REFUND_STATUS}
     * 是枚举值（16 字符），都在列长内；万一对端送超长值，Oracle 报 {@code ORA-12899} 会被本方法的 catch 兜住，
     * 代价只是丢这一行证据 + 一条 ERROR 日志，不影响回调收口。</p>
     */
    private void recordRefundCallback(AlipayTripRefundNotifyReqDTO request) {
        try {
            AlipayPayCallbackLog callbackLog = new AlipayPayCallbackLog();
            callbackLog.setCallbackSeq(UUID.randomUUID().toString().replaceAll("-", ""));
            callbackLog.setOrderNo(request.getOrderNo());
            callbackLog.setCallbackType(CALLBACK_TYPE_REFUND);
            callbackLog.setRefundOrderNo(request.getOutRefundNo());
            callbackLog.setRefundAmount(parseRefundAmount(request.getRefundAmount(), request.getOrderNo()));
            callbackLog.setRefundStatus(request.getRefundResult());
            callbackLog.setRawBody(JSON.toJSONString(request));
            callbackLog.setHandleStatus(HANDLE_STATUS_PENDING);
            callbackLog.setHandleMsg("退款回调回写未接线，仅留证据");
            callbackLog.setCreateTime(LocalDateTime.now());
            callbackLog.setUpdateTime(LocalDateTime.now());
            alipayPayCallbackLogMapper.insert(callbackLog);
        } catch (Exception e) {
            log.error("支付宝退款回调凭据落库失败，本次处理继续, orderNo={}", request.getOrderNo(), e);
        }
    }

    /**
     * 把回调里的退款金额字符串解析成分。
     *
     * <p>契约 §5.2 的 {@code refundAmount} 是字符串、单位分。解析不出时返回 null 让该列留空，
     * <b>NEVER 抛异常</b> —— 一个金额解析失败不该把整行证据连带丢掉，原文在 {@code RAW_BODY} 里可复核。</p>
     */
    private Integer parseRefundAmount(String refundAmount, String orderNo) {
        if (!StringUtils.hasText(refundAmount)) {
            return null;
        }
        try {
            return Integer.valueOf(refundAmount.trim());
        } catch (NumberFormatException e) {
            log.warn("支付宝退款回调的退款金额不是整数分，本列留空、原文已进 RAW_BODY, orderNo={}, refundAmount={}",
                    orderNo, refundAmount);
            return null;
        }
    }

    /**
     * 沿 {@code getCause()} 链判断是否为唯一约束冲突。
     */
    private boolean isIntegrityViolation(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof DuplicateKeyException || current instanceof DataIntegrityViolationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

}

