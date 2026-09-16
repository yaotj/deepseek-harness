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

/**
 * AGM 乘车状态服务实现。
 *
 * <p>承接 fep-dev-server 转发的闸机侧请求：
 * <ul>
 *   <li>{@link GateTicketHandler} - IF1A-01 闸机检票处理（本包自有）</li>
 * </ul>
 *
 */
@Service
public class AgmRideStatusServiceImpl implements AgmRideStatusService {

    private static final Logger log = LoggerFactory.getLogger(AgmRideStatusServiceImpl.class);
    private static final String RET_SUCCESS = "0000";

    /**
     * QRCODE_STATUS 的唯一访问口。**NEVER 改回直接注 {@code QRCodeStatusMapper}** ——
     * 该表的写权归 gate 包（见 {@link QRCodeStatusStore} 类注释）。
     */
    @Autowired
    private QRCodeStatusStore qrCodeStatusStore;

    @Autowired
    private GateTicketHandler gateTicketHandler;


    /**
     * IF1A-01 闸机检票通知。
     *
     * <p><b>本方法 NEVER 加 @Transactional。</b>整条检票链路要调 6 个远端（日票进站校验、
     * 真实卡类型查询、日票出站标记、HCE 回写、支付宝行程推送、日票信息查询），其中 4 个发生在
     * 写库之后；一旦事务包住整个方法，{@code QRCODE_STATUS} 按 {@code CARD_ID} 命中的单行锁
     * 会被持有到全部 RPC 返回为止（每个 10s 超时），同一张卡连续进出站与 AGM 超时重推会串行
     * 堆积，超过 Druid {@code remove-abandoned-timeout=60} 后连接被强杀、提交失败、事务丢弃。
     * 详见 AGENTS.md §5.2 与 {@link GateTicketWriter} 的类注释。</p>
     *
     * <p>事务边界已收窄到 {@link GateTicketWriter#saveTxnAndAdvanceStatus}，只覆盖
     * 「交易明细入库 + 票卡状态推进」两条本地 SQL。</p>
     */
    @Override
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
