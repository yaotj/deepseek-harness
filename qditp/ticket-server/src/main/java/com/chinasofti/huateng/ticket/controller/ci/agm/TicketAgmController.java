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
import com.chinasofti.huateng.ticket.service.AgmRideStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ticket-server AGM 闸机接口控制器。
 *
 * <p>承接来自 fep-dev-server 转发的闸机请求，提供以下能力：
 * <ul>
 *   <li>IF1A-01 闸机检票通知</li>
 *   <li>IF5A-01 票卡分析（BOM 操作辅助）</li>
 *   <li>IF5A-03 票卡更新（BOM 补进站/补出站）</li>
 *   <li>查询票卡当前状态</li>
 * </ul>
 *
 * <p>所有接口统一日志规范：请求前打印入参，响应前打印 retCode，异常时打印堆栈。
 */
@RestController
@RequestMapping("/ci/agm")
public class TicketAgmController {

    private static final Logger log = LoggerFactory.getLogger(TicketAgmController.class);

    private final AgmRideStatusService agmRideStatusService;

    public TicketAgmController(AgmRideStatusService agmRideStatusService) {
        this.agmRideStatusService = agmRideStatusService;
    }

    // ==================== IF1A-01 闸机检票 ====================

    /**
     * IF1A-01 闸机检票通知。
     * 由 fep-dev-server 转发，携带 deviceId 用于链路追踪。
     */
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

    // ==================== 票卡状态查询 ====================

    /**
     * 查询票卡当前状态。
     */
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

    // ==================== IF5A 票卡 BOM 操作 ====================

    /**
     * IF5A-01 请求票卡分析。
     * BOM 终端在补进站/补出站前调用，获取系统建议操作。
     */
    @PostMapping("/requestCardDataAnalyse")
    public RequestCardDataAnalyseRespDTO requestCardDataAnalyse(@RequestBody RequestCardDataAnalyseReqDTO request) {
        log.info("IF5A-01 票卡分析 入参, cardId={}, updateType={}",
                request == null ? null : request.getCardId(),
                request == null ? null : request.getUpdateType());

        if (request == null || !StringUtils.hasText(request.getCardId())) {
            return buildCardDataAnalyseError(TicketErrorCodeEnum.INVALID_PARAM, "cardId不能为空");
        }

        RequestCardDataAnalyseRespDTO response = agmRideStatusService.requestCardDataAnalyse(request);
        log.info("IF5A-01 票卡分析 响应, cardId={}, retCode={}", request.getCardId(), response.getRetCode());
        return response;
    }

    /**
     * IF5A-03 请求票卡更新。
     * BOM 终端执行补进站/补出站操作。
     */
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

        RequestCardDataUpdateRespDTO response = agmRideStatusService.requestCardDataUpdate(request);
        log.info("IF5A-03 票卡更新 响应, cardId={}, retCode={}", request.getCardId(), response.getRetCode());
        return response;
    }

    // ==================== 响应构建工具方法 ====================

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
