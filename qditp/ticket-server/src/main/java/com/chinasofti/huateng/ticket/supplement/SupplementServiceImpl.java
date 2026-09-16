package com.chinasofti.huateng.ticket.supplement;

import com.chinasofti.huateng.model.app.RequestExcessFareReqDTO;
import com.chinasofti.huateng.model.app.RequestExcessFareResult;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 补站域门面实现。
 *
 * <p>本类**只做入参校验 + 委派**，NEVER 在这里写补站业务逻辑：
 * 逻辑归 {@link ExcessFareHandler}（IF8A-04 APP 自助补站）、
 * {@link CardDataAnalyseHandler}（IF5A-01 票卡分析）与
 * {@link CardDataUpdateHandler}（IF5A-03 票卡更新）。
 *
 * <p>原先的 {@code CardDataHandler} 已于 2026-09-14 按「分析 / 执行」拆成后两个类（审查项 U006），
 * 拆出的 {@code SupplementStateRules} / {@code SupplementFareQuery} /
 * {@code SupplementGateRequestAssembler} / {@code SupplementRequestLedger} / {@code SupplementCodec}
 * 全部是**包级可见**的协作者。<b>包外仍只准注入本门面，NEVER 直接注入任何处理器。</b>
 *
 * <p>三个方法都**没有 @Transactional**，这是刻意的：三条链路都要调远端
 * （补站要 para / gate-txn-pay，票卡分析要 para / account，票卡更新要 account / para / fep-dev），
 * 事务包住即把行锁与 Druid 连接持有到 RPC 返回为止，见 AGENTS.md §5.2。
 */
@Service
public class SupplementServiceImpl implements SupplementService {

    private final ExcessFareHandler excessFareHandler;
    private final CardDataAnalyseHandler cardDataAnalyseHandler;
    private final CardDataUpdateHandler cardDataUpdateHandler;

    public SupplementServiceImpl(ExcessFareHandler excessFareHandler,
                                 CardDataAnalyseHandler cardDataAnalyseHandler,
                                 CardDataUpdateHandler cardDataUpdateHandler) {
        this.excessFareHandler = excessFareHandler;
        this.cardDataAnalyseHandler = cardDataAnalyseHandler;
        this.cardDataUpdateHandler = cardDataUpdateHandler;
    }

    @Override
    public RequestExcessFareResult requestExcessFare(RequestExcessFareReqDTO request) {
        RequestExcessFareResult response = new RequestExcessFareResult();
        if (request == null || !StringUtils.hasText(request.getCardId())
                || !StringUtils.hasText(request.getUpgradeAreaType())
                || !StringUtils.hasText(request.getUpgradeStationCode())
                || !StringUtils.hasText(request.getUpgradeDateTime())) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("cardId/upgradeAreaType/upgradeStationCode/upgradeDateTime不能为空");
            return response;
        }
        excessFareHandler.handleExcessFare(request, response);
        return response;
    }

    @Override
    public RequestCardDataAnalyseRespDTO requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request) {
        return cardDataAnalyseHandler.handle(request, new RequestCardDataAnalyseRespDTO());
    }

    @Override
    public RequestCardDataUpdateRespDTO requestCardDataUpdate(RequestCardDataUpdateReqDTO request) {
        return cardDataUpdateHandler.handle(request, new RequestCardDataUpdateRespDTO());
    }
}
