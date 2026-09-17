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

/** 补站域门面实现。 */
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
