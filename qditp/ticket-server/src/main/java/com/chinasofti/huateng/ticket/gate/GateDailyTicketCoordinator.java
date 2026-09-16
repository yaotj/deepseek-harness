package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.app.dailyticket.DailyTicketBaseResult;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoReqDTO;
import com.chinasofti.huateng.model.app.dailyticket.QueryDailyTicketInfoResult;
import com.chinasofti.huateng.model.enums.CardTypeCodeEnum;
import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.rpc.dailyticket.DailyTicketClient;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * IF1A-01 日票协同器 —— 与 {@code daily-ticket-server} 的三次交互（进站校验 / 出站扣次 / 票号查询）
 * 以及两个恒有值字段（{@code countingFlag} / {@code countingTimes}）的推导。
 *
 * <p>2026-09-14 从 {@code GateTicketHandler}（拆分前 887 行）拆出。拆分判据是**这五段的失败处置规则互相印证、
 * 拆开就会被改歪**：
 * <ul>
 *   <li>进站校验不可达 → <b>MUST 抛异常</b>（可重试故障，NEVER 转成确定性业务码）</li>
 *   <li>出站扣次失败 → <b>MUST 放行 + ERROR 留证据</b>（乘客已在站内，拦住等于把人困在里面）</li>
 *   <li>票号查询失败 → 只影响 {@code ticketCode} 一个字段，其余照常</li>
 *   <li>{@code countingFlag} / {@code countingTimes} <b>只由卡种推导、不依赖远端</b>，
 *       因此上面三个都失败时它们仍有值</li>
 * </ul>
 * 三个方向相反的处置写在一处，改任何一个都能立刻看见另外两个的口径。</p>
 */
@Component
class GateDailyTicketCoordinator {

    private static final Logger log = LoggerFactory.getLogger(GateDailyTicketCoordinator.class);
    private static final String RET_SUCCESS = "0000";
    /** 一趟行程消耗的日票次数，恒为 1。见 {@link #resolveCountingTimes} 的语义说明。 */
    private static final int DAILY_TICKET_TIMES_PER_TRIP = 1;
    /** 非日票卡种的 countingTimes。见 {@link #resolveCountingTimes}——该字段恒有值，NEVER 留 null。 */
    private static final int COUNTING_TIMES_NOT_DAILY_TICKET = 0;
    /** 日票（记期票 0445~0447 + 计次票 0448）的 countingFlag。见 {@link #resolveCountingFlag}。 */
    private static final String COUNTING_FLAG_DAILY_TICKET = "Y";
    /** 非日票卡种的 countingFlag。见 {@link #resolveCountingFlag}——该字段恒有值，NEVER 留 null。 */
    private static final String COUNTING_FLAG_NOT_DAILY_TICKET = "N";

    @Autowired
    private DailyTicketClient dailyTicketClient;

    /**
     * 远端调用的**技术失败**（RPC 抛异常 / 响应解析为 null）处置口径。
     *
     * <p>这三个常量的差异就是本类存在的理由——它们方向相反，且各自的理由写在常量上，
     * 新增第四次日票交互 MUST 从这里选一个，<b>NEVER 再手写一份 try/catch</b>。
     * 业务失败（{@code retCode} 非 0000）不在此枚举内：三处的措辞与后续动作本就不同，
     * 由各调用点自己判，见各方法注释。</p>
     */
    private enum FailurePolicy {
        /**
         * 失败即抛。可重试故障 MUST 保持异常语义，由全局异常处理器返错、闸机按重试处理；
         * <b>NEVER 就地转成确定性业务码</b>——闸机据此不重试，一次网络抖动就把正常票判死。
         */
        PROPAGATE,
        /**
         * 失败即放行 + ERROR 留证据。乘客已在站内，拦住等于把人困在付费区；
         * 扣次丢失是资损，因此一律 ERROR 而非 WARN。
         */
        PASS_THROUGH("已放行出站，次数未扣减需人工核对"),
        /** 失败即降级 + ERROR 留证据。只影响单个可选字段，其余字段照常。 */
        DEGRADE("仅本字段降级，其余字段照常");

        private final String description;

        FailurePolicy() {
            this(null);
        }

