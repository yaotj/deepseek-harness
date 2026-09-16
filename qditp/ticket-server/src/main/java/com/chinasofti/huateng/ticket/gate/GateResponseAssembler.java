package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.utils.SignChannelUtils;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.merchant.MerchantParty;
import com.chinasofti.huateng.ticket.merchant.MerchantPartyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * IF1A-01 应答装配器 —— 把返回给 fep-dev-server / 闸机的全部字段填齐。
 *
 * <p>2026-09-14 从 {@code GateTicketHandler}（拆分前 887 行）拆出。拆分判据是**这些字段有共同的收口纪律**：
 * 每一个都由「本次交易的入向字段 + 已持久化的状态」推导，不再查库、不改 {@code request}，
 * 且其中三个（{@code countingFlag} / {@code countingTimes} / {@code attributableParty}）
 * <b>恒有值、NEVER 留 null</b>。原先它们散在编排方法尾部 30 行里，与写库、RPC 交织，
 * 「哪个字段在哪个失败分支下会变成 null」看不出来。</p>
 *
 * <p><b>MUST 用 {@code effectiveStatus}（{@link GateTicketWriter.WriteResult#status()}）填
 * {@code ticketStatus}，NEVER 用 {@code buildNextStatus} 的返回值</b>：后者的 {@code useCount} /
 * {@code txnSeq} 是「当前值 + 1」的相对增量，AGM 超时重推时写库会跳过状态推进并回传库里那一行，
 * 用相对增量会让返回闸机的状态与库里差 1。</p>
 */
@Component
class GateResponseAssembler {

    private static final Logger log = LoggerFactory.getLogger(GateResponseAssembler.class);
    private static final String RET_SUCCESS = "0000";
    /** 订单异常类型：正常。 */
    private static final String ORDER_EXP_TYPE_NORMAL = "0";
    /** 订单异常类型：双段计费正常订单_行程超时。 */
    private static final String ORDER_EXP_TYPE_OVERTIME = "5";
    /** 离线码的签约渠道代码（0x17）。 */
    private static final String SIGN_CHANNEL_OFFLINE = "17";

    @Autowired
    private GateDailyTicketCoordinator dailyTicketCoordinator;

    @Autowired
    private MerchantPartyResolver merchantResolver;

    /**
     * 填齐成功应答。
     *
     * @param request         闸机检票请求（已被 {@code GateCardTypeEnricher} 富化过）
     * @param response        待填充的应答
     * @param effectiveStatus 已持久化的票卡状态，来自 {@link GateTicketWriter.WriteResult#status()}
     * @param resolvedCardType {@code CardTypeMapping.toIssueCardType(signChannelCode)} 的结果，
     *                         用于判日票；<b>NEVER 换成 {@code request.cardType}</b> —— 后者会被
     *                         账户域覆盖，两者语义不同
     */
    public void fillSuccessResponse(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response,
                                    QRCodeStatus effectiveStatus, String resolvedCardType) {
        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");
        response.setTicketStatus(effectiveStatus.getCodeStatus());
        response.setOrderExpType(resolveOrderExpType(request.getTrxType()));
        response.setOfflineFlag(resolveOfflineFlag(request.getSignChannelCode()));
        response.setCompanionFlag(request.getCompanionFlag());
        response.setPayChannelCode(request.getPaymentVendor());
        // countingFlag / countingTimes MUST 恒有值、NEVER 留 null（用户 2026-09-10 要求）。
        // 它们只由卡种推导、不依赖 daily-ticket-server，因此在日票票号查询之前无条件先赋值——
        // 这样「非日票卡种」与「日票但 queryDailyTicketInfo 失败/抛异常」两种情况都有值。
        response.setCountingFlag(dailyTicketCoordinator.resolveCountingFlag(resolvedCardType));
        response.setCountingTimes(dailyTicketCoordinator.resolveCountingTimes(resolvedCardType));
        // 日票票号透传（signChannelCode ∈ {12,13,14,15} 时填充）
        if (CardTypeCodeEnum.isDailyTicket(resolvedCardType)) {
            dailyTicketCoordinator.applyDailyTicketFields(request, response);
        }
        // 商户号分账字段透传
        resolveMerchantParties(request, response);
        log.info("IF1A-01 返回 ticketResponse 字段, cardId={}, ticketStatus={}, orderExpType={}, offlineFlag={}, companionFlag={}, ticketCode={}, countingTimes={}, countingFlag={}, attributableParty={}, receivingParty={}, payChannelCode={}, signChannelCode={}",
                request.getCardId(), response.getTicketStatus(), response.getOrderExpType(),
                response.getOfflineFlag(), response.getCompanionFlag(), response.getTicketCode(),
                response.getCountingTimes(), response.getCountingFlag(),
                response.getAttributableParty(), response.getReceivingParty(),
                response.getPayChannelCode(), request.getSignChannelCode());
    }

    /**
     * 商户号分账解析：
     * <ul>
     *   <li>正常进出站：以 {@code handleDateTime} 的日期判断归属方 / 收款方</li>
     *   <li>单边 / 补站 / 超时（{@code orderExpType != 0} 或 {@code trxType} 为异常类型）：
     *       <b>无条件</b>使用城交商户，与乘车日期无关</li>
     * </ul>
     *
     * <p><b>本方法此前的分支条件把日期也读进去了</b>（{@code isSingleSideOrSupplement
     * && hasText(txnDate) && !resolveFor(txnDate).useOld()}），而那两项是冗余的：旧商户期本来就会从
     * else 分支的 {@code resolveFor} 拿到同一对城交商户号，日期判断只改变了打哪条日志。
     * 它唯一真正改变结果的入参是 <b>{@code txnDate} 缺失</b> —— 当时落进 else、拿到<b>新</b>商户号，
     * 恰好与「单边补站一律归城交」相反。2026-09-14 去掉后两项，让条件与规则一致。
     * <b>NEVER 再把日期判断加回这一支</b>。</p>
     *
     * <p><b>MUST 在 {@code orderExpType} 已赋值之后调用</b> —— 它读 {@code response.getOrderExpType()}
     * 判「是否单边补站」。<b>异常分支 MUST 降级为旧商户</b>，NEVER 留空：这两个字段一路透传到
     * {@code GATE_TXN_PAY}，留空会让那笔扣费无归属方、对账无法归集。</p>
     */
    private void resolveMerchantParties(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response) {
        try {
            String txnDate = StringUtils.hasText(request.getHandleDateTime())
                    && request.getHandleDateTime().length() >= 8
                    ? request.getHandleDateTime().substring(0, 8) : null;
            boolean isSingleSideOrSupplement = !ORDER_EXP_TYPE_NORMAL.equals(response.getOrderExpType())
                    || TrxTypeCodeEnum.ABNORMAL.getCode().equals(request.getTrxType())
                    || TrxTypeCodeEnum.ENTRY_FAIL.getCode().equals(request.getTrxType());
            if (isSingleSideOrSupplement) {
                // 单边 / 补站 / 超时强制回退城交：这一支**与日期无关**，因此走 oldParty() 而不是 resolveFor，
                // 且条件里 NEVER 再出现 txnDate —— 业务规则就是「新商户期的单边补站也归城交」。
                applyMerchantParty(response, merchantResolver.oldParty());
                log.info("IF1A-01 商户号解析-单边补站回退, cardId={}, txnDate={}, attributableParty={}, receivingParty={}",
                        request.getCardId(), txnDate, response.getAttributableParty(), response.getReceivingParty());
            } else {
                applyMerchantParty(response, merchantResolver.resolveFor(txnDate));
                log.info("IF1A-01 商户号解析完成, cardId={}, txnDate={}, orderExpType={}, isSingleSide={}, attributableParty={}, receivingParty={}",
                        request.getCardId(), txnDate, response.getOrderExpType(), isSingleSideOrSupplement,
                        response.getAttributableParty(), response.getReceivingParty());
            }
        } catch (Exception e) {
            log.error("IF1A-01 商户号解析异常，降级使用旧商户号, cardId={}", request.getCardId(), e);
            applyMerchantParty(response, merchantResolver.oldParty());
        }
    }

    /**
     * 把一对商户号整体写进应答。
     *
     * <p><b>MUST 成对写</b>：{@link MerchantParty} 的两个字段来自同一侧（老 / 新），
     * 分开取会让「老归属方 + 新收单方」变成可能，而商户号配错在数据里事后完全看不出来
     * （2026-09-14 把四个单字段 getter 降级为 private 就是为了从语法上消除这种写法）。</p>
     */
    private void applyMerchantParty(NotifyVerifyResultRespDTO response, MerchantParty party) {
        response.setAttributableParty(party.attributableParty());
        response.setReceivingParty(party.receivingParty());
    }

    /**
     * 根据出站交易类型解析订单异常类型。
     * {@code 02}=正常出站 → {@code 0}（正常），{@code 03}=超时出站 → {@code 5}（双段计费正常订单_行程超时）。
     */
    private String resolveOrderExpType(String trxType) {
        if (TrxTypeCodeEnum.EXIT_OVERTIME.getCode().equals(trxType)) {
            return ORDER_EXP_TYPE_OVERTIME;
        }
        return ORDER_EXP_TYPE_NORMAL;
    }

    /**
     * 根据签约渠道代码判断离线码标识。{@code 0x17}=离线码 → {@code Y}，其他 → {@code null}。
     */
    private String resolveOfflineFlag(String signChannelCode) {
        String resolved = SignChannelUtils.resolve(signChannelCode);
        return SIGN_CHANNEL_OFFLINE.equals(resolved) ? "Y" : null;
    }
}
