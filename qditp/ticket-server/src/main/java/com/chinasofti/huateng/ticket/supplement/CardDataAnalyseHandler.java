package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.alipaytrip.AlipayUserInfoDTO;
import com.chinasofti.huateng.model.app.QueryUserInfoResult;
import com.chinasofti.huateng.model.app.RequestStationLineInfoResult;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.enums.AdviceOptEnum;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.rpc.account.AccountClient;
import com.chinasofti.huateng.rpc.alipay.account.AlipayAccountClient;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.gate.QRCodeStatusStore;
import com.chinasofti.huateng.ticket.station.StationLineResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * IF5A-01 票卡分析：按票卡状态给 BOM 返回建议操作与付费更新的**预估**金额。
 *
 * <p>本类连同 {@link CardDataUpdateHandler} 是 2026-09-14 把原 714 行的 {@code CardDataHandler}
 * 按「分析 / 执行」拆开的结果（审查项 U006）。<b>包外 NEVER 直接注入本类</b>，
 * 唯一入口是 {@link SupplementService}。</p>
 *
 * <p><b>本方法 NEVER 加 {@code @Transactional}。</b>方法内要调 para-server 查线路、
 * account-server 查用户，且**一条写 SQL 都没有**。事务包住它只会让 Druid 连接
 * 被持有到全部 RPC 返回为止，没有任何一致性收益（AGENTS.md §5.2）。</p>
 */
@Component
class CardDataAnalyseHandler {

    private static final Logger log = LoggerFactory.getLogger(CardDataAnalyseHandler.class);

    /**
     * QRCODE_STATUS 只读访问。**NEVER 改回直接注 {@code QRCodeStatusMapper}** ——
     * 该表的写权归 gate 包，本包只准读（见 {@link QRCodeStatusStore} 类注释）。
     */
    @Autowired
    private QRCodeStatusStore qrCodeStatusStore;

    @Autowired
    private StationLineResolver stationLineResolver;

    @Autowired
    private AccountClient accountClient;

    @Autowired
    private AlipayAccountClient alipayAccountClient;

    @Autowired
    private SupplementStateRules stateRules;

    @Autowired
    private SupplementFareQuery fareQuery;

    RequestCardDataAnalyseRespDTO handle(RequestCardDataAnalyseReqDTO request,
                                        RequestCardDataAnalyseRespDTO response) {
        String cardId = request.getCardId();

        QRCodeStatus status = qrCodeStatusStore.findByCardId(cardId);
        if (status == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return response;
        }

        QRCodeStatusEnum codeStatus = stateRules.resolveCodeStatus(status.getCodeStatus());
        if (codeStatus == null) {
            log.warn("IF5A-01 票卡状态不是已登记取值，拒绝, cardId={}, codeStatus={}", cardId, status.getCodeStatus());
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("票卡状态不是已登记取值: " + status.getCodeStatus());
            return response;
        }

        String unknownStation = stateRules.unknownStationCode();
        String gateInStation = SupplementCodec.defaultString(status.getGateInStation(), unknownStation);
        String lastTxnStation = SupplementCodec.defaultString(status.getLastTxnStation(), unknownStation);

        // 审查项 C003：此前还查了一次 gateInStation 的线路信息，结果赋给局部变量后**从未被读取**，
        // 纯粹白费一次 para-server 往返。NEVER 加回来。
        String lastLineCode = queryLineCode(lastTxnStation, cardId);

        String updateType = SupplementCodec.defaultString(
                request.getUpdateType(), SupplementCodec.UPDATE_TYPE_FREE_AREA);
        List<String> adviceOpt = stateRules.resolveAdviceOpt(
                codeStatus, gateInStation, lastTxnStation, updateType, status.getGateInTime(), cardId);
        response.setAdviceOpt(adviceOpt);

        String transAmount = SupplementCodec.AMOUNT_ZERO;
        if (adviceOpt.contains(AdviceOptEnum.PAID_UPDATE.getCode())) {
            transAmount = estimatePayAmount(gateInStation, lastTxnStation, cardId);
        }
        response.setTransAmount(transAmount);

        // NEVER 在这之后用局部变量回写 msisdn / cardIssueDate——queryUserInfo 内部已经 set 好，
        // 曾因此把 account-server 查到的手机号与发卡日期无条件覆盖成空串（2026-09-10 修复）。
        response.setMsisdn(SupplementCodec.defaultString(request.getMsisdn(), ""));
        response.setCardIssueDate("");
        try {
            queryUserInfo(cardId, request.getProviderId(), response);
        } catch (Exception e) {
            log.warn("IF5A-01 查询用户信息失败, cardId={}", cardId, e);
        }

        response.setRetCode(SupplementCodec.RET_SUCCESS);
        response.setRetMsg("成功");
        response.setProviderId(SupplementCodec.defaultString(
                request.getProviderId(), SupplementCodec.PROVIDER_ID_DEFAULT));
        response.setCardId(cardId);
        response.setCardStatus(codeStatus.getCode());
        response.setLastLineCode(lastLineCode);
        response.setLastStationCode(lastTxnStation);
        response.setLastUpdateDate(SupplementCodec.defaultString(status.getLastTxnTime(), ""));
        response.setLastTransAmout(status.getTrxAmount() == null
                ? SupplementCodec.AMOUNT_ZERO : String.valueOf(status.getTrxAmount()));
        response.setLastTicketTransSeq(SupplementCodec.defaultString(
                status.getTxnSeq(), SupplementCodec.AMOUNT_ZERO));
        response.setManagerCode(SupplementCodec.defaultString(request.getManagerCode(), ""));

        return response;
    }

