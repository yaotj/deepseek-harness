package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.enums.TrxTypeCodeEnum;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;
import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.rpc.outcome.RpcOutcome;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.gate.AgmRideStatusService;
import com.chinasofti.huateng.ticket.gate.QRCodeStatusStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * IF5A-03 票卡更新：按 BOM 上送的 {@code adviceOpt} 执行补进站 / 补出站。
 *
 * <p><b>本方法 NEVER 加 {@code @Transactional}。</b>两条理由，缺一不可：</p>
 * <ol>
 *   <li>方法内要调 account-server（查用户）、para-server（006 查票价），并进程内进入
 *       {@code gate.GateTicketHandler}——后者自己还要调 6 个远端；事务包住 RPC 违反 AGENTS.md §5.2，
 *       会把 Druid 连接持有到全部远端返回为止；</li>
 *   <li>真正改 {@code QRCODE_STATUS} / {@code QRCODE_TXN_DETAIL} 的是
 *       {@code gate.GateTicketWriter}，它有**自己的窄事务**（只覆盖两条本地 SQL）。
 *       在这里再包一层只会把那个窄边界撑成整条链路，且本方法的 rollback 也收不回
 *       已经推给行业数据 / 支付宝 / 扣费域的那几笔。</li>
 * </ol>
 * <p><b>2026-09-14（ADR-D63）修正</b>：本段此前写「状态推进发生在 fep-dev-server 回调
 * {@code /ci/agm/notiVerifyResult} 那个独立请求里，所以这里的 rollback 语义是空的」。
 * 那条双向 RPC 环已删除，状态推进现在就发生在**本请求内**（`GateTicketWriter` 的独立事务里），
 * 「rollback 语义是空的」已不成立，**NEVER 回退成那个说法** —— 它会让人误判本方法加事务无害。</p>
 * <p>本类自己的写库动作只有 {@link SupplementRequestLedger} 的声明 / 收口，它<b>刻意各自独立提交</b>，
 * 目的正是「远端失败也要留下证据」（AGENTS.md §5.2 那条事务内回滚会连证据一起丢弃的事故）。</p>
 *
 * <p>入口是 {@link SupplementService}，<b>包外 NEVER 直接注入本类</b>。</p>
 */
