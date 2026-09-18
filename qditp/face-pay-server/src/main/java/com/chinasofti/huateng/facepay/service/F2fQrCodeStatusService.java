package com.chinasofti.huateng.facepay.service;

import com.alibaba.fastjson2.JSONObject;
import com.chinasofti.huateng.facepay.api.device.bom.BomResponses;
import com.chinasofti.huateng.facepay.api.device.bom.RequestQrCodeStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.rpc.ticket.TicketClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * IF1A-04 票卡状态查询的设备前缀别名，透传 ticket-server。
 *
 * <p>与 {@code fep-dev-server} 的 {@code /itpagm/ci/agm/requestQrCodeStatus} 同一个下游
 * （{@code TicketClient.queryQrCodeStatus} → ticket-server {@code /ci/app/queryQrCodeStatus}），
 * 本类只是给把该报文打到 BOM / TVM 前缀的设备补一条落点。
 *
 * <p>两条口径 NEVER 改：
 * <ol>
 *   <li>{@code thirdUserId} 原样填设备送来的 {@code itpUserId}、**不做十六进制换算**：
 *       {@code AgmRideStatusServiceImpl.queryQrCodeStatus} 只按 {@code cardId} 查
 *       {@code QRCODE_STATUS}（{@code findByCardId}），{@code thirdUserId} 仅被原样回显，
 *       换算没有任何收益。**一旦下游改成按 {@code thirdUserId} 参与查询，这里 MUST 同步补换算。**</li>
 *   <li>本类刻意不落库、不带 {@code @Transactional}：这是只读查询，与本模块整批服务类一致。</li>
 * </ol>
 */
@Service
public class F2fQrCodeStatusService {

    private static final Logger log = LoggerFactory.getLogger(F2fQrCodeStatusService.class);

    /** 下游成功码。 */
    private static final String REMOTE_SUCCESS = "0000";

    private final TicketClient ticketClient;

    public F2fQrCodeStatusService(TicketClient ticketClient) {
        this.ticketClient = ticketClient;
    }

    /** IF1A-04 查询票卡状态。 */
    public JSONObject requestQrCodeStatus(RequestQrCodeStatusReqDTO request) {
        QueryStatusReqDTO remote = new QueryStatusReqDTO();
        remote.setCardId(request.getCardId());
        remote.setThirdUserId(request.getItpUserId());

        QueryStatusRespDTO response;
        try {
            response = ticketClient.queryQrCodeStatus(remote);
        } catch (RuntimeException e) {
            log.error("票卡状态查询 调 ticket-server 异常, cardId={}", request.getCardId(), e);
            return BomResponses.qrCodeStatusFail(BomResponses.CODE_FAIL, "票卡状态查询失败:下游异常");
        }
        if (response == null) {
            log.warn("票卡状态查询 下游无应答, cardId={}", request.getCardId());
            return BomResponses.qrCodeStatusFail(BomResponses.CODE_FAIL, "票卡状态查询失败");
        }
        if (!REMOTE_SUCCESS.equals(response.getRetCode())) {
            log.warn("票卡状态查询 下游返回失败, cardId={}, retCode={}, retMsg={}",
                    request.getCardId(), response.getRetCode(), response.getRetMsg());
            return BomResponses.qrCodeStatusFail(response.getRetCode(), response.getRetMsg());
        }
        return BomResponses.qrCodeStatus(request.getItpUserId(), response.getCardId(),
                response.getStatus(), response.getLastTxnTime());
    }
}