        FailurePolicy(String description) {
            this.description = description;
        }

        /** 供日志说明「失败后做了什么」。{@link #PROPAGATE} 不用（它直接抛）。 */
        String description() {
            return description;
        }
    }

    /**
     * 一次远端调用的结果。{@code payload} 为 null 表示技术失败（且 policy 不是
     * {@link FailurePolicy#PROPAGATE}，否则已经抛出去了）。
     */
    private record RemoteOutcome<T extends DailyTicketBaseResult>(T payload, boolean accepted) {
        boolean technicalFailure() {
            return payload == null;
        }
    }

    /**
     * 三次交互共用的调用骨架：发起 → 技术失败按 {@code policy} 处置 → 判 {@code retCode}。
     *
     * <p>{@code DailyTicketClient} 的三个方法都**不吞异常**（{@code rpc/.../DailyTicketClient.java:186/187}
     * 直接 postJsonAndGetResponse 后解析），因此不可达时会原样抛到这里。</p>
     */
    private <T extends DailyTicketBaseResult> RemoteOutcome<T> invoke(
            String step, String logContext, FailurePolicy policy, Supplier<T> remoteCall) {
        T payload;
        try {
            payload = remoteCall.get();
        } catch (Exception e) {
            if (policy == FailurePolicy.PROPAGATE) {
                log.error("IF1A-01 {}调用异常, {}", step, logContext, e);
                throw new RuntimeException(step + "服务不可用, " + logContext, e);
            }
            log.error("IF1A-01 {}调用异常，{}, {}", step, policy.description(), logContext, e);
            return new RemoteOutcome<>(null, false);
        }
        if (payload == null) {
            if (policy == FailurePolicy.PROPAGATE) {
                log.error("IF1A-01 {}响应解析为空, {}", step, logContext);
                throw new RuntimeException(step + "服务不可用, " + logContext);
            }
            log.error("IF1A-01 {}响应解析为空，{}, {}", step, policy.description(), logContext);
            return new RemoteOutcome<>(null, false);
        }
        return new RemoteOutcome<>(payload, RET_SUCCESS.equals(payload.getRetCode()));
    }

    /**
     * 日票进站校验：{@code SIGN_CHANNEL_CODE ∈ {12,13,14,15}} 且**本笔是进站交易**时才拦截。
     *
     * <p>MUST 带 trxType 白名单（与 {@link #markUsedOnExit} 的 {@code "02"} 白名单成对，按方向分流）：
     * entryCheck 问的是「这张票还能不能进站」，计次票次数用尽 / 已进站未出站在出站时必然返非 0000。
     * 若不分流，出站也会走到这里并在写库前 return，后果是闸机不开门、出站明细不落库、
     * 状态卡在「已进站」、gate-txn-pay 的 shouldPay 不触发——乘客困在付费区。</p>
     *
     * <p><b>校验服务不可用（RPC 异常或响应解析为 null）时 MUST 让异常继续往上抛，
     * NEVER 就地转成业务 retCode</b>：
     * 放行不行——等于让次数已用尽或已过期的票入站，出站扣次走同一个服务、同样调不通；
     * 但转成 {@code QR_CODE_NOT_FOUND} 也不行——那是「这张票不存在」的**确定性**拒绝，
     * 闸机据此不会重试，一次网络抖动就把正常票判死。服务不可用属可重试故障，
     * MUST 保持异常语义，由全局异常处理器返回错误响应、闸机按重试处理。
     * {@code DailyTicketClient.entryCheck} 本身不吞异常（{@code rpc/.../DailyTicketClient.java:186}
     * 直接 postJsonAndGetResponse 后解析），daily-ticket-server 不可达时会原样抛出。</p>
     *
     * @return true=允许继续检票流程；false=已被日票侧拒绝，{@code response} 已填好错误码，调用方 MUST 直接返回
     */
    public boolean checkEntryAllowed(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response,
                                     String resolvedCardType) {
        if (!TrxTypeCodeEnum.isEntryTxn(request.getTrxType())
                || !CardTypeCodeEnum.isDailyTicket(resolvedCardType)) {
            return true;
        }
        String logContext = "cardId=" + request.getCardId() + ", signChannelCode=" + request.getSignChannelCode();
        RemoteOutcome<DailyTicketBaseResult> outcome = invoke("日票进站校验", logContext,
                FailurePolicy.PROPAGATE, () -> dailyTicketClient.entryCheck(request.getCardId()));
        // 走到这里 technicalFailure 必为 false —— PROPAGATE 已经把技术失败抛出去了。
        // 剩下的只有「对方明确拒绝」，那是确定性结论，MUST 转成业务码并透传对方原因。
        if (!outcome.accepted()) {
            log.warn("IF1A-01 日票进站校验拒绝, {}, retMsg={}", logContext, outcome.payload().getRetMsg());
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(outcome.payload().getRetMsg());
            return false;
        }
        log.info("IF1A-01 日票进站校验通过, cardId={}, signChannelCode={}",
                request.getCardId(), request.getSignChannelCode());
        return true;
    }

