package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.enums.QRCodeStatusEnum;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.gate.AgmRideStatusService;
import com.chinasofti.huateng.ticket.gate.QRCodeStatusStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * IF8A-04 自助补站处理。
 *
 * <p>负责处理用户自助补站请求，包括：
 * <ul>
 *   <li>状态机判断（允许的补站类型）</li>
 *   <li>票价计算（补出站）</li>
 *   <li>进程内调用 {@code gate} 的检票编排 完成补站（2026-09-14 前是 RPC 打 fep-dev-server）</li>
 * </ul>
 *
 * <p>入口是 {@link SupplementService}，<b>包外 NEVER 直接注入本类</b>。
 * 票价查询、闸机报文骨架、{@code defaultString} / hex 编码已于 2026-09-14 收口到
 * {@link SupplementFareQuery} / {@link SupplementGateRequestAssembler} / {@link SupplementCodec}
 * （审查项 U001 / U002 / U004），<b>NEVER 在本类里再复制一份</b>。</p>
 */
@Component
class ExcessFareHandler {

    private static final Logger log = LoggerFactory.getLogger(ExcessFareHandler.class);

    /** 补出站，需要算票价。 */
    private static final String UPGRADE_TYPE_EXIT = "02";

    /**
     * QRCODE_STATUS 只读访问。**NEVER 改回直接注 {@code QRCodeStatusMapper}** ——
     * 该表的写权归 gate 包，本包只准读（见 {@link QRCodeStatusStore} 类注释）。
     */
    @Autowired
    private QRCodeStatusStore qrCodeStatusStore;

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

    /**
     * 处理自助补站请求。
     *
     * @param request  补站请求
     * @param response 响应对象
     */
    void handleExcessFare(RequestExcessFareReqDTO request, RequestExcessFareResult response) {
        String cardId = request.getCardId();
        String upgradeAreaType = request.getUpgradeAreaType();
        String upgradeStationCode = request.getUpgradeStationCode();
        String upgradeDateTime = request.getUpgradeDateTime();
        String unknownStation = stateRules.unknownStationCode();

        QRCodeStatus currentStatus = qrCodeStatusStore.findByCardId(cardId);
        if (currentStatus == null) {
            currentStatus = new QRCodeStatus();
            currentStatus.setCardId(cardId);
            currentStatus.setCreateTime(LocalDateTime.now());
            currentStatus.setUseCount(0);
            currentStatus.setGateInStation(unknownStation);
            currentStatus.setLastTxnStation(unknownStation);
            currentStatus.setCodeStatus(QRCodeStatusEnum.SJT_ISSUE.getCode());
        }

        String codeStatus = SupplementCodec.defaultString(
                currentStatus.getCodeStatus(), QRCodeStatusEnum.SJT_ISSUE.getCode());
        QRCodeStatusEnum statusEnum = QRCodeStatusEnum.fromCode(codeStatus);
        if (statusEnum == null) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("当前票卡状态不支持补站，codeStatus=" + codeStatus);
            return;
        }

        AllowedTypesResult allowedResult = resolveAllowedTypes(statusEnum, codeStatus);
        // MUST 用集合的相等语义，NEVER 退回 String.contains 做子串匹配：
        // "02,03,04".contains("0") 与 "01".contains("1") 都为 true，","、"2,0" 同样能过，
        // 非法的 upgradeAreaType 会被原样组装进 trxType 发给闸机。
        if (!allowedResult.allowedTypes.contains(upgradeAreaType)) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg(allowedResult.friendlyMsg);
            return;
        }

        NotifyVerifyResultReqDTO bizData = gateRequestAssembler.newBaseRequest(
                currentStatus, cardId, request.getThirdUserId(), upgradeAreaType,
                upgradeDateTime, upgradeStationCode, request.getCardType(), "");
        bizData.setDeviceId(upgradeStationCode + "36" + "01");
        bizData.setExcessFareType(upgradeAreaType);

        if (UPGRADE_TYPE_EXIT.equals(upgradeAreaType)) {
            String entryStation = SupplementCodec.defaultString(
                    currentStatus.getGateInStation(), unknownStation);
            SupplementFareQuery.FareResult fare = fareQuery.query(entryStation, upgradeStationCode, "IF8A-04");
            if (!fare.isOk()) {
                response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
                response.setRetMsg("票价查询失败，请稍后重试或前往车站服务台办理");
                return;
            }
            bizData.setTrxAmount(fare.ticketPrice());
        }

        // 进程内直调检票编排。原实现还有一个「gateResponse == null 即 GATE_COMM_ERROR」分支，
        // 那是 RPC 时代的产物：响应体现在由本方法自己 new 出来，恒非 null，该分支已随环一起删除。
        // catch 保留但语义变了：现在只可能是本进程内的落库 / 状态推进异常，MUST 仍按「结果未知」
        // 回给 BOM —— GateTicketWriter 有自己的事务，它提交后本方法再抛异常时状态其实已推进。
        try {
            NotifyVerifyResultRespDTO gateResponse = agmRideStatusService.notifyVerifyResult(bizData);
            if (!SupplementCodec.RET_SUCCESS.equals(gateResponse.getRetCode())) {
                response.setRetCode(gateResponse.getRetCode());
                response.setRetMsg(gateResponse.getRetMsg());
                return;
            }
            log.info("IF8A-04 补站进程内完成检票编排, cardId={}, trxType={}, station={}",
                    cardId, upgradeAreaType, upgradeStationCode);
        } catch (Exception e) {
            log.error("IF8A-04 补站检票编排异常, cardId={}, trxType={}", cardId, upgradeAreaType, e);
            response.setRetCode(TicketErrorCodeEnum.GATE_COMM_ERROR.getCode());
            response.setRetMsg(TicketErrorCodeEnum.GATE_COMM_ERROR.getMsg());
            return;
        }

        response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
    }

    /**
     * 根据票卡状态解析允许的补站类型。
     *
     * <p>改这里的允许集合 MUST 同步看齐 {@code QRCodeStatusEnum.ALLOWED} 的 80 / 81 入边，
     * 否则每笔补站都会打一条「迁移不在白名单内」WARN。</p>
     */
    private AllowedTypesResult resolveAllowedTypes(QRCodeStatusEnum statusEnum, String codeStatus) {
        switch (statusEnum) {
            case END_TRIP:
            case SJT_ISSUE:
            case EXIT:
            case EXIT_OVERTIME:
            case SELF_SERVICE_EXIT:
            case UPDATE_FREE:
            case UPDATE_PAY:
                return new AllowedTypesResult(Set.of("01"), "码状态正常，无需更新，请正常刷码");
            case ENTRY:
            case SELF_SERVICE_ENTRY:
                return new AllowedTypesResult(Set.of("02", "03", "04"), "码状态正常，无需更新，请正常刷码");
            case UPDATE_ENTRY:
                return new AllowedTypesResult(Set.of("01"), "码状态正常，无需更新，请正常刷码");
            default:
                return new AllowedTypesResult(Set.of(), "当前票卡状态不支持补站，codeStatus=" + codeStatus);
        }
    }

    /**
     * 允许的补站类型结果。
     *
     * <p>{@code allowedTypes} 用 {@code Set} 而不是逗号分隔的字符串：
     * 字符串配 {@code contains} 是子串匹配，"0" / "1" / "," 这类非法值都能穿过白名单。</p>
     */
    private record AllowedTypesResult(Set<String> allowedTypes, String friendlyMsg) {
    }
}
