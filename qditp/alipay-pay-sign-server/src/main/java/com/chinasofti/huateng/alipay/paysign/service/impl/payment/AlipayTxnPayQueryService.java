package com.chinasofti.huateng.alipay.paysign.service.impl.payment;

import com.alibaba.fastjson2.JSON;
import com.chinasofti.huateng.alipay.paysign.domain.PayCenterTradeStatus;
import com.chinasofti.huateng.alipay.paysign.entity.AlipayPayTxnDetail;
import com.chinasofti.huateng.alipay.paysign.exception.BusinessException;
import com.chinasofti.huateng.alipay.paysign.mapper.AlipayPayTxnDetailMapper;
import com.chinasofti.huateng.alipay.paysign.model.request.AlipayTripPayQueryReqDTO;
import com.chinasofti.huateng.alipay.paysign.model.response.AlipayTripPayQueryRespDTO;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterPort;
import com.chinasofti.huateng.alipay.paysign.port.PayCenterReply;
import com.chinasofti.huateng.common.constant.FepAppErrorCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付宝出行（小程序）支付结果查询 —— <b>落新表 {@code ALIPAY_PAY_TXN_DETAIL} 的实现</b>。
 *
 * <p>与同包的旧 {@link PaymentQueryService#payQuery} <b>并存</b>：旧类逐字保留、零改动，只是
 * 支付结果查询的 HTTP 入口已由本类接管。
 * <b>NEVER 在旧类上改任何东西</b>；要回退就把两边的注释对调。
 *
 * <p><b>2026-09-18 URL 已迁到 internal 前缀</b>：现宿主是
 * {@code controller/internal/AlipayPaymentInternalController}，路径
 * {@code POST /internal/alipay/payment/payQuery}（原 {@code POST /api/payment/payQuery}，
 * <b>旧路径已删除、无别名</b>）。本类实现一行未改。
 *
 * <p><b>本方向刻意不写 {@code ALIPAY_PAY_CENTER_MSG_LOG}</b>（用户 2026-09-18 裁决，对齐 pay-sign
 * 的「payQuery 方向零落库」）：查询是只读的、可重复发起，留痕价值远低于扣费与退款，而 MSG_LOG 每次
 * 调用落一行会把这张流水表灌成查询日志。**要留痕就去看应用日志**，本方法把请求 bizData 与应答
 * 四要素都打了 INFO。写 MSG_LOG 的只有 {@code requestPay} 与退款类，NEVER 在这里加。
 *
 * <p><b>NEVER 加 {@code @Transactional}</b>：方法内有支付中心 HTTP 调用，事务包住它会让行锁持有
 * 时长等于对端响应时长（pay-sign 侧 2026-08-26 已因此出过循环重推 8 分钟的事故）。
 *
 * <p><b>与旧实现的三处实质差异</b>（不是重构，是修缺陷）：
 * <ol>
 *   <li><b>{@code retCode != SUCCESS} 不再落 {@code FAIL}</b>。payQuery 的 {@code retCode} 表达的是
 *       「这次查询请求成不成立」（订单号不存在、参数缺失都会非 SUCCESS），<b>不等于这笔钱没扣</b>。
 *       旧实现在这一支直接把本地写成 {@code FAIL}，与「{@code NoAnswer} 落 FAIL」是同型资损路径：
 *       单子被当成失败后既不回查也不补偿。本实现该支<b>一个字都不回写</b>。</li>
 *   <li><b>成功与否改看应答里的交易状态，不再看 {@code retCode}</b>。见
 *       {@link #resolveTradeStatus}：只有明确可判定的取值才回写，其余一律不动 + 打 ERROR。</li>
 *   <li><b>{@code channelAgreementNo} 直接取本表的独立列</b>，不再从 {@code ALIPAY_PAY_LOG.INDUSTRY_DETAIL}
 *       的 JSON 里解析（旧实现那段 try-catch 解析失败时只打 WARN、随后静默不送该字段）。</li>
 * </ol>
 */
@Service
public class AlipayTxnPayQueryService {

    private static final Logger log = LoggerFactory.getLogger(AlipayTxnPayQueryService.class);

    /** 支付宝渠道的卡机构编号，出向 bizData 固定值，与旧实现逐字一致。 */
    private static final String CARD_ISSUE_CODE_ALIPAY = "0007";

    private static final String PAY_STATUS_SUCCESS = "SUCCESS";
    private static final String PAY_STATUS_FAIL = "FAIL";

    private static final String PAY_CENTER_SUCCESS = "SUCCESS";

    private final AlipayPayTxnDetailMapper alipayPayTxnDetailMapper;
    private final PayCenterPort payCenterPort;

    public AlipayTxnPayQueryService(AlipayPayTxnDetailMapper alipayPayTxnDetailMapper,
                                    PayCenterPort payCenterPort) {
        this.alipayPayTxnDetailMapper = alipayPayTxnDetailMapper;
        this.payCenterPort = payCenterPort;
    }

    /** 支付结果查询：主动问支付中心，按可判定的结果回写 {@code PAY_STATUS}。 */
    public AlipayTripPayQueryRespDTO payQuery(AlipayTripPayQueryReqDTO request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())) {
            throw new BusinessException(FepAppErrorCodeEnum.INVALID_PARAM.getCode(), "订单号不能为空");
        }
        String orderNo = request.getOrderNo();
        log.info("收到支付结果查询（新链路）: orderNo={}", orderNo);

        AlipayPayTxnDetail detail = alipayPayTxnDetailMapper.selectByOrderNo(orderNo);
        if (detail == null) {
            throw new BusinessException(FepAppErrorCodeEnum.FAIL.getCode(), "支付明细不存在");
        }

        Map<String, Object> bizData = buildBizData(request, detail);
        log.info("支付宝出行-支付结果查询,调用支付中心,orderNo={}, 请求参数={}", orderNo, JSON.toJSONString(bizData));

        PayCenterReply reply;
        try {
            reply = payCenterPort.payQuery(bizData);
        } catch (RuntimeException e) {
            log.error("支付宝出行-支付结果查询调用支付中心抛异常，本地支付明细保持原状、NEVER 落 FAIL, orderNo={}", orderNo, e);
            return response(alipayPayTxnDetailMapper.selectByOrderNo(orderNo), null, "查询支付中心失败");
        }

        String centerPayDate = applyReply(reply, orderNo);
        return response(alipayPayTxnDetailMapper.selectByOrderNo(orderNo), centerPayDate, null);
    }

    /**
     * 按支付中心应答回写状态，返回应答里的支付时间（本表没有支付时间列，只用于透传给调用方）。
     *
     * <p>三分支的处置：
     * <ul>
     *   <li>{@code Accepted} + {@code retCode == SUCCESS}：查询成立，交由
     *       {@link #resolveTradeStatus} 判交易状态，判不出就不回写。</li>
     *   <li>{@code Accepted} + 非 {@code SUCCESS}：查询本身没成立，<b>NEVER 落 FAIL</b>，理由见类注释。</li>
     *   <li>{@code Rejected} / {@code NoAnswer}：拿不到业务应答，本地保持原状，<b>NEVER 落 FAIL</b>。
     *       两支处置相同也 MUST 各写一个 case —— 合并会抹掉 {@code requestPay} 那处的差异。</li>
     * </ul>
     */
    private String applyReply(PayCenterReply reply, String orderNo) {
        switch (reply) {
            case PayCenterReply.Accepted accepted -> {
                String tradeStatus = firstNonBlank(accepted.field("status"), accepted.field("tradeStatus"));
                String channelOrderNo = firstNonBlank(accepted.field("channelOrderNo"), accepted.field("tradeNo"));
                String payDate = firstNonBlank(accepted.field("payDate"), accepted.field("paymentTime"));
                log.info("支付宝出行-支付结果查询,支付中心业务应答: orderNo={}, retCode={}, retMsg={}, tradeStatus={}, channelOrderNo={}, payDate={}, totalAmount={}",
                        orderNo, accepted.retCode(), accepted.retMsg(), tradeStatus, channelOrderNo, payDate,
                        accepted.field("totalAmount"));

                if (!PAY_CENTER_SUCCESS.equals(accepted.retCode())) {
                    log.error("支付宝出行-支付结果查询被支付中心拒绝，本地支付明细保持原状、NEVER 落 FAIL, orderNo={}, retCode={}, retMsg={}",
                            orderNo, accepted.retCode(), accepted.retMsg());
                    return payDate;
                }
                String payStatus = resolveTradeStatus(tradeStatus);
                if (payStatus == null) {
                    log.error("支付宝出行-支付结果查询拿到的交易状态无法判定，拒绝回写、MUST 人工到支付中心核对, orderNo={}, tradeStatus={}",
                            orderNo, tradeStatus);
                    return payDate;
                }
                applyQueryResult(orderNo, payStatus, channelOrderNo, accepted.field("payCenterOrderNo"));
                return payDate;
            }
            case PayCenterReply.Rejected rejected -> {
                log.error("支付宝出行-支付结果查询未拿到业务应答，本地支付明细保持原状、NEVER 落 FAIL, orderNo={}, code={}, msg={}, success={}",
                        orderNo, rejected.code(), rejected.msg(), rejected.success());
                return null;
            }
            case PayCenterReply.NoAnswer noAnswer -> {
                log.error("支付宝出行-支付结果查询无响应，本地支付明细保持原状、NEVER 落 FAIL, orderNo={}", orderNo);
                return null;
            }
        }
    }

    /**
     * 把支付中心的交易状态归一成本表的 {@code PAY_STATUS}，<b>判不出就返回 {@code null}（不回写）</b>。
     *
     * <p>判据本体已收口到 {@link PayCenterTradeStatus}（本模块唯一一份），因为
     * {@code AlipayPayRequestServiceImpl} 加黑名单前的「确认真的失败」用的是同一套值域 ——
     * <b>NEVER 在这里抄回一份</b>，两处分叉会造出「查询认为成功、加黑认为失败」的矛盾现场。
     * 值域来源、已知缺口与「NEVER 改成黑名单式判定」的理由都写在那个类的注释里。
     */
    private String resolveTradeStatus(String tradeStatus) {
        return PayCenterTradeStatus.normalize(tradeStatus);
    }

    /**
     * 回写本地支付明细，带「当前不是 {@code SUCCESS}」白名单。
     *
     * <p>影响 0 行有两种截然不同的成因，MUST 回读区分：本地已是目标状态（重复查询，幂等跳过）
     * 与本地已是 {@code SUCCESS} 而支付中心给了别的（口径冲突，只打 ERROR 等人工，
     * <b>NEVER 覆盖已成功的终态</b>）。
     */
    private void applyQueryResult(String orderNo, String payStatus, String channelOrderNo, String payCenterOrderNo) {
        AlipayPayTxnDetail update = new AlipayPayTxnDetail();
        update.setOrderNo(orderNo);
        update.setPayStatus(payStatus);
        update.setChannelOrderNo(channelOrderNo);
        update.setPayCenterOrderNo(payCenterOrderNo);
        int affected = alipayPayTxnDetailMapper.updatePayQueryResultIfNotSuccess(update);
        if (affected > 0) {
            log.info("支付宝出行-支付结果查询,本地支付明细已回写, orderNo={}, payStatus={}, channelOrderNo={}",
                    orderNo, payStatus, channelOrderNo);
            return;
        }
        AlipayPayTxnDetail current = alipayPayTxnDetailMapper.selectByOrderNo(orderNo);
        String currentStatus = current == null ? null : current.getPayStatus();
        if (payStatus.equals(currentStatus)) {
            log.info("支付宝出行-支付结果查询,本地已是目标状态，跳过回写, orderNo={}, payStatus={}", orderNo, currentStatus);
            return;
        }
        log.error("支付宝出行-支付结果查询与本地口径冲突，本地为 SUCCESS 终态、拒绝覆盖，MUST 人工到支付中心核对, orderNo={}, 本地PAY_STATUS={}, 支付中心给出={}",
                orderNo, currentStatus, payStatus);
    }

    /**
     * 组装出向 bizData。
     *
     * <p>供方文档 §1.2 只要求 {@code orderNo} 与 {@code merchantOrderNo} 至少填一个，其余三个键是
     * 旧实现一直在送、且线上已通的形态，因此照送 —— 外部网关的契约细节 MUST 以真实应答为准，
     * <b>NEVER 因为文档没列就顺手删掉在跑的字段</b>（ADR-D92：退款查询漏送 {@code merchantRefundNo}
     * 导致退款单永久空转，就是反例）。
     *
     * <p>{@code cardNum} 只在请求带了才送：本表刻意没有卡号列（主体 {@code GATE_TXN_PAY.CARD_ID} 已有），
     * <b>NEVER 为了补这个字段再加一次 RPC 或往本表加卡号列</b>。
     */
    private Map<String, Object> buildBizData(AlipayTripPayQueryReqDTO request, AlipayPayTxnDetail detail) {
        Map<String, Object> bizData = new LinkedHashMap<>();
        bizData.put("orderNo", request.getOrderNo());
        bizData.put("cardIssueCode", StringUtils.hasText(request.getCardIssueCode())
                ? request.getCardIssueCode() : CARD_ISSUE_CODE_ALIPAY);
        if (StringUtils.hasText(request.getCardNum())) {
            bizData.put("cardNum", request.getCardNum());
        }
        String channelAgreementNo = firstNonBlank(request.getChannelAgreementNo(), detail.getChannelAgreementNo());
        if (StringUtils.hasText(channelAgreementNo)) {
            bizData.put("channelAgreementNo", channelAgreementNo);
        }
        return bizData;
    }

    /**
     * 组装响应：状态与金额一律取<b>回写后的本地明细</b>，只有支付时间取支付中心应答。
     *
     * <p>后者是因为本表刻意不存字符串时间列（回调原文时间在 {@code ALIPAY_PAY_CALLBACK_LOG}），
     * 拿不到就返 null，<b>NEVER 用本地当前时间冒充支付时间</b>。
     */
    private AlipayTripPayQueryRespDTO response(AlipayPayTxnDetail detail, String centerPayDate, String tradeDesc) {
        AlipayTripPayQueryRespDTO response = new AlipayTripPayQueryRespDTO();
        response.setRetCode(FepAppErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg("查询成功");
        if (detail != null) {
            response.setOutTradeNo(detail.getOrderNo());
            response.setTradeStatus(detail.getPayStatus());
            response.setTotalAmount(detail.getAmount() == null ? null : String.valueOf(detail.getAmount()));
            response.setTradeNo(detail.getChannelOrderNo());
        }
        response.setPaymentTime(centerPayDate);
        response.setTradeDesc(StringUtils.hasText(tradeDesc) ? tradeDesc : response.getTradeStatus());
        log.info("支付宝出行-支付结果查询完成（新链路）: {}", JSON.toJSONString(response));
        return response;
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }
}