    /**
     * 日票出站处理：出站时扣减计次票次数、标记已使用（仅出站 trxType 触发）。
     *
     * <p><b>出站 MUST 放行，NEVER 因扣次失败中断流程或回非 0000</b>：乘客已经在站内，
     * 拦住等于把人困在站里。{@code markUsed} 同样不吞异常（{@code DailyTicketClient.java:187}），
     * 所以这里必须自己接住——不接住会让整条检票流程中断在这一行，应答的 {@code 0000} 走不到。
     * 扣次丢失是资损（乘客白坐一次），因此失败一律按 ERROR 落日志留证据。</p>
     *
     * <p>注意：本项目不用 MQ，跨服务补偿的形态还没定（用户已否决扫表/定时任务），
     * 所以当前只保证「放行 + 留证据」，次数追补需要另行设计。</p>
     */
    public void markUsedOnExit(NotifyVerifyResultReqDTO request, String resolvedCardType) {
        if (!TrxTypeCodeEnum.EXIT.getCode().equals(request.getTrxType())
                || !CardTypeCodeEnum.isDailyTicket(resolvedCardType)) {
            return;
        }
        // ticket-server 没有 GATE_TXN_PAY.ORDER_NO（由 gate-txn-pay-server 生成），
        // 传 null 时 daily-ticket-server 的 UK_DTUL_ORDER 不拦截（Oracle 允许多个 null），
        // handleStationCode = 本次交易站、lastHandleStationCode = 上次交易站（进站）
        RemoteOutcome<DailyTicketBaseResult> outcome = invoke("日票出站标记", "cardId=" + request.getCardId(),
                FailurePolicy.PASS_THROUGH,
                () -> dailyTicketClient.markUsed(
                        request.getCardId(), null,
                        null,
                        request.getLastHandleStationCode(),
                        request.getHandleStationCode()));
        if (outcome.technicalFailure()) {
            return;
        }
        if (!outcome.accepted()) {
            log.error("IF1A-01 日票出站标记被拒，{}, cardId={}, retCode={}, retMsg={}",
                    FailurePolicy.PASS_THROUGH.description(), request.getCardId(),
                    outcome.payload().getRetCode(), outcome.payload().getRetMsg());
            return;
        }
        log.info("IF1A-01 日票出站处理完成, cardId={}", request.getCardId());
    }

    /**
     * 解析 {@code countingTimes}（本次行程消耗次数），**恒有值、NEVER 返回 null**（用户 2026-09-10 裁定）。
     *
     * <pre>
     * 0  非日票：普通二维码 / 员工卡 / NFC 等，不存在计次概念
     * 1  日票：一日票 / 三日票 / 七日票是「这趟用掉一次日票」，计次票是「这趟扣一次」，都是 1
     * </pre>
     *
     * <p><b>NEVER 回填 {@code DAILY_TICKET_INSTANCE.ACTUAL_TIMES}（剩余次数）</b>——该列用 {@code -99}
     * 表示「不限次」，透给 APP 会渲染成「-99 次」并污染 {@code GATE_TXN_PAY.COUNTING_TIMES}
     * （2026-09-10 已发生并修正）。剩余次数属票卡资产状态，归日票查询接口，不属于行程记录。</p>
     */
    public int resolveCountingTimes(String resolvedCardType) {
        return CardTypeCodeEnum.isDailyTicket(resolvedCardType)
                ? DAILY_TICKET_TIMES_PER_TRIP : COUNTING_TIMES_NOT_DAILY_TICKET;
    }

