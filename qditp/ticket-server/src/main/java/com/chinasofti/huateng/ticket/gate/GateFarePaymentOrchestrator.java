package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.enums.IssueChannelCodeEnum;
import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.pay.GateTxnPayReqDTO;
import com.chinasofti.huateng.model.pay.GateTxnPayRespDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import com.chinasofti.huateng.rpc.pay.GateTxnPayClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * IF1A-01 出站扣费编排：判断本笔该不该扣 → 按渠道组行业明细 → 调 gate-txn-pay-server 落单。
 *
 * <p>2026-09-14 从 {@code fep-dev-server} 的 {@code GateTransactionHandler} 迁入，
 * **{@code shouldPay} 的判断与 {@code sendGateTxnPay} 的异常兜底逐行未改**。</p>
 *
 * <p><b>迁移理由（不是风格问题）</b>：扣费决策的输入 {@code adviceOpt} 由 ticket-server 自己生产
 * （BOM 补站链路的 {@code supplement.CardDataUpdateHandler}），此前却要编码进
 * {@code NotifyVerifyResultReqDTO} 出网、由接入层重新解读一遍才生效；而接入层无法区分
 * 「ticket-server 告诉我的」与「闸机上送的」，只能依赖「真实 AGM 不上送该字段」这条经验事实。
 * 判断与输入同进程后，那条依赖消失。<b>NEVER 把本类挪回接入层。</b></p>
 *
 * <p><b>本类 NEVER 加 {@code @Transactional}</b>：内部要调 gate-txn-pay-server（还可能连带
 * para-server 查线路 / 站名），且调用点在 {@link GateTicketWriter} 的事务**之外**。
 * 理由见 AGENTS.md §5.2「@Transactional 方法内 NEVER 发起任何 RPC」记录的 2026-08-26 生产事故。</p>
 */