@Component
class CardDataUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(CardDataUpdateHandler.class);

    /** {@code optDate} / {@code handleDateTime} 的固定长度 {@code yyyyMMddHHmmss}。 */
    private static final int OPT_DATE_LENGTH = 14;

    /** 车站码固定 4 位。 */
    private static final int STATION_CODE_LENGTH = 4;

    /**
     * QRCODE_STATUS 只读访问。**NEVER 改回直接注 {@code QRCodeStatusMapper}** ——
     * 该表的写权归 gate 包，本包只准读（见 {@link QRCodeStatusStore} 类注释）。
     */
    @Autowired
    private QRCodeStatusStore qrCodeStatusStore;

    @Autowired
    private SupplementUserLookup userLookup;

    /**
     * IF1A-01 检票编排门面，<b>进程内直调</b>。
     *
     * <p>2026-09-14（ADR-D63）从 {@code fepDevClient.notifyVerifyResult} 换成进程内调用：原链路是
     * ticket-server → fep-dev-server → ticket-server 的**双向 RPC 环**，而 fep-dev 侧那一跳
     * 自 ADR-D62 起只剩「itpUserId 归一 + 原样转发」，补站报文的 itpUserId 又是本模块自己造的
     * ——整跳没有任何净效果，只贡献两次序列化、一次网络超时面与一个「结果未知」窗口。</p>
     *
     * <p><b>2026-09-14（ADR-D65）起注入的是门面 {@link AgmRideStatusService}，不再是
     * {@code gate.GateTicketHandler} 那个内部实现类。</b>此前只能注内部类，是因为
     * {@code AgmRideStatusServiceImpl} 当时反过来持有 {@code SupplementService}（IF5A-01/03 的委派壳），
     * 注门面会成 {@code AgmRideStatusServiceImpl → SupplementService → 本类 → AgmRideStatusServiceImpl}
     * 的构造环、Spring Boot 3 启动即失败。那两个委派壳已随 {@code TicketAgmController} 改为直连补站门面
     * 一起删除，{@code gate → supplement} 这条边不存在了，因此现在可以正常依赖门面。
     * <b>NEVER 退回注入 {@code GateTicketHandler}</b> —— 那是跨包抓内部实现类。</p>
     */
    @Autowired
    private AgmRideStatusService agmRideStatusService;

    @Autowired
    private SupplementStateRules stateRules;

    @Autowired
    private SupplementFareQuery fareQuery;

    @Autowired
    private SupplementGateRequestAssembler gateRequestAssembler;

    @Autowired
    private SupplementRequestLedger requestLedger;

    RequestCardDataUpdateRespDTO handle(RequestCardDataUpdateReqDTO request,
                                       RequestCardDataUpdateRespDTO response) {
        String cardId = request.getCardId();
        String adviceOpt = request.getAdviceOpt();
        String updateType = SupplementCodec.defaultString(
                request.getUpdateType(), SupplementCodec.UPDATE_TYPE_FREE_AREA);
        String requestTransAmount = SupplementCodec.defaultString(
                request.getTransAmount(), SupplementCodec.AMOUNT_ZERO);
        String optDate = request.getOptDate();
        String updateStationCode = request.getUpdateStationCode();

        String paramError = validateRequired(cardId, adviceOpt, updateStationCode, optDate);
        if (paramError != null) {
            log.warn("IF5A-03 入参校验失败[{}], cardId={}, adviceOpt={}, updateStationCode={}, optDate={}",
                    paramError, cardId, adviceOpt, updateStationCode, optDate);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg(paramError);
            return response;
        }

        QRCodeStatus currentStatus = qrCodeStatusStore.findByCardId(cardId);
        if (currentStatus == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return response;
        }

        String rawCodeStatus = currentStatus.getCodeStatus();
        QRCodeStatusEnum codeStatus = stateRules.resolveCodeStatus(rawCodeStatus);
        if (codeStatus == null) {
            log.warn("IF5A-03 票卡状态不是已登记取值，拒绝, cardId={}, codeStatus={}", cardId, rawCodeStatus);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("票卡状态不是已登记取值: " + rawCodeStatus);
            return response;
        }

        // 005 的时间窗单独前置判一次，只为给 BOM 一个**可辨识**的返回码：
        // 走到 isUpdateAllowed 里超窗与「状态本来就不允许」都返回 false，共用 8001 时 BOM 分不清
        // 「该重新做票卡分析改走 006」和「这张卡根本不能操作」。isUpdateAllowed 内仍保留同一校验作兜底。
        // 这里 MUST 只判 005：006 本来就是超时分支，020 是补进站方向（刷卡未进站成功），
        // 把它们一起判进来会把合法请求误拒（UPDATE_RULES 里两者的 checksFreeWindow 也都是 false）。
        if (AdviceOptEnum.FREE_UPDATE.matches(adviceOpt)
                && !stateRules.isWithinFreeWindow(currentStatus.getGateInTime())) {
            log.warn("IF5A-03 免费更新超 20 分钟时间窗, cardId={}, codeStatus={}, adviceOpt={}, gateInTime={}",
                    cardId, rawCodeStatus, adviceOpt, currentStatus.getGateInTime());
            response.setRetCode(TicketErrorCodeEnum.CARD_STATUS_CHANGED.getCode());
            response.setRetMsg("免费更新时间窗（20 分钟）已过，请重新执行票卡分析");
            return response;
        }
        if (!stateRules.isUpdateAllowed(codeStatus, adviceOpt, updateType, currentStatus.getGateInTime())) {
            log.warn("IF5A-03 票卡状态不允许此操作, cardId={}, codeStatus={}, adviceOpt={}, updateType={},"
                            + " gateInTime={}",
                    cardId, rawCodeStatus, adviceOpt, updateType, currentStatus.getGateInTime());
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("票卡状态不允许此操作: codeStatus=" + rawCodeStatus + ", adviceOpt=" + adviceOpt);
            return response;
        }

        String trxType = resolveTrxType(adviceOpt);
        if (trxType == null) {
            log.warn("IF5A-03 不支持的操作类型, cardId={}, adviceOpt={}", cardId, adviceOpt);
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("不支持的操作类型: " + adviceOpt);
            return response;
        }

        SupplementUserLookup.Result lookup = userLookup.query(cardId);
        if (!lookup.outcome().isOk()) {
            return fillUserLookupFailure(response, lookup, cardId);
        }
        QueryUserInfoResult userInfo = lookup.userInfo();

        NotifyVerifyResultReqDTO gateRequest = gateRequestAssembler.newBaseRequest(
                currentStatus, cardId, userInfo.getThirdUserId(), trxType, optDate, updateStationCode,
                userInfo.getCardType(), SupplementCodec.defaultString(userInfo.getChannel(), ""));
        gateRequest.setDeviceId(SupplementCodec.defaultString(request.getOperaterId(), ""));
        // reserve1 借位落 adviceOpt：QRCODE_TXN_DETAIL 没有 ADVICE_OPT 列（实测 21 列），
        // 不借位的话一笔 BOM 补站与一笔真实检票在明细表里完全同形，对账侧只能靠 DEVICE_ID 猜。
        gateRequest.setAdviceOpt(adviceOpt);
        gateRequest.setReserve1(adviceOpt);

        // 金额一律以 ITP 重算为准，NEVER 直接用 BOM 上送的 transAmount——那是设备侧显示值，
        // 只做一致性比对并告警，避免设备侧算错时把差额带进 QRCODE_TXN_DETAIL。
        if (AdviceOptEnum.PAID_UPDATE.matches(adviceOpt)) {
            RequestCardDataUpdateRespDTO fareFailure = fillPaidUpdateAmount(
                    request, response, gateRequest, currentStatus, updateStationCode, requestTransAmount, cardId);
            if (fareFailure != null) {
                return fareFailure;
            }
        }

        return dispatchToGate(gateRequest, currentStatus, adviceOpt, updateType, cardId,
                requestTransAmount, rawCodeStatus, response);
    }

    /**
     * 必填与格式校验。
     *
     * <p>face-pay-server 已校验过一遍，但 ticket-server 的 {@code /ci/app/requestUpdateCardData}
     * （{@code TicketSupplementController:73}）是**零校验直调**、集群内可被直接打到，
     * 所以这里 MUST 自校验，NEVER 依赖接入层。</p>
     *
     * <p>审查项 X002：原实现只判 {@code hasText}。于是一个 8 位的 {@code optDate}
     * 会让 {@code isWithinFreeWindow} 的 {@code length() < 14} 静默返回 false、
     * 整笔请求走进「超窗」分支（表面像业务拒绝、实则是脏入参）；而畸形的
     * {@code updateStationCode} 会一路进 {@code QRCODE_TXN_DETAIL} 与闸机报文。
     * <b>MUST 连长度与字符集一起判，NEVER 只判非空。</b></p>
     *
     * @return null 表示通过，否则为可直接回给 BOM 的原因
     */
    private String validateRequired(String cardId, String adviceOpt, String updateStationCode, String optDate) {
        if (!StringUtils.hasText(cardId) || !StringUtils.hasText(adviceOpt)
                || !StringUtils.hasText(updateStationCode) || !StringUtils.hasText(optDate)) {
            return "必填参数缺失: cardId/adviceOpt/updateStationCode/optDate";
        }
        if (optDate.length() != OPT_DATE_LENGTH || !isAllDigits(optDate)) {
            return "optDate 必须是 " + OPT_DATE_LENGTH + " 位数字(yyyyMMddHHmmss): " + optDate;
        }
        if (updateStationCode.length() != STATION_CODE_LENGTH || !isAlphaNumeric(updateStationCode)) {
            return "updateStationCode 必须是 " + STATION_CODE_LENGTH + " 位字母或数字: " + updateStationCode;
        }
        return null;
    }

    private boolean isAllDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private boolean isAlphaNumeric(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isLetterOrDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 建议操作 → 闸机交易方向。
     *
     * <p>方向判据全在 {@link AdviceOptEnum}：{@code isSupplementEntry()} 是 {@code 018 / 020}、
     * {@code isSupplementExit()} 是 {@code 005 / 006}。<b>NEVER 在这里写码值字面量</b> ——
     * 020 的方向 2026-09-15 从出站反转成进站，靠的就是改枚举那一处、本方法一行不动。
     *
     * @return null 表示不支持的操作类型（含 {@code 000} 无需操作与未登记取值），由调用方拒绝整笔
     */
    private String resolveTrxType(String adviceOpt) {
        AdviceOptEnum opt = AdviceOptEnum.fromCode(adviceOpt);
        if (opt == null) {
            return null;
        }
        if (opt.isSupplementEntry()) {
            return TrxTypeCodeEnum.ENTRY.getCode();
        }
        if (opt.isSupplementExit()) {
            return TrxTypeCodeEnum.EXIT.getCode();
        }
        return null;
    }

    /**
     * 006 付费更新的票价重算与一致性比对。
     *
     * @return null 表示成功（金额已写进 {@code gateRequest}），否则为可直接返回的失败响应
     */
    private RequestCardDataUpdateRespDTO fillPaidUpdateAmount(
            RequestCardDataUpdateReqDTO request, RequestCardDataUpdateRespDTO response,
            NotifyVerifyResultReqDTO gateRequest, QRCodeStatus currentStatus,
            String updateStationCode, String requestTransAmount, String cardId) {

        String entryStation = SupplementCodec.defaultString(
                currentStatus.getGateInStation(), stateRules.unknownStationCode());
        if (!updateStationCode.equals(currentStatus.getLastTxnStation())) {
            log.warn("IF5A-03 付费更新的出站站与 IF5A-01 报价基准不一致, cardId={}, 报价基准lastTxn={},"
                            + " 本次updateStation={}",
                    cardId, currentStatus.getLastTxnStation(), updateStationCode);
        }

        SupplementFareQuery.FareResult fare = fareQuery.query(entryStation, updateStationCode, "IF5A-03");
        if (!fare.isOk()) {
            // 审查项 M004：「para-server 答了但没票价」与「连不上」MUST 用不同返回码。
            // 前者重试无意义，后者可重试。NEVER 一起压成 INVALID_PARAM(8001)。
            switch (fare.outcome()) {
                case RpcOutcome.Unreachable unreachable -> {
                    log.error("IF5A-03 票价查询不可达, cardId={}", cardId, unreachable.cause());
                    response.setRetCode(TicketErrorCodeEnum.ACC_COMM_ERROR.getCode());
                    response.setRetMsg("票价服务暂不可用，请稍后重试");
                }
                case RpcOutcome.BizRejected rejected -> {
                    log.warn("IF5A-03 票价查询被拒, cardId={}, retCode={}, retMsg={}",
                            cardId, rejected.retCode(), rejected.retMsg());
                    response.setRetCode(TicketErrorCodeEnum.NO_DATA.getCode());
                    response.setRetMsg("票价查询失败，请前往车站服务台办理");
                }
                case RpcOutcome.Ok ok -> throw new IllegalStateException("Ok 分支不可达: " + ok);
            }
            return response;
        }

        String ticketPrice = fare.ticketPrice();
        // 审查项 C005：金额 MUST 用 BigDecimal.compareTo 比。原实现用 String.equals，
        // "2.00" 与 "2" 会被判成不一致、每笔都刷一条假告警，真正的金额偏差反而被噪声埋掉。
        if (!isSameAmount(ticketPrice, requestTransAmount)) {
            log.warn("IF5A-03 BOM 上送金额与 ITP 重算不一致，以 ITP 为准, cardId={}, bom={}, itp={}",
                    cardId, requestTransAmount, ticketPrice);
        }
        gateRequest.setTrxAmount(ticketPrice);
        log.info("IF5A-03 付费更新票价已重算, cardId={}, entry={}, exit={}, itp={}, operaterId={}",
                cardId, entryStation, updateStationCode, ticketPrice, request.getOperaterId());
        return null;
    }

    private boolean isSameAmount(String left, String right) {
        try {
            return new BigDecimal(left).compareTo(new BigDecimal(right)) == 0;
        } catch (NumberFormatException e) {
            log.warn("金额格式无法解析，退化为字符串比较, left={}, right={}", left, right);
            return left.equals(right);
        }
    }

    /**
     * 声明幂等 → 下发闸机 → 按结果收口。
     *
     * <p>审查项 L001 / L002：原实现在这里做的是「再 select 一次比对快照」，即纯 TOCTOU ——
     * 两条并发的同 {@code cardId} 请求都能通过比对、各自下发一次闸机。现在改为
     * {@link SupplementRequestLedger} 的**唯一索引声明**：同一 {@code (cardId, txnSeq, adviceOpt)}
     * 只有一条能插进去，后来者拿到 {@code 8305} 让 BOM 重新做票卡分析。</p>
     */
    private RequestCardDataUpdateRespDTO dispatchToGate(
            NotifyVerifyResultReqDTO gateRequest, QRCodeStatus currentStatus, String adviceOpt,
            String updateType, String cardId, String requestTransAmount, String rawCodeStatus,
            RequestCardDataUpdateRespDTO response) {

        SupplementRequestLedger.Claim claim = requestLedger.claim(
                cardId, currentStatus.getTxnSeq(), adviceOpt, rawCodeStatus,
                gateRequest.getHandleStationCode(), gateRequest.getHandleDateTime(), gateRequest.getTrxAmount());
        if (!claim.acquired()) {
            log.warn("IF5A-03 同一票卡同一交易序号的补站已在处理或已完成, cardId={}, txnSeq={}, adviceOpt={}",
                    cardId, currentStatus.getTxnSeq(), adviceOpt);
            response.setRetCode(TicketErrorCodeEnum.CARD_STATUS_CHANGED.getCode());
            response.setRetMsg(TicketErrorCodeEnum.CARD_STATUS_CHANGED.getMsg());
            return response;
        }

        // 编排返回非 0000（业务拒绝，结果明确）与抛异常（结果未知）MUST 用不同返回码：
        // 前者 BOM 可直接放弃，后者 GateTicketWriter 的独立事务可能已提交、QRCODE_STATUS 已推进，
        // BOM 直接重试会造成卡内与后台不一致。NEVER 把两者压成 INVALID_PARAM(8001)。
        // 原实现还有一个「gateResponse == null」分支，那是 RPC 时代的产物：响应体现在由本方法
        // 自己 new 出来、恒非 null，已随双向 RPC 环一起删除（ADR-D63）。
        try {
            NotifyVerifyResultRespDTO gateResponse = agmRideStatusService.notifyVerifyResult(gateRequest);
            String gateRetCode = gateResponse.getRetCode();
            String gateRetMsg = gateResponse.getRetMsg();
            log.info("IF5A-03 进程内检票编排结束, cardId={}, adviceOpt={}, retCode={}, retMsg={}",
                    cardId, adviceOpt, gateRetCode, gateRetMsg);

            if (!SupplementCodec.RET_SUCCESS.equals(gateRetCode)) {
                requestLedger.markRejected(claim, gateRetCode, gateRetMsg);
                response.setRetCode(TicketErrorCodeEnum.AGM_RETURN_STATUS_ABNORMAL1.getCode());
                response.setRetMsg("闸机检票失败: " + gateRetCode);
                return response;
            }
            requestLedger.markSuccess(claim);
        } catch (Exception e) {
            // 结果未知：状态可能已推进。MUST 留库证据，NEVER 只打一行 log.error 就返回。
            requestLedger.markUnknown(claim, "检票编排异常: " + e.getClass().getSimpleName());
            log.error("IF5A-03 进程内检票编排异常, cardId={}, adviceOpt={}", cardId, adviceOpt, e);
            response.setRetCode(TicketErrorCodeEnum.GATE_COMM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.GATE_COMM_ERROR.getMsg());
            return response;
        }

        response.setRetCode(SupplementCodec.RET_SUCCESS);
        response.setRetMsg("成功");
        log.info("IF5A-03 票卡更新完成, cardId={}, adviceOpt={}, updateType={}, codeStatus={}, bom={}, itp={}",
                cardId, adviceOpt, updateType, rawCodeStatus, requestTransAmount, gateRequest.getTrxAmount());
        return response;
    }

    private RequestCardDataUpdateRespDTO fillUserLookupFailure(RequestCardDataUpdateRespDTO response,
                                                              SupplementUserLookup.Result lookup,
                                                              String cardId) {
        switch (lookup.outcome()) {
            case RpcOutcome.Unreachable unreachable -> {
                log.error("IF5A-03 查询用户信息不可达, cardId={}", cardId, unreachable.cause());
                response.setRetCode(TicketErrorCodeEnum.ACC_COMM_ERROR.getCode());
                response.setRetMsg("账户服务暂不可用，请稍后重试");
            }
            case RpcOutcome.BizRejected rejected -> {
                log.warn("IF5A-03 查询用户信息被拒, cardId={}, retCode={}, retMsg={}",
                        cardId, rejected.retCode(), rejected.retMsg());
                response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
                response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            }
            case RpcOutcome.Ok ok -> throw new IllegalStateException("Ok 分支不可达: " + ok);
        }
        return response;
    }
}
