package com.chinasofti.huateng.ticket.service.impl;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.mapper.QRCodeStatusMapper;
import com.chinasofti.huateng.ticket.service.AgmRideStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AGM 乘车状态服务实现。
 *
 * <p>承接 fep-dev-server 转发的闸机侧请求，委托给各 Handler 执行具体业务逻辑：
 * <ul>
 *   <li>{@link GateTicketHandler} - IF1A-01 闸机检票处理</li>
 *   <li>{@link CardDataHandler} - IF5A-01/03 票卡分析/更新处理</li>
 * </ul>
 */
@Service
public class AgmRideStatusServiceImpl implements AgmRideStatusService {

    private static final Logger log = LoggerFactory.getLogger(AgmRideStatusServiceImpl.class);
    private static final String RET_SUCCESS = "0000";

    @Autowired
    private QRCodeStatusMapper qrCodeStatusMapper;

    @Autowired
    private GateTicketHandler gateTicketHandler;

    @Autowired
    private CardDataHandler cardDataHandler;

    /**
     * IF1A-01 闸机检票通知。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotifyVerifyResultRespDTO notifyVerifyResult(NotifyVerifyResultReqDTO request) {
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();
        gateTicketHandler.handleNotifyVerifyResult(request, request.getCardId(), response);
        return response;
    }

    /**
     * 查询票卡当前状态。
     */
    @Override
    public QueryStatusRespDTO queryQrCodeStatus(QueryStatusReqDTO request) {
        QueryStatusRespDTO response = new QueryStatusRespDTO();

        QRCodeStatus qrCodeStatus = qrCodeStatusMapper.selectByCardId(request.getCardId());
        if (qrCodeStatus == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            response.setCardId(request.getCardId());
            response.setThirdUserId(request.getThirdUserId());
            return response;
        }

        response.setThirdUserId(request.getThirdUserId());
        response.setGateInTime(qrCodeStatus.getGateInTime());
        response.setGateInStation(qrCodeStatus.getGateInStation());
        response.setStatus(qrCodeStatus.getCodeStatus());
        response.setCardId(qrCodeStatus.getCardId());
        response.setLastTxnTime(qrCodeStatus.getLastTxnTime());
        response.setLastTxnStation(qrCodeStatus.getLastTxnStation());
        response.setTxnSeq(qrCodeStatus.getTxnSeq());
        response.setRetCode(RET_SUCCESS);
        response.setRetMsg("成功");
        return response;
    }

    /**
     * IF5A-01 请求票卡分析。
     */
    @Override
    public RequestCardDataAnalyseRespDTO requestCardDataAnalyse(RequestCardDataAnalyseReqDTO request) {
        RequestCardDataAnalyseRespDTO response = new RequestCardDataAnalyseRespDTO();
        return cardDataHandler.handleCardDataAnalyse(request, response);
    }

    /**
     * IF5A-03 请求票卡更新。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RequestCardDataUpdateRespDTO requestCardDataUpdate(RequestCardDataUpdateReqDTO request) {
        RequestCardDataUpdateRespDTO response = new RequestCardDataUpdateRespDTO();
        return cardDataHandler.handleCardDataUpdate(request, response);
    }
}