@Component
class GateFarePaymentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GateFarePaymentOrchestrator.class);

    /**
     * BOM 补站里属于「出站方向」的 adviceOpt 白名单：{@code 005} 免费更新、{@code 006} 补出站。
     * 只有这两个取值才跳过扣费；{@code 018} / {@code 020} 是补进站、走 {@code trxType=01}，本就不进扣费分支。
     * <b>NEVER 改成「非空即跳过」</b>——理由见 {@link #shouldPay}。
     *
     * <p><b>{@code 020} 已于 2026-09-15 移出本白名单</b>（随 {@code AdviceOptEnum.isSupplementExit()} 一起收窄）：
     * 它的口径改成「乘客刷卡未进站成功时的免费进闸更新」后，乘客随后要真实出站并**正常扣费**，
     * 留在这里等于整程免费（资损）。<b>NEVER 因为它名字里有「免费更新」就加回来</b> ——
     * 「免费」指这次进闸更新不收钱，不是这一程不收钱。</p>
     *
     * <p>取值来自 {@code model} 的 {@link AdviceOptEnum}，与 {@code gate/GateCodeStatusResolver}
     * 的 {@code ADVICE_OPT_TABLE}、{@code supplement/SupplementStateRules} 的 {@code UPDATE_RULES}
     * 共用同一份字典（2026-09-14 全模块 grep 实测：引用 {@code AdviceOptEnum} 的 main 代码只有
     * 本类与那两个类，外加 {@code supplement} 的两个 handler；{@code GateTicketHandler} **已不再引用**，
     * 本段此前把它列进来是错的，NEVER 回退）。
     * <b>NEVER 在本文件里改回裸字面量。</b>
     */
    private static final Set<String> BOM_SUPPLEMENT_EXIT_ADVICE_OPTS = AdviceOptEnum.SUPPLEMENT_EXIT_CODES;

    private final GateTxnPayClient gateTxnPayClient;
    private final AlipayIndustryDetailAssembler industryDetailAssembler;
    private final GateTxnPayRequestAssembler payRequestAssembler;

    public GateFarePaymentOrchestrator(GateTxnPayClient gateTxnPayClient,
                                       AlipayIndustryDetailAssembler industryDetailAssembler,
                                       GateTxnPayRequestAssembler payRequestAssembler) {
        this.gateTxnPayClient = gateTxnPayClient;
        this.industryDetailAssembler = industryDetailAssembler;
        this.payRequestAssembler = payRequestAssembler;
    }
    /**
     * 按需发起出站扣费。<b>MUST 在 {@link GateTicketWriter} 落库成功之后调用</b>：
     * 扣费入参里的 12 个字段来自 {@code ticketResponse}，而那些值是落库后才填齐的。
     *
     * <p>本方法**不抛异常、不影响过闸应答**：扣费落单失败只记 ERROR，闸机照常开门。
     * 乘客已经在付费区，因为落单失败拦住等于把人困在站里；欠费由对账与后续扣费重试收敛。</p>
     *
     * @param request        闸机检票报文（{@code cardType} / {@code itpUserId} 等已被前序步骤规范化）
     * @param ticketResponse 本笔检票的应答对象，字段已填齐
     */
    public void settleIfNeeded(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO ticketResponse) {
        if (!shouldPay(request)) {
            return;
        }
        boolean alipayTransaction = isAlipayTransaction(request);
        log.info("IF1A-01 出站交易调用扣费交易服务, cardId={}, alipay={}", request.getCardId(), alipayTransaction);
        // 两个渠道都走 gate-txn-pay-server 落单，由它按 ISSUE_CHANNEL_CODE 分派出账口：
        // 07 分派到 alipay-pay-sign，其余分派到支付中心。**NEVER 在本类直连支付宝扣费** ——
        // 那条老路不落 GATE_TXN_PAY，APP 乘车记录、对账与欠费判定三处都看不到这趟行程。
        // 支付宝要的 21 键 industryDetail 只有此刻拿得到（9 个键在 GATE_TXN_PAY 没有列），
        // 因此在这里组好整块透传下去，下游只存不算。
        String industryDetail = alipayTransaction ? industryDetailAssembler.assemble(request) : null;
        sendGateTxnPay(request, ticketResponse, industryDetail);
    }

    /**
     * 判断是否为支付宝交易：issueChannelCode == "07" 表示支付宝渠道。
     */
    private boolean isAlipayTransaction(NotifyVerifyResultReqDTO request) {
        return request != null && IssueChannelCodeEnum.isAlipay(request.getIssueChannelCode());
    }
    /**
     * 判断是否需要发起扣费：仅 trxType 为 "02"（正常出站）或 "03"（超时出站）时触发，
     * 且 <b>BOM 补站的 005 / 006 一律不扣</b>。其他类型（如异常开闸、免费放行）不扣费。
     *
     * <p><b>MUST 用白名单枚举 adviceOpt，NEVER 写成「adviceOpt 非空即跳过」。</b>
     * 黑名单写法下，上游一旦上送任意非空值（含无意义值、误填值），出站扣费就被**静默跳过**，
     * 是资损方向且只有一行 info 日志。所以这里只认 {@code 005} / {@code 006} 两个补出站取值，
     * 其余非空值按正常出站扣费并打 WARN 提示未知取值。</p>
     *
     * <p><b>为什么用 {@code adviceOpt} 识别 BOM 补站：</b>该字段只有 IF5A-03 链路会上送
     * （{@code supplement.SupplementGateRequestAssembler}）；真实闸机 / AGM 的 IF1A-01 报文里
     * 它恒为 {@code null}（实测），APP 自助补站用的是 {@code excessFareType} 而非 {@code adviceOpt}。
     * 所以这个判据精确命中 BOM 补站，不误伤另外两条链路。</p>
     *
     * <p><b>为什么补站不扣费：</b>{@code 005} 免费更新本就不该收费；{@code 006} 补出站的钱由 BOM
     * 现场向乘客收取（用户 2026-09-10 裁决），ITP 再发起免密扣款即双重收费。此前
     * {@code resolveTrxType} 把 {@code 005}/{@code 006} 都映射成 {@code trxType=02}，于是无条件走扣费——
     * 实测 005 落出订单 {@code GT20260910172129730135717}（{@code ORIGINAL_FARE=700}、
     * {@code TRX_AMOUNT=0}、{@code ORDER_EXP_TYPE=0}），在 {@code GATE_TXN_PAY} 里与一笔正常出站
     * 完全同形，污染对账。</p>
     */
    private boolean shouldPay(NotifyVerifyResultReqDTO request) {
        if (request == null || !TrxTypeCodeEnum.isExitTxn(request.getTrxType())) {
            return false;
        }
        String adviceOpt = request.getAdviceOpt();
        if (!StringUtils.hasText(adviceOpt)) {
            return true;
        }
        if (BOM_SUPPLEMENT_EXIT_ADVICE_OPTS.contains(adviceOpt)) {
            log.info("IF1A-01 BOM 补站交易不触发扣费, cardId={}, trxType={}, adviceOpt={}",
                    request.getCardId(), request.getTrxType(), adviceOpt);
            return false;
        }
        log.warn("IF1A-01 出站报文带未知 adviceOpt，按正常出站扣费, cardId={}, trxType={}, adviceOpt={}",
                request.getCardId(), request.getTrxType(), adviceOpt);
        return true;
    }
    /**
     * 组装并调 gate-txn-pay-server 入库出站扣费交易记录。
     * 异常时记录 error 日志，不向上抛出（入库失败由对账补偿）。
     */
    private void sendGateTxnPay(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO ticketResponse,
                                String industryDetail) {
        try {
            GateTxnPayReqDTO payRequest = payRequestAssembler.assemble(request, ticketResponse, industryDetail);
            log.info("IF1A-01 出站交易调用扣费交易服务, cardId={}, trxType={}", request.getCardId(), request.getTrxType());
            GateTxnPayRespDTO payResponse = gateTxnPayClient.requestGateTxnPay(payRequest);
            log.info("IF1A-01 出站交易调用扣费交易服务, retCode={}", payResponse != null ? payResponse.getRetCode() : "null");
        } catch (Exception e) {
            log.error("IF1A-01 出站交易调用扣费交易服务异常, cardId={}, trxType={}, handleDateTime={}",
                    request.getCardId(), request.getTrxType(), request.getHandleDateTime(), e);
        }
    }
}
