package com.chinasofti.huateng.ticket.gate;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** AGM 乘车状态服务实现。 */
@Service
public class AgmRideStatusServiceImpl implements AgmRideStatusService {

    private static final Logger log = LoggerFactory.getLogger(AgmRideStatusServiceImpl.class);
    private static final String RET_SUCCESS = "0000";

    /** QRCODE_STATUS 的唯一访问口。 */
    @Autowired
    private QRCodeStatusStore qrCodeStatusStore;

    @Autowired
    private GateTicketHandler gateTicketHandler;

    /** IF1A-01 闸机检票通知。 */
    @Override
    public NotifyVerifyResultRespDTO notifyVerifyResult(NotifyVerifyResultReqDTO request) {
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();
        gateTicketHandler.handleNotifyVerifyResult(request, request.getCardId(), response);
        return response;
    }

    /** 查询票卡当前状态。 */
    @Override
    public QueryStatusRespDTO queryQrCodeStatus(QueryStatusReqDTO request) {
        QueryStatusRespDTO response = new QueryStatusRespDTO();

        QRCodeStatus qrCodeStatus = qrCodeStatusStore.findByCardId(request.getCardId());
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

}