    /**
     * 解析 {@code countingFlag}（计次标志），**恒有值、NEVER 返回 null**（用户 2026-09-10 要求）。
     *
     * <pre>
     * Y  日票：记期票（一日票 0445 / 三日票 0446 / 七日票 0447）与计次票（0448）都是 Y
     * N  非日票：普通二维码 / 员工卡 / NFC 等
     * </pre>
     *
     * <p><b>记期票也是 {@code Y}</b>（用户 2026-09-10 裁定）——本字段是「按日票规则计次/计期管理」的标志，
     * 不是「仅计次票」，因此 0445~0448 一律 {@code Y}。取值与库表注释
     * {@code GATE_TXN_PAY.COUNTING_FLAG='计次票标志：Y是，N否'} 一致。
     * <b>NEVER 退回 1/2</b>——2026-09-10 曾按「1=计时票、2=计次票」实现，与库表注释和甲方口径都不符。</p>
     *
     * <p>只依赖卡种，**不依赖 daily-ticket-server**——因此日票查询 RPC 失败或抛异常时该字段仍有值。
     * 该值随 {@code GateTxnPayReqDTO} 一路透传，**入库时就写进 {@code GATE_TXN_PAY.COUNTING_FLAG}**，
     * 不靠查询出口兜底。</p>
     */
    public String resolveCountingFlag(String resolvedCardType) {
        return CardTypeCodeEnum.isDailyTicket(resolvedCardType)
                ? COUNTING_FLAG_DAILY_TICKET : COUNTING_FLAG_NOT_DAILY_TICKET;
    }

    /**
     * 查询日票票号并写入 {@code response.ticketCode}（仅日票卡种调用）。
     *
     * <p>{@code countingTimes} / {@code countingFlag} <b>不在本方法里赋值</b>——它们由
     * {@link #resolveCountingTimes} / {@link #resolveCountingFlag} 在本方法之前无条件赋好，
     * 这样本方法查询失败或抛异常时两个字段仍有值。**NEVER 把那两个字段挪回本方法的
     * {@code if (retCode==0000)} 分支里**，那正是 2026-09-10 修掉的缺陷形状。</p>
     *
     * <p><b>NEVER 回填 {@code DAILY_TICKET_INSTANCE.ACTUAL_TIMES}（剩余次数）</b>——该列用 {@code -99}
     * 表示「不限次」（见 {@code daily-ticket-server-schema.sql:99}），透给 APP 会渲染成
     * 「剩余 -99 次」，且哨兵值会被 {@code GATE_TXN_PAY.COUNTING_TIMES} 持久化、污染报表与对账
     * （2026-09-10 实测订单 {@code GT20260910143159899000084} 落库即为 -99）。</p>
     */
    public void applyDailyTicketFields(NotifyVerifyResultReqDTO request, NotifyVerifyResultRespDTO response) {
        QueryDailyTicketInfoReqDTO queryReq = new QueryDailyTicketInfoReqDTO();
        queryReq.setCardId(request.getCardId());
        RemoteOutcome<QueryDailyTicketInfoResult> outcome = invoke("查询日票信息",
                "cardId=" + request.getCardId(), FailurePolicy.DEGRADE,
                () -> dailyTicketClient.queryDailyTicketInfo(queryReq));
        if (outcome.technicalFailure()) {
            return;
        }
        if (!outcome.accepted()) {
            log.warn("IF1A-01 查询日票信息失败, cardId={}, retCode={}",
                    request.getCardId(), outcome.payload().getRetCode());
            return;
        }
        QueryDailyTicketInfoResult info = outcome.payload();
        response.setTicketCode(info.getTicketCode());
        log.info("IF1A-01 日票信息解析完成, cardId={}, ticketCode={}, countingTimes={}, countingFlag={}, 剩余次数(仅日志)={}",
                request.getCardId(), info.getTicketCode(), response.getCountingTimes(),
                response.getCountingFlag(), info.getActualTimes());
    }
}
