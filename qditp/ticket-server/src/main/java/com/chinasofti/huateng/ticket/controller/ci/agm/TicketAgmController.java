package com.chinasofti.huateng.ticket.controller.ci.agm;

import com.chinasofti.huateng.model.ticket.NotifyVerifyResultReqDTO;
import com.chinasofti.huateng.model.ticket.NotifyVerifyResultRespDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusReqDTO;
import com.chinasofti.huateng.model.ticket.QueryStatusRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataAnalyseRespDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateReqDTO;
import com.chinasofti.huateng.model.ticket.RequestCardDataUpdateRespDTO;
import com.chinasofti.huateng.ticket.constant.TicketErrorCodeEnum;
import com.chinasofti.huateng.ticket.gate.AgmRideStatusService;
import com.chinasofti.huateng.ticket.supplement.SupplementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ticket-server AGM 闸机接口控制器。 */
@RestController
@RequestMapping("/ci/agm")
public class TicketAgmController {

    private static final Logger log = LoggerFactory.getLogger(TicketAgmController.class);

    private final AgmRideStatusService agmRideStatusService;
    /** IF5A-01/03 直连补站域门面。 */
    private final SupplementService supplementService;

    public TicketAgmController(AgmRideStatusService agmRideStatusService,
                               SupplementService supplementService) {
        this.agmRideStatusService = agmRideStatusService;
        this.supplementService = supplementService;
    }

    /** IF1A-01 闸机检票通知。 */
    @PostMapping("/notiVerifyResult")
    public NotifyVerifyResultRespDTO notifyVerifyResult(@RequestBody NotifyVerifyResultReqDTO request) {
        String traceId = request == null ? null : request.getDeviceId();
        log.info("IF1A-01 闸机检票通知 入参, deviceId={}, cardId={}, trxType={}, handleDateTime={}",
                traceId,
                request == null ? null : request.getCardId(),
                request == null ? null : request.getTrxType(),
                request == null ? null : request.getHandleDateTime());

        if (request == null) {
            return buildInvalidParamResponse("请求体不能为空");
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return buildInvalidParamResponse("cardId不能为空");
        }
        if (!StringUtils.hasText(request.getHandleDateTime()) || request.getHandleDateTime().length() < 8) {
            return buildInvalidParamResponse("handleDateTime不能为空且长度不能小于8");
        }
        if (!StringUtils.hasText(request.getTrxType())) {
            return buildInvalidParamResponse("trxType不能为空");
        }

        NotifyVerifyResultRespDTO response = agmRideStatusService.notifyVerifyResult(request);
        log.info("IF1A-01 闸机检票通知 响应, deviceId={}, cardId={}, retCode={}, retMsg={}",
                traceId, request.getCardId(), response.getRetCode(), response.getRetMsg());
        return response;
    }

    /** 查询票卡当前状态。 */
    @PostMapping("/queryCardStatus")
    public QueryStatusRespDTO queryCardStatus(@RequestBody QueryStatusReqDTO request) {
        log.info("查询票卡状态 入参, cardId={}", request == null ? null : request.getCardId());

        if (request == null || !StringUtils.hasText(request.getCardId())) {
            return buildQueryStatusError(TicketErrorCodeEnum.INVALID_PARAM, "cardId不能为空");
        }

        QueryStatusRespDTO response = agmRideStatusService.queryQrCodeStatus(request);
        log.info("查询票卡状态 响应, cardId={}, retCode={}, status={}",
                request.getCardId(), response.getRetCode(), response.getStatus());
        return response;
    }

    /** IF5A-01 请求票卡分析。 */
    @PostMapping("/requestCardDataAnalyse")
    public RequestCardDataAnalyseRespDTO requestCardDataAnalyse(@RequestBody RequestCardDataAnalyseReqDTO request) {
        log.info("IF5A-01 票卡分析 入参, cardId={}, updateType={}",
                request == null ? null : request.getCardId(),
                request == null ? null : request.getUpdateType());

        if (request == null || !StringUtils.hasText(request.getCardId())) {
            return buildCardDataAnalyseError(TicketErrorCodeEnum.INVALID_PARAM, "cardId不能为空");
        }

        RequestCardDataAnalyseRespDTO response = supplementService.requestCardDataAnalyse(request);
        log.info("IF5A-01 票卡分析 响应, cardId={}, retCode={}", request.getCardId(), response.getRetCode());
        return response;
    }

    /** IF5A-03 请求票卡更新。 */
    @PostMapping("/requestCardDataUpdate")
    public RequestCardDataUpdateRespDTO requestCardDataUpdate(@RequestBody RequestCardDataUpdateReqDTO request) {
        log.info("IF5A-03 票卡更新 入参, cardId={}, adviceOpt={}, updateStationCode={}",
                request == null ? null : request.getCardId(),
                request == null ? null : request.getAdviceOpt(),
                request == null ? null : request.getUpdateStationCode());

        if (request == null) {
            return buildCardDataUpdateError(TicketErrorCodeEnum.INVALID_PARAM, "请求体不能为空");
        }
        if (!StringUtils.hasText(request.getCardId())) {
            return buildCardDataUpdateError(TicketErrorCodeEnum.INVALID_PARAM, "cardId不能为空");
        }
        if (!StringUtils.hasText(request.getAdviceOpt())) {
            return buildCardDataUpdateError(TicketErrorCodeEnum.INVALID_PARAM, "adviceOpt不能为空");
        }
        if (!StringUtils.hasText(request.getUpdateStationCode())) {
            return buildCardDataUpdateError(TicketErrorCodeEnum.INVALID_PARAM, "updateStationCode不能为空");
        }
        if (!StringUtils.hasText(request.getOptDate())) {
            return buildCardDataUpdateError(TicketErrorCodeEnum.INVALID_PARAM, "optDate不能为空");
        }

        RequestCardDataUpdateRespDTO response = supplementService.requestCardDataUpdate(request);
        log.info("IF5A-03 票卡更新 响应, cardId={}, retCode={}", request.getCardId(), response.getRetCode());
        return response;
    }

    private NotifyVerifyResultRespDTO buildInvalidParamResponse(String msg) {
        NotifyVerifyResultRespDTO response = new NotifyVerifyResultRespDTO();
        response.setRetCode(TicketErrorCodeEnum.INVALID_PARAM.getCode());
        response.setRetMsg(msg);
        return response;
    }

    private QueryStatusRespDTO buildQueryStatusError(TicketErrorCodeEnum errorCode, String msg) {
        QueryStatusRespDTO response = new QueryStatusRespDTO();
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
        return response;
    }

    private RequestCardDataAnalyseRespDTO buildCardDataAnalyseError(TicketErrorCodeEnum errorCode, String msg) {
        RequestCardDataAnalyseRespDTO response = new RequestCardDataAnalyseRespDTO();
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
        return response;
    }

    private RequestCardDataUpdateRespDTO buildCardDataUpdateError(TicketErrorCodeEnum errorCode, String msg) {
        RequestCardDataUpdateRespDTO response = new RequestCardDataUpdateRespDTO();
        response.setRetCode(errorCode.getCode());
        response.setRetMsg(msg);
        return response;
    }
}