    /**
     * 查询车站所属线路号，查不到返回空串。
     *
     * <p>审查项 M005：原实现<b>既不 catch 也不判 {@code retCode}</b>。para-server 一抖，
     * 整笔 IF5A-01 就退化成全局异常处理器的 UUID {@code retCode}；而 {@code lastLineCode}
     * 只是回显字段、拿不到完全不影响建议操作与报价。<b>NEVER 让回显字段的失败打断主流程。</b></p>
     */
    private String queryLineCode(String stationCode, String cardId) {
        if (stateRules.isUnknownStation(stationCode)) {
            return "";
        }
        RequestStationLineInfoResult lineResult = stationLineResolver.resolveLineInfo(stationCode);
        if (!stationLineResolver.isUsable(lineResult)) {
            log.warn("IF5A-01 线路信息查询未成功，lastLineCode 置空, station={}, retCode={}, cardId={}",
                    stationCode, lineResult == null ? "null" : lineResult.getRetCode(), cardId);
            return "";
        }
        return SupplementCodec.defaultString(lineResult.getLineCode(), "");
    }

    /**
     * 付费更新（{@code 006}）的**预估**报价。
     *
     * <p>与 IF5A-03 的<b>基准站不同</b>：分析阶段 BOM 还没选出站站，只能用 {@code lastTxnStation}
     * 预估；执行阶段用真实的 {@code updateStationCode} 重算。因此本方法返回的是**参考价**，
     * 最终以 IF5A-03 的重算值入账。查不到票价时返回 {@code "0"}（保持 IF5A-01 恒能成功返回），
     * 而 IF5A-03 查不到会直接拒绝 —— 两者行为差异是有意的。</p>
     *
     * <p>进站站未知的情况已由 {@code SupplementStateRules} 在建议阶段拦掉（审查项 M007），
     * 走不到这里；这里只需处理「上次交易站未知」。</p>
     */
    private String estimatePayAmount(String gateInStation, String lastTxnStation, String cardId) {
        if (stateRules.isUnknownStation(lastTxnStation)) {
            log.warn("IF5A-01 上次交易站未知，付费更新预估按 0 元返回，实扣以 IF5A-03 重算为准, cardId={}", cardId);
            return SupplementCodec.AMOUNT_ZERO;
        }
        return fareQuery.query(gateInStation, lastTxnStation, "IF5A-01").priceOrZero();
    }

    /**
     * 查询用户信息，把 msisdn / cardIssueDate 写进 response。
     *
     * <p>{@code queryCardTypeByCardId} 的返回里**已经带 msisdn 与 regTms**，因此非支付宝发行方
     * 不再二次调 {@code queryUserInfo} —— 那一次 RPC 拿不到额外信息，只是白白多一次网络往返。
     * 支付宝发行方（{@code providerId=07}）的手机号在 alipay-account-server，仍需单独查。</p>
     */
    private void queryUserInfo(String cardId, String providerId, RequestCardDataAnalyseRespDTO response) {
        QueryUserInfoResult cardTypeResult = accountClient.queryCardTypeByCardId(cardId);
        if (cardTypeResult == null || !SupplementCodec.RET_SUCCESS.equals(cardTypeResult.getRetCode())
                || !StringUtils.hasText(cardTypeResult.getThirdUserId())) {
            log.warn("IF5A-01 查询用户卡类型失败, cardId={}", cardId);
            return;
        }

        if (SupplementCodec.PROVIDER_ID_ALIPAY.equals(providerId)) {
            AlipayUserInfoDTO alipayUser = alipayAccountClient.selectByThirdUserId(cardTypeResult.getThirdUserId());
            if (alipayUser != null && StringUtils.hasText(alipayUser.getPhone())) {
                response.setMsisdn(alipayUser.getPhone());
            }
            return;
        }

        if (StringUtils.hasText(cardTypeResult.getMsisdn())) {
            response.setMsisdn(cardTypeResult.getMsisdn());
        }
        if (StringUtils.hasText(cardTypeResult.getRegTms())) {
            response.setCardIssueDate(cardTypeResult.getRegTms());
        }
    }
}
