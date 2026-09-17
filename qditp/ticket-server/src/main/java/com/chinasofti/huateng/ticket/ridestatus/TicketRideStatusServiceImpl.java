package com.chinasofti.huateng.ticket.ridestatus;

import com.chinasofti.huateng.model.app.MemberItineraryDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryReqDTO;
import com.chinasofti.huateng.model.app.QueryUserItineraryResult;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusReqDTO;
import com.chinasofti.huateng.model.ticket.RegisterRideStatusRespDTO;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.entity.QRCodeStatus;
import com.chinasofti.huateng.ticket.entity.QRCodeTxnDetail;
import com.chinasofti.huateng.ticket.gate.QRCodeStatusStore;
import com.chinasofti.huateng.ticket.mapper.QRCodeTxnDetailMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/** APP 侧乘车码状态服务实现。 */
@Service
public class TicketRideStatusServiceImpl implements TicketRideStatusService {

    private static final String INITIAL_TIME = "00000000000000";
    private static final String INITIAL_TXN_SEQ = "0";

    /** 复位时的交易金额。 */
    private static final Long INITIAL_TRX_AMOUNT = 0L;

    private final String defaultChannel;
    private final String defaultCodeStatus;
    private final String defaultGateStatus;
    private final String defaultLastTxnStation;
    private final String defaultGateInStation;

    /** QRCODE_STATUS 的唯一访问口。 */
    private final QRCodeStatusStore qrCodeStatusStore;
    private final QRCodeTxnDetailMapper qrCodeTxnDetailMapper;
    private final MemberItineraryAssembler memberItineraryAssembler;

    /** 五个默认值走构造器注入而非字段注入，使本类可脱离 Spring 直接单测。 */
    public TicketRideStatusServiceImpl(QRCodeStatusStore qrCodeStatusStore,
                                       QRCodeTxnDetailMapper qrCodeTxnDetailMapper,
                                       MemberItineraryAssembler memberItineraryAssembler,
                                       @Value("${ticket.default-channel:01}") String defaultChannel,
                                       @Value("${ticket.default-code-status:03}") String defaultCodeStatus,
                                       @Value("${ticket.default-gate-status:00}") String defaultGateStatus,
                                       @Value("${ticket.default-last-txn-station:FFFF}") String defaultLastTxnStation,
                                       @Value("${ticket.default-gate-in-station:FFFF}") String defaultGateInStation) {
        this.qrCodeStatusStore = qrCodeStatusStore;
        this.qrCodeTxnDetailMapper = qrCodeTxnDetailMapper;
        this.memberItineraryAssembler = memberItineraryAssembler;
        this.defaultChannel = defaultChannel;
        this.defaultCodeStatus = defaultCodeStatus;
        this.defaultGateStatus = defaultGateStatus;
        this.defaultLastTxnStation = defaultLastTxnStation;
        this.defaultGateInStation = defaultGateInStation;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterRideStatusRespDTO registerRideStatus(RegisterRideStatusReqDTO request) {
        RegisterRideStatusRespDTO response = new RegisterRideStatusRespDTO();
        if (request == null || !StringUtils.hasText(request.getCardId())) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("cardId不能为空");
            return response;
        }

        LocalDateTime now = LocalDateTime.now();
        QRCodeStatus qrCodeStatus = qrCodeStatusStore.findByCardId(request.getCardId());
        if (qrCodeStatus == null) {
            qrCodeStatus = new QRCodeStatus();
            qrCodeStatus.setCardId(request.getCardId());
            qrCodeStatus.setCreateTime(now);
            qrCodeStatus.setUseCount(0);
        }

        qrCodeStatus.setChannel(StringUtils.hasText(request.getChannel())
                ? request.getChannel() : defaultChannel);
        qrCodeStatus.setCodeStatus(defaultCodeStatus);
        qrCodeStatus.setLastTxnTime(INITIAL_TIME);
        qrCodeStatus.setLastTxnStation(defaultLastTxnStation);
        qrCodeStatus.setTxnSeq(INITIAL_TXN_SEQ);
        qrCodeStatus.setGateStatus(defaultGateStatus);
        qrCodeStatus.setGateInStation(defaultGateInStation);
        qrCodeStatus.setGateInTime(INITIAL_TIME);
        qrCodeStatus.setTrxAmount(INITIAL_TRX_AMOUNT);
        qrCodeStatus.setUpdateTime(now);
        qrCodeStatusStore.upsert(qrCodeStatus);

        response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
        response.setCardId(qrCodeStatus.getCardId());
        response.setItpUserId(request.getThirdUserId());
        response.setCardStatus(qrCodeStatus.getCodeStatus());
        return response;
    }

    @Override
    public QueryUserItineraryResult queryUserItinerary(QueryUserItineraryReqDTO request) {
        QueryUserItineraryResult response = new QueryUserItineraryResult();
        if (request == null || !StringUtils.hasText(request.getCardNum())) {
            response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
            response.setRetMsg("cardNum不能为空");
            return response;
        }

        QRCodeStatus currentStatus = qrCodeStatusStore.findByCardId(request.getCardNum());
        if (currentStatus == null) {
            response.setRetCode(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getCode());
            response.setRetMsg(TicketErrorCodeEnum.QR_CODE_NOT_FOUND.getMsg());
            return response;
        }

        QRCodeTxnDetail latestDetail = qrCodeTxnDetailMapper.selectLatestByCardId(request.getCardNum());
        MemberItineraryDTO itinerary = memberItineraryAssembler.assemble(currentStatus, latestDetail);
        response.setMemberItinerary(itinerary);
        response.setRetCode(TicketErrorCodeEnum.SUCCESS.getCode());
        response.setRetMsg(TicketErrorCodeEnum.SUCCESS.getMsg());
        return response;
    }
}
