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

/**
 * APP 侧乘车码状态服务实现。
 *
 * <p>只做两件事：开卡复位写 {@code QRCODE_STATUS}、IF8A-29 读行程。
 * 展示层字段拼装委托 {@link MemberItineraryAssembler}，本类不碰 DTO 默认值与站名翻译。
 *
 * <p>AGM 侧接口请使用 {@code gate/AgmRideStatusService}。**这里刻意不用 {@code @link}** ——
 * 见 {@link TicketRideStatusService} 类注释里的同款说明。
 */
@Service
public class TicketRideStatusServiceImpl implements TicketRideStatusService {

    private static final String INITIAL_TIME = "00000000000000";
    private static final String INITIAL_TXN_SEQ = "0";

    /**
     * 复位时的交易金额。**MUST 与上面两个初始值成组设置**：
     * 已存在的行走 {@code selectByCardId} 读出旧快照，而 {@code upsert} 的 MATCHED 分支带
     * {@code T.TRX_AMOUNT = S.TRX_AMOUNT}，不显式归零就会把上一趟的票价原样写回，
     * 于是 IF5A-01 票卡分析（{@code supplement/CardDataHandler} 的 {@code lastTransAmout}）
     * 在复位后仍返回旧金额，而同一响应里的末次时间 / 流水号已是初始值。
     */
    private static final Long INITIAL_TRX_AMOUNT = 0L;

    private final String defaultChannel;
    private final String defaultCodeStatus;
    private final String defaultGateStatus;
    private final String defaultLastTxnStation;
    private final String defaultGateInStation;

    /**
     * QRCODE_STATUS 的唯一访问口。**NEVER 改回直接注 {@code QRCodeStatusMapper}** ——
     * 该表的 owner 是 gate 包，本类的开卡复位是**唯一被允许的包外写入**
     * （无条件覆盖分支，语义见 {@link TicketRideStatusService#registerRideStatus}）。
     */
    private final QRCodeStatusStore qrCodeStatusStore;
    private final QRCodeTxnDetailMapper qrCodeTxnDetailMapper;
    private final MemberItineraryAssembler memberItineraryAssembler;

    /**
     * 五个默认值走构造器注入而非字段注入，使本类可脱离 Spring 直接单测。
     *
     * <p>{@code ticket.default-code-status} 的兜底值 **MUST 与
     * {@code application.properties} 保持一致（当前 03 初始化）**，两处不一致时
     * NEVER 只改一边——`gate/GateTicketHandler` 有同名配置项、同款陷阱。
     */
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
